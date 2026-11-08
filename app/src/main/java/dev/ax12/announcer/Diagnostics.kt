// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * What the app knows about itself, for the diagnostics page and the bug report: the events of the session (see [DiagLog]), the record of
 * the last crash, and the report that puts it together. The report holds no GPS position, no aircraft or pack name and nothing personal.
 */
object Diagnostics {
    val log = DiagLog()

    /** The pilot's own words for the next report (kept until the app closes). */
    @Volatile var note: String = ""

    private var crashFile: File? = null

    /** Called first thing when the app starts: from now on an unexpected stop leaves a record. */
    fun install(app: Application) {
        crashFile = File(app.filesDir, "crash_report.txt")
        event("app", "started")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile?.writeText(CrashFile.format(System.currentTimeMillis(), thread.name, error), Charsets.UTF_8)
            } catch (e: Throwable) { /* nothing more can be done in a crash */ }
            previous?.uncaughtException(thread, error)
        }
    }

    fun event(tag: String, message: String, level: String = "info") {
        log.add(System.currentTimeMillis(), level, tag, message)
    }

    fun crashText(): String? = try {
        crashFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun clearCrash() {
        crashFile?.delete()
    }

    private fun mb(bytes: Long): String = (bytes / 1048576L).toString()

    @Suppress("DEPRECATION")        // versionCode: the old field is what API levels below 28 offer
    fun report(activity: Activity, link: AxSdkRadioLink?, logger: FlightLogger, numbers: Map<String, Double>, logDir: File): String {
        val now = System.currentTimeMillis()
        val info = try { activity.packageManager.getPackageInfo(activity.packageName, 0) } catch (e: Exception) { null }
        val code = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val app = BugReport.Section("App")
            .add("Version: " + (info?.versionName ?: "?") + " (build " + code + ")")
            .add("Package: " + activity.packageName)
            .add("Running for: " + ApprovalWindow.clock((SystemClock.elapsedRealtime() - StartupState.processStartMs) / 1000.0))

        val dm = activity.resources.displayMetrics
        val rt = Runtime.getRuntime()
        val device = BugReport.Section("Device")
            .add("Radio: " + Build.MANUFACTURER + " " + Build.MODEL)
            .add("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            .add("Screen: " + dm.widthPixels + "x" + dm.heightPixels + " px, " + (dm.widthPixels / dm.density).toInt() + "x" + (dm.heightPixels / dm.density).toInt() +
                " dp, density " + String.format(Locale.US, "%.2f", dm.density))
            .add("Memory: heap limit " + mb(rt.maxMemory()) + " MB, in use " + mb(rt.totalMemory() - rt.freeMemory()) + " MB")
        try {
            val dir = logDir.takeIf { it.exists() } ?: activity.filesDir
            device.add("Free space: " + mb(StatFs(dir.absolutePath).availableBytes) + " MB")
        } catch (e: Exception) { /* not important */ }

        val radio = BugReport.Section("Radio link")
            .add("Link: " + (link?.name ?: "none"))
            .add("SDK libraries loaded: " + (if (AxSdkJni.loaded) "yes" else "no"))
            .add("Connected: " + (if (StartupState.connected) "yes" else "no") + ", " + String.format(Locale.US, "%.1f", StartupState.secondsSinceLinkStart()) + " s since the link started")
        val values = ArrayList<String>()
        for (k in arrayOf("lq", "up_lq", "dbm", "tlm")) numbers[k]?.let { values.add(k + "=" + String.format(Locale.US, "%.0f", it)) }
        radio.add("Telemetry: " + (if (values.isEmpty()) "no values" else values.joinToString(" ")))
        for (l in StartupState.text(link).trim().split("\n")) radio.add(l.trim())
        try {
            val f = File(activity.filesDir, "startup_timings.txt")
            if (f.exists()) {
                radio.add("Last launches:")
                for (l in f.readLines().takeLast(5)) radio.add("  " + l)
            }
        } catch (e: Exception) { /* not important */ }

        val data = BugReport.Section("Flight data")
            .add("Logging: " + (if (logger.isLogging) "recording now, " + logger.rows() + " rows, " + logger.dropped() + " dropped" else "not recording"))
        logger.problem()?.let { data.add("Logger problem: $it") }
        try {
            val files = LogStore.files(logDir)
            data.add("Flight logs on disk: " + files.size + " (" + mb(files.sumOf { it.length() }) + " MB)")
        } catch (e: Exception) { /* not important */ }

        val settings = BugReport.Section("Settings")
        for (l in BugReport.settingsLines(Prefs.exportMap(activity))) settings.add(l)

        val sections = ArrayList<BugReport.Section>()
        sections.add(app)
        sections.add(device)
        sections.add(radio)
        sections.add(data)
        sections.add(settings)
        sections.add(BugReport.events(log.snapshot(), TimeZone.getDefault(), BugReport.MAX_EVENTS))
        crashText()?.let { text ->
            val crash = BugReport.Section("Last crash")
            for (l in text.trim().split("\n")) crash.add(l)
            sections.add(crash)
        }
        return BugReport.build("AX-HUD bug report", now, TimeZone.getDefault(), sections, note)
    }
}
