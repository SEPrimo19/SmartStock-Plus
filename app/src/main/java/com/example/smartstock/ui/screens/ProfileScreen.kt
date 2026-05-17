package com.example.smartstock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Tune
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.smartstock.BuildConfig
import com.example.smartstock.R
import com.example.smartstock.core.auth.BiometricAuth
import com.example.smartstock.core.export.ReportExporter
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.core.preferences.ThemeMode
import com.example.smartstock.core.sync.SyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.example.smartstock.data.auth.TeamMember
import com.example.smartstock.data.entity.LocalUser
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.UserRole
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.adaptive.SmartStockDimens
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.navigation.Screen
import java.text.DateFormat
import java.util.Date

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProfileEntryPoint {
    fun appPreferences(): AppPreferences
    fun syncManager(): SyncManager
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun ProfileScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by dashboardViewModel.syncState.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val teamMembers by viewModel.teamMembers.collectAsStateWithLifecycle()
    val isLoadingTeam by viewModel.isLoadingTeam.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProfileEntryPoint::class.java
        )
    }
    val appPreferences = remember(entryPoint) { entryPoint.appPreferences() }
    val syncManager = remember(entryPoint) { entryPoint.syncManager() }
    var notificationsEnabled by remember {
        mutableStateOf(appPreferences.lowStockNotificationsEnabled)
    }
    var lowStockThresholdText by remember {
        mutableStateOf(appPreferences.lowStockThreshold.toString())
    }
    val biometricAvailable = remember(context) { BiometricAuth.isAvailable(context) }
    val biometricUnavailableReason = remember(context) {
        if (biometricAvailable) null else BiometricAuth.availabilityReason(context)
    }
    val currentUserId = loggedInUser?.id.orEmpty()
    var biometricEnabled by remember(currentUserId) {
        mutableStateOf(
            biometricAvailable &&
                currentUserId.isNotBlank() &&
                appPreferences.isBiometricEnabled(currentUserId)
        )
    }

    val cloudSyncEnabled by appPreferences.cloudSyncEnabledFlow.collectAsStateWithLifecycle()
    val lastSyncedAt by appPreferences.lastSyncedAtFlow.collectAsStateWithLifecycle()
    val lastSyncError by appPreferences.lastSyncErrorFlow.collectAsStateWithLifecycle()
    val themeMode by appPreferences.themeModeFlow.collectAsStateWithLifecycle()
    val onThemeModeChange: (ThemeMode) -> Unit = { appPreferences.themeMode = it }
    val onCloudSyncToggle: (Boolean) -> Unit = { enabled ->
        appPreferences.cloudSyncEnabled = enabled
        if (enabled) {
            syncManager.schedulePeriodicSync()
            syncManager.requestImmediateSync()
        } else {
            syncManager.cancelAllSync()
        }
    }
    val onSyncNow: () -> Unit = { syncManager.requestImmediateSync() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            syncManager.runLowStockCheckNow()
        } else {
            notificationsEnabled = false
            appPreferences.lowStockNotificationsEnabled = false
        }
    }

    val requestEnableNotifications: () -> Unit = {
        notificationsEnabled = true
        appPreferences.lowStockNotificationsEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                syncManager.runLowStockCheckNow()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            syncManager.runLowStockCheckNow()
        }
    }

    val doLogout: () -> Unit = {
        viewModel.logout()
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val onExportData: () -> Unit = {
        if (allItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No inventory to export yet.") }
        } else {
            runCatching {
                val intent = ReportExporter.exportInventoryCsv(context, allItems)
                context.startActivity(
                    Intent.createChooser(intent, "Export inventory (CSV)")
                )
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar("Export failed: ${it.message ?: "unknown error"}")
                }
            }
        }
    }

    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }

    AdaptiveScreenScaffold(
        title = "Profile & Settings",
        showTopBar = !adaptiveInfo.isTwoPane,
        actions = { ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (adaptiveInfo.isTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(SmartStockDimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(SmartStockDimens.paneSpacing)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
                ) {
                    SessionAccessSection(
                        currentRole = currentRole,
                        userName = loggedInUser?.name ?: "${currentRole.displayName} User",
                        userEmail = loggedInUser?.email ?: "",
                        onLogout = doLogout
                    )
                }

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
                ) {
                    PreferencesSection(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsChange = { enabled ->
                            if (enabled) {
                                requestEnableNotifications()
                            } else {
                                notificationsEnabled = false
                                appPreferences.lowStockNotificationsEnabled = false
                            }
                        },
                        thresholdText = lowStockThresholdText,
                        onThresholdTextChange = { raw ->
                            val sanitized = raw.filter(Char::isDigit).take(4)
                            lowStockThresholdText = sanitized
                            sanitized.toIntOrNull()?.takeIf { it >= 1 }?.let {
                                appPreferences.lowStockThreshold = it
                                if (notificationsEnabled) syncManager.runLowStockCheckNow()
                            }
                        },
                        biometricEnabled = biometricEnabled,
                        biometricAvailable = biometricAvailable,
                        biometricUnavailableReason = biometricUnavailableReason,
                        onBiometricChange = { enabled ->
                            if (currentUserId.isNotBlank()) {
                                biometricEnabled = enabled
                                appPreferences.setBiometricEnabled(currentUserId, enabled)
                            }
                        }
                    )
                    CloudSyncSection(
                        cloudSyncEnabled = cloudSyncEnabled,
                        isOnline = isOnline,
                        lastSyncedAtMillis = lastSyncedAt,
                        lastSyncError = lastSyncError,
                        onCloudSyncToggle = onCloudSyncToggle,
                        onSyncNow = onSyncNow
                    )
                    TeamManagementSection(
                        isAdmin = currentRole == UserRole.Admin,
                        currentUserId = currentUserId,
                        teamName = loggedInUser?.teamName ?: "Your team",
                        members = teamMembers,
                        isLoading = isLoadingTeam,
                        onRefresh = { viewModel.refreshTeamMembers() },
                        onAddStaff = { name, email, password, onDone ->
                            viewModel.createStaffAccount(name, email, password, onDone)
                        },
                        onSetRole = { member, makeAdmin ->
                            viewModel.setMemberRole(member, makeAdmin)
                        },
                        onSetActive = { member, active ->
                            viewModel.setMemberActive(member, active)
                        }
                    )
                    AdminToolsSection(
                        isAdmin = currentRole == UserRole.Admin,
                        roleLabel = currentRole.displayName,
                        biometricEnabled = biometricEnabled,
                        biometricAvailable = biometricAvailable,
                        cloudSyncEnabled = cloudSyncEnabled,
                        itemCount = allItems.size,
                        onExport = onExportData,
                        onLockNow = doLogout
                    )
                    AppInfoSection()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(SmartStockDimens.screenPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
                ) {
                    SessionAccessSection(
                        currentRole = currentRole,
                        userName = loggedInUser?.name ?: "${currentRole.displayName} User",
                        userEmail = loggedInUser?.email ?: "",
                        onLogout = doLogout
                    )
                    PreferencesSection(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsChange = { enabled ->
                            if (enabled) {
                                requestEnableNotifications()
                            } else {
                                notificationsEnabled = false
                                appPreferences.lowStockNotificationsEnabled = false
                            }
                        },
                        thresholdText = lowStockThresholdText,
                        onThresholdTextChange = { raw ->
                            val sanitized = raw.filter(Char::isDigit).take(4)
                            lowStockThresholdText = sanitized
                            sanitized.toIntOrNull()?.takeIf { it >= 1 }?.let {
                                appPreferences.lowStockThreshold = it
                                if (notificationsEnabled) syncManager.runLowStockCheckNow()
                            }
                        },
                        biometricEnabled = biometricEnabled,
                        biometricAvailable = biometricAvailable,
                        biometricUnavailableReason = biometricUnavailableReason,
                        onBiometricChange = { enabled ->
                            if (currentUserId.isNotBlank()) {
                                biometricEnabled = enabled
                                appPreferences.setBiometricEnabled(currentUserId, enabled)
                            }
                        }
                    )
                    CloudSyncSection(
                        cloudSyncEnabled = cloudSyncEnabled,
                        isOnline = isOnline,
                        lastSyncedAtMillis = lastSyncedAt,
                        lastSyncError = lastSyncError,
                        onCloudSyncToggle = onCloudSyncToggle,
                        onSyncNow = onSyncNow
                    )
                    TeamManagementSection(
                        isAdmin = currentRole == UserRole.Admin,
                        currentUserId = currentUserId,
                        teamName = loggedInUser?.teamName ?: "Your team",
                        members = teamMembers,
                        isLoading = isLoadingTeam,
                        onRefresh = { viewModel.refreshTeamMembers() },
                        onAddStaff = { name, email, password, onDone ->
                            viewModel.createStaffAccount(name, email, password, onDone)
                        },
                        onSetRole = { member, makeAdmin ->
                            viewModel.setMemberRole(member, makeAdmin)
                        },
                        onSetActive = { member, active ->
                            viewModel.setMemberActive(member, active)
                        }
                    )
                    AdminToolsSection(
                        isAdmin = currentRole == UserRole.Admin,
                        roleLabel = currentRole.displayName,
                        biometricEnabled = biometricEnabled,
                        biometricAvailable = biometricAvailable,
                        cloudSyncEnabled = cloudSyncEnabled,
                        itemCount = allItems.size,
                        onExport = onExportData,
                        onLockNow = doLogout
                    )
                    AppInfoSection()
                }
            }
        }
    }
}

