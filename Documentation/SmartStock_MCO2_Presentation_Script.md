# SmartStock+ — MCO 2 Final Presentation Script

**Team 2 · Mobile Programming 2 · Instructor: Mr. Aaron Jude Pael · May 19, 2026**

**Target length:** ~10–12 minutes (≈6–7 min slides + ≈4 min live demo + Q&A)
Deck: `SmartStock+_MCO2_Presentation.pptx` (9 slides)

> How to use this: each section maps to one slide. The **[SAY]** lines are
> spoken almost verbatim — keep them conversational, don't read robotically.
> **[DO]** lines are actions (advance slide, switch to device). **[TIME]**
> is the running budget. Assign 2–4 speakers; suggested splits are marked
> **(Speaker A/B/C)**.

---

## Slide 1 — Title  ·  (Speaker A)  ·  [TIME 0:00–0:45]

**[DO]** Have the app already open on the device, mirrored to the screen.

**[SAY]**
> "Good morning, Mr. Pael. We're Team 2, and this is our MCO 2 final
> presentation for **SmartStock+** — an offline-first inventory and asset
> management system for Android.
>
> In MCO 1 we built the offline foundation. For MCO 2 we added a full
> cloud backend with secure multi-tenant sync. I'll start with an overview,
> then my teammates will walk through the architecture, the cloud
> integration, a live demo, and what we learned."

**[DO]** Advance to Slide 2.

---

## Slide 2 — Project Overview  ·  (Speaker A)  ·  [TIME 0:45–2:00]

**[SAY]**
> "The problem we set out to solve: small businesses, school labs, and
> field teams still track equipment on paper or in spreadsheets. Those
> break the moment the network is unreliable — data gets lost or
> duplicated.
>
> SmartStock+ replaces that. The key design decision is **offline-first**:
> the app is fully usable with no connection, and it syncs to the cloud
> automatically when it's back online.
>
> Our target users are small businesses, school and IT laboratories,
> field teams, and asset custodians managing shared equipment.
>
> The core modules are: inventory CRUD with asset codes, barcode and QR
> scanning with per-unit checkout, usage reporting with CSV and PDF export,
> dashboard charts and low-stock alerts, a stock-take flow, and — new in
> MCO 2 — multi-tenant cloud sync and authentication."

**[DO]** Advance to Slide 3.

---

## Slide 3 — Architecture  ·  (Speaker B)  ·  [TIME 2:00–3:30]

**[SAY]**
> "Architecturally we follow **MVVM with the Repository pattern**.
>
> Following the diagram: the Jetpack Compose UI talks only to a ViewModel,
> which exposes state with StateFlow. The ViewModel talks to a single
> Repository. The Repository is the only thing that touches data — the
> Room database locally, and the cloud sync source remotely. ViewModels
> never touch DAOs or the network directly.
>
> The important part is the flow: every write hits **Room instantly**, so
> the UI never blocks waiting on a network. A WorkManager job —
> SyncWorker — then reconciles with Supabase in the background. We use
> last-write-wins on an updatedAt timestamp, soft-deletes, and
> **natural-key matching** on asset code and name. That last part is a fix
> we'll come back to — it's what keeps sync duplicate-free even when the
> app is reinstalled or used on a second device.
>
> Everything is wired with Hilt dependency injection, which keeps the
> layers cleanly separated — data, domain, and UI."

**[DO]** Advance to Slide 4.

---

## Slide 4 — Cloud / API Integration  ·  (Speaker B)  ·  [TIME 3:30–5:00]

**[SAY]**
> "For the cloud we use **Supabase**. Supabase is a REST-API backend —
> PostgreSQL exposed through PostgREST — plus GoTrue for authentication,
> object Storage, and Edge Functions. We consume it from Kotlin with the
> supabase-kt library over Ktor.
>
> Authentication supports email/password with session persistence,
> password reset, and biometric unlock. An Admin can provision Staff
> accounts through a secure Edge Function.
>
> Sync is bidirectional across six tables, connectivity-gated, with the
> natural-key reconciliation I mentioned so we never get duplicate rows.
> Item photos sync across devices through **Supabase Storage**, scoped
> per team.
>
> The security highlight is **multi-tenant isolation**. Every row carries
> a team_id, and PostgreSQL **Row-Level Security** — backed by a
> SECURITY DEFINER function `my_team()` — guarantees each account can only
> ever read its own team's data. We tested this aggressively and hardened
> it after finding a real isolation bug, which we'll cover in the
> challenges slide."

