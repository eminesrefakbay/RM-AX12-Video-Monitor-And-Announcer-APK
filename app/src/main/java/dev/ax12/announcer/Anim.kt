// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/** The slides and fades of the app. All of them take the times in [Motion]. */
object Anim {
    private fun px(view: View, dp: Float): Float = dp * view.resources.displayMetrics.density

    /** Brings a view in: it fades in while it slides the given distance (dp) to its place. */
    fun show(view: View, fromXdp: Float = 0f, fromYdp: Float = 0f) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = px(view, fromXdp)
        view.translationY = px(view, fromYdp)
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).translationX(0f).translationY(0f)
            .setDuration(Motion.IN_MS.toLong()).setInterpolator(DecelerateInterpolator()).start()
    }

    /** Takes a view out: it fades while it slides the given distance (dp), then goes. */
    fun hide(view: View, toXdp: Float = 0f, toYdp: Float = 0f) {
        if (view.visibility != View.VISIBLE) return
        view.animate().cancel()
        view.animate().alpha(0f).translationX(px(view, toXdp)).translationY(px(view, toYdp))
            .setDuration(Motion.OUT_MS.toLong()).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationX = 0f
                view.translationY = 0f
            }.start()
    }

    /** A full-screen panel (Settings, About, the rules): in from the right edge. */
    fun panelIn(view: View) = show(view, Motion.SLIDE_DP.toFloat(), 0f)
    fun panelOut(view: View) = hide(view, Motion.SLIDE_DP.toFloat(), 0f)

    /** The menu: down from the top. */
    fun menuIn(view: View) = show(view, 0f, -Motion.SLIDE_DP.toFloat() / 2)
    fun menuOut(view: View) = hide(view, 0f, -Motion.SLIDE_DP.toFloat() / 2)
}
