package com.example.smartstock.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartstock.core.auth.BiometricAuth
import com.example.smartstock.ui.adaptive.rememberAdaptiveInfo
import com.example.smartstock.ui.navigation.NavGraphEntryPoint
import com.example.smartstock.ui.navigation.Screen
import com.example.smartstock.ui.navigation.SmartStockNavGraph
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private const val RELOCK_TIMEOUT_MS = 60_000L
private const val INACTIVITY_TIMEOUT_MS = 60_000L
// Once the inactivity threshold trips we show a confirmation prompt; this
// is how long the user has to tap "Stay signed in" before we sign them out
// anyway. Without this fallback an abandoned phone would sit on the prompt
// forever, defeating the purpose of the timeout.
private const val INACTIVITY_PROMPT_GRACE_MS = 30_000L

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home     : BottomNavItem(Screen.Home.route,      "Home",      Icons.Filled.Home,       Icons.Outlined.Home)
    object Inventory: BottomNavItem(Screen.Inventory.route, "Inventory", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    object CameraScan: BottomNavItem(Screen.CameraScan.route, "Scan",      Icons.Filled.CameraAlt,   Icons.Outlined.CameraAlt)
    object Reports  : BottomNavItem(Screen.UsageReport.route, "Reports",   Icons.Filled.Assessment,  Icons.Outlined.Assessment)
    object Profile  : BottomNavItem(Screen.Profile.route,   "Profile",   Icons.Filled.Person,     Icons.Outlined.Person)
}

@Composable
fun MainScreen(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    windowSizeClass: WindowSizeClass,
    isDarkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit
) {
    val adaptiveInfo       = rememberAdaptiveInfo(windowSizeClass)
    val navController      = rememberNavController()
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val pendingDeepLinkItemId by viewModel.pendingDeepLinkItemId.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val appPreferences = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavGraphEntryPoint::class.java
        ).appPreferences()
    }
    val processLifecycleOwner = remember { ProcessLifecycleOwner.get() }
    val backgroundedAt = remember { mutableStateOf<Long?>(null) }
    var pendingRelock by remember { mutableStateOf(false) }

    DisposableEffect(processLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAt.value = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    viewModel.markUserActivity()
                    val timestamp = backgroundedAt.value
                    backgroundedAt.value = null
                    if (timestamp == null) return@LifecycleEventObserver
                    val elapsed = System.currentTimeMillis() - timestamp
                    if (elapsed < RELOCK_TIMEOUT_MS) return@LifecycleEventObserver
                    val userId = viewModel.loggedInUser.value?.id.orEmpty()
                    val needsRelock = viewModel.isLoggedIn.value &&
                        userId.isNotBlank() &&
                        appPreferences.isBiometricEnabled(userId) &&
                        BiometricAuth.isAvailable(context)
                    if (needsRelock) pendingRelock = true
                }
                else -> Unit
            }
        }
        processLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { processLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingRelock) {
        if (pendingRelock) {
            val current = navController.currentBackStackEntry?.destination?.route
            val skipRoutes = setOf(
                Screen.Splash.route,
                Screen.Login.route,
                Screen.BiometricGate.route
            )
            if (current !in skipRoutes) {
                navController.navigate(Screen.BiometricGate.route) {
                    launchSingleTop = true
                }
            }
            pendingRelock = false
        }
    }

    var showInactivityPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        viewModel.markUserActivity()
        while (true) {
            val elapsed = System.currentTimeMillis() - viewModel.lastUserActivityAt.value
            val remaining = INACTIVITY_TIMEOUT_MS - elapsed
            if (remaining <= 0) {
                // Hand control to the user. The dialog itself handles the
                // sign-out / stay-signed-in branches; we just wait for the
                // user to react (or for the prompt grace window to expire).
                showInactivityPrompt = true
                break
            }
            delay(remaining.coerceAtLeast(5_000L))
        }
    }

    // Auto-dismiss the prompt with a logout if the user doesn't react within
    // the grace window. Keeps the "abandoned phone" case covered.
    LaunchedEffect(showInactivityPrompt) {
        if (!showInactivityPrompt) return@LaunchedEffect
        delay(INACTIVITY_PROMPT_GRACE_MS)
        if (showInactivityPrompt) {
            showInactivityPrompt = false
            viewModel.logout()
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (showInactivityPrompt) {
        AlertDialog(
            onDismissRequest = {
                // Tapping outside the dialog counts as "stay signed in" —
                // it's the less destructive default. If the user actually
                // wants to log out they can tap the button.
                showInactivityPrompt = false
                viewModel.markUserActivity()
            },
            title = { Text("Still there?") },
            text = {
                Text(
                    "You've been inactive for a while. Stay signed in to keep " +
                        "working, or sign out now."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showInactivityPrompt = false
                    viewModel.markUserActivity()
                }) {
                    Text("Stay signed in")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showInactivityPrompt = false
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text("Sign out")
                }
            }
        )
    }

    LaunchedEffect(pendingDeepLinkItemId, isLoggedIn) {
        val targetId = pendingDeepLinkItemId ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        viewModel.selectInventoryItem(targetId)
        navController.navigate(Screen.Inventory.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        viewModel.consumeDeepLink()
    }

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Inventory,
        BottomNavItem.CameraScan,
        BottomNavItem.Reports,
        BottomNavItem.Profile
    )

    fun shouldHideNav(route: String?): Boolean {
        if (route == null) return false
        return route == Screen.Splash.route ||
            route == Screen.Login.route ||
            route == Screen.BiometricGate.route
    }

    val showNav = currentDestination
        ?.hierarchy
        ?.none { shouldHideNav(it.route) }
        ?: true

    val useRail = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
    val view = LocalView.current

    DisposableEffect(useRail, showNav, view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }

        if (useRail && showNav && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (useRail && showNav) {
        // ── LANDSCAPE ────────────────────────────────────────────────────────
        // The outer Box fills the entire screen including behind system bars.
        // The surface color is drawn as the background of the whole screen so
        // no black gap appears behind the transparent status bar or nav bar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface) // fills status bar area
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavigationRail(
                    modifier       = Modifier
                        .fillMaxHeight()
                        .padding(top = 16.dp, bottom = 8.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor   = MaterialTheme.colorScheme.onSurface
                ) {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            label  = { Text(item.title) },
                            icon   = {
                                Icon(
                                    imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                SmartStockNavGraph(
                    navController      = navController,
                    viewModel          = viewModel,
                    dashboardViewModel = dashboardViewModel,
                    adaptiveInfo       = adaptiveInfo,
                    isDarkThemeEnabled = isDarkThemeEnabled,
                    onDarkThemeChanged = onDarkThemeChanged,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showNav) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor   = MaterialTheme.colorScheme.onSurface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick  = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                label  = { Text(item.title) },
                                icon   = {
                                    Icon(
                                        imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                                    indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            SmartStockNavGraph(
                navController      = navController,
                viewModel          = viewModel,
                dashboardViewModel = dashboardViewModel,
                adaptiveInfo       = adaptiveInfo,
                isDarkThemeEnabled = isDarkThemeEnabled,
                onDarkThemeChanged = onDarkThemeChanged,
                modifier           = Modifier.padding(innerPadding)
            )
        }
    }
}
