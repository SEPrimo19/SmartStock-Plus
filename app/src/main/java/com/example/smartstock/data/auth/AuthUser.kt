package com.example.smartstock.data.auth

data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val isActive: Boolean = true,
    val teamId: String? = null,
    val teamName: String? = null
)
