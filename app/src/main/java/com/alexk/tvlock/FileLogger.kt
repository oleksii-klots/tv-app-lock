package com.alexk.tvlock

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.FileWriter

/**
 * File-based event log.
 *
 * Why: the Rockteck ROM filters third-party apps' logcat output entirely —
 * Log.i/w/e from LockPollService never reach `adb logcat`, so field races
 * (fast-button press) were unmeasurable. This appends to
 * <externalFiles>/tvlock.log, which adb shell can read without root.
 *
 * Kept deliberately cheap: one line per event, monotonic clock + wall time,
 * file trimmed when it grows past 256 KB. Every gate-lifecycle decision point
 * writes here; treat it as the flight recorder.
 */
object FileLogger {

    @Volatile private var writer: FileWriter? = null
    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, "tvlock.log")
            if ((logFile?.length() ?: 0L) > TRIM_AT) logFile?.delete()
            writer = FileWriter(logFile, true)
            android.util.Log.i("TVLOCK", "file logger at ${logFile?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("TVLOCK", "file logger init failed", e)
        }
    }

    fun log(msg: String) {
        android.util.Log.i("TVLOCK", msg) // still emit in case logcat works on other ROMs
        val w = writer ?: return
        try {
            synchronized(w) {
                w.write("${System.currentTimeMillis()} up=${SystemClock.elapsedRealtime()} $msg\n")
                w.flush()
            }
            val f = logFile
            if (f != null && f.length() > TRIM_AT) {
                synchronized(w) {
                    runCatching { w.close() }
                    f.delete()
                    writer = FileWriter(f, true).also { writer = it }
                }
            }
        } catch (_: Exception) { /* never break the lock over logging */ }
    }

    private const val TRIM_AT = 256L * 1024
}
