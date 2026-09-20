package com.alexk.tvlock

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class PinActivity : Activity() {

    private lateinit var display: TextView
    private lateinit var keypad: KeypadHelper
    private var entered = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        display = findViewById(R.id.pin_display)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        keypad = KeypadHelper(this, findViewById(R.id.pin_root),
            onDigit = { feed(it) }, onBackspace = { back() }, onClear = { clear() })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Launch-shortcut hardware keys must be consumed while the gate is up,
        // otherwise the system relaunches the blocked app over the PIN screen
        // and sustains the swap storm that deadlocks change-only gating.
        if (isShortcutLaunchKey(keyCode)) return true
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
        if (digit != null) { feed(digit); return true }
        if (keyCode == KeyEvent.KEYCODE_BACK && keypad.onBackPressed()) return true
        if (keyCode == KeyEvent.KEYCODE_DEL && entered.isNotEmpty()) { back(); return true }
        keypad.onKeyDown(keyCode)
        return super.onKeyDown(keyCode, event)
    }

    private fun feed(d: String) {
        if (entered.length < 4) {
            entered += d
            display.text = "•".repeat(entered.length)
            if (entered.length == 4) checkPin()
        }
    }

    private fun back() {
        if (entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            display.text = "•".repeat(entered.length)
        }
    }

    private fun clear() {
        entered = ""
        display.text = ""
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

    private var accepted = false

    override fun onPause() {
        super.onPause()
        if (!accepted) LockPollService.instance?.onGateDismissed()
    }

    // Launch-shortcut class of the RT-G2 remote.
    //   268  = the dedicated YouTube button. There is NO public named constant for
    //          it in android.view.KeyEvent (KEYCODE_YOUTUBE is not a real SDK symbol;
    //          it is the raw int the button emits).
    //   177  = the dedicated Netflix/SmartTube button = KeyEvent.KEYCODE_MENU
    //          (stock AOSP names 177 MENU; forensics 20.09: it relaunches the
    //          blocked app over the gate).
    // DPAD / navigation-DPAD keys are deliberately NOT consumed — the PIN UI still
    // runs on the D-pad, 3×DPAD_UP within 2 s summons the hidden keypad, and BACK
    // hides it without leaving the screen.
    private val LAUNCH_SHORTCUTS: Set<Int> = setOf(
        268,                       // RT-G2 YouTube button (raw keycode)
        KeyEvent.KEYCODE_MENU,     // 177 — RT-G2 Netflix/SmartTube button
    )

    private fun isShortcutLaunchKey(keyCode: Int): Boolean = LAUNCH_SHORTCUTS.contains(keyCode)
}
