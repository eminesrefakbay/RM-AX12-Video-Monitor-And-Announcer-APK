// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Where the updates come from. Forks change [REPO]. */
object UpdateConfig {
    const val REPO = "eminesrefakbay/RM-AX12-Video-Monitor-And-Announcer-APK"

    /** "stable" = the latest release; anything else = Nightly (the build of the latest commit on main). */
    fun baseUrl(channel: String): String =
        if (channel == "stable") "https://github.com/" + REPO + "/releases/latest/download/"
        else "https://github.com/" + REPO + "/releases/download/nightly/"
}

/**
 * Looks for a newer build on GitHub, downloads it, checks its SHA-256 against update.json, and hands it to
 * Android's installer, which asks you to confirm. Android itself refuses an APK that is not signed with the same
 * key as the installed app, so a forged file cannot replace the app.
 * Nothing is sent except the request for two files on github.com.
 */
class Updater(private val activity: Activity) {

    companion object {
        private const val ACTION_RESULT = "dev.ax12.announcer.INSTALL_RESULT"
    }

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    private var receiver: BroadcastReceiver? = null

    @Suppress("DEPRECATION")
    fun installedCode(): Long = try {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    fun installedName(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun channel(): String = Prefs.updateChannel(activity)

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    private fun message(title: String, text: String) {
        Immersive.show(Sunlight.builder(activity).setTitle(title).setMessage(text).setPositiveButton("OK", null).create())
    }

    // ------------------------------------------------------------------ looking for an update

    /** @param manual true when the user asked (answers are shown); false at start-up (only a short notice if there is news) */
    fun check(manual: Boolean) {
        if (busy) {
            if (manual) toast(tr("Already checking"))
            return
        }
        busy = true
        val ch = channel()
        if (manual) toast(tr("Looking for an update on github.com ..."))
        Thread {
            var info: UpdateInfo? = null
            var error: String? = null
            try {
                val text = UpdateClient.fetchText(UpdateConfig.baseUrl(ch) + "update.json", 8000, 20000)
                info = UpdateInfo.parse(text)
                if (info == null) error = tr("The update information could not be read")
            } catch (e: Exception) {
                error = e.message ?: tr("No connection")
            }
            ui.post {
                busy = false
                onChecked(info, error, manual, ch)
            }
        }.start()
    }

    private fun onChecked(info: UpdateInfo?, error: String?, manual: Boolean, channel: String) {
        if (info == null) Diagnostics.event("update", "check failed on the " + channel + " channel: " + (error ?: "no connection"), "warn")
        else Diagnostics.event("update", "checked the " + channel + " channel: build " + info.versionCode + " is published")
        if (info == null) {
            if (manual) {
                val friendly = if (error != null && error.contains("404")) tr("No build has been published on this channel yet.")
                else tr("Could not check: {0}\n\nIs the phone hotspot on?", (error ?: tr("No connection")))
                message(tr("Updates"), friendly)
            }
            return
        }
        val have = installedCode()
        if (!info.isNewerThan(have.toInt())) {
            if (manual) toast(tr("You have the latest build: version {0} (build {1})", installedName(), have))
            return
        }
        if (!manual) {
            toast(tr("AX-HUD update available: {0} (build {1}). Open Settings, then Updates.", info.versionName, info.versionCode))
            return
        }
        val notes = if (info.notes.isEmpty()) "" else "\n\n" + info.notes
        Immersive.show(
            Sunlight.builder(activity)
                .setTitle(tr("Update available"))
                .setMessage(tr("New: version {0} (build {1}, {2} MB)\nInstalled: version {3} (build {4}){5}\n\nDo not do this during a flight.", info.versionName, info.versionCode, (info.size / 1024 / 1024), installedName(), have, notes))
                .setPositiveButton(tr("Download and install")) { _, _ -> download(info, channel) }
                .setNegativeButton(tr("Later"), null)
                .create()
        )
    }

    // ------------------------------------------------------------------ downloading

    private fun download(info: UpdateInfo, channel: String) {
        val dir = File(activity.cacheDir, "update")
        dir.mkdirs()
        val dest = File(dir, info.apk)
        val cancelled = AtomicBoolean(false)

        val body = LinearLayout(activity)
        body.orientation = LinearLayout.VERTICAL
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        body.setPadding(pad, pad, pad, pad)
        val label = TextView(activity)
        label.text = tr("Starting ...")
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        body.addView(label)
        body.addView(bar)
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Downloading {0}", info.versionName))
            .setView(body)
            .setCancelable(false)
            .setNegativeButton(tr("Cancel")) { _, _ -> cancelled.set(true) }
            .create()
        Immersive.show(dlg)

        Thread {
            var err: String? = null
            try {
                UpdateClient.download(UpdateConfig.baseUrl(channel) + info.apk, dest, info.size, info.sha256, 20000,
                    object : UpdateClient.Progress {
                        private var lastUi = 0L
                        override fun onProgress(done: Long, total: Long) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUi < 200 && done != total) return
                            lastUi = now
                            ui.post {
                                bar.progress = if (total > 0) ((done * 1000) / total).toInt() else 0
                                label.text = (done / 1024 / 1024).toString() + " of " + (total / 1024 / 1024) + " MB"
                            }
                        }

                        override fun cancelled(): Boolean = cancelled.get()
                    })
            } catch (e: Exception) {
                err = e.message ?: tr("Download failed")
            }
            ui.post {
                dlg.dismiss()
                if (err == null) install(dest)
                else if (!cancelled.get()) message(tr("Download failed"), err)
            }
        }.start()
    }

    // ------------------------------------------------------------------ installing

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            Immersive.show(
                Sunlight.builder(activity)
                    .setTitle(tr("Allow installing"))
                    .setMessage(tr("Android needs your permission before AX-HUD can install an update. Open the page, switch it on for AX-HUD, come back and run the update again. The download is kept, so it will not be downloaded twice."))
                    .setPositiveButton(tr("Open the page")) { _, _ ->
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                    }
                    .setNegativeButton(tr("Cancel"), null)
                    .create()
            )
            return
        }
        try {
            registerReceiver()
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(activity.packageName)
            val id = installer.createSession(params)
            val session = installer.openSession(id)
            try {
                FileInputStream(file).use { input ->
                    session.openWrite("ax-hud.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(ACTION_RESULT).setPackage(activity.packageName)
                val pi = PendingIntent.getBroadcast(activity, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                session.commit(pi.intentSender)
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            message(tr("Could not start the installation"), e.message ?: tr("Unknown error"))
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                val text = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm != null) activity.startActivity(confirm)
                    }
                    PackageInstaller.STATUS_SUCCESS -> toast(tr("Updated. The app will reopen."))
                    PackageInstaller.STATUS_FAILURE_ABORTED -> toast(tr("Installation canceled"))
                    else -> {
                        val help = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || text.contains("UPDATE_INCOMPATIBLE"))
                            tr("\n\nThe installed copy was signed with a different key. Export your rules (Settings), uninstall AX-HUD once and install this build; after that updates work. See UPDATES.md.")
                        else if (text.contains("DOWNGRADE")) tr("\n\nThis build is older than the installed one.")
                        else ""
                        message(tr("Installation stopped"), (if (text.isEmpty()) tr("Android did not say why (code {0}).", status) else text) + help)
                    }
                }
            }
        }
        activity.registerReceiver(r, IntentFilter(ACTION_RESULT))
        receiver = r
    }

    /** Call from onDestroy. */
    fun release() {
        receiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        receiver = null
    }
}
