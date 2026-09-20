package com.alexk.tvlock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat

/**
 * Instant opaque blackout window raised the moment a blocked app is detected,
 * BEFORE PinActivity is launched.
 *
 * Why: the gate is an ACTIVITY — showing it costs a full AMS launch + inflate
 * (~1-2 s on these boxes). UsageStats detection already lands ~0.5 s late, so
 * a fast dedicated-button press used to reach YouTube's profile picker before
 * the gate painted. An overlay is NOT an activity: WindowManager.addView from
 * the service paints in <100 ms, needs no BAL exemption (SYSTEM_ALERT_WINDOW
 * is granted at setup), and a focusable full-screen window swallows every key
 * and touch — video cannot render visibly and input cannot reach the app.
 *
 * Lifetime: shown by raiseGate(), hidden when the poller confirms the real
 * gate on top, whenever gateUp goes false, and by a hard cap (OVERLAY_MAX_MS)
 * so a broken overlay can never brick the screen permanently.
 */
object GateOverlay {

    private var wm: WindowManager? = null
    private var view: View? = null
    var shownAt: Long = 0L
        private set

    val isShown: Boolean get() = view != null

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, now: Long) {
        if (view != null) return
        if (!canShow(context)) {
            android.util.Log.w("TVLOCK", "overlay permission missing — skipping blackout")
            return
        }
        try {
            wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = object : android.widget.FrameLayout(context) {
                override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = true
                override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true
                override fun onTouchEvent(event: MotionEvent): Boolean = true
            }
            val label = android.widget.TextView(context).apply {
                text = context.getString(R.string.overlay_locked)
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = android.view.Gravity.CENTER
            }
            root.addView(label, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER))
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                // focusable (no FLAG_NOT_FOCUSABLE) so ALL key events land here,
                // not on the blocked app underneath
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE)
            root.setBackgroundColor(Color.BLACK)
            wm!!.addView(root, lp)
            root.requestFocus()
            view = root
            shownAt = now
            android.util.Log.i("TVLOCK", "blackout overlay shown")
        } catch (e: Exception) {
            android.util.Log.e("TVLOCK", "overlay show FAILED", e)
            view = null
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        try {
            wm?.removeView(v)
            android.util.Log.i("TVLOCK", "blackout overlay hidden")
        } catch (e: Exception) {
            android.util.Log.e("TVLOCK", "overlay hide FAILED", e)
        }
    }
}
