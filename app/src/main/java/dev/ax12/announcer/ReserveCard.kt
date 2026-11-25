// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The return reserve card: the margin (what is left after the way home and the landing) as a big number, what it means in flying time, a bar
 * of where the pack's charge goes, and the way home. It sits at the top and shows only while the aircraft is armed and the numbers are there.
 * See [ReturnReserve].
 */
class ReserveCard(context: Context) {
    val view = LinearLayout(context)
    private val density = context.resources.displayMetrics.density
    private val title = TextView(context)
    private val number = TextView(context)
    private val line = TextView(context)
    private val bar = Bar(context)
    private val home = TextView(context)
    private var shownState: ReturnReserve.State? = null

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** Spare charge, the way home and the landing, one after the other on a scale of the whole pack. */
    private class Bar(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var spare = 0.0
        private var home = 0.0
        private var land = 0.0
        private var margin = 0.0

        fun set(spare: Double, margin: Double, home: Double, land: Double) {
            this.spare = spare; this.margin = margin; this.home = home; this.land = land
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.color = 0xFF2B3A4A.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)
            var x = 0f
            fun seg(pct: Double, color: Int) {
                val len = (pct.coerceIn(0.0, 100.0) / 100.0 * w).toFloat()
                if (len <= 0f) return
                paint.color = color
                canvas.drawRect(x, 0f, minOf(w, x + len), h, paint)
                x += len
            }
            seg(spare, CardStyle.ACCENT)
            seg(margin, 0xFFFFB300.toInt())
            seg(home, 0xFFEF5350.toInt())
            seg(land, 0xFF8E6BBF.toInt())
        }
    }

    init {
        view.orientation = LinearLayout.VERTICAL
        view.setPadding(dp(12), dp(8), dp(12), dp(8))
        view.elevation = dp(6).toFloat()
        view.visibility = View.GONE
        title.text = tr("RETURN RESERVE")
        title.textSize = 11f
        title.setTextColor(CardStyle.LABEL)
        view.addView(title)
        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        number.textSize = 30f
        number.setTypeface(null, Typeface.BOLD)
        top.addView(number, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.textSize = 13f
        line.setTextColor(Color.WHITE)
        top.addView(line, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(10) })
        view.addView(top)
        view.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).also { it.topMargin = dp(4) })
        home.textSize = 12f
        home.setTextColor(CardStyle.LABEL)
        view.addView(home, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4) })
    }

    /** Size: the width and every text of the card scale together (1.0 is the old size). */
    fun setScale(f: Double) {
        view.layoutParams?.let { it.width = dp((380 * f).roundToInt()); view.layoutParams = it }
        title.textSize = (11 * f).toFloat()
        number.textSize = (30 * f).toFloat()
        line.textSize = (13 * f).toFloat()
        home.textSize = (12 * f).toFloat()
        (bar.layoutParams as? LinearLayout.LayoutParams)?.let { it.height = dp((10 * f).roundToInt().coerceAtLeast(5)); bar.layoutParams = it }
        view.setPadding(dp((12 * f).roundToInt()), dp((8 * f).roundToInt()), dp((12 * f).roundToInt()), dp((8 * f).roundToInt()))
    }

    fun setOpacity(a: Float) { view.alpha = a }

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    fun show(r: ReturnReserve.Result, distM: Double) {
        val bad = r.state == ReturnReserve.State.CRIT
        val warn = r.state == ReturnReserve.State.WARN
        val color = if (bad) CardStyle.BAD else if (warn) CardStyle.WARN else CardStyle.OK
        if (shownState != r.state) {
            shownState = r.state
            view.background = CardStyle.panelBackground(density, if (bad) 0xF2300F0F.toInt() else if (warn) 0xF2281E0A.toInt() else 0xE6161C22.toInt(), color)
        }
        number.setTextColor(color)
        number.text = String.format(Locale.US, "%+d %%", Math.round(r.marginPct))
        line.text = ReturnReserve.spareText(r)
        bar.set(Math.max(0.0, r.marginPct), 0.0, r.needHomePct, r.landPct)
        home.text = tr("Home {0}  ·  {1} s  ·  needs {2} % + {3} % to land", String.format(Locale.US, "%.1f km", distM / 1000.0), Math.round(r.homeS), Math.round(r.needHomePct), Math.round(r.landPct))
        view.visibility = View.VISIBLE
    }

    fun hide() {
        view.visibility = View.GONE
        shownState = null
    }
}
