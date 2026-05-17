package com.example.smartstock.core.sync

import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CloudSyncMode {
    NOT_CONFIGURED,
    READY
}

data class CloudSyncStatus(
    val mode: CloudSyncMode,
    val pendingOperations: Int = 0,
    val lastSyncAt: Long? = null,
    val message: String? = null
)

interface CloudSyncDataSource {
    val status: StateFlow<CloudSyncStatus>

    suspend fun queueUpsertItem(item: InventoryItem)

    suspend fun queueSoftDeleteItem(cloudId: String, deletedAtMillis: Long)

    suspend fun queueHistory(history: ItemHistory, parentItemCloudId: String?)

    suspend fun queueUsageRecord(
        record: ItemUsageRecord,
        parentItemCloudId: String?,
        barcodeCloudId: String?
    )

    suspend fun queueLinkedBarcode(barcode: LinkedBarcode, parentItemCloudId: String?)

    suspend fun queueSoftDeleteLinkedBarcode(cloudId: String, deletedAtMillis: Long)

    suspend fun queueUpsertCategory(category: CategoryEntity)

    suspend fun queueSoftDeleteCategory(cloudId: String, deletedAtMillis: Long)

    suspend fun queueUpsertAssetStatus(status: AssetStatusEntity)

    suspend fun queueSoftDeleteAssetStatus(cloudId: String, deletedAtMillis: Long)

    suspend fun requestSync()
}

class NoOpCloudSyncDataSource : CloudSyncDataSource {
    private val _status = MutableStateFlow(
        CloudSyncStatus(
            mode = CloudSyncMode.NOT_CONFIGURED,
            message = "Cloud sync not connected yet"
        )
    )

    override val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    override suspend fun queueUpsertItem(item: InventoryItem) = Unit

    override suspend fun queueSoftDeleteItem(cloudId: String, deletedAtMillis: Long) = Unit

    override suspend fun queueHistory(history: ItemHistory, parentItemCloudId: String?) = Unit

    override suspend fun queueUsageRecord(
        record: ItemUsageRecord,
        parentItemCloudId: String?,
        barcodeCloudId: String?
    ) = Unit

    override suspend fun queueLinkedBarcode(barcode: LinkedBarcode, parentItemCloudId: String?) = Unit

    override suspend fun queueSoftDeleteLinkedBarcode(cloudId: String, deletedAtMillis: Long) = Unit

    override suspend fun queueUpsertCategory(category: CategoryEntity) = Unit

    override suspend fun queueSoftDeleteCategory(cloudId: String, deletedAtMillis: Long) = Unit

    override suspend fun queueUpsertAssetStatus(status: AssetStatusEntity) = Unit

    override suspend fun queueSoftDeleteAssetStatus(cloudId: String, deletedAtMillis: Long) = Unit

    override suspend fun requestSync() = Unit
}
