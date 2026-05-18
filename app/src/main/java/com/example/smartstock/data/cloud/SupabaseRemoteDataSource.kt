package com.example.smartstock.data.cloud

import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct postgrest calls for the four sync-tracked tables.
 * The repository layer uses this to push every CRUD locally to the cloud.
 * Failures bubble up so the caller can fall back to the local pending_sync
 * queue (drained later by SyncWorker).
 */
@Singleton
class SupabaseRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient
) {

    private fun requireAuth(): Boolean = supabase.auth.currentSessionOrNull() != null

    /**
     * Pushes the item, then mirrors its photo to Supabase Storage so other
     * devices can render it. The cloud row's image_uri holds the storage
     * object key ("<cloudId>.jpg"), never the device-local file:// path.
     * Image transfer is best-effort: a failed upload never fails the row
     * push (the next item edit retries it).
     */
    suspend fun upsertItem(item: InventoryItem, cloudId: String): String {
        if (!requireAuth()) return cloudId
        // Reconcile by asset_code first. Items are otherwise keyed only by
        // the client-generated cloudId, so a wiped/reinstalled/second-device
        // copy re-pushes the SAME item under a fresh UUID — creating cloud
        // duplicates. If this team already has a live row with this asset
        // code (RLS scopes the lookup), reuse its id so the push is an
        // idempotent UPDATE. Returns the effective id for the caller to
        // persist locally. Blank asset codes are not reconciled.
        val effectiveId = if (item.assetCode.isNotBlank()) {
            supabase.postgrest.from(TABLE_ITEMS).select {
                filter { eq("asset_code", item.assetCode) }
            }.decodeList<CloudInventoryItem>()
                .firstOrNull { it.deletedAt == null }
                ?.id ?: cloudId
        } else cloudId

        val local = item.imageUri
        val localFile = local
            ?.takeIf { it.startsWith("file://") }
            ?.let { File(it.removePrefix("file://")) }
            ?.takeIf { it.exists() }
        // file:// → storage key; an already-remote key → keep; none → null.
        val remoteKey = when {
            localFile != null -> "$effectiveId.jpg"
            local != null && !local.startsWith("file://") -> local
            else -> null
        }
        supabase.postgrest.from(TABLE_ITEMS).upsert(item.toCloudDto(effectiveId, remoteKey))
        if (localFile != null) {
            runCatching {
                supabase.storage.from(BUCKET_ITEM_IMAGES)
                    .upload(remoteKey!!, localFile.readBytes(), upsert = true)
            }.onFailure { Log.w(TAG, "image upload failed for $effectiveId", it) }
        } else if (item.imageUri == null) {
            // Photo removed — purge the stored object so it doesn't linger
            // in Storage and can't be re-pulled by other devices.
            runCatching {
                supabase.storage.from(BUCKET_ITEM_IMAGES)
                    .delete(listOf("$effectiveId.jpg"))
            }.onFailure { Log.w(TAG, "image delete failed for $effectiveId", it) }
        }
        return effectiveId
    }

    /** Downloads a stored item image. Returns null on any failure. */
    suspend fun downloadItemImage(storageKey: String): ByteArray? {
        if (!requireAuth()) return null
        return runCatching {
            supabase.storage.from(BUCKET_ITEM_IMAGES).downloadAuthenticated(storageKey)
        }.getOrElse {
            Log.w(TAG, "image download failed for $storageKey", it)
            null
        }
    }

    suspend fun softDeleteItem(cloudId: String, deletedAtMillis: Long, updatedAtMillis: Long) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_ITEMS).update(
            update = {
                set("deleted_at", deletedAtMillis.toIso())
                set("updated_at", updatedAtMillis.toIso())
            }
        ) { filter { eq("id", cloudId) } }
    }

    suspend fun upsertHistory(history: ItemHistory, cloudId: String, parentItemCloudId: String?) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_HISTORY).upsert(
            history.toCloudDto(cloudId, parentItemCloudId)
        )
    }

    suspend fun upsertUsageRecord(
        record: ItemUsageRecord,
        cloudId: String,
        parentItemCloudId: String?,
        barcodeCloudId: String?
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_USAGE).upsert(
            record.toCloudDto(cloudId, parentItemCloudId, barcodeCloudId)
        )
    }

    suspend fun softDeleteUsageRecord(
        cloudId: String,
        deletedAtMillis: Long,
        updatedAtMillis: Long
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_USAGE).update(
            update = {
                set("deleted_at", deletedAtMillis.toIso())
                set("updated_at", updatedAtMillis.toIso())
            }
        ) { filter { eq("id", cloudId) } }
    }

    suspend fun upsertLinkedBarcode(
        barcode: LinkedBarcode,
        cloudId: String,
        parentItemCloudId: String?
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_BARCODES).upsert(
            barcode.toCloudDto(cloudId, parentItemCloudId)
        )
    }

    suspend fun softDeleteLinkedBarcode(
        cloudId: String,
        deletedAtMillis: Long,
        updatedAtMillis: Long
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_BARCODES).update(
            update = {
                set("deleted_at", deletedAtMillis.toIso())
                set("updated_at", updatedAtMillis.toIso())
            }
        ) { filter { eq("id", cloudId) } }
    }

    // ---- Pull (sync) queries — return all rows whose updated_at is newer than
    // the local checkpoint. Soft-deleted rows are included so we can mirror
    // the deletion locally; the worker filters on deleted_at.

    suspend fun fetchItemsSince(sinceMillis: Long): List<CloudInventoryItem> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_ITEMS).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    /**
     * Fetch a single inventory item by its cloud UUID, regardless of
     * updated_at. Used by the sync worker to pull a child row's parent
     * item on-demand when it isn't resolvable locally (otherwise the
     * child would be silently dropped and, once the checkpoint advances,
     * lost forever — the "Staff Reports stays empty" bug). RLS still
     * scopes this to the caller's team.
     */
    suspend fun fetchItemById(cloudId: String): CloudInventoryItem? {
        if (!requireAuth()) return null
        return supabase.postgrest.from(TABLE_ITEMS).select {
            filter { eq("id", cloudId) }
        }.decodeList<CloudInventoryItem>().firstOrNull()
    }

    /**
     * The authoritative set of live (not soft-deleted) item cloud-ids for
     * the caller's team, independent of any sync checkpoint. Used to prune
     * local rows that were deleted server-side but linger after an APK
     * update-install (old DB + old non-zero checkpoint survive, so the
     * incremental pull never sees — and never removes — them).
     *
     * Returns null on any failure so the caller can skip pruning rather
     * than mass-deleting on a transient network/auth hiccup.
     */
    suspend fun fetchAllItemIds(): List<String>? {
        if (!requireAuth()) return null
        return runCatching {
            supabase.postgrest.from(TABLE_ITEMS).select()
                .decodeList<CloudInventoryItem>()
                .filter { it.deletedAt == null }
                .map { it.id }
        }.getOrNull()
    }

    /**
     * The set of usage-record cloud-ids that actually made it to the
     * server, RLS-scoped to the caller's team. The push-side reconciler
     * compares this against the local synced rows so any record whose
     * original upload failed (and that the checkpoint has moved past) is
     * re-pushed instead of being stranded — the cause of an empty
     * item_usage_records table / blank Reports.
     */
    suspend fun fetchAllUsageRecordIds(): List<String>? {
        if (!requireAuth()) return null
        return runCatching {
            supabase.postgrest.from(TABLE_USAGE).select()
                .decodeList<CloudUsageRecord>()
                .map { it.id }
        }.getOrNull()
    }

    suspend fun fetchHistorySince(sinceMillis: Long): List<CloudItemHistory> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_HISTORY).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun fetchUsageRecordsSince(sinceMillis: Long): List<CloudUsageRecord> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_USAGE).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun fetchLinkedBarcodesSince(sinceMillis: Long): List<CloudLinkedBarcode> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_BARCODES).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    /**
     * Reference rows are keyed by a client-generated UUID, but the cloud
     * also enforces a per-team unique index on (team_id, lower(name)).
     * If another session already created this name, a blind insert with
     * our divergent UUID violates that index and poisons the whole sync.
     * So reconcile by name first: when the team already has this category
     * (RLS scopes the lookup to our team), reuse the server's id so the
     * push becomes an idempotent UPDATE. Returns the effective cloud id
     * the caller must persist locally.
     */
    suspend fun upsertCategory(category: CategoryEntity, cloudId: String): String {
        if (!requireAuth()) return cloudId
        val existingId = supabase.postgrest.from(TABLE_CATEGORIES).select {
            filter { eq("name", category.name) }
        }.decodeList<CloudCategory>()
            .firstOrNull { it.deletedAt == null }
            ?.id
        val effectiveId = existingId ?: cloudId
        supabase.postgrest.from(TABLE_CATEGORIES).upsert(category.toCloudDto(effectiveId))
        return effectiveId
    }

    suspend fun softDeleteCategory(
        cloudId: String,
        deletedAtMillis: Long,
        updatedAtMillis: Long
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_CATEGORIES).update(
            update = {
                set("deleted_at", deletedAtMillis.toIso())
                set("updated_at", updatedAtMillis.toIso())
            }
        ) { filter { eq("id", cloudId) } }
    }

    suspend fun fetchCategoriesSince(sinceMillis: Long): List<CloudCategory> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_CATEGORIES).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    /** See [upsertCategory] — same per-team name reconciliation. */
    suspend fun upsertAssetStatus(status: AssetStatusEntity, cloudId: String): String {
        if (!requireAuth()) return cloudId
        val existingId = supabase.postgrest.from(TABLE_STATUSES).select {
            filter { eq("name", status.name) }
        }.decodeList<CloudAssetStatus>()
            .firstOrNull { it.deletedAt == null }
            ?.id
        val effectiveId = existingId ?: cloudId
        supabase.postgrest.from(TABLE_STATUSES).upsert(status.toCloudDto(effectiveId))
        return effectiveId
    }

    suspend fun softDeleteAssetStatus(
        cloudId: String,
        deletedAtMillis: Long,
        updatedAtMillis: Long
    ) {
        if (!requireAuth()) return
        supabase.postgrest.from(TABLE_STATUSES).update(
            update = {
                set("deleted_at", deletedAtMillis.toIso())
                set("updated_at", updatedAtMillis.toIso())
            }
        ) { filter { eq("id", cloudId) } }
    }

    suspend fun fetchAssetStatusesSince(sinceMillis: Long): List<CloudAssetStatus> {
        if (!requireAuth()) return emptyList()
        return supabase.postgrest.from(TABLE_STATUSES).select {
            filter { gt("updated_at", sinceMillis.toIso()) }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    private companion object {
        const val TAG = "SupabaseRemote"
        const val TABLE_ITEMS = "inventory_items"
        const val TABLE_HISTORY = "item_history"
        const val TABLE_USAGE = "item_usage_records"
        const val TABLE_BARCODES = "linked_barcodes"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_STATUSES = "asset_statuses"
        const val BUCKET_ITEM_IMAGES = "item-images"
    }
}
