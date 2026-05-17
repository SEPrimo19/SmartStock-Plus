package com.example.smartstock.data.auth

data class TeamMember(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val isActive: Boolean
)
