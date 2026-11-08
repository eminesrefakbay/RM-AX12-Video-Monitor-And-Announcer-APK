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
import android.widget.ScrollView
import android.widget.TextView

/**
 * The welcome guide: one card in the middle of the screen with four short pages, shown on the first start and from Settings.
 * The video stays visible around it. Leaving it (Done, Skip or the back key) marks this version of the guide as seen.
 */
class WelcomeGuide(private val activity: Activity, private val onFinished: () -> Unit) {
    val root = FrameLayout(activity)
    private val card = LinearLayout(activity)
    private val stepView = TextView(activity)
    private val titleView = TextView(activity)
    private val bodyView = TextView(activity)
    private val dots = LinearLayout(activity)
    private val skipButton = Button(activity)
    private val backButton = Button(activity)
    private val nextButton = Button(activity)
    private val density = activity.resources.displayMetrics.density

    private var pages: List<FirstRun.Page> = emptyList()
    private var index = 0
    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** The colours of the guide, set again each time it opens, because the theme may have changed. */
    private fun skin() {
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        card.setBackgroundColor(Sunlight.c(CardStyle.PANEL))
        stepView.setTextColor(Sunlight.c(CardStyle.LABEL))
        titleView.setTextColor(Sunlight.c(Color.WHITE))
        bodyView.setTextColor(Sunlight.c(0xFFDDE3E8.toInt()))
    }

    init {
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(32).toFloat()          // above every other screen

        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Sunlight.c(CardStyle.PANEL))
        card.setPadding(dp(24), dp(16), dp(24), dp(16))

        val top = LinearLayout(activity)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        stepView.textSize = 12f
        stepView.setTextColor(Sunlight.c(CardStyle.LABEL))
        top.addView(stepView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        skipButton.setOnClickListener { finish() }
        top.addView(skipButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(CardStyle.MIN_TOUCH_DP)))
        card.addView(top)

        titleView.textSize = 22f
        titleView.setTextColor(Sunlight.c(Color.WHITE))
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setPadding(0, dp(8), 0, dp(8))
        card.addView(titleView)

        bodyView.textSize = 16f
        bodyView.setTextColor(Sunlight.c(0xFFDDE3E8.toInt()))
        bodyView.setLineSpacing(0f, 1.15f)
        val bodyScroll = ScrollView(activity)           // a language that needs more room scrolls instead of being cut
        bodyScroll.addView(bodyView)
        card.addView(bodyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(activity)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        dots.orientation = LinearLayout.HORIZONTAL
        bottom.addView(dots, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backButton.setOnClickListener { index = FirstRun.back(index); render() }
        nextButton.setOnClickListener { if (FirstRun.isLast(index)) finish() else { index = FirstRun.next(index); render() } }
        bottom.addView(backButton, LinearLayout.LayoutParams(dp(112), dp(CardStyle.MIN_TOUCH_DP)))
        bottom.addView(nextButton, LinearLayout.LayoutParams(dp(112), dp(CardStyle.MIN_TOUCH_DP)).also { it.leftMargin = dp(8) })
        card.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) })

        val metrics = activity.resources.displayMetrics
        val lp = FrameLayout.LayoutParams(minOf(dp(600), (metrics.widthPixels * 0.82f).toInt()), minOf(dp(440), (metrics.heightPixels * 0.86f).toInt()), Gravity.CENTER)
        root.addView(card, lp)
    }

    fun show() {
        skin()
        pages = FirstRun.pages()
        index = 0
        render()
        shown = true
        Anim.panelIn(root)
    }

    /** The back key: one page back, and from the first page out of the guide. */
    fun back() {
        if (FirstRun.isFirst(index)) finish() else { index = FirstRun.back(index); render() }
    }

    private fun finish() {
        if (!shown) return
        shown = false
        Prefs.setGuideSeen(activity, FirstRun.VERSION)
        Anim.panelOut(root)
        onFinished()
    }

    private fun render() {
        renderRows()
        Fonts.apply(root)
    }

    private fun renderRows() {
        val page = pages[index]
        stepView.text = tr("Step {0} of {1}", index + 1, pages.size)
        titleView.text = page.title
        bodyView.text = page.body
        skipButton.text = tr("Skip")
        skipButton.visibility = if (FirstRun.isLast(index)) View.INVISIBLE else View.VISIBLE
        backButton.text = tr("Back")
        backButton.visibility = if (FirstRun.isFirst(index)) View.INVISIBLE else View.VISIBLE
        nextButton.text = if (FirstRun.isLast(index)) tr("Done") else tr("Next")
        CardStyle.fitText(titleView, 16, 22)
        dots.removeAllViews()
        for (i in pages.indices) {
            val d = View(activity)
            d.setBackgroundColor(if (i == index) Sunlight.c(CardStyle.ACCENT) else Sunlight.c(0x66B0BEC5))
            dots.addView(d, LinearLayout.LayoutParams(dp(8), dp(8)).also { it.rightMargin = dp(8) })
        }
    }
}
