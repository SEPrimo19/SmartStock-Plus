package com.example.smartstock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartstock.core.network.ConnectivityObserver
import com.example.smartstock.core.sync.SyncManager
import com.example.smartstock.core.sync.SyncState
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: InventoryRepository,
    connectivityObserver: ConnectivityObserver,
    private val syncManager: SyncManager
) : ViewModel() {

    fun requestSync() {
        syncManager.requestImmediateSync()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }

    val allItems: StateFlow<List<InventoryItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val availableItems: StateFlow<List<InventoryItem>> = repository.availableItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val inUseItems: StateFlow<List<InventoryItem>> = repository.inUseItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val syncState: StateFlow<SyncState> = syncManager.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SyncState.IDLE)

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)
}
