package com.example.smartstock.core.sync

import android.util.Log
import com.example.smartstock.core.network.ConnectivityObserver
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.data.cloud.SupabaseRemoteDataSource
import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Best-effort writer to Supabase. The repository awaits these calls, but
 * the actual HTTP request is dispatched onto this data source's own
 * coroutine scope — so a slow/dead network never freezes the UI flow that
 * just wrote to Room. Failures are logged; SupabaseSyncWorker reconciles
 * any rows whose `updatedAt` advanced past the last successful checkpoint.
 *
 * Push attempts are skipped entirely when the user has cloud sync turned
 * off OR ConnectivityObserver reports offline. Both cases would otherwise
 * waste a coroutine on a doomed 30-second ktor timeout.
 */
@Singleton
class SupabaseCloudSyncDataSource @Inject constructor(
    private val remote: SupabaseRemoteDataSource,
    private val syncManager: SyncManager,
    private val preferences: AppPreferences,
    private val connectivityObserver: ConnectivityObserver
) : CloudSyncDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(initialStatus())
    override val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    init {
        scope.launch {
            preferences.cloudSyncEnabledFlow.collectLatest { enabled ->
                _status.value = if (enabled) {
                    CloudSyncStatus(
                        mode = CloudSyncMode.READY,
                        message = "Connected to Supabase"
                    )
                } else {
                    CloudSyncStatus(
                        mode = CloudSyncMode.NOT_CONFIGURED,
                        message = "Cloud sync paused by user"
                    )
                }
            }
        }
    }

    private fun initialStatus(): CloudSyncStatus = if (preferences.cloudSyncEnabled) {
        CloudSyncStatus(mode = CloudSyncMode.READY, message = "Connected to Supabase")
    } else {
        CloudSyncStatus(mode = CloudSyncMode.NOT_CONFIGURED, message = "Cloud sync paused by user")
    }

    override suspend fun queueUpsertItem(item: InventoryItem) {
        val cloudId = item.cloudId ?: return
        push("upsertItem(${item.id})") { remote.upsertItem(item, cloudId) }
    }

    override suspend fun queueSoftDeleteItem(cloudId: String, deletedAtMillis: Long) {
        push("softDeleteItem($cloudId)") {
            remote.softDeleteItem(cloudId, deletedAtMillis, deletedAtMillis)
        }
    }

    override suspend fun queueHistory(history: ItemHistory, parentItemCloudId: String?) {
        val cloudId = history.cloudId ?: return
        push("upsertHistory(${history.historyId})") {
            remote.upsertHistory(history, cloudId, parentItemCloudId)
        }
    }

    override suspend fun queueUsageRecord(
        record: ItemUsageRecord,
        parentItemCloudId: String?,
        barcodeCloudId: String?
    ) {
        val cloudId = record.cloudId ?: return
        push("upsertUsageRecord(${record.id})") {
            remote.upsertUsageRecord(record, cloudId, parentItemCloudId, barcodeCloudId)
        }
    }

    override suspend fun queueLinkedBarcode(
        barcode: LinkedBarcode,
        parentItemCloudId: String?
    ) {
        val cloudId = barcode.cloudId ?: return
        push("upsertLinkedBarcode(${barcode.id})") {
            remote.upsertLinkedBarcode(barcode, cloudId, parentItemCloudId)
        }
    }

    override suspend fun queueSoftDeleteLinkedBarcode(cloudId: String, deletedAtMillis: Long) {
        push("softDeleteLinkedBarcode($cloudId)") {
            remote.softDeleteLinkedBarcode(cloudId, deletedAtMillis, deletedAtMillis)
        }
    }

    override suspend fun queueUpsertCategory(category: CategoryEntity) {
        val cloudId = category.cloudId ?: return
        push("upsertCategory(${category.id})") {
            remote.upsertCategory(category, cloudId)
        }
    }

    override suspend fun queueSoftDeleteCategory(cloudId: String, deletedAtMillis: Long) {
        push("softDeleteCategory($cloudId)") {
            remote.softDeleteCategory(cloudId, deletedAtMillis, deletedAtMillis)
        }
    }

    override suspend fun queueUpsertAssetStatus(status: AssetStatusEntity) {
        val cloudId = status.cloudId ?: return
        push("upsertAssetStatus(${status.id})") {
            remote.upsertAssetStatus(status, cloudId)
        }
    }

    override suspend fun queueSoftDeleteAssetStatus(cloudId: String, deletedAtMillis: Long) {
        push("softDeleteAssetStatus($cloudId)") {
            remote.softDeleteAssetStatus(cloudId, deletedAtMillis, deletedAtMillis)
        }
    }

    override suspend fun requestSync() {
        if (!preferences.cloudSyncEnabled) return
        syncManager.requestImmediateSync()
    }

    /**
     * Fire-and-forget. Returns immediately so the caller (repository) is
     * never blocked on a network round-trip. Skips the attempt when sync
     * is disabled or the device is offline — the worker will pick the
     * row up later via its `updatedAt > lastSyncedAt` push pass.
     */
    private suspend fun push(opName: String, block: suspend () -> Unit) {
        if (!preferences.cloudSyncEnabled) return
        if (!connectivityObserver.isOnline.first()) return
        scope.launch {
            runCatching { block() }
                .onSuccess { preferences.lastSyncError = null }
                .onFailure {
                    // Treat coroutine cancellation as a normal teardown
                    // (e.g. the data source's scope being shut down on
                    // sign-out). Don't surface it as a user-facing error.
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    logFailure(opName, it)
                }
        }
    }

    private fun logFailure(op: String, error: Throwable) {
        Log.w(TAG, "Direct cloud push failed for $op — will reconcile on next sync", error)
        preferences.lastSyncError = "$op: ${error.message ?: error::class.java.simpleName}"
    }

    private companion object {
        const val TAG = "SupabaseCloudSync"
    }
}
