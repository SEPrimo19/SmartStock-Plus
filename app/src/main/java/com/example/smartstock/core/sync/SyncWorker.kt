package com.example.smartstock.core.sync

import android.content.Context
import android.util.Log
import java.io.File
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.data.cloud.CloudAssetStatus
import com.example.smartstock.data.cloud.CloudCategory
import com.example.smartstock.data.cloud.CloudInventoryItem
import com.example.smartstock.data.cloud.CloudItemHistory
import com.example.smartstock.data.cloud.CloudLinkedBarcode
import com.example.smartstock.data.cloud.CloudUsageRecord
import com.example.smartstock.data.cloud.SupabaseRemoteDataSource
import com.example.smartstock.data.cloud.toEntity
import com.example.smartstock.data.dao.InventoryDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

/**
 * Bidirectional Supabase sync worker.
 *
 * Pull: fetch every row from the four sync-tracked tables whose
 * `updated_at` exceeds [AppPreferences.lastSyncedAtMillis], merging into
 * the local Room db with last-write-wins semantics keyed on `cloudId`.
 *
 * Push: any local rows whose `updatedAt` advanced past the same checkpoint
 * are re-pushed. This catches cases where Phase 5's best-effort cloud push
 * silently failed (no network, server hiccup, etc).
 *
 * Gating: respects [AppPreferences.cloudSyncEnabled]. When the user turns
 * sync off, this worker exits immediately as a success — it does not
 * touch local data and does not advance the checkpoint, so the next time
 * the user re-enables sync it picks up from the same point.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventoryDao: InventoryDao,
    private val remote: SupabaseRemoteDataSource,
    private val supabase: SupabaseClient,
    private val preferences: AppPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SmartStockSync"
        const val WORK_NAME_PERIODIC = "smartstock_periodic_sync"
        const val WORK_NAME_ONE_TIME = "smartstock_one_time_sync"
    }

    override suspend fun doWork(): Result {
        if (!preferences.cloudSyncEnabled) {
            Log.d(TAG, "Cloud sync disabled by user — skipping")
            return Result.success()
        }
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.d(TAG, "No active session — skipping")
            return Result.success()
        }

        val checkpoint = preferences.lastSyncedAtMillis
        // Capture wall-clock at start so any local rows touched during sync
        // are still ahead of the new checkpoint (and picked up next run).
        val syncStartedAt = System.currentTimeMillis()

        return runCatching {
            pullItems(checkpoint)
            pullLinkedBarcodes(checkpoint) // before usage records (FK lookup)
            pullHistory(checkpoint)
            pullUsageRecords(checkpoint)
            pullCategories(checkpoint)
            pullAssetStatuses(checkpoint)
            val anyPushFailed = pushLocalChanges(checkpoint)

            if (anyPushFailed) {
                // Don't move the checkpoint forward — if we did, the failed
                // rows would never be picked up again because their updatedAt
                // would no longer satisfy `updatedAt > lastSyncedAt`. Asking
                // WorkManager to retry gets us another shot under backoff.
                Log.w(TAG, "Some pushes failed; leaving checkpoint at $checkpoint")
                if (runAttemptCount >= 3) Result.failure() else Result.retry()
            } else {
                preferences.lastSyncedAtMillis = syncStartedAt
                Log.d(TAG, "Sync completed; checkpoint advanced to $syncStartedAt")
                Result.success()
            }
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "Sync failed", error)
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private suspend fun pullItems(since: Long) {
        val rows: List<CloudInventoryItem> = remote.fetchItemsSince(since)
        for (row in rows) {
            upsertPulledItem(row)
        }
        // Prune items deleted server-side — but ONLY on a full pull
        // (since == 0: fresh install / logout / account switch), where the
        // fetched `rows` are the cloud's authoritative set for this team.
        //
        // The previous checkpoint-independent prune was actively
        // destructive: on every refresh it deleted any local item whose
        // cloudId wasn't in the cloud yet (e.g. an item whose push hadn't
        // landed), and because item_usage_records / item_history FK onto
        // inventory_items with ON DELETE CASCADE, that silently wiped the
        // item's usage records too — exactly the "Reports records vanish
        // after refresh, item_usage_records stays empty" report. Legacy
        // mock data from pre-sync builds is already handled once by
        // MIGRATION_10_11, so the aggressive version is not needed.
        //
        // Extra safety: if a full pull returns zero rows but we still hold
        // synced items locally, that's far more likely an empty/transient
        // cloud or a team-scope blip than the user deleting everything —
        // skip the prune so we never mass-delete (and cascade-wipe usage
        // history). Offline-created rows (cloudId null) are left untouched.
        if (since == 0L && rows.isNotEmpty()) {
            val keep = rows.mapTo(HashSet()) { it.id }
            inventoryDao.getAllSyncedItems()
                .filter { it.cloudId != null && it.cloudId !in keep }
                .forEach { inventoryDao.deleteItem(it) }
        }
    }

    /**
     * Turns the cloud image_uri (a Supabase Storage object key) into a
     * device-local file:// path the UI/Coil can render. Downloads once and
     * caches in filesDir/images. Falls back to the existing local image on
     * any miss so a transient failure never blanks a photo.
     */
    private suspend fun resolvePulledImage(
        remoteKey: String?,
        cloudId: String,
        existingLocal: String?
    ): String? {
        // We only reach here when the cloud row won last-write-wins, so a
        // null/blank image_uri is the authoritative "photo removed" state —
        // clear it locally (and drop the stale cache file) instead of
        // keeping the old image forever.
        if (remoteKey.isNullOrBlank()) {
            runCatching {
                File(File(applicationContext.filesDir, "images"),
                     "cloud_$cloudId.jpg").delete()
            }
            return null
        }
        // A file:// value is a legacy local path from another device — unusable here.
        if (remoteKey.startsWith("file://")) return existingLocal
        val dir = File(applicationContext.filesDir, "images")
        val dest = File(dir, "cloud_$cloudId.jpg")
        if (dest.exists() && dest.length() > 0) return "file://${dest.absolutePath}"
        val bytes = remote.downloadItemImage(remoteKey) ?: return existingLocal
        return runCatching {
            dir.mkdirs()
            dest.writeBytes(bytes)
            "file://${dest.absolutePath}"
        }.getOrElse {
            Log.w(TAG, "caching pulled image failed for $cloudId", it)
            existingLocal
        }
    }

    private suspend fun pullLinkedBarcodes(since: Long) {
        val rows: List<CloudLinkedBarcode> = remote.fetchLinkedBarcodesSince(since)
        for (row in rows) {
            val parentLocalId = resolveItemLocalId(row.itemId, row.itemLocalId) ?: continue
            val local = inventoryDao.findLinkedBarcodeByCloudId(row.id)
            if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) continue
            inventoryDao.upsertLinkedBarcode(
                row.toEntity(localId = local?.id ?: 0, parentLocalId = parentLocalId)
            )
        }
    }

    private suspend fun pullHistory(since: Long) {
        val rows: List<CloudItemHistory> = remote.fetchHistorySince(since)
        for (row in rows) {
            val parentLocalId = resolveItemLocalId(row.itemId, row.itemLocalId) ?: continue
            val local = inventoryDao.findHistoryByCloudId(row.id)
            if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) continue
            inventoryDao.upsertHistory(
                row.toEntity(localId = local?.historyId ?: 0, parentLocalId = parentLocalId)
            )
        }
    }

    private suspend fun pullUsageRecords(since: Long) {
        val rows: List<CloudUsageRecord> = remote.fetchUsageRecordsSince(since)
        for (row in rows) {
            val parentLocalId = resolveItemLocalId(row.itemId, row.itemLocalId) ?: continue
            val barcodeLocalId = row.barcodeId?.let {
                inventoryDao.findLinkedBarcodeByCloudId(it)?.id
            }
            val local = inventoryDao.findUsageRecordByCloudId(row.id)
            if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) continue
            inventoryDao.upsertUsageRecord(
                row.toEntity(
                    localId = local?.id ?: 0,
                    parentLocalId = parentLocalId,
                    barcodeLocalId = barcodeLocalId
                )
            )
        }
    }

    private suspend fun pullCategories(since: Long) {
        val rows: List<CloudCategory> = remote.fetchCategoriesSince(since)
        for (row in rows) {
            // First match by cloudId (the canonical link). If absent, fall
            // back to a name match so a locally-seeded "Equipment" doesn't
            // duplicate a cloud row with the same name (categories.name is
            // unique on the local side).
            val local = inventoryDao.findCategoryByCloudId(row.id)
                ?: inventoryDao.findCategoryByName(row.name)
            if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) continue
            inventoryDao.upsertCategory(row.toEntity(localId = local?.id ?: 0))
        }
    }

    private suspend fun pullAssetStatuses(since: Long) {
        val rows: List<CloudAssetStatus> = remote.fetchAssetStatusesSince(since)
        for (row in rows) {
            val local = inventoryDao.findAssetStatusByCloudId(row.id)
                ?: inventoryDao.findAssetStatusByName(row.name)
            if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) continue
            inventoryDao.upsertAssetStatus(row.toEntity(localId = local?.id ?: 0))
        }
    }

    /**
     * Returns true if any single push raised. Caller uses this to decide
     * whether to advance the lastSyncedAt checkpoint.
     */
    private suspend fun pushLocalChanges(since: Long): Boolean {
        var anyFailed = false

        // CancellationException must propagate so structured concurrency
        // can tear down the worker cleanly when WorkManager preempts us
        // (e.g. `enqueueUniqueWork(REPLACE)` from a fresh Sync now tap).
        // Without the rethrow, runCatching would swallow it and we'd
        // mis-report it as a real push failure to the user.
        fun recordFailure(label: String, error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "push $label failed", error)
            preferences.lastSyncError =
                "$label: ${error.message ?: error::class.java.simpleName}"
            anyFailed = true
        }

        // Items first so children always have a valid parent cloudId.
        for (item in inventoryDao.getItemsModifiedSince(since)) {
            val cloudId = item.cloudId ?: continue
            runCatching {
                val effectiveId = remote.upsertItem(item, cloudId)
                if (effectiveId != cloudId) {
                    inventoryDao.setItemCloudId(item.id, effectiveId)
                }
            }.onFailure { recordFailure("item ${item.id}", it) }
        }
        for (barcode in inventoryDao.getLinkedBarcodesModifiedSince(since)) {
            val cloudId = barcode.cloudId ?: continue
            val parentCloudId = inventoryDao.getItemByIdRaw(barcode.itemId)?.cloudId
            runCatching { remote.upsertLinkedBarcode(barcode, cloudId, parentCloudId) }
                .onFailure { recordFailure("barcode ${barcode.id}", it) }
        }
        for (history in inventoryDao.getHistoryModifiedSince(since)) {
            val cloudId = history.cloudId ?: continue
            val parentCloudId = inventoryDao.getItemByIdRaw(history.itemId)?.cloudId
            runCatching { remote.upsertHistory(history, cloudId, parentCloudId) }
                .onFailure { recordFailure("history ${history.historyId}", it) }
        }
        for (record in inventoryDao.getUsageRecordsModifiedSince(since)) {
            val cloudId = record.cloudId ?: continue
            val parentCloudId = inventoryDao.getItemByIdRaw(record.itemId)?.cloudId
            val barcodeCloudId = record.barcodeId?.let {
                inventoryDao.getLinkedBarcodeById(it)?.cloudId
            }
            runCatching {
                remote.upsertUsageRecord(record, cloudId, parentCloudId, barcodeCloudId)
            }.onFailure { recordFailure("usage ${record.id}", it) }
        }

        // Self-heal stranded usage records. The incremental loop above only
        // sees rows with updatedAt > checkpoint, so a checkout whose first
        // push failed (offline, or the parent item not yet in the cloud)
        // is never retried once the checkpoint advances — leaving
        // item_usage_records empty and Reports blank for every other user.
        // Compare local synced rows against the cloud's authoritative id
        // set and re-upload anything missing. A null fetch (network/auth
        // hiccup) skips this so we never thrash.
        val cloudUsageIds = remote.fetchAllUsageRecordIds()
        if (cloudUsageIds != null) {
            val present = cloudUsageIds.toHashSet()
            for (record in inventoryDao.getAllSyncedUsageRecords()) {
                val cloudId = record.cloudId ?: continue
                if (cloudId in present) continue
                val parentCloudId = inventoryDao.getItemByIdRaw(record.itemId)?.cloudId
                val barcodeCloudId = record.barcodeId?.let {
                    inventoryDao.getLinkedBarcodeById(it)?.cloudId
                }
                runCatching {
                    remote.upsertUsageRecord(record, cloudId, parentCloudId, barcodeCloudId)
                }.onFailure { recordFailure("usage(reconcile) ${record.id}", it) }
            }
        }

        for (category in inventoryDao.getCategoriesModifiedSince(since)) {
            val cloudId = category.cloudId ?: continue
            runCatching {
                val effectiveId = remote.upsertCategory(category, cloudId)
                if (effectiveId != cloudId) {
                    inventoryDao.setCategoryCloudId(category.id, effectiveId)
                }
            }.onFailure { recordFailure("category ${category.id}", it) }
        }
        for (status in inventoryDao.getAssetStatusesModifiedSince(since)) {
            val cloudId = status.cloudId ?: continue
            runCatching {
                val effectiveId = remote.upsertAssetStatus(status, cloudId)
                if (effectiveId != cloudId) {
                    inventoryDao.setAssetStatusCloudId(status.id, effectiveId)
                }
            }.onFailure { recordFailure("status ${status.id}", it) }
        }

        if (!anyFailed) preferences.lastSyncError = null
        return anyFailed
    }

    /**
     * Resolve the parent inventory_item local row id given the cloud id
     * (preferred) or the item_local_id hint the original writer included.
     * Returns null if neither resolves — the caller should skip that row;
     * the next sync run will pick it up after the parent has landed.
     */
    /**
     * Upsert one pulled cloud item into Room and return its local id.
     * Match by cloudId first; fall back to asset_code so a cloud row
     * reconciles with an existing local item instead of creating a second
     * local copy (mirror of the push-side asset_code reconciliation —
     * both directions stay dup-free). Last-write-wins on updatedAt.
     */
    private suspend fun upsertPulledItem(row: CloudInventoryItem): Int {
        val local = inventoryDao.findItemByCloudId(row.id)
            ?: row.assetCode.takeIf { it.isNotBlank() }
                ?.let { inventoryDao.findItemByAssetCodeRaw(it) }
        if (local != null && local.updatedAt >= row.updatedAt.toMillisOrZero()) {
            return local.id
        }
        val merged = row.toEntity(localId = local?.id ?: 0)
        val image = resolvePulledImage(row.imageUri, row.id, local?.imageUri)
        return inventoryDao.upsertItem(merged.copy(imageUri = image)).toInt()
    }

    private suspend fun resolveItemLocalId(parentCloudId: String?, hint: Int?): Int? {
        if (parentCloudId != null) {
            val byCloud = inventoryDao.findItemByCloudId(parentCloudId)
            if (byCloud != null) return byCloud.id
        }
        if (hint != null && hint > 0) {
            val byHint = inventoryDao.getItemByIdRaw(hint)
            if (byHint != null && byHint.cloudId == parentCloudId) return byHint.id
        }
        // Parent not present locally. This happens on a device that didn't
        // create the parent (e.g. Staff pulling Admin's data) when the
        // parent landed outside this checkpoint window or a page boundary.
        // Without this, the child row is dropped and — once the checkpoint
        // advances past it — never re-fetched, so Reports stays empty with
        // no error. Pull the parent on-demand by UUID and resolve again.
        if (parentCloudId != null) {
            val cloudParent = runCatching { remote.fetchItemById(parentCloudId) }
                .getOrNull()
            if (cloudParent != null) return upsertPulledItem(cloudParent)
        }
        return null
    }

    private fun String.toMillisOrZero(): Long = runCatching {
        java.time.Instant.parse(this).toEpochMilli()
    }.getOrDefault(0L)
}
