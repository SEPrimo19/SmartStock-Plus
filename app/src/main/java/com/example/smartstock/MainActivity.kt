package com.example.smartstock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.smartstock.core.notifications.LowStockCheckWorker
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.core.preferences.ThemeMode
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.MainScreen
import com.example.smartstock.ui.theme.SmartStockTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

@AndroidEntryPoint(FragmentActivity::class)
class MainActivity : Hilt_MainActivity() {

    private val viewModel: InventoryViewModel by viewModels()

    private val dashboardViewModel: DashboardViewModel by viewModels()

    @Inject lateinit var appPreferences: AppPreferences

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // THE MASTER SWITCH — must be called before setContent.
        // This makes the status bar and navigation bar transparent so Compose
        // can control insets via WindowInsets APIs (systemBarsPadding, etc.).
        // Calling this inside a Compose SideEffect runs after the first frame
        // is already laid out, so it has no effect on header/bar sizing.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleLowStockIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val systemDarkTheme = isSystemInDarkTheme()
            val themeMode by appPreferences.themeModeFlow.collectAsStateWithLifecycle()
            val isDarkThemeEnabled = remember(themeMode, systemDarkTheme) {
                when (themeMode) {
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                    ThemeMode.System -> systemDarkTheme
                }
            }

            SmartStockTheme(
                darkTheme    = isDarkThemeEnabled,
                dynamicColor = false
            ) {
                MainScreen(
                    viewModel          = viewModel,
                    dashboardViewModel = dashboardViewModel,
                    windowSizeClass    = windowSizeClass,
                    isDarkThemeEnabled = isDarkThemeEnabled,
                    onDarkThemeChanged = { enabled ->
                        appPreferences.themeMode =
                            if (enabled) ThemeMode.Dark else ThemeMode.Light
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLowStockIntent(intent)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.markUserActivity()
    }

    private fun handleLowStockIntent(intent: Intent?) {
        val itemId = intent?.getIntExtra(LowStockCheckWorker.EXTRA_ITEM_ID, -1) ?: -1
        if (itemId > 0) {
            viewModel.requestDeepLinkToItem(itemId)
        }
    }
}
