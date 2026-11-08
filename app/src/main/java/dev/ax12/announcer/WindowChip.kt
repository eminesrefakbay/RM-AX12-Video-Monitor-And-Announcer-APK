// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.TextView

/** A small line under the MENU button with the time left in the approval window; yellow when it is running out, red at the end. */
class WindowChip(context: Context) {
    val view = TextView(context)
    private val density = context.resources.displayMetrics.density

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        view.textSize = 14f
        view.setTypeface(null, Typeface.BOLD)
        view.setTextColor(Color.WHITE)
        view.setPadding(dp(8), dp(4), dp(8), dp(4))
        view.background = CardStyle.panelBackground(density, 0xB3000000.toInt())
        view.elevation = dp(7).toFloat()
        view.visibility = View.GONE
    }

    /** level: 0 plain, 1 running out, 2 over or very near the end. */
    fun show(text: String, level: Int) {
        if (view.text.toString() != text) view.text = text
        view.setTextColor(when (level) { 2 -> CardStyle.BAD; 1 -> CardStyle.WARN; else -> Color.WHITE })
        view.visibility = View.VISIBLE
    }

    fun hide() {
        view.visibility = View.GONE
    }
}
