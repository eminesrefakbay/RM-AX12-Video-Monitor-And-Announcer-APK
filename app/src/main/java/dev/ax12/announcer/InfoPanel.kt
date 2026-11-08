// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A full-screen page over the video with a title, a Close button and content that a function builds each time it opens. */
class InfoPanel(
    private val activity: Activity,
    private val title: () -> String,
    private val build: (InfoPanel) -> Unit
) {
    val root = LinearLayout(activity)
    val content = LinearLayout(activity)
    private val titleView = TextView(activity)
    private val closeButton = Button(activity)
    private val density = activity.resources.displayMetrics.density
    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private var barView: LinearLayout? = null

    /** The colours of the frame of the screen, set again each time it opens, because the theme may have changed. */
    private fun skin() {
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        barView?.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        titleView.setTextColor(Sunlight.c(Color.WHITE))
        content.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(28).toFloat()
        val bar = LinearLayout(activity)
        barView = bar
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        bar.setPadding(dp(8), 0, dp(8), 0)
        titleView.textSize = 22f
        titleView.setTextColor(Sunlight.c(Color.WHITE))
        titleView.setTypeface(null, Typeface.BOLD)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        closeButton.setOnClickListener { hide() }
        bar.addView(closeButton)
        root.addView(bar)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        content.setPadding(dp(16), dp(8), dp(16), dp(24))
        val page = ScrollView(activity)
        page.addView(content)
        root.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show() {
        skin()
        titleView.text = title()
        closeButton.text = tr("Close")
        refresh()
        shown = true
        Anim.panelIn(root)
    }

    fun refresh() {
        refreshRows()
        Look.apply(root)
    }

    private fun refreshRows() {
        content.removeAllViews()
        build(this)
    }

    fun hide() {
        shown = false
        Anim.panelOut(root)
    }

    fun text(s: String, sp: Float, color: Int, bold: Boolean = false, topDp: Int = 0): TextView {
        val t = TextView(activity)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, 0)
        content.addView(t)
        return t
    }

    /** A card that holds several lines; returns it so that the caller can add more to it. */
    fun card(): LinearLayout {
        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(8), dp(16), dp(8))
        card.background = CardStyle.panelBackground(density, Sunlight.c(0xE6232C36.toInt()))
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        return card
    }

    fun line(parent: LinearLayout, s: String, sp: Float, color: Int, bold: Boolean = false, topDp: Int = 0) {
        val t = TextView(activity)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, 0)
        parent.addView(t)
    }

    fun button(parent: LinearLayout, label: String, enabled: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.isEnabled = enabled
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CardStyle.MIN_TOUCH_DP)).also { it.topMargin = dp(8) })
        return b
    }

    fun confirm(message: String, positive: String, onYes: () -> Unit) {
        val dlg = Sunlight.builder(activity)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }
}
