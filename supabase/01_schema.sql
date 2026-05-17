-- SmartStock+ Supabase schema (idempotent — safe to re-run).
-- Re-run this in the Supabase SQL editor any time the canonical schema changes.
-- Every Kotlin DTO in app/src/main/java/com/example/smartstock/data/cloud/CloudDtos.kt
-- maps 1:1 to columns declared here, so do NOT rename columns without updating
-- both sides.
--
-- Multi-tenant model ("circles"):
--   * Self sign-up creates a brand-new team, signer becomes its Admin.
--   * The create-staff Edge Function creates Staff accounts under an
--     existing team's id (set in raw_user_meta_data.team_id).
--   * RLS isolates every team's data — Admin/Staff in team A cannot see
--     anything from team B.
--
-- Migration safety: this script preserves existing rows by parking any
-- orphaned (team_id = null) data into a single "Default Team" during
-- backfill. If you'd rather start clean, drop the data tables manually
-- before running this.

-- =====================================================================
-- Extensions
-- =====================================================================
create extension if not exists "uuid-ossp";

-- =====================================================================
-- Teams ("circles")
-- =====================================================================
create table if not exists public.teams (
    id          uuid primary key default uuid_generate_v4(),
    name        text not null,
    created_by  uuid references auth.users(id) on delete set null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- =====================================================================
-- Profiles (one row per auth user)
-- =====================================================================
create table if not exists public.profiles (
    id              uuid primary key references auth.users(id) on delete cascade,
    email           text,
    display_name    text,
    role            text not null default 'Staff' check (role in ('Admin','Staff')),
    is_active       boolean not null default true,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

-- Migration: earlier revisions of this schema named the column `name`
-- instead of `display_name`. Rename it in place so the trigger / RPC /
-- app DTO all line up. Idempotent — only runs when the old column is
-- present and the new one isn't.
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'profiles'
          and column_name = 'name'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'profiles'
          and column_name = 'display_name'
    ) then
        alter table public.profiles rename column name to display_name;
    end if;
end$$;

-- Belt-and-suspenders: make sure both columns the trigger expects exist
-- regardless of which prior schema version produced this database.
alter table public.profiles add column if not exists display_name text;
alter table public.profiles add column if not exists updated_at   timestamptz not null default now();

alter table public.profiles
    add column if not exists team_id uuid references public.teams(id) on delete set null;

-- is_admin() must be created AFTER profiles exists. SECURITY DEFINER bypasses
-- RLS so callers don't need read access to profiles.
create or replace function public.is_admin(uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (select 1 from public.profiles where id = uid and role = 'Admin');
$$;

-- my_team() returns the caller's team_id. Used as the default for every
-- domain row's team_id and inside RLS policies for per-team isolation.
create or replace function public.my_team()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select team_id from public.profiles where id = auth.uid() limit 1;
$$;

-- Sign-up handler. Intentionally minimal: it ONLY inserts a profile row.
-- Team creation is deferred to public.bootstrap_team_for_user() (called
-- by the app right after sign-up) so that any RLS/permission quirk in
-- the auth-trigger context can never block sign-up.
--
-- The Edge Function (create-staff) path supplies team_id + role in
-- raw_user_meta_data; the trigger threads those through so the new
-- profile already belongs to the inviter's team. Self sign-up leaves
-- team_id null — the app then calls the bootstrap RPC, which creates
-- a fresh team and promotes this user to Admin.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_team_id uuid;
    v_role    text;
    v_display text;
begin
    v_team_id := nullif(new.raw_user_meta_data->>'team_id', '')::uuid;
    v_role    := coalesce(nullif(new.raw_user_meta_data->>'role', ''), 'Staff');
    v_display := coalesce(
        nullif(new.raw_user_meta_data->>'display_name', ''),
        split_part(new.email, '@', 1)
    );

    -- Self sign-up has no inviter team_id in metadata. Provision a
    -- brand-new team for this user RIGHT HERE so the account is its own
    -- isolated tenant from the instant it exists. This closes the
    -- team-less window that previously let the "Default Team" backfill
    -- (or a failed/late bootstrap RPC) merge fresh self-signups into
    -- another admin's team. The create-staff path still threads the
    -- inviter's team_id through metadata, so invited Staff are unaffected.
    if v_team_id is null then
        insert into public.teams (name, created_by)
        values (coalesce(nullif(v_display, ''), 'My') || '''s Team', new.id)
        returning id into v_team_id;
        v_role := 'Admin';
    end if;

    insert into public.profiles (id, email, display_name, role, team_id)
    values (new.id, new.email, v_display, v_role, v_team_id)
    on conflict (id) do nothing;

    return new;
exception when others then
    -- Never let a profile-row hiccup take down sign-up. The bootstrap
    -- RPC will repair the profile (and create the team) on first call.
    raise warning 'handle_new_user failed for %: %', new.id, sqlerrm;
    return new;
end;
$$;

-- Idempotent team setup, called by the app after sign-up / sign-in.
-- Runs as the calling authenticated user (auth.uid()) under SECURITY
-- DEFINER so it can SELECT/INSERT/UPDATE across teams + profiles even
-- when those tables are RLS-locked. Returns the user's team_id.
create or replace function public.bootstrap_team_for_user()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_team_id uuid;
    v_display text;
    v_email   text;
    v_found   boolean;
begin
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;

    select p.team_id, p.display_name, p.email, true
    into v_team_id, v_display, v_email, v_found
    from public.profiles p
    where p.id = v_user_id;

    if coalesce(v_found, false) and v_team_id is not null then
        return v_team_id;
    end if;

    if not coalesce(v_found, false) then
        select u.email,
               coalesce(u.raw_user_meta_data->>'display_name',
                        split_part(u.email, '@', 1))
        into v_email, v_display
        from auth.users u
        where u.id = v_user_id;

        insert into public.profiles (id, email, display_name, role)
        values (v_user_id, v_email, v_display, 'Staff')
        on conflict (id) do nothing;
    end if;

    insert into public.teams (name, created_by)
    values (coalesce(nullif(v_display, ''), 'My') || '''s Team', v_user_id)
    returning id into v_team_id;

    update public.profiles
       set team_id = v_team_id,
           role    = 'Admin'
     where id = v_user_id;

    return v_team_id;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Generic updated_at touch trigger reused on every domain table.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

-- =====================================================================
-- inventory_items
-- =====================================================================
create table if not exists public.inventory_items (
    id                uuid primary key default uuid_generate_v4(),
    user_id           uuid not null default auth.uid() references auth.users(id) on delete cascade,
    asset_code        text not null default '',
    name              text not null,
    description       text,
    category          text not null default '',
    quantity          integer not null default 0,
    in_use_quantity   integer not null default 0,
    condition         text not null default '',
    status            text not null default '',
    location          text,
    image_uri         text,
    created_at        timestamptz not null default now(),
    last_updated      timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
alter table public.inventory_items
    add column if not exists team_id uuid references public.teams(id) on delete cascade;
-- Backfill columns onto pre-existing tables created by an earlier schema rev.
alter table public.inventory_items add column if not exists asset_code      text not null default '';
alter table public.inventory_items add column if not exists description     text;
alter table public.inventory_items add column if not exists in_use_quantity integer not null default 0;
alter table public.inventory_items add column if not exists condition       text not null default '';
alter table public.inventory_items add column if not exists status          text not null default '';
alter table public.inventory_items add column if not exists location        text;
alter table public.inventory_items add column if not exists image_uri       text;
alter table public.inventory_items add column if not exists last_updated    timestamptz not null default now();
alter table public.inventory_items add column if not exists updated_at      timestamptz not null default now();
alter table public.inventory_items add column if not exists deleted_at      timestamptz;
create index if not exists inventory_items_updated_at_idx on public.inventory_items(updated_at);
create index if not exists inventory_items_team_id_idx on public.inventory_items(team_id);

drop trigger if exists set_updated_at_inventory_items on public.inventory_items;
create trigger set_updated_at_inventory_items
    before update on public.inventory_items
    for each row execute function public.set_updated_at();

-- =====================================================================
-- item_history
-- =====================================================================
create table if not exists public.item_history (
    id              uuid primary key default uuid_generate_v4(),
    user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
    item_id         uuid references public.inventory_items(id) on delete cascade,
    item_local_id   integer,
    action          text not null,
    details         text not null default '',
    timestamp       timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz
);
alter table public.item_history
    add column if not exists team_id uuid references public.teams(id) on delete cascade;
alter table public.item_history add column if not exists item_local_id integer;
alter table public.item_history add column if not exists details       text not null default '';
alter table public.item_history add column if not exists updated_at    timestamptz not null default now();
alter table public.item_history add column if not exists deleted_at    timestamptz;
create index if not exists item_history_item_id_idx on public.item_history(item_id);
create index if not exists item_history_team_id_idx on public.item_history(team_id);

drop trigger if exists set_updated_at_item_history on public.item_history;
create trigger set_updated_at_item_history
    before update on public.item_history
    for each row execute function public.set_updated_at();

-- =====================================================================
-- item_usage_records
-- =====================================================================
create table if not exists public.item_usage_records (
    id                uuid primary key default uuid_generate_v4(),
    user_id           uuid not null default auth.uid() references auth.users(id) on delete cascade,
    item_id           uuid references public.inventory_items(id) on delete cascade,
    item_local_id     integer,
    quantity          integer not null default 1,
    location          text not null default '',
    used_by           text not null default '',
    checked_out_at    timestamptz not null default now(),
    returned_at       timestamptz,
    return_reason     text,
    status            text not null default 'Active',
    barcode_id        uuid,
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
alter table public.item_usage_records
    add column if not exists team_id uuid references public.teams(id) on delete cascade;
alter table public.item_usage_records add column if not exists item_local_id integer;
alter table public.item_usage_records add column if not exists return_reason text;
alter table public.item_usage_records add column if not exists status        text not null default 'Active';
alter table public.item_usage_records add column if not exists barcode_id    uuid;
alter table public.item_usage_records add column if not exists returned_at   timestamptz;
alter table public.item_usage_records add column if not exists updated_at    timestamptz not null default now();
alter table public.item_usage_records add column if not exists deleted_at    timestamptz;
create index if not exists item_usage_records_item_id_idx on public.item_usage_records(item_id);
create index if not exists item_usage_records_team_id_idx on public.item_usage_records(team_id);

drop trigger if exists set_updated_at_item_usage_records on public.item_usage_records;
create trigger set_updated_at_item_usage_records
    before update on public.item_usage_records
    for each row execute function public.set_updated_at();

-- =====================================================================
-- linked_barcodes
-- =====================================================================
create table if not exists public.linked_barcodes (
    id              uuid primary key default uuid_generate_v4(),
    user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
    item_id         uuid references public.inventory_items(id) on delete cascade,
    item_local_id   integer,
    barcode_value   text not null,
    label           text not null default '',
    linked_at       timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz
);
alter table public.linked_barcodes
    add column if not exists team_id uuid references public.teams(id) on delete cascade;
alter table public.linked_barcodes add column if not exists item_local_id integer;
alter table public.linked_barcodes add column if not exists label         text not null default '';
alter table public.linked_barcodes add column if not exists updated_at    timestamptz not null default now();
alter table public.linked_barcodes add column if not exists deleted_at    timestamptz;
create index if not exists linked_barcodes_team_id_idx on public.linked_barcodes(team_id);

drop trigger if exists set_updated_at_linked_barcodes on public.linked_barcodes;
create trigger set_updated_at_linked_barcodes
    before update on public.linked_barcodes
    for each row execute function public.set_updated_at();

-- =====================================================================
-- categories
-- =====================================================================
create table if not exists public.categories (
    id          uuid primary key default uuid_generate_v4(),
    user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name        text not null,
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);
alter table public.categories
    add column if not exists team_id uuid references public.teams(id) on delete cascade;

drop trigger if exists set_updated_at_categories on public.categories;
create trigger set_updated_at_categories
    before update on public.categories
    for each row execute function public.set_updated_at();

-- =====================================================================
-- asset_statuses
-- =====================================================================
create table if not exists public.asset_statuses (
    id          uuid primary key default uuid_generate_v4(),
    user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name        text not null,
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);
alter table public.asset_statuses
    add column if not exists team_id uuid references public.teams(id) on delete cascade;

drop trigger if exists set_updated_at_asset_statuses on public.asset_statuses;
create trigger set_updated_at_asset_statuses
    before update on public.asset_statuses
    for each row execute function public.set_updated_at();

-- =====================================================================
-- Migration: convert legacy bigint timestamp columns to timestamptz.
-- Earlier schema versions stored these as bigint (epoch millis to match
-- Kotlin's Long). The current DTO sends ISO-8601 strings, which collide
-- with bigint columns ("invalid input syntax for type bigint"). The
-- check is per-column (not per-table) because columns added in later
-- migrations — `deleted_at` especially — were already created as
-- timestamptz, while their siblings are still bigint. Each column is
-- only touched when it's still bigint, so re-runs are harmless.
-- =====================================================================
do $$
declare
    spec record;
begin
    for spec in
        select * from (values
            ('inventory_items',    'created_at',     true),
            ('inventory_items',    'last_updated',   true),
            ('inventory_items',    'updated_at',     true),
            ('inventory_items',    'deleted_at',     false),
            ('item_history',       'timestamp',      true),
            ('item_history',       'updated_at',     true),
            ('item_history',       'deleted_at',     false),
            ('item_usage_records', 'checked_out_at', true),
            ('item_usage_records', 'returned_at',    false),
            ('item_usage_records', 'updated_at',     true),
            ('item_usage_records', 'deleted_at',     false),
            ('linked_barcodes',    'linked_at',      true),
            ('linked_barcodes',    'updated_at',     true),
            ('linked_barcodes',    'deleted_at',     false),
            ('categories',         'created_at',     true),
            ('categories',         'updated_at',     true),
            ('categories',         'deleted_at',     false),
            ('asset_statuses',     'created_at',     true),
            ('asset_statuses',     'updated_at',     true),
            ('asset_statuses',     'deleted_at',     false)
        ) as t(tbl, col, has_default)
    loop
        if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name  = spec.tbl
              and column_name = spec.col
              and data_type   = 'bigint'
        ) then
            execute format(
                'alter table public.%I alter column %I drop default',
                spec.tbl, spec.col
            );
            execute format(
                'alter table public.%I alter column %I type timestamptz using ' ||
                '(case when %I is null then null else to_timestamp(%I::double precision / 1000) end)',
                spec.tbl, spec.col, spec.col, spec.col
            );
            if spec.has_default then
                execute format(
                    'alter table public.%I alter column %I set default now()',
                    spec.tbl, spec.col
                );
            end if;
        end if;
    end loop;
end$$;

-- =====================================================================
-- Backfill: give every team-less profile its OWN team (never a shared
-- one) and migrate each user's data into that team by ownership
-- (user_id). The old behaviour parked everyone into a single shared
-- "Default Team", which collapsed unrelated accounts into one tenant —
-- the cause of new sign-ups appearing in another admin's member list.
-- Each profile becomes the Admin of its own isolated team.
-- =====================================================================
do $$
declare
    r       record;
    v_team  uuid;
begin
    for r in
        select id,
               coalesce(nullif(display_name, ''), split_part(email, '@', 1)) as dn
        from public.profiles
        where team_id is null
    loop
        insert into public.teams (name, created_by)
        values (coalesce(nullif(r.dn, ''), 'My') || '''s Team', r.id)
        returning id into v_team;

        update public.profiles set team_id = v_team, role = 'Admin' where id = r.id;
        update public.inventory_items    set team_id = v_team where user_id = r.id and team_id is null;
        update public.item_history       set team_id = v_team where user_id = r.id and team_id is null;
        update public.item_usage_records set team_id = v_team where user_id = r.id and team_id is null;
        update public.linked_barcodes    set team_id = v_team where user_id = r.id and team_id is null;
        update public.categories         set team_id = v_team where user_id = r.id and team_id is null;
        update public.asset_statuses     set team_id = v_team where user_id = r.id and team_id is null;
    end loop;

    -- Any domain rows still team-less belong to a user with no profile
    -- (legacy/mock data from before multi-tenancy). Park them in a single
    -- quarantine team that no real account is a member of, so they stay
    -- invisible to every tenant instead of leaking into one.
    if exists (
        select 1 from public.inventory_items where team_id is null
        union all select 1 from public.item_history where team_id is null
        union all select 1 from public.item_usage_records where team_id is null
        union all select 1 from public.linked_barcodes where team_id is null
        union all select 1 from public.categories where team_id is null
        union all select 1 from public.asset_statuses where team_id is null
        limit 1
    ) then
        select id into v_team from public.teams
            where name = 'Orphaned (legacy)' and created_by is null limit 1;
        if v_team is null then
            insert into public.teams (name, created_by)
            values ('Orphaned (legacy)', null) returning id into v_team;
        end if;
        update public.inventory_items    set team_id = v_team where team_id is null;
        update public.item_history       set team_id = v_team where team_id is null;
        update public.item_usage_records set team_id = v_team where team_id is null;
        update public.linked_barcodes    set team_id = v_team where team_id is null;
        update public.categories         set team_id = v_team where team_id is null;
        update public.asset_statuses     set team_id = v_team where team_id is null;
    end if;
end$$;

-- =====================================================================
-- One-time repair: un-merge accounts already collapsed into the shared
-- "Default Team" by a previous run of the old backfill. Each member is
-- split into its own team and its owned data (by user_id) follows it.
-- Targets ONLY the merge artifact (name = 'Default Team', created_by
-- null) so legitimate invited multi-member teams are never touched.
-- =====================================================================
do $$
declare
    v_default uuid;
    r         record;
    v_team    uuid;
begin
    select id into v_default from public.teams
        where name = 'Default Team' and created_by is null limit 1;
    if v_default is null then
        return;
    end if;

    for r in
        select id,
               coalesce(nullif(display_name, ''), split_part(email, '@', 1)) as dn
        from public.profiles
        where team_id = v_default
    loop
        insert into public.teams (name, created_by)
        values (coalesce(nullif(r.dn, ''), 'My') || '''s Team', r.id)
        returning id into v_team;

        update public.profiles set team_id = v_team, role = 'Admin' where id = r.id;
        update public.inventory_items    set team_id = v_team where user_id = r.id and team_id = v_default;
        update public.item_history       set team_id = v_team where user_id = r.id and team_id = v_default;
        update public.item_usage_records set team_id = v_team where user_id = r.id and team_id = v_default;
        update public.linked_barcodes    set team_id = v_team where user_id = r.id and team_id = v_default;
        update public.categories         set team_id = v_team where user_id = r.id and team_id = v_default;
        update public.asset_statuses     set team_id = v_team where user_id = r.id and team_id = v_default;
    end loop;

    -- Rename the now-emptied bucket so it can't recapture anyone and is
    -- obvious in the dashboard. Residual rows here are ownerless legacy
    -- data, invisible to all real teams.
    update public.teams set name = 'Orphaned (legacy)' where id = v_default;
end$$;

-- =====================================================================
-- Tighten team_id: NOT NULL + default my_team() so future inserts auto-fill.
-- =====================================================================
alter table public.inventory_items    alter column team_id set default public.my_team();
alter table public.inventory_items    alter column team_id set not null;
alter table public.item_history       alter column team_id set default public.my_team();
alter table public.item_history       alter column team_id set not null;
alter table public.item_usage_records alter column team_id set default public.my_team();
alter table public.item_usage_records alter column team_id set not null;
alter table public.linked_barcodes    alter column team_id set default public.my_team();
alter table public.linked_barcodes    alter column team_id set not null;
alter table public.categories         alter column team_id set default public.my_team();
alter table public.categories         alter column team_id set not null;
alter table public.asset_statuses     alter column team_id set default public.my_team();
alter table public.asset_statuses     alter column team_id set not null;

-- =====================================================================
-- Same problem as team_id: pre-existing tables created by an earlier
-- schema rev have user_id NOT NULL but with NO default, so inserts that
-- omit user_id fail with "null value in column user_id". The cloud-sync
-- DTO relies on auth.uid() filling this in. Re-apply the default on
-- every sync-tracked table.
-- =====================================================================
alter table public.inventory_items    alter column user_id set default auth.uid();
alter table public.item_history       alter column user_id set default auth.uid();
alter table public.item_usage_records alter column user_id set default auth.uid();
alter table public.linked_barcodes    alter column user_id set default auth.uid();
alter table public.categories         alter column user_id set default auth.uid();
alter table public.asset_statuses     alter column user_id set default auth.uid();

-- =====================================================================
-- Per-team unique indexes (replace any older single-column versions).
-- =====================================================================
drop index if exists public.linked_barcodes_value_active_idx;
create unique index if not exists linked_barcodes_value_active_idx
    on public.linked_barcodes(team_id, barcode_value) where deleted_at is null;

drop index if exists public.categories_name_active_idx;
create unique index if not exists categories_name_active_idx
    on public.categories(team_id, lower(name)) where deleted_at is null;

drop index if exists public.asset_statuses_name_active_idx;
create unique index if not exists asset_statuses_name_active_idx
    on public.asset_statuses(team_id, lower(name)) where deleted_at is null;

-- =====================================================================
-- Row-Level Security
-- =====================================================================
alter table public.teams               enable row level security;
alter table public.profiles            enable row level security;
alter table public.inventory_items     enable row level security;
alter table public.item_history        enable row level security;
alter table public.item_usage_records  enable row level security;
alter table public.linked_barcodes     enable row level security;
alter table public.categories          enable row level security;
alter table public.asset_statuses      enable row level security;

-- Teams: a member can see their own team; only an admin can rename it.
drop policy if exists teams_member_select on public.teams;
drop policy if exists teams_admin_update  on public.teams;
create policy teams_member_select on public.teams for select
    using (id = public.my_team());
create policy teams_admin_update on public.teams for update
    using (id = public.my_team() and public.is_admin())
    with check (id = public.my_team() and public.is_admin());

-- Profiles: only members of the same team can see each other; users edit
-- their own row, admins edit any teammate's row, admins can delete teammates.
drop policy if exists profiles_select         on public.profiles;
drop policy if exists profiles_team_select    on public.profiles;
drop policy if exists profiles_self_select    on public.profiles;
drop policy if exists profiles_admin_select   on public.profiles;
drop policy if exists profiles_self_update    on public.profiles;
drop policy if exists profiles_admin_update   on public.profiles;
drop policy if exists profiles_admin_delete   on public.profiles;
create policy profiles_team_select   on public.profiles for select
    using (team_id = public.my_team());
create policy profiles_self_update   on public.profiles for update
    using (id = auth.uid()) with check (id = auth.uid());
create policy profiles_admin_update  on public.profiles for update
    using (public.is_admin() and team_id = public.my_team())
    with check (public.is_admin() and team_id = public.my_team());
create policy profiles_admin_delete  on public.profiles for delete
    using (public.is_admin() and team_id = public.my_team());

-- Domain tables: per-team isolation.
--   SELECT/INSERT/UPDATE: any authenticated team member.
--   DELETE: admin of the same team only (the app uses soft deletes, so
--   hard DELETE is rare).
do $$
declare t text;
begin
    foreach t in array array[
        'inventory_items','item_history','item_usage_records',
        'linked_barcodes','categories','asset_statuses'
    ] loop
        execute format('drop policy if exists %I_auth_select  on public.%I', t, t);
        execute format('drop policy if exists %I_auth_insert  on public.%I', t, t);
        execute format('drop policy if exists %I_auth_update  on public.%I', t, t);
        execute format('drop policy if exists %I_admin_delete on public.%I', t, t);
        execute format('drop policy if exists %I_team_select  on public.%I', t, t);
        execute format('drop policy if exists %I_team_insert  on public.%I', t, t);
        execute format('drop policy if exists %I_team_update  on public.%I', t, t);
        execute format('drop policy if exists %I_team_delete  on public.%I', t, t);

        execute format(
            'create policy %I on public.%I for select using (team_id = public.my_team())',
            t || '_team_select', t
        );
        execute format(
            'create policy %I on public.%I for insert with check (team_id = public.my_team())',
            t || '_team_insert', t
        );
        execute format(
            'create policy %I on public.%I for update using (team_id = public.my_team()) with check (team_id = public.my_team())',
            t || '_team_update', t
        );
        execute format(
            'create policy %I on public.%I for delete using (team_id = public.my_team() and public.is_admin())',
            t || '_team_delete', t
        );
    end loop;
end$$;

-- =====================================================================
-- Grants for the GoTrue trigger context.
--
-- handle_new_user() fires as supabase_auth_admin (the role GoTrue uses
-- when inserting into auth.users on sign-up). The old trigger only
-- touched public.profiles; the new one also inserts into public.teams,
-- and supabase_auth_admin has no implicit DML on public objects, so
-- without these grants the trigger raises "permission denied" and the
-- caller sees the generic "Database error saving new user".
-- =====================================================================
grant usage on schema public to supabase_auth_admin;
grant select, insert, update on public.teams    to supabase_auth_admin;
grant select, insert, update on public.profiles to supabase_auth_admin;

-- =====================================================================
-- Re-own the functions to `postgres` (BYPASSRLS) so SECURITY DEFINER
-- actually bypasses RLS inside the sign-up trigger. `create or replace
-- function` does NOT change the existing owner, so if the function was
-- ever created under a non-bypass role this is what unsticks it.
-- =====================================================================
alter function public.handle_new_user()         owner to postgres;
alter function public.my_team()                  owner to postgres;
alter function public.is_admin(uuid)             owner to postgres;
alter function public.set_updated_at()           owner to postgres;
alter function public.bootstrap_team_for_user()  owner to postgres;

-- Grant EXECUTE on the helpers that get called *as the caller* (i.e. evaluated
-- inside an INSERT default expression or an RLS USING/CHECK clause). Without
-- these grants, a logged-in user inserting into inventory_items would hit
-- "permission denied for function my_team" because the column default
-- `team_id default public.my_team()` runs under the authenticated role —
-- SECURITY DEFINER doesn't help when the caller can't even reach the function.
grant execute on function public.my_team()                 to authenticated;
grant execute on function public.is_admin(uuid)            to authenticated;
grant execute on function public.bootstrap_team_for_user() to authenticated;

-- =====================================================================
-- INSERT-policy safety net. The trigger inserts into teams + profiles
-- during sign-up; if SECURITY DEFINER bypass somehow doesn't kick in
-- (older Postgres, role quirk), these permissive INSERT policies still
-- let the row land. The strict per-team SELECT/UPDATE/DELETE policies
-- already declared above keep reads and writes locked down afterwards.
-- =====================================================================
drop policy if exists teams_signup_insert    on public.teams;
drop policy if exists profiles_signup_insert on public.profiles;
create policy teams_signup_insert    on public.teams    for insert with check (true);
create policy profiles_signup_insert on public.profiles for insert with check (true);

-- =====================================================================
-- De-duplicate inventory_items and hard-prevent future duplicates.
-- Cause: items were keyed only by the client-generated cloudId, so a
-- wiped / reinstalled / second-device copy re-pushed the same item under
-- a fresh UUID. Keep the most recently updated live row per
-- (team_id, asset_code); delete the older live duplicates (their child
-- rows cascade). Blank asset codes and soft-deleted rows are left alone.
-- The app now also reconciles by asset_code, so this is a one-time clean
-- plus a safety-net unique index.
-- =====================================================================
delete from public.inventory_items i
using (
    select id,
           row_number() over (
               partition by team_id, asset_code
               order by updated_at desc, created_at desc, id
           ) as rn
    from public.inventory_items
    where deleted_at is null and asset_code <> ''
) d
where i.id = d.id and d.rn > 1;

drop index if exists public.inventory_items_team_assetcode_idx;
create unique index if not exists inventory_items_team_assetcode_idx
    on public.inventory_items (team_id, asset_code)
    where deleted_at is null and asset_code <> '';

-- =====================================================================
-- Supabase Storage: item photos (cross-device image sync).
-- Private bucket; object key = "<inventory_items.id>.jpg". Access is
-- gated to the team that owns the referenced item, so a teammate sees
-- the photo and another tenant never can — same isolation as the rows.
-- =====================================================================
insert into storage.buckets (id, name, public)
values ('item-images', 'item-images', false)
on conflict (id) do nothing;

drop policy if exists item_images_team_select on storage.objects;
drop policy if exists item_images_team_insert on storage.objects;
drop policy if exists item_images_team_update on storage.objects;
drop policy if exists item_images_team_delete on storage.objects;

create policy item_images_team_select on storage.objects for select
    to authenticated
    using (
        bucket_id = 'item-images'
        and exists (
            select 1 from public.inventory_items i
            where i.id::text = split_part(storage.objects.name, '.', 1)
              and i.team_id = public.my_team()
        )
    );

create policy item_images_team_insert on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'item-images'
        and exists (
            select 1 from public.inventory_items i
            where i.id::text = split_part(storage.objects.name, '.', 1)
              and i.team_id = public.my_team()
        )
    );

create policy item_images_team_update on storage.objects for update
    to authenticated
    using (
        bucket_id = 'item-images'
        and exists (
            select 1 from public.inventory_items i
            where i.id::text = split_part(storage.objects.name, '.', 1)
              and i.team_id = public.my_team()
        )
    )
    with check (
        bucket_id = 'item-images'
        and exists (
            select 1 from public.inventory_items i
            where i.id::text = split_part(storage.objects.name, '.', 1)
              and i.team_id = public.my_team()
        )
    );

create policy item_images_team_delete on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'item-images'
        and exists (
            select 1 from public.inventory_items i
            where i.id::text = split_part(storage.objects.name, '.', 1)
              and i.team_id = public.my_team()
        )
    );


-- =====================================================================
-- Tell PostgREST to reload its schema cache. New columns (item_local_id,
-- updated_at, etc.) added by the migrations above are invisible to the
-- REST API until this fires — that's the source of the
-- "Could not find the 'item_local_id' column ... in the schema cache"
-- errors during cloud sync.

-- =====================================================================
notify pgrst, 'reload schema';
