package com.example.smartstock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.smartstock.core.auth.BiometricAuth
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.screens.AuthScreen
import com.example.smartstock.ui.screens.BiometricGateScreen
import com.example.smartstock.ui.screens.DashboardScreen
import com.example.smartstock.ui.screens.CameraScanScreen
import com.example.smartstock.ui.screens.InventoryListScreen
import com.example.smartstock.ui.screens.HistoryScreen
import com.example.smartstock.ui.screens.UsageReportScreen
import com.example.smartstock.ui.screens.StockTakeScreen
import com.example.smartstock.ui.screens.ProfileScreen
import com.example.smartstock.ui.screens.SplashScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavGraphEntryPoint {
    fun appPreferences(): AppPreferences
}

@Composable
fun SmartStockNavGraph(
    navController: NavHostController,
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo,
    isDarkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPreferences = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavGraphEntryPoint::class.java
        ).appPreferences()
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            // First name only — feels more personal than the full name, and
            // long names don't blow out the typography on narrow screens.
            val welcomeName = viewModel.loggedInUser.value?.name
                ?.trim()
                ?.substringBefore(' ')
                ?.takeIf { it.isNotBlank() }
            SplashScreen(
                welcomeName = welcomeName,
                onNavigateNext = {
                    val currentUserId = viewModel.loggedInUser.value?.id.orEmpty()
                    val biometricOptedIn = currentUserId.isNotBlank() &&
                        appPreferences.isBiometricEnabled(currentUserId) &&
                        BiometricAuth.isAvailable(context)
                    val nextRoute = when {
                        !viewModel.isLoggedIn.value -> Screen.Login.route
                        biometricOptedIn -> Screen.BiometricGate.route
                        else -> Screen.Home.route
                    }
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.BiometricGate.route) {
            BiometricGateScreen(
                userDisplayName = viewModel.loggedInUser.value?.name,
                onUnlocked = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.BiometricGate.route) { inclusive = true }
                    }
                },
                onCancel = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.BiometricGate.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            AuthScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            DashboardScreen(
                navController = navController,
                inventoryViewModel = viewModel,
                viewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo
            )
        }
        
        composable(Screen.Inventory.route) {
            InventoryListScreen(
                viewModel = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo
            )
        }

        composable(Screen.CameraScan.route) {
            CameraScanScreen(
                inventoryViewModel = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo
            )
        }

        composable(Screen.UsageReport.route) {
            UsageReportScreen(
                viewModel = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo
            )
        }

        composable(Screen.StockTake.route) {
            StockTakeScreen(
                viewModel = viewModel,
                adaptiveInfo = adaptiveInfo,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                viewModel = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo = adaptiveInfo,
                isDarkMode = isDarkThemeEnabled,
                onDarkModeChange = onDarkThemeChanged
            )
        }
        
    }
}
