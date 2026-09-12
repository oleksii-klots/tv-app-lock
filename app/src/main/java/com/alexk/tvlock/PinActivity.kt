package com.alexk.tvlock

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast

class PinActivity : Activity() {

    private lateinit var display: TextView
    private var entered = ""
    private var accepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        display = findViewById(R.id.pin_display)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val digit = when (keyCode) {
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
        when {
            digit != null -> {
                if (entered.length < 4) {
                    entered += digit
                    display.text = "•".repeat(entered.length)
                    if (entered.length == 4) checkPin()
                }
                return true
            }
            keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK -> {
                if (entered.isNotEmpty()) {
                    entered = entered.dropLast(1)
                    display.text = "•".repeat(entered.length)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkPin() {
        val expected = Prefs(this).pin()
        if (expected.isEmpty() || entered == expected) {
            accepted = true
            LockPollService.instance?.onUnlocked()
            Toast.makeText(this, R.string.unlocked, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            entered = ""
            display.text = getString(R.string.wrong_pin)
            display.postDelayed({ display.text = "" }, 1500)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!accepted) LockPollService.instance?.onGateDismissed()
    }
}
