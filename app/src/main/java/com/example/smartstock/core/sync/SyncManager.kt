package com.example.smartstock.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.smartstock.core.notifications.LowStockCheckWorker
import com.example.smartstock.core.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class SyncManager(
    private val context: Context,
    private val preferences: AppPreferences
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedule periodic sync every 15 minutes (minimum WorkManager interval).
     * No-op when the user has cloud sync turned off — and cancels any
     * previously-scheduled periodic work so it doesn't tick in the background.
     */
    fun schedulePeriodicSync() {
        if (!preferences.cloudSyncEnabled) {
            workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Trigger an immediate one-time sync when connectivity is restored
     * or after a write operation. Skipped when sync is disabled.
     */
    fun requestImmediateSync() {
        if (!preferences.cloudSyncEnabled) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancel all scheduled sync work. Called when the user disables sync.
     */
    fun cancelAllSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_ONE_TIME)
    }

    fun scheduleLowStockChecks() {
        val request = PeriodicWorkRequestBuilder<LowStockCheckWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .addTag(LowStockCheckWorker.TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            LowStockCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runLowStockCheckNow() {
        val request = OneTimeWorkRequestBuilder<LowStockCheckWorker>()
            .addTag(LowStockCheckWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            "${LowStockCheckWorker.WORK_NAME}_oneshot",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelLowStockChecks() {
        workManager.cancelUniqueWork(LowStockCheckWorker.WORK_NAME)
    }

    /**
     * Observe the sync work state for UI display.
     */
    fun observeSyncState(): Flow<SyncState> {
        return workManager.getWorkInfosByTagFlow(SyncWorker.TAG).map { workInfos ->
            val running = workInfos.any { it.state == WorkInfo.State.RUNNING }
            val enqueued = workInfos.any { it.state == WorkInfo.State.ENQUEUED }
            when {
                running -> SyncState.SYNCING
                enqueued -> SyncState.PENDING
                else -> SyncState.IDLE
            }
        }
    }
}

enum class SyncState {
    IDLE,
    PENDING,
    SYNCING
}
