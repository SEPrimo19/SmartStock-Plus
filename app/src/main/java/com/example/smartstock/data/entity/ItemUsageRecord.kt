package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_usage_records",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class ItemUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val quantity: Int,
    val location: String,
    val usedBy: String,
    val checkedOutAt: Long,
    val returnedAt: Long? = null,
    val returnReason: String? = null,
    val status: String = "Active",
    val barcodeId: Int? = null,
    val cloudId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
