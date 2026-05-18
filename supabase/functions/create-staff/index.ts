// SmartStock+ — create-staff Edge Function
//
// Why this function exists:
//   The Android app cannot ship the Supabase service-role key — anyone could
//   decompile the APK and use it to hand themselves Admin. Creating new auth
//   users is therefore done server-side. The Admin's normal session JWT is
//   verified here, then this function uses the service-role key (held only
//   in Supabase environment variables) to create the staff account under the
//   Admin's team_id.
//
// Request:
//   POST /functions/v1/create-staff
//   Authorization: Bearer <admin's session JWT — supabase-kt sends this automatically>
//   Content-Type: application/json
//   Body: { "name": "...", "email": "...", "password": "...", "role": "Staff" }
//
// Response:
//   200 { "user_id": "<uuid>", "email": "..." }
//   4xx { "error": "..." }
//
// Deploy with the Supabase CLI:
//   supabase functions deploy create-staff --project-ref vhkeosasyvposdpbddoc
//
// Or paste this file's contents into Supabase Dashboard → Edge Functions →
// New function → name "create-staff" → Save → Deploy.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4"

const SUPABASE_URL              = Deno.env.get("SUPABASE_URL")!
const SUPABASE_ANON_KEY         = Deno.env.get("SUPABASE_ANON_KEY")!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!

interface CreateStaffPayload {
    name?: string
    email?: string
    password?: string
    role?: "Staff" | "Admin"
}

const corsHeaders: Record<string, string> = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
}

Deno.serve(async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") {
        return new Response("ok", { headers: corsHeaders })
    }
    if (req.method !== "POST") {
        return json({ error: "method not allowed" }, 405)
    }

    const authHeader = req.headers.get("Authorization") ?? ""
    if (!authHeader.toLowerCase().startsWith("bearer ")) {
        return json({ error: "missing bearer token" }, 401)
    }

    let payload: CreateStaffPayload
    try {
        payload = await req.json()
    } catch {
        return json({ error: "invalid JSON body" }, 400)
    }

    const name     = (payload.name ?? "").trim()
    const email    = (payload.email ?? "").trim().toLowerCase()
    const password = payload.password ?? ""
    const role     = payload.role === "Admin" ? "Admin" : "Staff"

    if (!email || !password) {
        return json({ error: "email and password are required" }, 400)
    }
    if (password.length < 6) {
        return json({ error: "password must be at least 6 characters" }, 400)
    }

    // Verify the caller's identity using their JWT. This lookup goes through
    // RLS, so it can only see profile rows in the caller's own team — i.e.
    // their own row.
    const callerClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
        global: { headers: { Authorization: authHeader } },
        auth: { autoRefreshToken: false, persistSession: false },
    })

    // Resolve who the caller actually is from their JWT. We must filter the
    // profile lookup by this id: profiles_team_select RLS returns EVERY
    // profile in the caller's team, so a bare .single() throws "multiple
    // rows" as soon as the team has any staff — which surfaced to the user
    // as the misleading "could not load caller profile".
    const { data: userData, error: userErr } = await callerClient.auth.getUser()
    const callerId = userData?.user?.id
    if (userErr || !callerId) {
        return json({ error: "your session is invalid. Sign out and sign in again." }, 401)
    }

    const { data: callerProfile, error: profileErr } = await callerClient
        .from("profiles")
        .select("id, role, is_active, team_id")
        .eq("id", callerId)
        .maybeSingle()

    if (profileErr) {
        return json({ error: "could not load caller profile" }, 401)
    }
    if (!callerProfile) {
        return json(
            { error: "your profile no longer exists. Ask an admin to re-create your account." },
            401
        )
    }
    if (!callerProfile.is_active) {
        return json({ error: "your account is inactive" }, 403)
    }
    if (callerProfile.role !== "Admin") {
        return json({ error: "only admins can create staff" }, 403)
    }
    if (!callerProfile.team_id) {
        return json({ error: "your account is not attached to a team" }, 400)
    }

    // Service-role call. Never reaches the client. user_metadata flows into
    // auth.users.raw_user_meta_data, which the handle_new_user trigger reads
    // to file the new profile under the admin's team with the correct role.
    const adminClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
        auth: { autoRefreshToken: false, persistSession: false },
    })

    const { data: created, error: createErr } =
        await adminClient.auth.admin.createUser({
            email,
            password,
            email_confirm: true,
            user_metadata: {
                display_name: name,
                team_id: callerProfile.team_id,
                role,
            },
        })

    if (createErr || !created?.user) {
        return json(
            { error: createErr?.message ?? "could not create user" },
            400
        )
    }

    return json(
        { user_id: created.user.id, email: created.user.email ?? email },
        200
    )
})

function json(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
    })
}
