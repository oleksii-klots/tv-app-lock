package com.alexk.tvlock

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // "grace until reboot" must truly end at reboot
                Prefs(context).closeGrace()
                startGuard(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> startGuard(context)
        }
    }

    private fun startGuard(context: Context) {
        try {
            context.startForegroundService(Intent(context, LockPollService::class.java))
        } catch (_: Exception) {}
    }
}
