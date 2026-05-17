package com.example.smartstock.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object BiometricGate : Screen("biometric_gate")
    object Home : Screen("home")
    object Inventory : Screen("inventory")
    object CameraScan : Screen("camera_scan")
    object History : Screen("history")
    object UsageReport : Screen("usage_report")
    object StockTake : Screen("stock_take")
    object Profile : Screen("profile")
}
