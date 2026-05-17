package com.example.smartstock.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartstock.MainActivity
import com.example.smartstock.R
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.data.dao.InventoryDao
import com.example.smartstock.data.entity.InventoryItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LowStockCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventoryDao: InventoryDao,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "LowStockWorker"
        const val WORK_NAME = "smartstock_low_stock_check"
        const val EXTRA_ITEM_ID = "low_stock_item_id"
        private const val SUMMARY_NOTIFICATION_ID = 1000
        private const val PER_ITEM_BASE_ID = 2000
    }

    override suspend fun doWork(): Result {
        if (!appPreferences.lowStockNotificationsEnabled) {
            Log.d(TAG, "Low-stock alerts disabled in preferences — skipping")
            return Result.success()
        }
        if (!hasPostNotificationPermission()) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted — skipping")
            return Result.success()
        }

        val threshold = appPreferences.lowStockThreshold
        val lowItems = inventoryDao.getLowStockItems(threshold)
        Log.d(TAG, "Low-stock check: threshold=$threshold, ${lowItems.size} item(s) below")

        if (lowItems.isEmpty()) {
            cancelAllLowStockNotifications()
            return Result.success()
        }

        postLowStockNotifications(lowItems, threshold)
        return Result.success()
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelAllLowStockNotifications() {
        val nm = NotificationManagerCompat.from(applicationContext)
        nm.cancel(SUMMARY_NOTIFICATION_ID)
    }

    private fun postLowStockNotifications(items: List<InventoryItem>, threshold: Int) {
        val nm = NotificationManagerCompat.from(applicationContext)
        val small = R.drawable.smartstock_logo

        items.forEachIndexed { index, item ->
            val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ITEM_ID, item.id)
            }
            val pending = PendingIntent.getActivity(
                applicationContext,
                item.id,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.LOW_STOCK_ID)
                .setSmallIcon(small)
                .setContentTitle("Low stock: ${item.name}")
                .setContentText("Only ${item.availableQuantity} available (threshold $threshold)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setGroup(NotificationChannels.LOW_STOCK_GROUP_KEY)
                .setContentIntent(pending)

            try {
                nm.notify(PER_ITEM_BASE_ID + index, builder.build())
            } catch (se: SecurityException) {
                Log.w(TAG, "Notification post denied", se)
            }
        }

        if (items.size > 1) {
            val summaryIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val summaryPending = PendingIntent.getActivity(
                applicationContext,
                0,
                summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle("${items.size} items low on stock")
            items.take(6).forEach {
                style.addLine("${it.name} — ${it.availableQuantity} left")
            }
            if (items.size > 6) {
                style.setSummaryText("+${items.size - 6} more")
            }
            val summary = NotificationCompat.Builder(applicationContext, NotificationChannels.LOW_STOCK_ID)
                .setSmallIcon(small)
                .setContentTitle("${items.size} items low on stock")
                .setContentText("Tap to review inventory")
                .setStyle(style)
                .setGroup(NotificationChannels.LOW_STOCK_GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(summaryPending)
                .build()
            try {
                nm.notify(SUMMARY_NOTIFICATION_ID, summary)
            } catch (se: SecurityException) {
                Log.w(TAG, "Summary notification post denied", se)
            }
        }
    }
}
