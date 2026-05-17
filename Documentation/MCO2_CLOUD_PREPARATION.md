# Cloud Preparation

SmartStock+ now includes a cloud-sync preparation layer without enabling actual cloud services yet.

## What Was Added

- `CloudSyncDataSource` abstraction for future Supabase or REST integration
- `CloudSyncStatus` model to expose sync readiness and pending operations
- `NoOpCloudSyncDataSource` placeholder implementation that keeps the app local-first
- repository hooks that queue:
  - item insert
  - item update
  - item delete
  - history records

## Current Behavior

- the app still runs fully offline with Room as the source of truth
- no network sync is performed yet
- the codebase is now prepared for a real cloud connector later
- the planned cloud platform for MCO 2 is `Supabase`, not Firebase

## Next MCO 2 Step

Replace `NoOpCloudSyncDataSource` with a real implementation, for example:

- Supabase sync adapter
- REST API sync adapter

That future implementation only needs to satisfy the existing `CloudSyncDataSource` interface.