**[DO]** *(Point to the screenshot once it's pasted in.)* "Here you can see
the live Supabase table with our synced inventory rows."

**[DO]** Advance to Slide 5, then switch the screen to the **device**.

---

## Slide 5 — UI Demo (LIVE)  ·  (Speaker C)  ·  [TIME 5:00–9:00]

> **This is the core of the presentation. Move deliberately, narrate every
> tap. If anything stalls, switch to Slides 6–7 screenshots and keep
> talking — never debug live.**

**[DO + SAY] — 1. Auth & multi-tenant isolation (≈45s)**
> "I'll register a brand-new account."
- Show the redesigned login screen; tap into the password field to show
  it **scrolls above the keyboard** (a UX fix we made).
- Register a fresh account.
> "Notice the inventory is **empty** — and in Profile, the team list shows
> **only this account**. That proves our multi-tenant isolation: a new
> account never sees another team's data."

**[DO + SAY] — 2. Create with photo + barcode (≈60s)**
> "Now I'll add an item."
- Add item: name, category, quantity. Attach a **camera/gallery photo**.
  Scan or link a **barcode**.
- Save.
> "That write went to the local Room database instantly — no spinner,
> works even offline."

**[DO + SAY] — 3. Cloud sync proof (≈45s)**
- Show the sync badge / pull-to-refresh.
- **[DO]** Switch to the second screen / Supabase dashboard, refresh
  `inventory_items`.
> "There's the row we just created, with its team_id — and the photo is
> in the Supabase Storage bucket. It synced across the network."

**[DO + SAY] — 4. Notification + Undo (≈45s)**
- Edit the item quantity below the low-stock threshold → low-stock
  **notification** fires.
- Delete an item → tap **UNDO** to restore it.
> "Low-stock alerts and a safe undo on destructive actions."

**[DO + SAY] — 5. Profile depth (≈45s)**
- Profile → toggle **theme** (Light/Dark/System).
- **Export Data** → show the CSV share sheet.
- **Security Settings** → open the dialog (role, biometric, RLS isolation,
  session).
> "The Profile screen is fully functional — theme, CSV export, security
> posture, and team management for admins."

**[DO]** Switch screen back to the deck. Advance to Slide 8.
*(Slides 6 & 7 are screenshot fallbacks — only present them if the live
demo failed.)*

---

## Slides 6 & 7 — Screenshot Fallbacks  ·  (only if live demo fails)

**[SAY, if used]**
> "In case of connectivity issues, here are captured screens of the same
> flow: login, dashboard, inventory, item creation, cloud sync, and the
> profile screen."
Walk each image in 2–3 sentences mirroring the demo script above.

---

## Slide 8 — Challenges & Learnings  ·  (Speaker A)  ·  [TIME 9:00–10:30]

**[SAY]**
> "A few real challenges we solved:
>
> **One — Room-to-REST synchronization.** The local database stores time
> as epoch-millis; the API uses ISO-8601 strings. We built a mapping layer
> and last-write-wins conflict resolution across six tables.
>
> **Two — and this is our biggest learning — multi-tenant data
> isolation.** During testing, a newly registered account could see
> another admin's data. We root-caused it to an over-broad `is_admin()`
> RLS policy combined with a local cache that wasn't wiped on logout. We
> fixed it with strict per-team Row-Level Security and a cache wipe on
> account switch.
>
> **Three — duplicate-free sync.** Keying rows only by a client-generated
> UUID meant a reinstall duplicated everything in the cloud. We fixed it
> by reconciling items, categories, and statuses by their natural key
> before pushing.
>
> **Four — cross-device images.** A local file path is meaningless on
> another device, so we moved photos into per-team Supabase Storage with
> upload and download in the sync loop.
>
> The best practices we applied throughout: MVVM, the Repository pattern,
> StateFlow, Hilt dependency injection, lifecycle-aware APIs, WorkManager,
> and RLS-backed security."

**[DO]** Advance to Slide 9.

---

## Slide 9 — Conclusion & Future Improvements  ·  (Speaker A)  ·  [TIME 10:30–11:30]

**[SAY]**
> "To summarize: SmartStock+ is a complete offline-first inventory system
> — full CRUD, scanning, reporting, charts, notifications, biometric auth,
> and secure multi-tenant Supabase sync with cross-device image storage.
> It stays fully usable with zero connectivity and reconciles
> automatically once back online. That was our core goal, and we achieved
> it.
>
> For future improvements: hardware-backed biometrics using the Android
> Keystore and a BiometricPrompt CryptoObject to encrypt the session
> token; an admin web panel; real-time updates via Supabase Realtime;
> FCM push notifications; and an audit log.
>
> Thank you, Mr. Pael, for your guidance throughout Mobile Programming 2.
> We'd be happy to answer any questions."

**[DO]** Stop. Stay on Slide 9 for Q&A.

---

## Q&A — Anticipated Questions & Answers

**Q: Why Supabase and not Firebase?**
> "The requirement allows Firebase *or* a REST API. Supabase IS a REST-API
> backend — PostgREST over PostgreSQL — plus managed auth and storage. We
> chose it because PostgreSQL's Row-Level Security gives us
> database-enforced multi-tenant isolation, which is stronger than
> application-level checks."

**Q: How does it work offline?**
> "Room is the single source of truth. Every read and write is local and
> instant. A WorkManager job syncs to the cloud opportunistically when a
> connection exists, so the network is never on the critical path."

**Q: What happens if two devices edit the same item?**
> "Last-write-wins on an updatedAt timestamp, with natural-key
> reconciliation so the same logical item never duplicates across
> devices."

**Q: Is the biometric login real authentication?**
> "It's a local unlock for an already-established session — biometrics
> can't authenticate against the server by itself. The production-grade
> version, which is in our future improvements, binds the session token to
> a hardware Keystore key released only by a biometric."

**Q: How is one team's data kept private from another?**
> "Every row has a team_id. PostgreSQL Row-Level Security policies, backed
> by a SECURITY DEFINER `my_team()` function, make it physically
> impossible for a query to return another team's rows — it's enforced at
> the database, not in the app."

---

## Pre-Demo Checklist (do this BEFORE you present)

- [ ] `supabase/01_schema.sql` re-run in Supabase (RLS + dedup + Storage)
- [ ] Release APK rebuilt & installed on the demo device
- [ ] Old app uninstalled first (so the new icon shows)
- [ ] Test account credentials ready; one item with a photo pre-synced
- [ ] Screenshots pasted into Slides 4, 6, 7 (delete placeholder boxes)
- [ ] Device on Wi-Fi; screen mirroring tested
- [ ] Supabase dashboard logged in on a second screen/tab
- [ ] Airplane-mode toggle rehearsed (to show offline → sync)
- [ ] Speaker roles assigned (A: overview/challenges, B: architecture/cloud,
      C: live demo)
- [ ] One full timed dry run completed
