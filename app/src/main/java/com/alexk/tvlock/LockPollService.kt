package com.alexk.tvlock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Foreground service that polls UsageStats for the top app.
 * No accessibility service → launcher key handling stays untouched.
 *
 * Robustness rules:
 *  - the gate is raised ONLY on a real foreground CHANGE into a blocked app
 *  - a raised gate that did not actually appear within 2.5 s (silent BAL deny,
 *    race, crash) is re-armed immediately: a stuck gateUp must never kill the lock
 *  - grace state lives in prefs, so a process restart cannot widen it invisibly
 */
class LockPollService : Service() {

    private lateinit var handler: Handler
    private lateinit var usm: UsageStatsManager

    private var blocked = setOf<String>()
    private var unlockUntil = 0L
    private var lastTop: String? = null
    private var seeded = false
    private var gateUp = false
    private var gateRaisedAt = 0L
    // Cooldown start for the watchdog recovery re-raise, set when the gate was
    // confirmed visible. 0 = first ever raise always allowed.
    private var recoveryReadyAt = 0L
    private var targetPkg: String? = null
    private var pendingConsume: String? = null
    private var pendingUntil = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        handler = Handler(Looper.getMainLooper())
        usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        LockNotification.makeChannel(this)
        startForeground(1, LockNotification.build(this))
        reloadPrefs()
        handler.postDelayed(poller, POLL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleKeepAlive()
        return START_STICKY
    }

    private val poller = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) {
                android.util.Log.e("TVLOCK", "tick error", e)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val from = if (seeded) now - 15_000 else now - 10 * 60_000L
        val events = usm.queryEvents(from, now)
        var evTop: String? = null
        var evTs = 0L
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if ((ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                 ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) && ev.timeStamp >= evTs) {
                evTs = ev.timeStamp
                evTop = ev.packageName
            }
        }
        val prevTop = lastTop
        val t = evTop ?: lastTop
        if (evTop != null || lastTop == null) lastTop = t
        val foregroundChanged = evTop != null && evTop != prevTop
        if (t == null) { seeded = true; return }
        if (!seeded) {
            android.util.Log.i("TVLOCK", "seeded top=$t")
            seeded = true
        }

        // Hard cap: the blackout must never outlive gate logic under any
        // circumstance, even a bug — an unremovable black screen is a brick.
        if (GateOverlay.isShown && now - GateOverlay.shownAt > OVERLAY_MAX_MS) {
            android.util.Log.w("TVLOCK", "overlay hard cap — forcing hide")
            GateOverlay.hide()
        }

        // watchdog: a gate we raised must actually be on screen
        if (gateUp) {
            if (t == packageName) {
                // gate is up — the blackout did its job, the PIN screen is
                // visible now; drop the overlay underneath it
                GateOverlay.hide()
                return
            }
            if (t !in blocked) {
                // neither the gate nor a blocked app is on top (parent pressed
                // HOME, launcher is showing): nothing to guard — stand down and
                // release the blackout immediately
                gateUp = false
                GateOverlay.hide()
                return
            }
            if (now - gateRaisedAt > GATE_CONFIRM_MS) {
                // Gate vanished while a blocked app was on top: re-arm and give the
                // gate a recovery re-raise chance even without a new foreground
                // change (swap storm collapses inside one poll window, so
                // foregroundChanged never fires again). ONE re-raise, gated by a
                // cooldown — only here, and only because gateUp was true and the
                // gate is proven gone. Presence-gating is NOT used: if the blocked
                // app is merely staying foreground (never gated), nothing re-fires.
                android.util.Log.w("TVLOCK", "gate did not appear — re-arming")
                gateUp = false
                if (t in blocked && now >= unlockUntil && now >= recoveryReadyAt) {
                    raiseGate(t, now)
                    return
                }
            } else {
                return
            }
        }

        if (t == packageName) return
        if (t == pendingConsume && now < pendingUntil) {
            pendingConsume = null
            return
        }
        unlockUntil = Prefs(this).unlockUntil()
        if (t in blocked && foregroundChanged && now >= unlockUntil) {
            raiseGate(t, now)
        }
    }

    private fun raiseGate(t: String, now: Long) {
        gateUp = true
        gateRaisedAt = now
        targetPkg = t
        android.util.Log.i("TVLOCK", "gate raising for $t")
        try {
            // instant blackout FIRST: paints in <100 ms from the service and
            // swallows input, so the blocked app stops being visible/usable
            // regardless of how long the gate activity takes to launch below.
            GateOverlay.show(this, now)
            startActivity(Intent(this, PinActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            // Cooldown window opens now (gate visible on the launch path / or when
            // the watchdog re-arms after seeing it on top, see tick()).
            recoveryReadyAt = now + RECOVERY_COOLDOWN_MS
        } catch (e: Exception) {
            // Fail CLOSED: keep gateUp true so the watchdog keeps re-raising
            // every GATE_CONFIRM_MS — the blackout (if shown) keeps the screen
            // dead meanwhile. Clearing gateUp here is what used to kill the
            // lock silently (pre-1.12 incident).
            android.util.Log.e("TVLOCK", "gate startActivity FAILED", e)
        }
    }

    fun reloadPrefs() {
        val p = Prefs(this)
        val set = p.blockedList().toMutableSet()
        set.remove(packageName)
        blocked = set
        p.setBlocked(set)
        unlockUntil = 0L
        gateUp = false            // re-arm instantly on any config change
        GateOverlay.hide()        // parent touched settings: never trap a black screen
        seeded = false            // re-seed top app
    }

    /** Correct PIN entered: start grace, relaunch the app the user wanted. */
    fun onUnlocked() {
        val p = Prefs(this)
        val grace = p.graceMinutes()
        val until = when {
            grace < 0 -> Long.MAX_VALUE
            else -> System.currentTimeMillis() + maxOf(grace * 60_000L, 15_000L)
        }
        p.setUnlockUntil(until)
        unlockUntil = until
        val t = targetPkg
        gateUp = false
        GateOverlay.hide()
        targetPkg = null
        pendingConsume = t
        pendingUntil = System.currentTimeMillis() + 10_000
        handler.postDelayed({
            t?.let {
                packageManager.getLaunchIntentForPackage(it)?.let { li ->
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    try { startActivity(li) } catch (_: Exception) {
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }, 350)
    }

    fun onGateDismissed() {
        gateUp = false
        GateOverlay.hide()   // never trap a black screen over a non-blocked screen
    }

    /** Self-heal if the ROM kills the FGS: re-arm every 5 min via AlarmManager. */
    private fun scheduleKeepAlive() {
        try {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getService(
                this, 99, Intent(this, LockPollService::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5 * 60_000L, 5 * 60_000L, pi)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val POLL_MS = 250L
        const val GATE_CONFIRM_MS = 2_500L
        // Cooldown window for the watchdog recovery re-raise, measured from the
        // last raise. The 2.5 s watchdog check paces it to >= 2.5 s anyway; this
        // is the explicit guard so a failed gate can't re-raise on every tick.
        const val RECOVERY_COOLDOWN_MS = 1_500L
        // Absolute ceiling for the blackout overlay regardless of gate state:
        // a stuck black screen is worse than a leaked frame. Well above
        // GATE_CONFIRM_MS + RECOVERY_COOLDOWN_MS so it never fires in normal
        // operation — it is the last-resort fuse.
        const val OVERLAY_MAX_MS = 10_000L
        var instance: LockPollService? = null
    }
}
