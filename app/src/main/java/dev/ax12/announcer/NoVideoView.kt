// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** What the screen shows when no video arrives: a big camera-off icon, "No video", and one line that says what is wrong or what to check. */
class NoVideoView(context: Context) : LinearLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val advice = TextView(context)

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val icon = ImageView(context)
        icon.setImageDrawable(Icons.drawable(context, "video_off", CardStyle.LABEL, 72))
        addView(icon, LayoutParams(dp(72), dp(72)))
        val title = TextView(context)
        title.text = tr("No video")
        title.textSize = 24f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        addView(title, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) })
        advice.textSize = 16f
        advice.setTextColor(CardStyle.LABEL)
        advice.gravity = Gravity.CENTER
        CardStyle.fitText(advice, 12, 16)
        addView(advice, LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
    }

    /** The line under the title: what the camera says (it is already advice: "Check the HDMI cable..."). */
    fun update(text: String) {
        if (advice.text.toString() != text) advice.text = text
    }
}
