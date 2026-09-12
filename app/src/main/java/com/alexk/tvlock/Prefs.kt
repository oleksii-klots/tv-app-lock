package com.alexk.tvlock

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("tvlock", Context.MODE_PRIVATE)

    fun pin(): String = sp.getString("pin", "") ?: ""
    fun setPin(v: String) = sp.edit().putString("pin", v).apply()

    fun blockedList(): Set<String> =
        sp.getStringSet("blocked", emptySet()) ?: emptySet()

    fun setBlocked(pkgs: Set<String>) = sp.edit().putStringSet("blocked", pkgs).apply()

    /** -1 = до перезавантаження, 0 = завжди питати PIN */
    fun graceMinutes(): Int = sp.getInt("grace", 30)
    fun setGraceMinutes(v: Int) = sp.edit().putInt("grace", v).apply()

    /** persisted so a service restart cannot silently wipe an active grace window */
    fun unlockUntil(): Long = sp.getLong("unlock_until", 0L)
    fun setUnlockUntil(v: Long) = sp.edit().putLong("unlock_until", v).apply()
    fun closeGrace() = sp.edit().putLong("unlock_until", 0L).apply()

    companion object {
        val GRACE_CYCLE = listOf(0, 5, 15, 30, 60, -1)
    }
}
