package com.alexk.tvlock

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button

/**
 * Hidden on-screen keypad for remotes without number buttons.
 * Summoned by 3x DPAD_UP within 2 s (the Up key still works normally as a
 * navigation key between presses); BACK hides it again.
 * The keypad is GONE until summoned — nothing is permanently visible.
 * It inflates as a ScrollView overlay into a FrameLayout root, so a short
 * screen never clips rows (they become scrollable instead).
 */
class KeypadHelper(
    private val activity: Activity,
    private val container: ViewGroup,
    private val onDigit: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClear: () -> Unit,
) {
    private var root: ViewGroup? = null
    private val upTimes = ArrayDeque<Long>()

    val visible: Boolean get() = root?.visibility == View.VISIBLE

    /** true = gesture fired and keypad now open (host may still consume the key) */
    fun onKeyDown(keyCode: Int): Boolean {
        if (visible) return false
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val now = System.currentTimeMillis()
            upTimes.addLast(now)
            while (upTimes.isNotEmpty() && now - upTimes.first() > GESTURE_WINDOW_MS) upTimes.removeFirst()
            if (upTimes.size >= GESTURE_TAPS) {
                upTimes.clear()
                show()
            }
        }
        return false
    }

    fun onBackPressed(): Boolean {
        if (!visible) return false
        hide()
        return true
    }

    private fun show() {
        if (root == null) {
            root = activity.layoutInflater
                .inflate(R.layout.keypad, container, false) as ViewGroup
            container.addView(root)
            val digitIds = mapOf(
                R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3",
                R.id.k4 to "4", R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7",
                R.id.k8 to "8", R.id.k9 to "9")
            digitIds.forEach { (id, label) ->
                root!!.findViewById<Button>(id).setOnClickListener { onDigit(label) }
            }
            root!!.findViewById<Button>(R.id.kc).setOnClickListener { onBackspace() }
            root!!.findViewById<Button>(R.id.kx).setOnClickListener { onClear() }
        }
        root!!.visibility = View.VISIBLE
        blurContent(true)
        root!!.findViewById<Button>(R.id.k1).requestFocus()
    }

    fun hide() {
        if (visible) {
            root!!.visibility = View.GONE
            blurContent(false)
        }
    }

    /** real blur of the screen behind the keypad (API 31+), scrim dim covers older */
    private fun blurContent(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        try {
            val content = (0 until container.childCount)
                .map { container.getChildAt(it) }
                .firstOrNull { it !== root }
            content?.setRenderEffect(
                if (on) android.graphics.RenderEffect.createBlurEffect(25f, 25f,
                        android.graphics.Shader.TileMode.CLAMP)
                else null)
        } catch (_: Exception) {}
    }

    companion object {
        const val GESTURE_WINDOW_MS = 2_000L
        const val GESTURE_TAPS = 3
    }
}
