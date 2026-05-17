package com.example.smartstock.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["assetCode"], unique = true),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(defaultValue = "''") val assetCode: String,
    val name: String,
    val description: String?,
    val category: String,
    val quantity: Int,
    val inUseQuantity: Int = 0,
    val condition: String,
    val status: String,
    val location: String?,
    val imageUri: String? = null,
    val createdAt: Long,
    val lastUpdated: Long,
    val cloudId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    val availableQuantity: Int
        get() = (quantity - inUseQuantity).coerceAtLeast(0)

    val isUsageTrackable: Boolean
        get() = status != "Damaged" && status != "Retired"
}
