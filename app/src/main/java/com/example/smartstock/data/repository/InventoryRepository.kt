package com.example.smartstock.data.repository

import com.example.smartstock.core.sync.CloudSyncDataSource
import com.example.smartstock.core.sync.CloudSyncStatus
import com.example.smartstock.core.sync.NoOpCloudSyncDataSource
import com.example.smartstock.data.dao.InventoryDao
import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.data.entity.LocalUser
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class InventoryRepository(
    private val inventoryDao: InventoryDao,
    private val cloudSyncDataSource: CloudSyncDataSource = NoOpCloudSyncDataSource()
) {

    val allItems: Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    val availableItems: Flow<List<InventoryItem>> = inventoryDao.getAvailableItems()
    val inUseItems: Flow<List<InventoryItem>> = inventoryDao.getInUseItems()
    val categoryNames: Flow<List<String>> = inventoryDao.getAllCategoryNames()
    val statusNames: Flow<List<String>> = inventoryDao.getAllStatusNames()
    val allHistory: Flow<List<ItemHistory>> = inventoryDao.getAllHistory()
    val allUsers: Flow<List<LocalUser>> = inventoryDao.getAllUsers()
    val activeUsers: Flow<List<LocalUser>> = inventoryDao.getActiveUsers()
    val activeUsageRecords: Flow<List<ItemUsageRecord>> = inventoryDao.getActiveUsageRecords()
    val allUsageRecords: Flow<List<ItemUsageRecord>> = inventoryDao.getAllUsageRecords()
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = cloudSyncDataSource.status
    val pendingSyncCount: Flow<Int> = inventoryDao.getPendingSyncCount()

    fun getItemsByCategory(category: String): Flow<List<InventoryItem>> {
        return inventoryDao.getItemsByCategory(category)
    }

    /**
     * Wipes every locally-cached tenant row. Called on logout and on
     * account switch so a different account on the same device never
     * sees the previous tenant's inventory, history, or pending pushes.
     */
    suspend fun clearLocalData() {
        inventoryDao.clearTenantData()
    }

    fun getItemsByStatus(status: String): Flow<List<InventoryItem>> {
        return inventoryDao.getItemsByStatus(status)
    }

    fun getItem(id: Int): Flow<InventoryItem?> {
        return inventoryDao.getItemById(id)
    }

    fun getItemByAssetCode(assetCode: String): Flow<InventoryItem?> {
        return inventoryDao.getItemByAssetCode(assetCode)
    }

    fun searchItems(query: String): Flow<List<InventoryItem>> {
        return inventoryDao.searchItems(query)
    }

    suspend fun insertItem(item: InventoryItem): Long {
        val now = System.currentTimeMillis()
        // Normalize the category string so trailing spaces ("Equipment ")
        // can't fork from the canonical ("Equipment"). Without this, the
        // FilterChip set on AddEditItem fills with near-duplicates.
        val normalizedCategory = item.category.trim()
        ensureCategoryRegistered(normalizedCategory)
        val cloudId = item.cloudId ?: UUID.randomUUID().toString()
        val prepared = item.copy(
            category = normalizedCategory,
            cloudId = cloudId,
            updatedAt = now,
            lastUpdated = if (item.lastUpdated == 0L) now else item.lastUpdated,
            createdAt = if (item.createdAt == 0L) now else item.createdAt
        )
        val itemId = inventoryDao.insertItem(prepared)
        val persisted = prepared.copy(id = itemId.toInt())
        cloudSyncDataSource.queueUpsertItem(persisted)
        return itemId
    }

    suspend fun updateItem(item: InventoryItem): Int {
        val now = System.currentTimeMillis()
        val normalizedCategory = item.category.trim()
        ensureCategoryRegistered(normalizedCategory)
        val cloudId = item.cloudId ?: UUID.randomUUID().toString()
        val prepared = item.copy(
            category = normalizedCategory,
            cloudId = cloudId,
            updatedAt = now,
            lastUpdated = now
        )
        val updatedCount = inventoryDao.updateItem(prepared)
        if (updatedCount > 0) {
            cloudSyncDataSource.queueUpsertItem(prepared)
        }
        return updatedCount
    }

    suspend fun restoreItem(item: InventoryItem) {
        val now = System.currentTimeMillis()
        val restored = item.copy(
            deletedAt = null,
            updatedAt = now,
            lastUpdated = now
        )
        inventoryDao.updateItem(restored)
        item.cloudId?.let { cloudSyncDataSource.queueUpsertItem(restored) }
    }

    // The Specify Category UX only writes the string into InventoryItem.category;
    // it never touches the categories table. Without this hop, a user-typed
    // "Electronics" lives only inside individual inventory_items rows and the
    // categories table stays at the seeded defaults. Inserting here means the
    // new category gets a cloudId + queued cloud push exactly like the
    // defaults do.
    private suspend fun ensureCategoryRegistered(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (inventoryDao.findCategoryByName(trimmed) != null) return
        insertCategory(trimmed)
    }

    suspend fun deleteItem(item: InventoryItem): Int {
        // Soft-delete locally so the row stays available for sync replay; the
        // cloud row mirrors the same deleted_at timestamp via the sync queue.
        val now = System.currentTimeMillis()
        val rowsAffected = inventoryDao.softDeleteItem(item.id, now)
        if (rowsAffected > 0) {
            val cloudId = item.cloudId ?: run {
                val freshId = UUID.randomUUID().toString()
                inventoryDao.setItemCloudId(item.id, freshId)
                freshId
            }
            cloudSyncDataSource.queueSoftDeleteItem(cloudId, now)
        }
        return rowsAffected
    }

    fun getHistoryDataItem(itemId: Int): Flow<List<ItemHistory>> {
        return inventoryDao.getHistoryDataItem(itemId)
    }

    suspend fun insertHistory(history: ItemHistory) {
        val now = System.currentTimeMillis()
        val cloudId = history.cloudId ?: UUID.randomUUID().toString()
        val prepared = history.copy(
            cloudId = cloudId,
            updatedAt = now
        )
        val rowId = inventoryDao.insertHistory(prepared)
        val persisted = prepared.copy(historyId = rowId.toInt())
        val parentCloudId = inventoryDao.getItemByIdRaw(history.itemId)?.cloudId
        cloudSyncDataSource.queueHistory(persisted, parentCloudId)
    }

    suspend fun requestCloudSync() {
        cloudSyncDataSource.requestSync()
    }

    suspend fun insertUser(user: LocalUser): Long {
        return inventoryDao.insertUser(user)
    }

    suspend fun updateUser(user: LocalUser): Int {
        return inventoryDao.updateUser(user)
    }

    suspend fun deleteUser(user: LocalUser): Int {
        return inventoryDao.deleteUser(user)
    }

    suspend fun authenticateUser(email: String, password: String): LocalUser? {
        return inventoryDao.authenticateUser(email, password)
    }

    suspend fun getUserById(id: Int): LocalUser? {
        return inventoryDao.getUserById(id)
    }

    // Usage records
    suspend fun insertUsageRecord(record: ItemUsageRecord): Long {
        val now = System.currentTimeMillis()
        val cloudId = record.cloudId ?: UUID.randomUUID().toString()
        val prepared = record.copy(cloudId = cloudId, updatedAt = now)
        val rowId = inventoryDao.insertUsageRecord(prepared)
        val persisted = prepared.copy(id = rowId.toInt())
        val parentItemCloudId = inventoryDao.getItemByIdRaw(record.itemId)?.cloudId
        val barcodeCloudId = record.barcodeId?.let { inventoryDao.getLinkedBarcodeById(it)?.cloudId }
        cloudSyncDataSource.queueUsageRecord(persisted, parentItemCloudId, barcodeCloudId)
        return rowId
    }

    suspend fun updateUsageRecord(record: ItemUsageRecord): Int {
        val now = System.currentTimeMillis()
        val cloudId = record.cloudId ?: UUID.randomUUID().toString()
        val prepared = record.copy(cloudId = cloudId, updatedAt = now)
        val updatedCount = inventoryDao.updateUsageRecord(prepared)
        if (updatedCount > 0) {
            val parentItemCloudId = inventoryDao.getItemByIdRaw(record.itemId)?.cloudId
            val barcodeCloudId = record.barcodeId?.let { inventoryDao.getLinkedBarcodeById(it)?.cloudId }
            cloudSyncDataSource.queueUsageRecord(prepared, parentItemCloudId, barcodeCloudId)
        }
        return updatedCount
    }

    fun getUsageRecordsByItem(itemId: Int): Flow<List<ItemUsageRecord>> {
        return inventoryDao.getUsageRecordsByItem(itemId)
    }

    fun getActiveUsageRecordsByItem(itemId: Int): Flow<List<ItemUsageRecord>> {
        return inventoryDao.getActiveUsageRecordsByItem(itemId)
    }

    fun getUsageRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<ItemUsageRecord>> {
        return inventoryDao.getUsageRecordsByDateRange(startDate, endDate)
    }

    suspend fun getUsageRecordById(id: Int): ItemUsageRecord? {
        return inventoryDao.getUsageRecordById(id)
    }

    // Linked barcodes
    fun getLinkedBarcodesByItem(itemId: Int): Flow<List<LinkedBarcode>> {
        return inventoryDao.getLinkedBarcodesByItem(itemId)
    }

    suspend fun insertLinkedBarcode(barcode: LinkedBarcode): Long {
        val now = System.currentTimeMillis()
        val cloudId = barcode.cloudId ?: UUID.randomUUID().toString()
        val prepared = barcode.copy(cloudId = cloudId, updatedAt = now)
        val rowId = inventoryDao.insertLinkedBarcode(prepared)
        val persisted = prepared.copy(id = rowId.toInt())
        val parentItemCloudId = inventoryDao.getItemByIdRaw(barcode.itemId)?.cloudId
        cloudSyncDataSource.queueLinkedBarcode(persisted, parentItemCloudId)
        return rowId
    }

    suspend fun deleteLinkedBarcode(barcode: LinkedBarcode) {
        val now = System.currentTimeMillis()
        val rowsAffected = inventoryDao.softDeleteLinkedBarcode(barcode.id, now)
        if (rowsAffected > 0) {
            val cloudId = barcode.cloudId ?: run {
                val freshId = UUID.randomUUID().toString()
                inventoryDao.setLinkedBarcodeCloudId(barcode.id, freshId)
                freshId
            }
            cloudSyncDataSource.queueSoftDeleteLinkedBarcode(cloudId, now)
        }
    }

    suspend fun findByBarcodeValue(value: String): LinkedBarcode? {
        return inventoryDao.findByBarcodeValue(value)
    }

    suspend fun getLinkedBarcodeById(id: Int): LinkedBarcode? {
        return inventoryDao.getLinkedBarcodeById(id)
    }

    suspend fun getActiveUsageRecordByBarcodeId(barcodeId: Int): ItemUsageRecord? {
        return inventoryDao.getActiveUsageRecordByBarcodeId(barcodeId)
    }

    // Dynamic category — assigned a cloudId up front so SyncWorker can
    // push it on the next pass. If a row with the same name already exists
    // (Room's unique index on `name`), insertCategory returns -1; we then
    // upgrade the existing row with a cloudId if it's missing one.
    suspend fun insertCategory(name: String): Long {
        val now = System.currentTimeMillis()
        val cloudId = UUID.randomUUID().toString()
        val entity = CategoryEntity(name = name, cloudId = cloudId, updatedAt = now)
        val rowId = inventoryDao.insertCategory(entity)
        val persisted = if (rowId == -1L) {
            // Already existed — backfill cloudId on the existing row if needed.
            val existing = inventoryDao.findCategoryByName(name) ?: return -1L
            if (existing.cloudId == null) {
                val patched = existing.copy(cloudId = cloudId, updatedAt = now)
                inventoryDao.updateCategory(patched)
                patched
            } else {
                existing
            }
        } else {
            entity.copy(id = rowId.toInt())
        }
        cloudSyncDataSource.queueUpsertCategory(persisted)
        return rowId
    }

    suspend fun seedReferenceData() {
        // Only seed the local defaults if the table is genuinely empty.
        // Once cloud sync runs, the canonical set comes from Supabase, so
        // we don't want to keep re-seeding "Equipment/Tools/Supplies" on
        // every launch (they'd lose their cloudId from earlier rounds).
        val now = System.currentTimeMillis()
        InventoryReferenceData.defaultCategories.forEach { name ->
            if (inventoryDao.findCategoryByName(name) == null) {
                inventoryDao.insertCategory(
                    CategoryEntity(
                        name = name,
                        cloudId = UUID.randomUUID().toString(),
                        updatedAt = now
                    )
                )
            }
        }
        InventoryReferenceData.defaultStatuses.forEach { name ->
            if (inventoryDao.findAssetStatusByName(name) == null) {
                inventoryDao.upsertAssetStatus(
                    AssetStatusEntity(
                        name = name,
                        cloudId = UUID.randomUUID().toString(),
                        updatedAt = now
                    )
                )
            }
        }

        // Backfill: rows that pre-date the cloud-sync wiring have cloudId =
        // null and would otherwise be silently filtered out of every push
        // (the *_ModifiedSince queries require `cloudId IS NOT NULL`). Assign
        // a fresh cloudId and bump updatedAt so the next sync picks them up.
        inventoryDao.getCategoriesWithoutCloudId().forEach { row ->
            val patched = row.copy(
                cloudId = UUID.randomUUID().toString(),
                updatedAt = now
            )
            inventoryDao.updateCategory(patched)
            cloudSyncDataSource.queueUpsertCategory(patched)
        }
        inventoryDao.getAssetStatusesWithoutCloudId().forEach { row ->
            val patched = row.copy(
                cloudId = UUID.randomUUID().toString(),
                updatedAt = now
            )
            inventoryDao.updateAssetStatus(patched)
            cloudSyncDataSource.queueUpsertAssetStatus(patched)
        }

        // Items inserted before ensureCategoryRegistered() existed reference
        // category names that were never written into the categories table.
        // Sweep the distinct strings actually in use and register any that
        // are missing so the cloud catalogue mirrors what the items use.
        inventoryDao.getDistinctItemCategoryNames().forEach { name ->
            ensureCategoryRegistered(name)
        }
    }

    suspend fun seedUsers() {
        if (inventoryDao.getUsersCount() == 0) {
            inventoryDao.insertUser(
                LocalUser(
                    name = "Admin User",
                    email = "admin@smartstock.local",
                    role = "Admin",
                    password = "admin123",
                    isActive = true
                )
            )
            inventoryDao.insertUser(
                LocalUser(
                    name = "Staff User",
                    email = "staff@smartstock.local",
                    role = "Staff",
                    password = "staff123",
                    isActive = true
                )
            )
        }
    }
}

object InventoryReferenceData {
    val defaultCategories = listOf("Equipment", "Tools", "Supplies")
    val defaultStatuses = listOf("Available", "In-Use", "Damaged", "Retired")
}
