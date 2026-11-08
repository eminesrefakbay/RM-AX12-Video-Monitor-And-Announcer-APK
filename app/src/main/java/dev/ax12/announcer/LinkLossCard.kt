// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The card that appears in the middle of the screen when the link is lost in flight: how long ago, and the last position with the
 * distance and bearing from home. It stays until the link is back or the pilot closes it, also after the arm switch went low.
 */
class LinkLossCard(context: Context) {
    val view = LinearLayout(context)
    private val density = context.resources.displayMetrics.density
    private val title = TextView(context)
    private val body = TextView(context)
    private val foot = TextView(context)
    val copyButton = Button(context)
    val closeButton = Button(context)

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        view.orientation = LinearLayout.VERTICAL
        view.setPadding(dp(16), dp(12), dp(16), dp(12))
        view.background = CardStyle.panelBackground(density, 0xF2161C22.toInt())
        view.elevation = dp(7).toFloat()
        view.visibility = View.GONE
        view.isClickable = true
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        title.gravity = Gravity.CENTER
        view.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.textSize = 17f
        body.setTextColor(Color.WHITE)
        body.gravity = Gravity.CENTER
        view.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        foot.textSize = 11f
        foot.setTextColor(CardStyle.LABEL)
        foot.gravity = Gravity.CENTER
        foot.text = tr("Saved on this radio: Settings > Flight logs > Last known position.")
        view.addView(foot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        copyButton.text = tr("Copy position")
        copyButton.isAllCaps = false
        closeButton.text = tr("Close")
        closeButton.isAllCaps = false
        row.addView(copyButton, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f).also { it.rightMargin = dp(8) })
        row.addView(closeButton, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f))
        view.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
    }

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    fun show(titleText: String, bodyText: String, bad: Boolean) {
        title.text = titleText
        title.setTextColor(if (bad) CardStyle.BAD else CardStyle.OK)
        if (body.text.toString() != bodyText) body.text = bodyText
        view.visibility = View.VISIBLE
    }

    fun hide() {
        view.visibility = View.GONE
    }
}
