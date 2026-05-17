package com.example.smartstock.ui.adaptive

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

data class AdaptiveInfo(
    val windowSizeClass: WindowSizeClass,
    val isLandscape: Boolean
) {
    val isTwoPane: Boolean
        get() = isLandscape && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
}

@Composable
fun rememberAdaptiveInfo(windowSizeClass: WindowSizeClass): AdaptiveInfo {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    return AdaptiveInfo(windowSizeClass = windowSizeClass, isLandscape = isLandscape)
}
