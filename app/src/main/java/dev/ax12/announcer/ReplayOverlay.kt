// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * What a replay adds to the screen: a dark backdrop where the video was, a small REPLAY tag, and a bar along the bottom with
 * Close, Play or Pause, the speed, a scrub bar and the time. The cards themselves are the ones of the live flight.
 */
class ReplayOverlay(
    private val activity: Activity,
    private val onToggle: () -> Unit,
    private val onSpeed: () -> Unit,
    private val onSeek: (Double) -> Unit,
    private val onClose: () -> Unit
) {
    val backdrop = View(activity)
    val tag = TextView(activity)
    val bar = LinearLayout(activity)
    private val closeButton = Button(activity)
    private val playButton = Button(activity)
    private val speedButton = Button(activity)
    private val seek = SeekBar(activity)
    private val timeView = TextView(activity)
    private val density = activity.resources.displayMetrics.density
    private var dragging = false

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        backdrop.setBackgroundColor(0xFF0B1016.toInt())
        backdrop.visibility = View.GONE
        backdrop.isClickable = true                       // a touch on the backdrop must not reach anything under it

        tag.setTextColor(Color.WHITE)
        tag.textSize = 13f
        tag.setTypeface(null, Typeface.BOLD)
        tag.setBackgroundColor(0xCCC62828.toInt())
        tag.setPadding(dp(12), dp(4), dp(12), dp(4))
        tag.visibility = View.GONE

        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(CardStyle.PANEL)
        bar.setPadding(dp(8), 0, dp(8), 0)
        bar.visibility = View.GONE
        bar.isClickable = true
        bar.elevation = dp(16).toFloat()

        fun square(b: Button, width: Int, click: () -> Unit) {
            b.setOnClickListener { click() }
            bar.addView(b, LinearLayout.LayoutParams(dp(width), dp(CardStyle.MIN_TOUCH_DP)).also { it.rightMargin = dp(8) })
        }
        square(closeButton, 96) { onClose() }
        square(playButton, 96) { onToggle() }
        square(speedButton, 72) { onSpeed() }
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) onSeek(progress / 1000.0) }
            override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar) { dragging = false }
        })
        bar.addView(seek, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f))
        timeView.setTextColor(Color.WHITE)
        timeView.textSize = 14f
        timeView.minWidth = dp(110)
        timeView.gravity = Gravity.END
        bar.addView(timeView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.leftMargin = dp(8) })
    }

    /** Adds the views to the frame: the backdrop under everything that draws, the tag and the bar on top. */
    fun attach(frame: FrameLayout, backdropIndex: Int) {
        frame.addView(backdrop, backdropIndex, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(tag, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).also { it.setMargins(dp(10), dp(10), 0, 0) })
        frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CardStyle.MIN_TOUCH_DP), Gravity.BOTTOM))
    }

    fun show(title: String) {
        closeButton.text = tr("Close")
        tag.text = title
        backdrop.visibility = View.VISIBLE
        tag.visibility = View.VISIBLE
        bar.visibility = View.VISIBLE
    }

    fun hide() {
        backdrop.visibility = View.GONE
        tag.visibility = View.GONE
        bar.visibility = View.GONE
    }

    fun update(positionMs: Long, durationMs: Long, playing: Boolean, speed: Int) {
        playButton.text = if (playing) tr("Pause") else tr("Play")
        speedButton.text = tr("{0}x", speed)
        timeView.text = LogText.duration(positionMs) + " / " + LogText.duration(durationMs)
        if (!dragging && durationMs > 0) seek.progress = (positionMs * 1000.0 / durationMs).toInt().coerceIn(0, 1000)
    }
}
