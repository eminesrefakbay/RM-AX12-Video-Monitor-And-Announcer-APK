package dev.ax12.announcer

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What happened while the app and the radio link started, and when. Observe-only: it records, it never changes behaviour.
 * Times count from the start of the app's process, which is about when you tapped the icon.
 */
object StartupState {
    val timeline = StartupTimeline()
    @Volatile var processStartMs = SystemClock.elapsedRealtime()
    @Volatile var nativeBaseMs = 0L           // when the native SDK start began (0 = not yet)
    @Volatile var method = 0                  // 0 standard, 1 like RadioMaster's demo
    @Volatile private var logged = false
    private const val LOG_FILE = "startup_timings.txt"
    private const val KEEP = 10

    /** Called once when the app's process starts. */
    fun begin() {
        processStartMs = if (Build.VERSION.SDK_INT >= 24) Process.getStartElapsedRealtime() else SystemClock.elapsedRealtime()
        timeline.mark(StartupTimeline.PROCESS, 0.0)
        mark(StartupTimeline.APP_CREATED)
    }

    fun rel(now: Long = SystemClock.elapsedRealtime()): Double = (now - processStartMs).toDouble()

    fun mark(label: String) = timeline.mark(label, rel())

    /** Brings the bridge's own steps onto the same clock. */
    fun refreshNative(link: AxSdkRadioLink?) {
        if (nativeBaseMs <= 0L) return
        val tl = link?.timeline() ?: return
        timeline.mergeNative(tl, rel(nativeBaseMs))
    }

    fun methodName(m: Int = method): String = if (m == 1) "demo-like" else "standard"

    /** The steps so far, one per line, for the variables panel. */
    fun text(link: AxSdkRadioLink?): String {
        refreshNative(link)
        val lines = timeline.lines()
        return "start-up timeline (" + methodName() + ", ms since the app started)\n" + (if (lines.isEmpty()) "  nothing yet\n" else lines.joinToString("\n") + "\n")
    }

    /** Writes one line for this launch, once: the moment the first stick value reached the screen. */
    fun logLaunch(context: Context, link: AxSdkRadioLink?) {
        if (logged) return
        logged = true
        refreshNative(link)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = stamp + " | " + timeline.summary(methodName())
        try {
            val f = File(context.filesDir, LOG_FILE)
            val old = if (f.exists()) f.readText() else ""
            f.writeText(StartupTimeline.appendLine(old, line, KEEP))
        } catch (e: Exception) {
            // a diagnostic must never get in the way
        }
    }

    fun readLog(context: Context): String = try {
        val f = File(context.filesDir, LOG_FILE)
        if (f.exists()) f.readText() else ""
    } catch (e: Exception) {
        ""
    }

    fun clearLog(context: Context) {
        try { File(context.filesDir, LOG_FILE).delete() } catch (e: Exception) { }
    }
}

/**
 * One thread for starting and stopping the SDK in the background, shared by every screen of the app, so a stop and the next
 * start always happen in the order they were asked for (for example when the screen is rebuilt).
 */
object SdkThread {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ax12-sdk") }

    fun run(task: () -> Unit) {
        executor.execute {
            try { task() } catch (e: Throwable) { /* reported by the caller's own checks */ }
        }
    }

    /** Waits until everything asked for so far has been done (at most 3 seconds). */
    fun awaitIdle() {
        try { executor.submit { }.get(3, TimeUnit.SECONDS) } catch (e: Exception) { }
    }
}
