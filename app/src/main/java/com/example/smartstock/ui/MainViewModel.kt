package com.example.smartstock.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartstock.core.auth.BiometricAuth
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.core.sync.SyncManager
import com.example.smartstock.core.util.ErrorText
import com.example.smartstock.data.auth.AuthUser
import com.example.smartstock.data.auth.SupabaseAuthRepository
import com.example.smartstock.data.auth.TeamMember
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.data.entity.LocalUser
import com.example.smartstock.data.repository.InventoryReferenceData
import com.example.smartstock.data.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed class SaveItemResult {
    data class Success(val item: InventoryItem) : SaveItemResult()
    data class ValidationError(val message: String) : SaveItemResult()
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val authRepository: SupabaseAuthRepository,
    private val syncManager: SyncManager,
    private val appPreferences: AppPreferences,
    private val application: Application
) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val FILTER_ALL = "All"
        private const val SORT_BY_NAME = "Name"
        private const val SORT_BY_QUANTITY = "Quantity"
        private const val SORT_BY_UPDATED = "Updated"
    }

    init {
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            // Supabase persists its session via the Auth plugin's Android
            // session manager — currentUser() resolves it (and fetches the
            // profile row) without us touching SharedPreferences ourselves.
            val user = authRepository.currentUser() ?: return@launch
            ensureLocalDataBelongsTo(user.id)
            applyAuthUser(user)
        }
    }

    /**
     * Guards against tenant data leaking across accounts on a shared
     * device. If the local Room cache currently holds another account's
     * rows, wipe it and reset the sync checkpoint so the incoming
     * account pulls a clean, fully-scoped dataset from the cloud.
     */
    private suspend fun ensureLocalDataBelongsTo(userId: String) {
        val previous = appPreferences.lastUserId
        if (previous != null && previous != userId) {
            repository.clearLocalData()
            appPreferences.lastSyncedAtMillis = 0L
            appPreferences.lastSyncError = null
        }
        appPreferences.lastUserId = userId
    }

    private fun applyAuthUser(user: AuthUser) {
        val role = UserRole.entries.firstOrNull { it.displayName == user.role } ?: UserRole.Staff
        _currentUserRole.value = role
        _loggedInUser.value = user
        _isLoggedIn.value = true
        // Now that we have a session, schedule periodic sync (no-op when the
        // user has cloud sync turned off) and kick off an immediate run so
        // remote changes start landing without waiting on the 15-min tick.
        syncManager.schedulePeriodicSync()
        syncManager.requestImmediateSync()
        refreshTeamMembers()
    }

    fun refreshTeamMembers() {
        viewModelScope.launch {
            _isLoadingTeam.value = true
            _teamMembers.value = authRepository.listTeamMembers()
            _isLoadingTeam.value = false
        }
    }

    fun createStaffAccount(
        name: String,
        email: String,
        password: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can add staff"
            onComplete(false)
            return
        }
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _operationError.value = "Name, email, and password are required"
            onComplete(false)
            return
        }
        if (password.length < 6) {
            _operationError.value = "Password must be at least 6 characters"
            onComplete(false)
            return
        }
        viewModelScope.launch {
            authRepository.createStaff(name, email, password).fold(
                onSuccess = {
                    _operationMessage.value = "Added staff \"${name.trim()}\""
                    refreshTeamMembers()
                    onComplete(true)
                },
                onFailure = { err ->
                    _operationError.value = ErrorText.friendly(err, "Failed to add staff")
                    onComplete(false)
                }
            )
        }
    }

    fun setMemberRole(member: TeamMember, makeAdmin: Boolean) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can change roles"
            return
        }
        if (member.id == _loggedInUser.value?.id && !makeAdmin) {
            _operationError.value = "You can't demote yourself"
            return
        }
        val activeAdmins = _teamMembers.value.count {
            it.role == UserRole.Admin.displayName && it.isActive
        }
        val isLastAdmin = member.role == UserRole.Admin.displayName &&
            member.isActive && activeAdmins <= 1
        if (isLastAdmin && !makeAdmin) {
            _operationError.value = "At least one active Admin is required"
            return
        }
        viewModelScope.launch {
            authRepository
                .setMemberRole(member.id, if (makeAdmin) "Admin" else "Staff")
                .fold(
                    onSuccess = {
                        _operationMessage.value =
                            "${member.displayName} is now ${if (makeAdmin) "Admin" else "Staff"}"
                        refreshTeamMembers()
                    },
                    onFailure = {
                        _operationError.value = it.message ?: "Failed to update role"
                    }
                )
        }
    }

    fun setMemberActive(member: TeamMember, isActive: Boolean) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can change access"
            return
        }
        if (member.id == _loggedInUser.value?.id && !isActive) {
            _operationError.value = "You can't deactivate yourself"
            return
        }
        val activeAdmins = _teamMembers.value.count {
            it.role == UserRole.Admin.displayName && it.isActive
        }
        if (member.role == UserRole.Admin.displayName && member.isActive &&
            activeAdmins <= 1 && !isActive) {
            _operationError.value = "At least one active Admin is required"
            return
        }
        viewModelScope.launch {
            authRepository.setMemberActive(member.id, isActive).fold(
                onSuccess = {
                    _operationMessage.value =
                        "${member.displayName} ${if (isActive) "activated" else "deactivated"}"
                    refreshTeamMembers()
                },
                onFailure = {
                    _operationError.value = it.message ?: "Failed to update access"
                }
            )
        }
    }

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _selectedInventoryItemId = MutableStateFlow<Int?>(null)
    val selectedInventoryItemId: StateFlow<Int?> = _selectedInventoryItemId.asStateFlow()

    private val _pendingDeepLinkItemId = MutableStateFlow<Int?>(null)
    val pendingDeepLinkItemId: StateFlow<Int?> = _pendingDeepLinkItemId.asStateFlow()

    fun requestDeepLinkToItem(itemId: Int) {
        _pendingDeepLinkItemId.value = itemId
    }

    fun consumeDeepLink() {
        _pendingDeepLinkItemId.value = null
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUserRole = MutableStateFlow(UserRole.Staff)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    val allItems: StateFlow<List<InventoryItem>> = repository.allItems.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        emptyList()
    )

    val isInventoryLoading: StateFlow<Boolean> = repository.allItems
        .map { false }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            true
        )

    val allUsers: StateFlow<List<LocalUser>> = repository.allUsers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        emptyList()
    )

    val categoryNames: StateFlow<List<String>> = repository.categoryNames
        .map { it.ifEmpty { InventoryReferenceData.defaultCategories } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            InventoryReferenceData.defaultCategories
        )

    val statusNames: StateFlow<List<String>> = repository.statusNames
        .map { it.ifEmpty { InventoryReferenceData.defaultStatuses } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            InventoryReferenceData.defaultStatuses
        )

    val allHistory: StateFlow<List<ItemHistory>> = repository.allHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        emptyList()
    )

    val isHistoryLoading: StateFlow<Boolean> = repository.allHistory
        .map { false }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            true
        )

    val allUsageRecords: StateFlow<List<ItemUsageRecord>> = repository.allUsageRecords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        emptyList()
    )

    val isUsageLoading: StateFlow<Boolean> = repository.allUsageRecords
        .map { false }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            true
        )

    // Inventory screen state
    private val _inventorySearchQuery = MutableStateFlow("")
    val inventorySearchQuery: StateFlow<String> = _inventorySearchQuery.asStateFlow()

    private val _inventoryCategoryFilter = MutableStateFlow(FILTER_ALL)
    val inventoryCategoryFilter: StateFlow<String> = _inventoryCategoryFilter.asStateFlow()

    private val _inventoryStatusFilter = MutableStateFlow(FILTER_ALL)
    val inventoryStatusFilter: StateFlow<String> = _inventoryStatusFilter.asStateFlow()

    private val _inventorySortField = MutableStateFlow(SORT_BY_NAME)
    val inventorySortField: StateFlow<String> = _inventorySortField.asStateFlow()

    private val _inventorySortDescending = MutableStateFlow(false)
    val inventorySortDescending: StateFlow<Boolean> = _inventorySortDescending.asStateFlow()

    private val inventorySearchState = combine(
        _inventorySearchQuery,
        _inventoryCategoryFilter,
        _inventoryStatusFilter
    ) { query, categoryFilter, statusFilter ->
        Triple(query, categoryFilter, statusFilter)
    }

    val inventorySearchResults: StateFlow<List<InventoryItem>> = combine(
        inventorySearchState,
        _inventorySortField,
        _inventorySortDescending,
        allItems
    ) { filters, sortField, sortDescending, items ->
        val (query, categoryFilter, statusFilter) = filters
        filterItems(
            query = query,
            items = items,
            categoryFilter = categoryFilter,
            statusFilter = statusFilter,
            sortField = sortField,
            sortDescending = sortDescending
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        emptyList()
    )

    fun onInventoryQueryChanged(query: String) {
        _inventorySearchQuery.value = query
    }

    fun onInventoryCategoryFilterChanged(category: String) {
        _inventoryCategoryFilter.value = category
    }

    fun onInventoryStatusFilterChanged(status: String) {
        _inventoryStatusFilter.value = status
    }

    fun onInventorySortFieldChanged(sortField: String) {
        _inventorySortField.value = sortField
    }

    fun onInventorySortDirectionChanged(descending: Boolean) {
        _inventorySortDescending.value = descending
    }

    fun clearInventoryFilters() {
        _inventorySearchQuery.value = ""
        _inventoryCategoryFilter.value = FILTER_ALL
        _inventoryStatusFilter.value = FILTER_ALL
        _inventorySortField.value = SORT_BY_NAME
        _inventorySortDescending.value = false
    }

    private fun filterItems(
        query: String,
        items: List<InventoryItem>,
        categoryFilter: String,
        statusFilter: String,
        sortField: String,
        sortDescending: Boolean
    ): List<InventoryItem> {
        return items
            .filterByCategory(categoryFilter)
            .filterByStatus(statusFilter)
            .let { filteredItems ->
                if (query.isBlank()) {
                    filteredItems
                } else {
                    filteredItems.filter {
                        it.name.contains(query, ignoreCase = true) ||
                            it.assetCode.contains(query, ignoreCase = true) ||
                            it.category.contains(query, ignoreCase = true) ||
                            (it.location?.contains(query, ignoreCase = true) == true)
                    }
                }
            }
            .sortedWith(
                when (sortField) {
                    SORT_BY_QUANTITY -> compareBy<InventoryItem> { it.quantity }
                    SORT_BY_UPDATED -> compareBy<InventoryItem> { it.lastUpdated }
                    else -> compareBy { it.name.lowercase() }
                }
            )
            .let { if (sortDescending) it.reversed() else it }
    }

    private fun List<InventoryItem>.filterByCategory(category: String): List<InventoryItem> {
        return if (category.isBlank() || category == FILTER_ALL) {
            this
        } else {
            filter { it.category == category }
        }
    }

    private fun List<InventoryItem>.filterByStatus(status: String): List<InventoryItem> {
        return if (status.isBlank() || status == FILTER_ALL) {
            this
        } else {
            filter { it.status == status }
        }
    }

    fun selectInventoryItem(itemId: Int?) {
        _selectedInventoryItemId.value = itemId
    }

    private val _loggedInUser = MutableStateFlow<AuthUser?>(null)
    val loggedInUser: StateFlow<AuthUser?> = _loggedInUser.asStateFlow()

    private val _teamMembers = MutableStateFlow<List<TeamMember>>(emptyList())
    val teamMembers: StateFlow<List<TeamMember>> = _teamMembers.asStateFlow()

    private val _isLoadingTeam = MutableStateFlow(false)
    val isLoadingTeam: StateFlow<Boolean> = _isLoadingTeam.asStateFlow()

    private val _lastUserActivityAt = MutableStateFlow(System.currentTimeMillis())
    val lastUserActivityAt: StateFlow<Long> = _lastUserActivityAt.asStateFlow()

    fun markUserActivity() {
        _lastUserActivityAt.value = System.currentTimeMillis()
    }

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun loginAs(role: UserRole) {
        _currentUserRole.value = role
        _isLoggedIn.value = true
    }

    fun authenticateAndLogin(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _loginError.value = "Email and password are required"
            return
        }
        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            result.fold(
                onSuccess = { user ->
                    if (!user.isActive) {
                        _loginError.value = "Account is deactivated. Contact an Admin."
                        authRepository.signOut()
                    } else {
                        ensureLocalDataBelongsTo(user.id)
                        applyAuthUser(user)
                        _loginError.value = null
                        onSuccess()
                    }
                },
                onFailure = { err ->
                    _loginError.value = ErrorText.friendly(err, "Invalid email or password")
                }
            )
        }
    }

    fun signUpAndLogin(name: String, email: String, password: String, onSuccess: () -> Unit) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _loginError.value = "Name, email, and password are required"
            return
        }
        if (password.length < 6) {
            _loginError.value = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            val result = authRepository.signUp(name, email, password)
            result.fold(
                onSuccess = { user ->
                    ensureLocalDataBelongsTo(user.id)
                    applyAuthUser(user)
                    _loginError.value = null
                    onSuccess()
                },
                onFailure = { err ->
                    _loginError.value = ErrorText.friendly(err, "Failed to create account")
                }
            )
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun logout() {
        val uid = appPreferences.lastUserId
        val biometricLock = uid != null &&
            appPreferences.isBiometricEnabled(uid) &&
            BiometricAuth.isAvailable(application)

        _isLoggedIn.value = false
        _loggedInUser.value = null
        _currentUserRole.value = UserRole.Staff
        syncManager.cancelAllSync()

        if (biometricLock) {
            // This account opted into biometric unlock. Treat "Log out" as
            // a lock: keep the Supabase session + local cache + lastUserId
            // so the fingerprint button on the auth screen can resume the
            // session instantly. A full sign-out only happens when the
            // user disables biometrics or signs in as someone else.
            return
        }

        viewModelScope.launch {
            authRepository.signOut()
            // Drop the local cache so the next account on this device
            // starts clean; reset the checkpoint so a returning user
            // does a full re-pull instead of a stale incremental one.
            repository.clearLocalData()
            appPreferences.lastSyncedAtMillis = 0L
            appPreferences.lastSyncError = null
            appPreferences.lastUserId = null
        }
    }

    /**
     * Resume the still-persisted session after a biometric lock. Called
     * from the auth screen's fingerprint button. If the session is gone
     * (e.g. token fully expired) the user is asked to sign in normally.
     */
    fun unlockWithBiometrics(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val user = authRepository.currentUser()
            if (user == null) {
                _loginError.value = "Session expired. Please sign in again."
                appPreferences.lastUserId = null
                return@launch
            }
            ensureLocalDataBelongsTo(user.id)
            applyAuthUser(user)
            _loginError.value = null
            onSuccess()
        }
    }

    fun addLocalUser(name: String, email: String, role: UserRole) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can manage users"
            return
        }
        if (name.isBlank()) {
            _operationError.value = "User name is required"
            return
        }
        if (email.isBlank()) {
            _operationError.value = "User email is required"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.insertUser(
                    LocalUser(
                        name = name.trim(),
                        email = email.trim().lowercase(),
                        role = role.displayName,
                        isActive = true
                    )
                )
            }.onFailure {
                _operationError.value = it.message ?: "Failed to add user"
            }
        }
    }

    fun toggleLocalUserActive(user: LocalUser) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can manage users"
            return
        }
        val activeAdmins = allUsers.value.count { it.role == UserRole.Admin.displayName && it.isActive }
        if (user.role == UserRole.Admin.displayName && user.isActive && activeAdmins <= 1) {
            _operationError.value = "At least one active Admin is required"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.updateUser(user.copy(isActive = !user.isActive))
            }.onFailure {
                _operationError.value = it.message ?: "Failed to update user"
            }
        }
    }

    fun deleteLocalUser(user: LocalUser) {
        if (!_currentUserRole.value.canManageUsers) {
            _operationError.value = "Only Admin can manage users"
            return
        }
        val totalAdmins = allUsers.value.count { it.role == UserRole.Admin.displayName }
        if (user.role == UserRole.Admin.displayName && totalAdmins <= 1) {
            _operationError.value = "At least one Admin must remain"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.deleteUser(user)
            }.onFailure {
                _operationError.value = it.message ?: "Failed to delete user"
            }
        }
    }

    fun insertItem(item: InventoryItem) {
        if (!_currentUserRole.value.canAddItem) {
            _operationError.value = "Only Admin can add items"
            return
        }
        viewModelScope.launch {
            runCatching {
                val itemId = repository.insertItem(item).toInt()
                repository.insertHistory(
                    ItemHistory(
                        itemId = itemId,
                        action = "Item Added",
                        details = "${item.name} added (Qty: ${item.quantity}).",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Added \"${item.name}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to insert item" }
        }
    }

    fun updateItem(item: InventoryItem) {
        if (!_currentUserRole.value.canEditItem) {
            _operationError.value = "Only Admin can edit items"
            return
        }
        viewModelScope.launch {
            runCatching {
                val updatedCount = repository.updateItem(item)
                if (updatedCount == 0) {
                    throw IllegalStateException("Failed to update item: item not found")
                }
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Item Updated",
                        details = "${item.name} updated (Qty: ${item.quantity}, Status: ${item.status}).",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Updated \"${item.name}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to update item" }
        }
    }

    fun deleteItem(item: InventoryItem) {
        if (!_currentUserRole.value.canDeleteItem) {
            _operationError.value = "Only Admin can delete items"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Item Deleted",
                        details = "${item.name} deleted (Qty: ${item.quantity}, Status: ${item.status}).",
                        timestamp = System.currentTimeMillis()
                    )
                )
                val deletedCount = repository.deleteItem(item)
                if (deletedCount == 0) {
                    throw IllegalStateException("Failed to delete item: item not found")
                }
            }.onSuccess {
                _operationMessage.value = "Deleted \"${item.name}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to delete item" }
        }
    }

    fun restoreItem(item: InventoryItem) {
        viewModelScope.launch {
            runCatching {
                repository.restoreItem(item)
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Item Restored",
                        details = "${item.name} restored from undo.",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Restored \"${item.name}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to restore item" }
        }
    }

    private val _passwordResetMessage = MutableStateFlow<String?>(null)
    val passwordResetMessage: StateFlow<String?> = _passwordResetMessage.asStateFlow()

    fun clearPasswordResetMessage() { _passwordResetMessage.value = null }

    /** Step 1: email the user a 6-digit recovery OTP. */
    fun sendPasswordResetOtp(email: String, onResult: (Boolean) -> Unit) {
        val normalized = email.trim()
        if (normalized.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            _passwordResetMessage.value = "Enter a valid email address first."
            onResult(false)
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordResetOtp(normalized)
                .onSuccess {
                    _passwordResetMessage.value =
                        "We sent a 6-digit code to $normalized. Enter it below."
                    onResult(true)
                }
                .onFailure {
                    _passwordResetMessage.value =
                        ErrorText.friendly(it, "Failed to send the code.")
                    onResult(false)
                }
        }
    }

    /** Step 2: verify the OTP and set the new password. */
    fun resetPasswordWithOtp(
        email: String,
        otp: String,
        newPassword: String,
        onResult: (Boolean) -> Unit
    ) {
        val normalized = email.trim()
        when {
            otp.trim().length < 6 -> {
                _passwordResetMessage.value = "Enter the 6-digit code from your email."
                onResult(false)
                return
            }
            newPassword.length < 6 -> {
                _passwordResetMessage.value = "New password must be at least 6 characters."
                onResult(false)
                return
            }
        }
        viewModelScope.launch {
            authRepository.resetPasswordWithOtp(normalized, otp.trim(), newPassword)
                .onSuccess {
                    _passwordResetMessage.value =
                        "Password updated. Sign in with your new password."
                    onResult(true)
                }
                .onFailure {
                    _passwordResetMessage.value =
                        ErrorText.friendly(it, "Invalid or expired code. Try again.")
                    onResult(false)
                }
        }
    }

    fun getItem(id: Int): Flow<InventoryItem?> = repository.getItem(id)

    fun getItemByAssetCode(assetCode: String): Flow<InventoryItem?> {
        return repository.getItemByAssetCode(normalizeAssetCode(assetCode))
    }

    fun getHistory(itemId: Int): Flow<List<ItemHistory>> = repository.getHistoryDataItem(itemId)

    fun clearOperationError() {
        _operationError.value = null
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun updateItemCondition(item: InventoryItem, newCondition: String) {
        if (!_currentUserRole.value.canUpdateCondition) {
            _operationError.value = "Your role cannot update item condition"
            return
        }
        val oldCondition = item.condition
        if (newCondition == oldCondition) return

        val newStatus = if (newCondition == "Damaged") "Damaged" else item.status
        val updatedItem = item.copy(
            condition = newCondition,
            status = newStatus,
            lastUpdated = System.currentTimeMillis()
        )
        viewModelScope.launch {
            runCatching {
                val count = repository.updateItem(updatedItem)
                if (count == 0) throw IllegalStateException("Item not found")
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Condition Updated",
                        details = "${item.name} condition changed from $oldCondition to $newCondition.",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onFailure { _operationError.value = it.message ?: "Failed to update condition" }
        }
    }

    fun useItem(item: InventoryItem, amount: Int, location: String = "") {
        if (!_currentUserRole.value.canAdjustUsage) {
            _operationError.value = "Your role cannot check out items"
            return
        }
        if (!item.isUsageTrackable) {
            _operationError.value = "Damaged or retired items cannot be checked out"
            return
        }
        if (amount <= 0) {
            _operationError.value = "Use quantity must be greater than 0"
            return
        }
        if (amount > item.availableQuantity) {
            _operationError.value = "Only ${item.availableQuantity} available to use"
            return
        }

        val now = System.currentTimeMillis()
        val updatedItem = item.copy(
            inUseQuantity = item.inUseQuantity + amount,
            status = "In-Use",
            lastUpdated = now
        )

        viewModelScope.launch {
            runCatching {
                repository.updateItem(updatedItem)
                repository.insertUsageRecord(
                    ItemUsageRecord(
                        itemId = item.id,
                        quantity = amount,
                        location = location.ifBlank { item.location ?: "Unspecified" },
                        usedBy = _loggedInUser.value?.name ?: _currentUserRole.value.displayName,
                        checkedOutAt = now
                    )
                )
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Item Checked Out",
                        details = "$amount of ${item.name} checked out to ${location.ifBlank { "unspecified location" }}. Available: ${updatedItem.availableQuantity}, In Use: ${updatedItem.inUseQuantity}.",
                        timestamp = now
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Checked out $amount × ${item.name}"
            }.onFailure { _operationError.value = it.message ?: "Failed to check out item" }
        }
    }

    fun returnItem(item: InventoryItem, amount: Int, reason: String = "") {
        if (!_currentUserRole.value.canAdjustUsage) {
            _operationError.value = "Your role cannot return items"
            return
        }
        if (amount <= 0) {
            _operationError.value = "Return quantity must be greater than 0"
            return
        }
        if (amount > item.inUseQuantity) {
            _operationError.value = "Only ${item.inUseQuantity} currently in use"
            return
        }

        val now = System.currentTimeMillis()
        val newInUseQuantity = item.inUseQuantity - amount
        val updatedItem = item.copy(
            inUseQuantity = newInUseQuantity,
            status = if (newInUseQuantity > 0) "In-Use" else "Available",
            lastUpdated = now
        )

        viewModelScope.launch {
            runCatching {
                repository.updateItem(updatedItem)

                // Close the oldest active usage record(s) for this item
                val records = repository.getActiveUsageRecordsByItem(item.id).first()
                var remaining = amount
                for (record in records) {
                    if (remaining <= 0) break
                    val returnQty = minOf(remaining, record.quantity)
                    if (returnQty == record.quantity) {
                        repository.updateUsageRecord(
                            record.copy(
                                returnedAt = now,
                                returnReason = reason.ifBlank { null },
                                status = "Returned"
                            )
                        )
                    }
                    remaining -= returnQty
                }

                val reasonText = if (reason.isNotBlank()) " Reason: $reason." else ""
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Item Returned",
                        details = "$amount of ${item.name} returned.$reasonText Available: ${updatedItem.availableQuantity}, In Use: ${updatedItem.inUseQuantity}.",
                        timestamp = now
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Returned $amount × ${item.name}"
            }.onFailure { _operationError.value = it.message ?: "Failed to return item" }
        }
    }

    // --- Linked Barcode Operations ---

    fun getLinkedBarcodes(itemId: Int): Flow<List<LinkedBarcode>> {
        return repository.getLinkedBarcodesByItem(itemId)
    }

    fun linkBarcode(itemId: Int, barcodeValue: String, label: String = "") {
        if (!_currentUserRole.value.canEditItem) {
            _operationError.value = "Only Admin can link barcodes"
            return
        }
        if (barcodeValue.isBlank()) {
            _operationError.value = "Barcode value cannot be empty"
            return
        }
        viewModelScope.launch {
            runCatching {
                val normalized = normalizeAssetCode(barcodeValue)
                val existing = repository.findByBarcodeValue(normalized)
                if (existing != null) {
                    _operationError.value = "This barcode is already linked to another item"
                    return@launch
                }
                repository.insertLinkedBarcode(
                    LinkedBarcode(
                        itemId = itemId,
                        barcodeValue = normalized,
                        label = label.trim(),
                        linkedAt = System.currentTimeMillis()
                    )
                )
                repository.insertHistory(
                    ItemHistory(
                        itemId = itemId,
                        action = "Barcode Linked",
                        details = "Barcode \"$normalized\" linked${if (label.isNotBlank()) " (label: $label)" else ""}.",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Linked barcode \"${normalizeAssetCode(barcodeValue)}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to link barcode" }
        }
    }

    fun unlinkBarcode(barcode: LinkedBarcode) {
        if (!_currentUserRole.value.canEditItem) {
            _operationError.value = "Only Admin can unlink barcodes"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.deleteLinkedBarcode(barcode)
                repository.insertHistory(
                    ItemHistory(
                        itemId = barcode.itemId,
                        action = "Barcode Unlinked",
                        details = "Barcode \"${barcode.barcodeValue}\" removed.",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Unlinked barcode \"${barcode.barcodeValue}\""
            }.onFailure { _operationError.value = it.message ?: "Failed to unlink barcode" }
        }
    }

    sealed class ScanResult {
        data class MatchedByAssetCode(val item: InventoryItem) : ScanResult()
        data class MatchedByLinkedBarcode(val item: InventoryItem, val barcode: LinkedBarcode) : ScanResult()
        data class NoMatch(val scannedCode: String) : ScanResult()
    }

    suspend fun lookupScannedBarcode(rawValue: String): ScanResult {
        val normalized = normalizeAssetCode(rawValue)

        // 1. Check assetCode match
        val byAssetCode = repository.getItemByAssetCode(normalized).first()
        if (byAssetCode != null) {
            return ScanResult.MatchedByAssetCode(byAssetCode)
        }

        // 2. Check linked_barcodes match
        val linkedBarcode = repository.findByBarcodeValue(normalized)
        if (linkedBarcode != null) {
            val item = repository.getItem(linkedBarcode.itemId).first()
            if (item != null) {
                return ScanResult.MatchedByLinkedBarcode(item, linkedBarcode)
            }
        }

        return ScanResult.NoMatch(normalized)
    }

    fun recordStockTakeReconciliation(
        sessionLabel: String,
        verifiedItemIds: List<Int>,
        missingItemIds: List<Int>
    ) {
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                verifiedItemIds.forEach { id ->
                    repository.insertHistory(
                        ItemHistory(
                            itemId = id,
                            action = "Stock-Take Verified",
                            details = "Confirmed present during $sessionLabel",
                            timestamp = now
                        )
                    )
                }
                missingItemIds.forEach { id ->
                    repository.insertHistory(
                        ItemHistory(
                            itemId = id,
                            action = "Stock-Take Missing",
                            details = "Flagged missing during $sessionLabel",
                            timestamp = now
                        )
                    )
                }
            }.onSuccess {
                _operationMessage.value =
                    "Stock-take saved: ${verifiedItemIds.size} verified, ${missingItemIds.size} missing"
            }.onFailure { e ->
                _operationError.value =
                    "Failed to save stock-take: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun scanCheckout(item: InventoryItem, barcodeId: Int, location: String) {
        if (!_currentUserRole.value.canAdjustUsage) {
            _operationError.value = "Your role cannot check out items"
            return
        }
        if (!item.isUsageTrackable) {
            _operationError.value = "Damaged or retired items cannot be checked out"
            return
        }
        if (item.availableQuantity <= 0) {
            _operationError.value = "No available units to check out"
            return
        }

        val now = System.currentTimeMillis()
        val updatedItem = item.copy(
            inUseQuantity = item.inUseQuantity + 1,
            status = "In-Use",
            lastUpdated = now
        )

        viewModelScope.launch {
            runCatching {
                // Verify this barcode isn't already checked out
                val activeRecord = repository.getActiveUsageRecordByBarcodeId(barcodeId)
                if (activeRecord != null) {
                    _operationError.value = "This unit is already checked out"
                    return@launch
                }

                val barcode = repository.getLinkedBarcodeById(barcodeId)
                val barcodeLabel = barcode?.barcodeValue ?: "Unknown"

                repository.updateItem(updatedItem)
                repository.insertUsageRecord(
                    ItemUsageRecord(
                        itemId = item.id,
                        quantity = 1,
                        location = location.ifBlank { item.location ?: "Unspecified" },
                        usedBy = _loggedInUser.value?.name ?: _currentUserRole.value.displayName,
                        checkedOutAt = now,
                        barcodeId = barcodeId
                    )
                )
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Scan Checkout",
                        details = "1 unit of ${item.name} (barcode: $barcodeLabel) checked out to ${location.ifBlank { "unspecified location" }}.",
                        timestamp = now
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Checked out 1 × ${item.name}"
            }.onFailure { _operationError.value = it.message ?: "Failed to check out item" }
        }
    }

    fun scanReturn(item: InventoryItem, barcodeId: Int, reason: String = "") {
        if (!_currentUserRole.value.canAdjustUsage) {
            _operationError.value = "Your role cannot return items"
            return
        }

        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                val activeRecord = repository.getActiveUsageRecordByBarcodeId(barcodeId)
                if (activeRecord == null) {
                    _operationError.value = "This unit is not currently checked out"
                    return@launch
                }

                val barcode = repository.getLinkedBarcodeById(barcodeId)
                val barcodeLabel = barcode?.barcodeValue ?: "Unknown"

                val newInUse = (item.inUseQuantity - 1).coerceAtLeast(0)
                val updatedItem = item.copy(
                    inUseQuantity = newInUse,
                    status = if (newInUse > 0) "In-Use" else "Available",
                    lastUpdated = now
                )

                repository.updateItem(updatedItem)
                repository.updateUsageRecord(
                    activeRecord.copy(
                        returnedAt = now,
                        returnReason = reason.ifBlank { null },
                        status = "Returned"
                    )
                )
                repository.insertHistory(
                    ItemHistory(
                        itemId = item.id,
                        action = "Scan Return",
                        details = "1 unit of ${item.name} (barcode: $barcodeLabel) returned.${if (reason.isNotBlank()) " Reason: $reason." else ""}",
                        timestamp = now
                    )
                )
            }.onSuccess {
                _operationMessage.value = "Returned 1 × ${item.name}"
            }.onFailure { _operationError.value = it.message ?: "Failed to return item" }
        }
    }

    fun prepareItemForSave(
        existingItem: InventoryItem?,
        itemId: Int?,
        assetCodeOverride: String? = null,
        name: String,
        category: String,
        quantityText: String,
        status: String,
        condition: String,
        location: String,
        description: String,
        imageUri: String? = null
    ): SaveItemResult {
        if (name.isBlank()) {
            return SaveItemResult.ValidationError("Item name is required")
        }

        val parsedQuantity = quantityText.toIntOrNull()
            ?: return SaveItemResult.ValidationError("Quantity must be a number")

        if (parsedQuantity <= 0) {
            return SaveItemResult.ValidationError("Quantity must be greater than 0")
        }

        val preservedInUseQuantity = existingItem?.inUseQuantity ?: 0
        if (parsedQuantity < preservedInUseQuantity) {
            return SaveItemResult.ValidationError("Total quantity cannot be lower than the in-use quantity")
        }
        if ((status == "Damaged" || status == "Retired") && preservedInUseQuantity > 0) {
            return SaveItemResult.ValidationError("Return all checked-out quantity before marking this item as $status")
        }

        val now = System.currentTimeMillis()
        val resolvedAssetCode = existingItem?.assetCode
            ?: assetCodeOverride?.let(::normalizeAssetCode)?.takeIf { it.isNotBlank() }
            ?: generateAssetCode()
        val item = InventoryItem(
            id = itemId ?: 0,
            assetCode = resolvedAssetCode,
            name = name.trim(),
            category = category,
            quantity = parsedQuantity,
            inUseQuantity = preservedInUseQuantity,
            status = when {
                status == "Damaged" || status == "Retired" -> status
                preservedInUseQuantity > 0 -> "In-Use"
                else -> "Available"
            },
            condition = condition,
            location = location.ifBlank { null },
            description = description.ifBlank { null },
            // Use the form's value verbatim. The edit screen seeds it from
            // the existing item and sets it to null when the user taps ×,
            // so a `?: existingItem?.imageUri` fallback would silently undo
            // a photo removal.
            imageUri = imageUri,
            createdAt = existingItem?.createdAt ?: now,
            lastUpdated = now
        )
        return SaveItemResult.Success(item)
    }

    private fun generateAssetCode(): String {
        return "SS-${UUID.randomUUID().toString().take(8).uppercase()}"
    }

    private fun normalizeAssetCode(value: String): String {
        return value
            .trim()
            .replace("\\s+".toRegex(), "")
            .uppercase(Locale.ROOT)
    }

    private fun persistQuantityChange(
        updatedItem: InventoryItem,
        action: String,
        details: String
    ) {
        viewModelScope.launch {
            runCatching {
                val updatedCount = repository.updateItem(updatedItem)
                if (updatedCount == 0) {
                    throw IllegalStateException("Failed to update item: item not found")
                }
                repository.insertHistory(
                    ItemHistory(
                        itemId = updatedItem.id,
                        action = action,
                        details = details,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onFailure { _operationError.value = it.message ?: "Failed to update item quantity" }
        }
    }
}

