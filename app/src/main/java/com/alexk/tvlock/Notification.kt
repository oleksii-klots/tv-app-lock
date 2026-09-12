package com.alexk.tvlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object LockNotification {
    private const val CHANNEL = "tvlock"

    fun makeChannel(context: Context) {
        val m = context.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "TV App Lock", NotificationManager.IMPORTANCE_MIN)
        m.createNotificationChannel(ch)
    }

    fun build(context: Context): Notification =
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("TV App Lock")
            .setContentText("running")
            .setOngoing(true)
            .build()
}
