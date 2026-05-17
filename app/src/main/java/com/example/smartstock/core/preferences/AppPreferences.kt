package com.example.smartstock.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var lowStockNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOW_STOCK_ENABLED, DEFAULT_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_LOW_STOCK_ENABLED, value).apply()
        }

    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) {
            prefs.edit().putInt(KEY_LOW_STOCK_THRESHOLD, value.coerceAtLeast(1)).apply()
        }

    private val _cloudSyncEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, DEFAULT_CLOUD_SYNC_ENABLED)
    )
    val cloudSyncEnabledFlow: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    var cloudSyncEnabled: Boolean
        get() = _cloudSyncEnabled.value
        set(value) {
            prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, value).apply()
            _cloudSyncEnabled.value = value
        }

    var lastSyncedAtMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, value).apply()
            _lastSyncedAtMillis.value = value
        }

    private val _lastSyncedAtMillis = MutableStateFlow(
        prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
    )
    val lastSyncedAtFlow: StateFlow<Long> = _lastSyncedAtMillis.asStateFlow()

    private val _lastSyncError = MutableStateFlow(
        prefs.getString(KEY_LAST_SYNC_ERROR, null)
    )
    val lastSyncErrorFlow: StateFlow<String?> = _lastSyncError.asStateFlow()

    var lastSyncError: String?
        get() = _lastSyncError.value
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNC_ERROR, value).apply()
            _lastSyncError.value = value
        }

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.storageKey).apply()
            _themeMode.value = value
        }

    /**
     * The Supabase user id of the account whose data currently populates
     * the local Room cache. Used to detect an account switch on the same
     * device so the previous tenant's rows can be wiped before the new
     * account's data is pulled. Null = no account has synced yet.
     */
    var lastUserId: String?
        get() = prefs.getString(KEY_LAST_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_USER_ID, value).apply()
        }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null)
        return ThemeMode.fromStorage(raw) ?: ThemeMode.System
    }

    fun isBiometricEnabled(userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs.getBoolean(biometricKey(userId), false)
    }

    fun setBiometricEnabled(userId: String, enabled: Boolean) {
        if (userId.isBlank()) return
        prefs.edit().putBoolean(biometricKey(userId), enabled).apply()
    }

    private fun biometricKey(userId: String): String = "$KEY_BIOMETRIC_ENABLED_PREFIX$userId"

    companion object {
        private const val PREF_NAME = "smartstock_app_prefs"
        private const val KEY_LOW_STOCK_ENABLED = "low_stock_notifications_enabled"
        private const val KEY_LOW_STOCK_THRESHOLD = "low_stock_threshold"
        private const val KEY_BIOMETRIC_ENABLED_PREFIX = "biometric_login_enabled_"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_LAST_SYNCED_AT = "cloud_last_synced_at"
        private const val KEY_LAST_SYNC_ERROR = "cloud_last_sync_error"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_USER_ID = "last_user_id"
        private const val DEFAULT_ENABLED = true
        private const val DEFAULT_THRESHOLD = 5
        private const val DEFAULT_CLOUD_SYNC_ENABLED = true
    }
}

enum class ThemeMode(val storageKey: String, val label: String) {
    Light("light", "Light"),
    Dark("dark", "Dark"),
    System("system", "System");

    companion object {
        fun fromStorage(raw: String?): ThemeMode? =
            entries.firstOrNull { it.storageKey == raw }
    }
}
