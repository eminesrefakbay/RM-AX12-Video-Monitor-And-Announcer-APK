// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/**
 * Loads the language before any screen is built, and watches which screens of the app are on show: the radio link is shut down
 * only when none is (the app went to the background or was closed), never while the rules editor or another screen of the app
 * is up. The main screen starts the link; see MainActivity.
 */
class App : Application() {
    companion object {
        /** How many screens of the app are on show; our own rebuilds and file pickers can ask for a short wait before the link is shut down. */
        val visibility = AppVisibility()

        fun stopRadioLink() {
            StartupState.connected = false                  // the next start has to connect again
            if (AxSdkJni.loaded) AxSdkJni.nativeStop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        StartupState.begin()
        Lang.apply(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                visibility.started()
                SdkThread.cancelScheduledStop()                 // back on the display: the link that is running stays
            }

            override fun onActivityStopped(activity: Activity) {
                val wait = visibility.stopped(SystemClock.elapsedRealtime())
                if (wait == 0L) SdkThread.run { stopRadioLink() }                        // the app left the display: release the serial port now
                else if (wait > 0L) SdkThread.scheduleStop(wait) { stopRadioLink() }     // a rebuild or a file picker: unless we come back first
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
