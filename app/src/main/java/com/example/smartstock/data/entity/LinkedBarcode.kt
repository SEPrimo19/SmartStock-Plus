package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "linked_barcodes",
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
        Index(value = ["barcodeValue"], unique = true),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class LinkedBarcode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val barcodeValue: String,
    val label: String = "",
    val linkedAt: Long = System.currentTimeMillis(),
    val cloudId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
