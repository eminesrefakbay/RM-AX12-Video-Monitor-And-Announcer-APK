// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The start-up splash: an animated screen (see [SplashView]) over the same navy as the window background Android shows at launch, so the
 * two look like one screen. It stays for [SHOW_MS], a little longer than its animation, and a tap or the Back key skips it. The app starts
 * up behind it, so it costs no extra waiting.
 */
class SplashOverlay(private val activity: Activity) {

    companion object {
        /** 2 seconds: the usual range for a splash is 1 to 3 s, long enough to read and short enough not to annoy. */
        const val SHOW_MS = 3100L
        private const val FADE_MS = Motion.OUT_MS.toLong()
    }

    val root = FrameLayout(activity)
    private var gone = false

    val isShowing: Boolean get() = !gone

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.setBackgroundColor(SplashView.BG)
        root.isClickable = true
        root.elevation = dp(60).toFloat()
        root.setOnClickListener { dismiss() }
        val (name, build) = versionPair()
        root.addView(SplashView(activity, name, build), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @Suppress("DEPRECATION")
    private fun versionPair(): Pair<String, Long> {
        return try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            Pair(info.versionName ?: "?", build)
        } catch (e: Exception) {
            Pair("?", 0L)
        }
    }

    fun dismiss() {
        if (gone) return
        gone = true
        root.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            root.visibility = View.GONE
            (root.parent as? ViewGroup)?.removeView(root)
        }.start()
    }
}
