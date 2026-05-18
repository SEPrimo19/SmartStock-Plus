package com.example.smartstock.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.data.entity.LocalUser
import com.example.smartstock.data.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    // Inventory Items
    @Query("SELECT * FROM inventory_items WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id AND deletedAt IS NULL")
    fun getItemById(id: Int): Flow<InventoryItem?>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemByIdRaw(id: Int): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE assetCode = :assetCode AND deletedAt IS NULL LIMIT 1")
    fun getItemByAssetCode(assetCode: String): Flow<InventoryItem?>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE deletedAt IS NULL
          AND (name LIKE '%' || :query || '%'
            OR assetCode LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%')
        """
    )
    fun searchItems(query: String): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: InventoryItem): Long

    @Query("SELECT COUNT(*) FROM inventory_items WHERE deletedAt IS NULL")
    suspend fun getItemsCount(): Int

    @Update
    suspend fun updateItem(item: InventoryItem): Int

    @Delete
    suspend fun deleteItem(item: InventoryItem): Int

    @Query(
        """
        UPDATE inventory_items
        SET deletedAt = :now, updatedAt = :now, lastUpdated = :now
        WHERE id = :id
        """
    )
    suspend fun softDeleteItem(id: Int, now: Long): Int

    @Query("UPDATE inventory_items SET cloudId = :cloudId WHERE id = :id")
    suspend fun setItemCloudId(id: Int, cloudId: String)

    @Query("SELECT * FROM inventory_items WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findItemByCloudId(cloudId: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE assetCode = :assetCode LIMIT 1")
    suspend fun findItemByAssetCodeRaw(assetCode: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getItemsModifiedSince(since: Long): List<InventoryItem>

    // Already-synced rows only (cloudId assigned). Used by the full-pull
    // reconciliation to detect items that were deleted server-side.
    @Query("SELECT * FROM inventory_items WHERE cloudId IS NOT NULL")
    suspend fun getAllSyncedItems(): List<InventoryItem>

    // History
    @Query("SELECT * FROM item_history WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ItemHistory>>

    @Query("SELECT * FROM item_history WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY timestamp DESC")
    fun getHistoryDataItem(itemId: Int): Flow<List<ItemHistory>>

    @Insert
    suspend fun insertHistory(history: ItemHistory): Long

    @Query("UPDATE item_history SET cloudId = :cloudId WHERE historyId = :historyId")
    suspend fun setHistoryCloudId(historyId: Int, cloudId: String)

    @Query("SELECT * FROM item_history WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findHistoryByCloudId(cloudId: String): ItemHistory?

    @Query("SELECT * FROM item_history WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getHistoryModifiedSince(since: Long): List<ItemHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ItemHistory): Long

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE deletedAt IS NULL
          AND (quantity - inUseQuantity) > 0
          AND status NOT IN ('Damaged', 'Retired')
        ORDER BY name ASC
        """
    )
    fun getAvailableItems(): Flow<List<InventoryItem>>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE deletedAt IS NULL
          AND (quantity - inUseQuantity) <= :threshold
          AND status NOT IN ('Damaged', 'Retired')
        ORDER BY (quantity - inUseQuantity) ASC, name ASC
        """
    )
    suspend fun getLowStockItems(threshold: Int): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE status = 'In-Use' AND deletedAt IS NULL")
    fun getInUseItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE category = :category AND deletedAt IS NULL ORDER BY name ASC")
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE status = :status AND deletedAt IS NULL ORDER BY name ASC")
    fun getItemsByStatus(status: String): Flow<List<InventoryItem>>

    // Categories and statuses
    @Query("SELECT name FROM categories ORDER BY name ASC")
    fun getAllCategoryNames(): Flow<List<String>>

    @Query("SELECT name FROM asset_statuses ORDER BY name ASC")
    fun getAllStatusNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssetStatuses(statuses: List<AssetStatusEntity>)

    // Local users
    @Query("SELECT * FROM local_users ORDER BY role ASC, name ASC")
    fun getAllUsers(): Flow<List<LocalUser>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: LocalUser): Long

    @Update
    suspend fun updateUser(user: LocalUser): Int

    @Delete
    suspend fun deleteUser(user: LocalUser): Int

    @Query("SELECT COUNT(*) FROM local_users")
    suspend fun getUsersCount(): Int

    @Query("SELECT * FROM local_users WHERE email = :email AND password = :password LIMIT 1")
    suspend fun authenticateUser(email: String, password: String): LocalUser?

    @Query("SELECT * FROM local_users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): LocalUser?

    @Query("SELECT * FROM local_users WHERE isActive = 1 ORDER BY role ASC, name ASC")
    fun getActiveUsers(): Flow<List<LocalUser>>

    // Item usage records
    @Insert
    suspend fun insertUsageRecord(record: ItemUsageRecord): Long

    @Update
    suspend fun updateUsageRecord(record: ItemUsageRecord): Int

    @Query("UPDATE item_usage_records SET cloudId = :cloudId WHERE id = :id")
    suspend fun setUsageRecordCloudId(id: Int, cloudId: String)

    @Query("SELECT * FROM item_usage_records WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findUsageRecordByCloudId(cloudId: String): ItemUsageRecord?

    @Query("SELECT * FROM item_usage_records WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getUsageRecordsModifiedSince(since: Long): List<ItemUsageRecord>

    // Every already-synced usage record. Used by the push-side
    // reconciliation to re-upload rows whose original push failed (offline
    // or a parent-FK race at checkout) and that the checkpoint has since
    // moved past — otherwise they'd never reach the cloud and Reports
    // would stay empty for everyone but the device that created them.
    @Query("SELECT * FROM item_usage_records WHERE cloudId IS NOT NULL")
    suspend fun getAllSyncedUsageRecords(): List<ItemUsageRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsageRecord(record: ItemUsageRecord): Long

    @Query("SELECT * FROM item_usage_records WHERE status = 'Active' AND deletedAt IS NULL ORDER BY checkedOutAt DESC")
    fun getActiveUsageRecords(): Flow<List<ItemUsageRecord>>

    @Query("SELECT * FROM item_usage_records WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY checkedOutAt DESC")
    fun getUsageRecordsByItem(itemId: Int): Flow<List<ItemUsageRecord>>

    @Query("SELECT * FROM item_usage_records WHERE itemId = :itemId AND status = 'Active' AND deletedAt IS NULL ORDER BY checkedOutAt DESC")
    fun getActiveUsageRecordsByItem(itemId: Int): Flow<List<ItemUsageRecord>>

    @Query("SELECT * FROM item_usage_records WHERE deletedAt IS NULL ORDER BY checkedOutAt DESC")
    fun getAllUsageRecords(): Flow<List<ItemUsageRecord>>

    @Query("SELECT * FROM item_usage_records WHERE checkedOutAt BETWEEN :startDate AND :endDate AND deletedAt IS NULL ORDER BY checkedOutAt DESC")
    fun getUsageRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<ItemUsageRecord>>

    @Query("SELECT * FROM item_usage_records WHERE id = :id AND deletedAt IS NULL")
    suspend fun getUsageRecordById(id: Int): ItemUsageRecord?

    // Single category insert for dynamic add
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity): Int

    @Query("UPDATE categories SET cloudId = :cloudId WHERE id = :id")
    suspend fun setCategoryCloudId(id: Int, cloudId: String)

    @Query("SELECT * FROM categories WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findCategoryByCloudId(cloudId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getCategoriesModifiedSince(since: Long): List<CategoryEntity>

    @Query(
        """
        UPDATE categories
        SET deletedAt = :now, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun softDeleteCategory(id: Int, now: Long): Int

    @Query("SELECT * FROM categories WHERE cloudId IS NULL AND deletedAt IS NULL")
    suspend fun getCategoriesWithoutCloudId(): List<CategoryEntity>

    @Query(
        """
        SELECT DISTINCT category FROM inventory_items
        WHERE category IS NOT NULL AND TRIM(category) != ''
          AND deletedAt IS NULL
        """
    )
    suspend fun getDistinctItemCategoryNames(): List<String>

    // Asset statuses
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssetStatus(status: AssetStatusEntity): Long

    @Update
    suspend fun updateAssetStatus(status: AssetStatusEntity): Int

    @Query("UPDATE asset_statuses SET cloudId = :cloudId WHERE id = :id")
    suspend fun setAssetStatusCloudId(id: Int, cloudId: String)

    @Query("SELECT * FROM asset_statuses WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findAssetStatusByCloudId(cloudId: String): AssetStatusEntity?

    @Query("SELECT * FROM asset_statuses WHERE name = :name LIMIT 1")
    suspend fun findAssetStatusByName(name: String): AssetStatusEntity?

    @Query("SELECT * FROM asset_statuses WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getAssetStatusesModifiedSince(since: Long): List<AssetStatusEntity>

    @Query("SELECT * FROM asset_statuses WHERE cloudId IS NULL AND deletedAt IS NULL")
    suspend fun getAssetStatusesWithoutCloudId(): List<AssetStatusEntity>

    @Query(
        """
        UPDATE asset_statuses
        SET deletedAt = :now, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun softDeleteAssetStatus(id: Int, now: Long): Int

    // Pending sync operations
    @Insert
    suspend fun insertPendingSync(entity: PendingSyncEntity): Long

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun getAllPendingSync(): List<PendingSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun getPendingSyncCount(): Flow<Int>

    @Delete
    suspend fun deletePendingSync(entity: PendingSyncEntity)

    @Query("DELETE FROM pending_sync")
    suspend fun clearAllPendingSync()

    @Update
    suspend fun updatePendingSync(entity: PendingSyncEntity)

    // Linked barcodes
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLinkedBarcode(barcode: LinkedBarcode): Long

    @Delete
    suspend fun deleteLinkedBarcode(barcode: LinkedBarcode)

    @Query("UPDATE linked_barcodes SET cloudId = :cloudId WHERE id = :id")
    suspend fun setLinkedBarcodeCloudId(id: Int, cloudId: String)

    @Query("SELECT * FROM linked_barcodes WHERE cloudId = :cloudId LIMIT 1")
    suspend fun findLinkedBarcodeByCloudId(cloudId: String): LinkedBarcode?

    @Query("SELECT * FROM linked_barcodes WHERE updatedAt > :since AND cloudId IS NOT NULL")
    suspend fun getLinkedBarcodesModifiedSince(since: Long): List<LinkedBarcode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinkedBarcode(barcode: LinkedBarcode): Long

    @Query(
        """
        UPDATE linked_barcodes
        SET deletedAt = :now, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun softDeleteLinkedBarcode(id: Int, now: Long): Int

    @Query("SELECT * FROM linked_barcodes WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY linkedAt DESC")
    fun getLinkedBarcodesByItem(itemId: Int): Flow<List<LinkedBarcode>>

    @Query("SELECT * FROM linked_barcodes WHERE barcodeValue = :value AND deletedAt IS NULL LIMIT 1")
    suspend fun findByBarcodeValue(value: String): LinkedBarcode?

    @Query("SELECT * FROM linked_barcodes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getLinkedBarcodeById(id: Int): LinkedBarcode?

    // Usage record lookup by barcodeId
    @Query("SELECT * FROM item_usage_records WHERE barcodeId = :barcodeId AND status = 'Active' AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveUsageRecordByBarcodeId(barcodeId: Int): ItemUsageRecord?

    // --- Tenant data wipe (account switch / logout) ---------------------
    // Removes every row that belongs to the signed-in team so a different
    // account on the same device never inherits the previous tenant's data.
    // local_users is intentionally preserved (generic offline-fallback seed
    // accounts, not tenant data).
    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM item_history")
    suspend fun deleteAllHistory()

    @Query("DELETE FROM item_usage_records")
    suspend fun deleteAllUsageRecords()

    @Query("DELETE FROM linked_barcodes")
    suspend fun deleteAllLinkedBarcodes()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM asset_statuses")
    suspend fun deleteAllAssetStatuses()

    @Transaction
    suspend fun clearTenantData() {
        deleteAllUsageRecords()
        deleteAllLinkedBarcodes()
        deleteAllHistory()
        deleteAllItems()
        deleteAllCategories()
        deleteAllAssetStatuses()
        clearAllPendingSync()
    }
}
