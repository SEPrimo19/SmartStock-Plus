package com.example.smartstock.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// All timestamps cross the wire as ISO-8601 strings. CloudMappers handles the
// Long(epoch-ms) <-> ISO conversion at the boundary.

@Serializable
data class CloudInventoryItem(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("asset_code") val assetCode: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val quantity: Int,
    @SerialName("in_use_quantity") val inUseQuantity: Int,
    val condition: String,
    val status: String,
    val location: String? = null,
    @SerialName("image_uri") val imageUri: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudItemHistory(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_local_id") val itemLocalId: Int? = null,
    val action: String,
    val details: String,
    val timestamp: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudUsageRecord(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_local_id") val itemLocalId: Int? = null,
    val quantity: Int,
    val location: String,
    @SerialName("used_by") val usedBy: String,
    @SerialName("checked_out_at") val checkedOutAt: String,
    @SerialName("returned_at") val returnedAt: String? = null,
    @SerialName("return_reason") val returnReason: String? = null,
    val status: String,
    @SerialName("barcode_id") val barcodeId: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudLinkedBarcode(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_local_id") val itemLocalId: Int? = null,
    @SerialName("barcode_value") val barcodeValue: String,
    val label: String,
    @SerialName("linked_at") val linkedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudCategory(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudAssetStatus(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)
