package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_history",
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
data class ItemHistory(
    @PrimaryKey(autoGenerate = true) val historyId: Int = 0,
    val itemId: Int,
    val action: String,
    val details: String,
    val timestamp: Long,
    val cloudId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
