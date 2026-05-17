package com.example.smartstock.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_users",
    indices = [Index(value = ["email"], unique = true)]
)
data class LocalUser(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val role: String,
    val password: String = "",
    val isActive: Boolean = true
)
