package com.example.smartstock.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand / Light theme ──────────────────────────────────────────────────────
val DeepBlue  = Color(0xFF1976D2)
val Green     = Color(0xFF4CAF50)
val Orange    = Color(0xFFFF9800)
val Red       = Color(0xFFF44336)  // mapped to colorScheme.error in LightColorScheme
val White     = Color(0xFFFFFFFF)
val Black     = Color(0xFF000000)
val LightGray = Color(0xFFF5F5F5)

// FIX 3 — Removed unused `DarkGray`. It was defined but not referenced anywhere
// in Theme.kt or the color scheme. If needed for a specific component, add it back
// and use MaterialTheme.colorScheme or a local CompositionLocal instead of a
// raw Color reference so it responds correctly to dark/light theme switching.

// ── Dark theme ───────────────────────────────────────────────────────────────
val DeepBlueDark   = Color(0xFF90CAF9)
val GreenDark      = Color(0xFF81C784)
val OrangeDark     = Color(0xFFFFCC80)
val RedDark        = Color(0xFFE57373)  // mapped to colorScheme.error in DarkColorScheme
val DarkBackground = Color(0xFF121212)
val DarkSurface    = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A3139)
val DarkOnSurfaceVariant = Color(0xFFD3D9E1)
val DarkOutline = Color(0xFF8A95A3)
val DarkOutlineVariant = Color(0xFF404854)

val LightSurfaceVariant = Color(0xFFEFF3F8)
val LightOnSurfaceVariant = Color(0xFF566372)
val LightOutline = Color(0xFF7C8795)
val LightOutlineVariant = Color(0xFFD4DAE2)
