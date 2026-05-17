package com.example.smartstock.ui

enum class UserRole(
    val displayName: String,
    val canAddItem: Boolean,
    val canEditItem: Boolean,
    val canDeleteItem: Boolean,
    val canUpdateCondition: Boolean,
    val canAdjustUsage: Boolean,
    val canManageUsers: Boolean
) {
    Admin(
        displayName = "Admin",
        canAddItem = true,
        canEditItem = true,
        canDeleteItem = true,
        canUpdateCondition = true,
        canAdjustUsage = true,
        canManageUsers = true
    ),
    Staff(
        displayName = "Staff",
        canAddItem = false,
        canEditItem = false,
        canDeleteItem = false,
        canUpdateCondition = true,
        canAdjustUsage = true,
        canManageUsers = false
    )
}
