package com.example.smartstock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.smartstock.core.notifications.NotificationChannels
import com.example.smartstock.core.sync.SyncManager
import com.example.smartstock.data.repository.InventoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp(Application::class)
class SmartStockApp : Hilt_SmartStockApp(), Configuration.Provider {

    @Inject lateinit var repository: InventoryRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
        applicationScope.launch {
            repository.seedReferenceData()
        }
        syncManager.schedulePeriodicSync()
        syncManager.scheduleLowStockChecks()
    }
}
