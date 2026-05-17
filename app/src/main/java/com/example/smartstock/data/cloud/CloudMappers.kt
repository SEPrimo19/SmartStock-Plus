package com.example.smartstock.data.cloud

import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import java.time.Instant

internal fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()
internal fun Long?.toIsoOrNull(): String? = this?.toIso()
internal fun String.fromIso(): Long = Instant.parse(this).toEpochMilli()
internal fun String?.fromIsoOrNull(): Long? = this?.let { Instant.parse(it).toEpochMilli() }

/**
 * [remoteImageUri] is the Supabase Storage object key (e.g. "<cloudId>.jpg")
 * — NOT the device-local file:// path, which is meaningless on other
 * devices. The caller (SupabaseRemoteDataSource) resolves/uploads it.
 */
internal fun InventoryItem.toCloudDto(
    cloudId: String,
    remoteImageUri: String?
): CloudInventoryItem = CloudInventoryItem(
    id = cloudId,
    userId = null, // server-side default auth.uid() fills this
    assetCode = assetCode,
    name = name,
    description = description,
    category = category,
    quantity = quantity,
    inUseQuantity = inUseQuantity,
    condition = condition,
    status = status,
    location = location,
    imageUri = remoteImageUri,
    createdAt = createdAt.toIso(),
    lastUpdated = lastUpdated.toIso(),
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

internal fun ItemHistory.toCloudDto(
    cloudId: String,
    parentItemCloudId: String?
): CloudItemHistory = CloudItemHistory(
    id = cloudId,
    userId = null,
    itemId = parentItemCloudId,
    itemLocalId = itemId,
    action = action,
    details = details,
    timestamp = timestamp.toIso(),
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

internal fun ItemUsageRecord.toCloudDto(
    cloudId: String,
    parentItemCloudId: String?,
    barcodeCloudId: String?
): CloudUsageRecord = CloudUsageRecord(
    id = cloudId,
    userId = null,
    itemId = parentItemCloudId,
    itemLocalId = itemId,
    quantity = quantity,
    location = location,
    usedBy = usedBy,
    checkedOutAt = checkedOutAt.toIso(),
    returnedAt = returnedAt.toIsoOrNull(),
    returnReason = returnReason,
    status = status,
    barcodeId = barcodeCloudId,
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

internal fun CategoryEntity.toCloudDto(cloudId: String): CloudCategory = CloudCategory(
    id = cloudId,
    userId = null,
    name = name,
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

internal fun LinkedBarcode.toCloudDto(
    cloudId: String,
    parentItemCloudId: String?
): CloudLinkedBarcode = CloudLinkedBarcode(
    id = cloudId,
    userId = null,
    itemId = parentItemCloudId,
    itemLocalId = itemId,
    barcodeValue = barcodeValue,
    label = label,
    linkedAt = linkedAt.toIso(),
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

// --- Cloud → local mappers (sync pull). The caller is responsible for
// mapping a parent's cloud_id back to the local row's Int id when needed
// (history/usage/barcodes); we expose itemLocalId as a fallback hint.

internal fun CloudInventoryItem.toEntity(localId: Int = 0): InventoryItem = InventoryItem(
    id = localId,
    assetCode = assetCode,
    name = name,
    description = description,
    category = category,
    quantity = quantity,
    inUseQuantity = inUseQuantity,
    condition = condition,
    status = status,
    location = location,
    imageUri = imageUri,
    createdAt = createdAt.fromIso(),
    lastUpdated = lastUpdated.fromIso(),
    cloudId = id,
    updatedAt = updatedAt.fromIso(),
    deletedAt = deletedAt.fromIsoOrNull()
)

internal fun CloudItemHistory.toEntity(localId: Int = 0, parentLocalId: Int): ItemHistory =
    ItemHistory(
        historyId = localId,
        itemId = parentLocalId,
        action = action,
        details = details,
        timestamp = timestamp.fromIso(),
        cloudId = id,
        updatedAt = updatedAt.fromIso(),
        deletedAt = deletedAt.fromIsoOrNull()
    )

internal fun CloudUsageRecord.toEntity(
    localId: Int = 0,
    parentLocalId: Int,
    barcodeLocalId: Int?
): ItemUsageRecord = ItemUsageRecord(
    id = localId,
    itemId = parentLocalId,
    quantity = quantity,
    location = location,
    usedBy = usedBy,
    checkedOutAt = checkedOutAt.fromIso(),
    returnedAt = returnedAt.fromIsoOrNull(),
    returnReason = returnReason,
    status = status,
    barcodeId = barcodeLocalId,
    cloudId = id,
    updatedAt = updatedAt.fromIso(),
    deletedAt = deletedAt.fromIsoOrNull()
)

internal fun CloudLinkedBarcode.toEntity(
    localId: Int = 0,
    parentLocalId: Int
): LinkedBarcode = LinkedBarcode(
    id = localId,
    itemId = parentLocalId,
    barcodeValue = barcodeValue,
    label = label,
    linkedAt = linkedAt.fromIso(),
    cloudId = id,
    updatedAt = updatedAt.fromIso(),
    deletedAt = deletedAt.fromIsoOrNull()
)

internal fun CloudCategory.toEntity(localId: Int = 0): CategoryEntity = CategoryEntity(
    id = localId,
    name = name,
    cloudId = id,
    updatedAt = updatedAt.fromIso(),
    deletedAt = deletedAt.fromIsoOrNull()
)

internal fun AssetStatusEntity.toCloudDto(cloudId: String): CloudAssetStatus = CloudAssetStatus(
    id = cloudId,
    userId = null,
    name = name,
    updatedAt = updatedAt.toIso(),
    deletedAt = deletedAt.toIsoOrNull()
)

internal fun CloudAssetStatus.toEntity(localId: Int = 0): AssetStatusEntity = AssetStatusEntity(
    id = localId,
    name = name,
    cloudId = id,
    updatedAt = updatedAt.fromIso(),
    deletedAt = deletedAt.fromIsoOrNull()
)
