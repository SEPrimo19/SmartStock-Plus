package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String,       // "upsert_item", "delete_item", "insert_history"
    val entityId: Int,           // ID of the item/history
    val payload: String,         // JSON serialized data
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