@Composable
private fun SessionAccessSection(
    currentRole: UserRole,
    userName: String,
    userEmail: String,
    onLogout: () -> Unit
) {
    SectionCard(
        title = "Account",
        subtitle = "Logged-in user and role information.",
        icon = Icons.Default.AccountCircle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (userEmail.isNotBlank()) {
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Role: ${currentRole.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreferencesSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    thresholdText: String,
    onThresholdTextChange: (String) -> Unit,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    biometricUnavailableReason: String?,
    onBiometricChange: (Boolean) -> Unit
) {
    SectionCard(
        title = "Preferences",
        subtitle = "Appearance and operational alerts.",
        icon = Icons.Default.Tune
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Light, Dark, or follow your device setting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        SettingsItemRow(
            icon = Icons.Default.Notifications,
            title = "Low-stock alerts",
            subtitle = "Notify when items reach the threshold"
        ) {
            Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChange)
        }

        if (notificationsEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Threshold",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Alert when available quantity ≤ threshold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = onThresholdTextChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(min = 80.dp, max = 96.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        SettingsItemRow(
            icon = Icons.Default.Fingerprint,
            title = "Biometric login",
            subtitle = if (biometricAvailable) {
                "Require fingerprint, face, or device PIN at launch"
            } else {
                biometricUnavailableReason ?: "Biometrics unavailable on this device"
            }
        ) {
            Switch(
                checked = biometricEnabled,
                onCheckedChange = onBiometricChange,
                enabled = biometricAvailable
            )
        }
    }
}

@Composable
private fun UserManagementSection(
    users: List<LocalUser>,
    showAddUserForm: Boolean,
    onShowAddUserForm: () -> Unit,
    onAddUser: (String, String, UserRole) -> Unit,
    onToggleUserActive: (LocalUser) -> Unit,
    onDeleteUser: (LocalUser) -> Unit
) {
    SectionCard(
        title = "User Management",
        subtitle = "Admin-only local user records and role assignment.",
        icon = Icons.Default.PersonAdd,
        headerAction = {
            TextButton(onClick = onShowAddUserForm) {
                Text(if (showAddUserForm) "Close Form" else "Add User")
            }
        }
    ) {
        if (showAddUserForm) {
            AddUserForm(onAddUser = onAddUser)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (users.isEmpty()) {
            EmptySectionState(
                title = "No local users",
                subtitle = "Add Admin or Staff records for offline role management."
            )
        } else {
            users.forEachIndexed { index, user ->
                UserRow(
                    user = user,
                    onToggleUserActive = { onToggleUserActive(user) },
                    onDeleteUser = { onDeleteUser(user) }
                )
                if (index != users.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CloudSyncSection(
    cloudSyncEnabled: Boolean,
    isOnline: Boolean,
    lastSyncedAtMillis: Long,
    lastSyncError: String?,
    onCloudSyncToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    SectionCard(
        title = "Cloud Sync",
        subtitle = "Mirror your inventory to Supabase across devices.",
        icon = Icons.Default.CloudSync
    ) {
        SettingsItemRow(
            icon = Icons.Default.CloudSync,
            title = "Sync to cloud",
            subtitle = if (cloudSyncEnabled) {
                "Changes upload to Supabase when online"
            } else {
                "Paused — your data stays on this device only"
            }
        ) {
            Switch(checked = cloudSyncEnabled, onCheckedChange = onCloudSyncToggle)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Last synced",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (lastSyncedAtMillis > 0L) {
                    DateFormat
                        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(lastSyncedAtMillis))
                } else {
                    "Never"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!lastSyncError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last error",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = lastSyncError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSyncNow,
                enabled = cloudSyncEnabled && isOnline,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !cloudSyncEnabled -> "Sync disabled"
                        !isOnline -> "Offline"
                        else -> "Sync now"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TeamManagementSection(
    isAdmin: Boolean,
    currentUserId: String,
    teamName: String,
    members: List<TeamMember>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddStaff: (String, String, String, (Boolean) -> Unit) -> Unit,
    onSetRole: (TeamMember, Boolean) -> Unit,
    onSetActive: (TeamMember, Boolean) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }

    SectionCard(
        title = "Team",
        subtitle = if (isAdmin) {
            "Manage staff in $teamName."
        } else {
            "Members of $teamName."
        },
        icon = Icons.Default.SupervisedUserCircle,
        headerAction = {
            if (isAdmin) {
                TextButton(onClick = { showAddForm = !showAddForm }) {
                    Text(if (showAddForm) "Close" else "Add staff")
                }
            }
        }
    ) {
        if (isAdmin && showAddForm) {
            AddStaffForm(
                onSubmit = { name, email, password ->
                    onAddStaff(name, email, password) { success ->
                        if (success) showAddForm = false
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tip: hand the new staff their email + password. They sign in normally — no email confirmation needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }

        if (isLoading && members.isEmpty()) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (members.isEmpty()) {
            EmptySectionState(
                title = "No teammates yet",
                subtitle = "Add staff above to share inventory access."
            )
        } else {
            members.forEachIndexed { index, member ->
                TeamMemberRow(
                    member = member,
                    isAdmin = isAdmin,
                    isSelf = member.id == currentUserId,
                    onSetRole = onSetRole,
                    onSetActive = onSetActive
                )
                if (index != members.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

@Composable
private fun AddStaffForm(onSubmit: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 6)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSubmit(name, email, password)
                },
                enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create staff account", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TeamMemberRow(
    member: TeamMember,
    isAdmin: Boolean,
    isSelf: Boolean,
    onSetRole: (TeamMember, Boolean) -> Unit,
    onSetActive: (TeamMember, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            member.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelf) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "(you)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        member.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(member.role) }
                )
            }

            Text(
                text = if (member.isActive) "Active" else "Deactivated",
                style = MaterialTheme.typography.bodyMedium,
                color = if (member.isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onSetRole(member, member.role != "Admin")
                        },
                        enabled = !isSelf
                    ) {
                        Text(if (member.role == "Admin") "Make Staff" else "Make Admin")
                    }
                    TextButton(
                        onClick = { onSetActive(member, !member.isActive) },
                        enabled = !isSelf
                    ) {
                        Text(if (member.isActive) "Deactivate" else "Activate")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminToolsSection(
    isAdmin: Boolean,
    roleLabel: String,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    cloudSyncEnabled: Boolean,
    itemCount: Int,
    onExport: () -> Unit,
    onLockNow: () -> Unit
) {
    var showSecurity by remember { mutableStateOf(false) }

    SectionCard(
        title = "Security & Data",
        subtitle = "Access protection and inventory export.",
        icon = Icons.Default.Security
    ) {
        SettingsActionItem(
            icon = Icons.Default.Security,
            title = "Security Settings",
            subtitle = "Review access protection and lock the app",
            onClick = { showSecurity = true }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        SettingsActionItem(
            icon = Icons.Default.Download,
            title = "Export Data",
            subtitle = if (itemCount > 0) {
                "Export all $itemCount items to a CSV file"
            } else {
                "No inventory to export yet"
            },
            onClick = onExport
        )
    }

    if (showSecurity) {
        AlertDialog(
            onDismissRequest = { showSecurity = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null) },
            title = { Text("Security Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecurityRow(
                        "Access role",
                        "$roleLabel — role-based permissions are enforced"
                    )
                    SecurityRow(
                        "Biometric lock",
                        when {
                            !biometricAvailable -> "Unavailable on this device"
                            biometricEnabled -> "On — required at app launch"
                            else -> "Off — enable it under Preferences"
                        }
                    )
                    SecurityRow(
                        "Cloud data isolation",
                        if (cloudSyncEnabled) {
                            "Per-team Row-Level Security — only your team can read your data"
                        } else {
                            "Local only — cloud sync is paused"
                        }
                    )
                    SecurityRow(
                        "Session",
                        "Auto-locks after inactivity; sign out to clear this device"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSecurity = false
                        onLockNow()
                    }
                ) {
                    Text("Lock app now", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurity = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SecurityRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppInfoSection() {
    SectionCard(
        title = "About",
        subtitle = "Build details and project context.",
        icon = Icons.Default.Info
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.smartstock_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(id = R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})  ·  Offline-first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Mobile Programming 2 — MCO 2",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Built with Kotlin · Jetpack Compose · Room · WorkManager · Supabase",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                headerAction?.invoke()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            content()
        }
    }
}

@Composable
private fun AddUserForm(
    onAddUser: (String, String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.Staff) }
    var isRoleMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Role",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { isRoleMenuExpanded = true }) {
                    Text(role.displayName)
                }
                DropdownMenu(
                    expanded = isRoleMenuExpanded,
                    onDismissRequest = { isRoleMenuExpanded = false }
                ) {
                    UserRole.entries.forEach { roleOption ->
                        DropdownMenuItem(
                            text = { Text(roleOption.displayName) },
                            onClick = {
                                role = roleOption
                                isRoleMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Button(
                onClick = { onAddUser(name, email, role) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save User")
            }
        }
    }
}

@Composable
private fun UserRow(
    user: LocalUser,
    onToggleUserActive: () -> Unit,
    onDeleteUser: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(user.role) }
                )
            }

            Text(
                text = if (user.isActive) "Status: Active" else "Status: Inactive",
                style = MaterialTheme.typography.bodyMedium,
                color = if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleUserActive) {
                    Text(if (user.isActive) "Deactivate" else "Activate")
                }
                TextButton(onClick = onDeleteUser) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptySectionState(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
