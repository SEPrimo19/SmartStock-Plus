package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "asset_statuses",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class AssetStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cloudId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
