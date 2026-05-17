# SmartStock+

An **offline-first** inventory and asset-management Android app, built with Kotlin
and Jetpack Compose, with secure **multi-tenant cloud sync** powered by Supabase.

> Mobile Programming 2 · MCO 2 Final Project · Team 2
> Instructor: Mr. Aaron Jude Pael

## Highlights

- **Offline-first** — every read/write hits a local Room database instantly; the
  UI never blocks on the network.
- **Background sync** — a WorkManager job reconciles with Supabase when online,
  using last-write-wins on `updatedAt` and natural-key matching so data never
  duplicates across reinstalls or devices.
- **Multi-tenant isolation** — every row carries a `team_id`; PostgreSQL
  Row-Level Security (backed by a `SECURITY DEFINER my_team()` function) makes it
  impossible for one team to read another team's data.
- **Auth** — email/password with session persistence, password reset, biometric
  unlock, and Admin-provisioned Staff accounts via a secure Edge Function.
- **Cross-device images** — item photos sync through a per-team Supabase Storage
  bucket.
- Inventory CRUD with asset codes, barcode/QR scanning with per-unit checkout,
  CSV/PDF reporting, dashboard charts, low-stock notifications, and a stock-take
  flow.

## Architecture

```
Jetpack Compose UI → ViewModel (StateFlow) → Repository ┬→ Room (local, source of truth)
                                                        └→ Supabase (PostgREST + GoTrue + Storage)
                                          WorkManager · SyncWorker (background reconcile)
```

MVVM + Repository pattern, wired with Hilt. ViewModels never touch DAOs or the
network directly.

## Build & run

1. Open the project in Android Studio (Giraffe or newer). Min SDK 26 (Android 8.0).
2. Create `local.properties` in the project root:

   ```properties
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   supabaseUrl=YOUR_SUPABASE_PROJECT_URL
   supabaseAnonKey=YOUR_SUPABASE_ANON_KEY
   ```

   `local.properties` is gitignored — it holds machine-specific paths and
   project secrets, which are **never committed**.
3. In Supabase, run `supabase/01_schema.sql` in the SQL Editor to create the
   schema, RLS policies, and the `item-images` Storage bucket.
4. Build: `./gradlew assembleDebug` (or Run from Android Studio).

A prebuilt installable APK is attached to the latest
[GitHub Release](../../releases).

## Tech stack

Kotlin · Jetpack Compose · Hilt · Room · WorkManager · Coroutines/Flow ·
supabase-kt (PostgREST, GoTrue, Storage, Functions) over Ktor · CameraX + ML Kit.
