package com.alexk.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var graceBtn: Button
    private lateinit var keypad: KeypadHelper
    private var pinBuffer = ""
    private var pinMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        graceBtn = findViewById(R.id.btn_grace)

        findViewById<Button>(R.id.btn_setup_pin).setOnClickListener { showPinEntry() }
        graceBtn.setOnClickListener { cycleGrace() }
        findViewById<Button>(R.id.btn_blocked).setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_service).setOnClickListener {
            // request usage-stats access — the only permission the guardian needs
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.no_usage_settings, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<TextView>(R.id.version_text).text =
            "TV App Lock  v " + (try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (_: Exception) { "?" })
        keypad = KeypadHelper(this, findViewById(R.id.main_root),
            onDigit = { kPinDigit(it) }, onBackspace = { kPinBack() }, onClear = { kPinClear() })
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageAccess()) {
            startServiceIfAllowed()
            LockPollService.instance?.reloadPrefs()
        }
        updateStatus()
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun startServiceIfAllowed() {
        try { startForegroundService(Intent(this, LockPollService::class.java)) }
        catch (_: Exception) {}
    }

    private fun cycleGrace() {
        val p = Prefs(this)
        val cur = p.graceMinutes()
        val idx = Prefs.GRACE_CYCLE.indexOf(cur).let { if (it < 0) 3 else it }
        val next = Prefs.GRACE_CYCLE[(idx + 1) % Prefs.GRACE_CYCLE.size]
        p.setGraceMinutes(next)
        LockPollService.instance?.reloadPrefs()
        Toast.makeText(this, getString(R.string.grace_set, graceLabel(next)), Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun graceLabel(v: Int): String = when {
        v < 0 -> getString(R.string.grace_reboot)
        v == 0 -> getString(R.string.grace_always)
        else -> "$v хв"
    }

    private fun updateStatus() {
        val p = Prefs(this)
        val running = LockPollService.instance != null
        val until = p.unlockUntil()
        val now = System.currentTimeMillis()
        val lockLine = when {
            until <= now -> getString(R.string.lock_armed)
            until == Long.MAX_VALUE -> getString(R.string.lock_open_now)
            else -> getString(R.string.lock_open_fmt,
                ((until - now + 59_999) / 60_000).toInt())
        }
        statusText.text = getString(
            R.string.status_fmt,
            if (p.pin().isEmpty()) "не встановлено ⚠️" else "встановлено ✅",
            p.blockedList().joinToString(", ").ifEmpty { "—" },
            graceLabel(p.graceMinutes()),
            if (!hasUsageAccess()) "немає дозволу на доступ ⚠️"
            else if (running) "активний ✅" else "зупинений ⚠️",
            lockLine)
    }

    private fun showPinEntry() {
        pinBuffer = ""
        pinMode = true
        currentFocus?.clearFocus() // button must not eat the OK key
        statusText.text = getString(R.string.enter_new_pin)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (pinMode) {
            val digit = keyToDigit(keyCode)
            if (digit != null) { kPinDigit(digit); return true }
            if (keyCode == KeyEvent.KEYCODE_DEL) { kPinBack(); return true }
            if (keyCode == KeyEvent.KEYCODE_BACK && keypad.onBackPressed()) return true
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (!keypad.visible) confirmPin()
                return true
            }
            keypad.onKeyDown(keyCode)
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && keypad.onBackPressed()) return true
        keypad.onKeyDown(keyCode)
        return super.onKeyDown(keyCode, event)
    }

    private fun kPinDigit(d: String) {
        if (pinBuffer.length < 4) {
            pinBuffer += d
            statusText.text = getString(R.string.pin_typing_fmt, "•".repeat(pinBuffer.length))
            if (pinBuffer.length == 4) confirmPin()
        }
    }

    private fun kPinBack() {
        if (pinBuffer.isNotEmpty()) {
            pinBuffer = pinBuffer.dropLast(1)
            statusText.text = getString(R.string.pin_typing_fmt, "•".repeat(pinBuffer.length))
        }
    }

    private fun kPinClear() {
        pinBuffer = ""
        statusText.text = getString(R.string.enter_new_pin)
    }

    private fun keyToDigit(k: Int): String? = when (k) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
        else -> null
    }

    private fun confirmPin() {
        if (pinBuffer.length == 4) {
            Prefs(this).setPin(pinBuffer)
            Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_LONG).show()
            pinBuffer = ""; pinMode = false
            updateStatus()
        } else {
            Toast.makeText(this, R.string.pin_need_4, Toast.LENGTH_LONG).show()
        }
    }
}
