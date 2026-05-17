package com.example.smartstock.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object NotificationChannels {
    const val LOW_STOCK_ID = "low_stock_alerts"
    private const val LOW_STOCK_NAME = "Low-stock alerts"
    private const val LOW_STOCK_DESC = "Alerts when an inventory item drops to its low-stock threshold."

    const val LOW_STOCK_GROUP_KEY = "com.example.smartstock.LOW_STOCK"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(LOW_STOCK_ID) != null) return

        val channel = NotificationChannel(
            LOW_STOCK_ID,
            LOW_STOCK_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = LOW_STOCK_DESC
            enableLights(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
