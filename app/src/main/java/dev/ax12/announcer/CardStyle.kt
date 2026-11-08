// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.TextView

/** One look for every card and panel on the video: the same background, edge, corners and colours. */
object CardStyle {
    val BG: Int = 0xD91A222C.toInt()            // cards over the video: dark navy-grey, a little see-through
    val PANEL: Int = 0xF21A222C.toInt()         // panels that need to be read closely: nearly solid
    val SEE_THROUGH: Int = 0x8C1A222C.toInt()   // the backdrop of Settings, About and the rules: the video stays visible behind it
    val BLOCK: Int = 0xCC1A222C.toInt()         // a block of text or rows on that backdrop: dark enough to read over moving video
    val MENU: Int = 0xBA1A222C.toInt()          // the MENU box
    val EDGE: Int = 0xFF90A4AE.toInt()
    val TITLE: Int = 0xFF9CDCFE.toInt()
    val LABEL: Int = 0xFFB0BEC5.toInt()
    val VALUE: Int = 0xFFFFFFFF.toInt()
    val ACCENT: Int = 0xFF4FC3F7.toInt()
    val OK: Int = 0xFF66BB6A.toInt()
    val WARN: Int = 0xFFFFB300.toInt()
    val BAD: Int = 0xFFEF5350.toInt()
    val ICON: Int = 0xFFE2E8EE.toInt()

    const val RADIUS_DP = 0f             // plain rectangles: no rounded corners anywhere
    const val EDGE_DP = 1.2f

    fun radius(density: Float): Float = RADIUS_DP * density

    /** The square background of a menu or a block of settings. */
    fun panelBackground(density: Float, color: Int = PANEL): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius(density)
        val lightFill = SunlightPalette.luminance(SunlightPalette.over(color, 0xFF808080.toInt())) > 0.5      // a light panel gets a light edge
        g.setStroke((1f * density).toInt().coerceAtLeast(1), if (lightFill) 0xFF90A4AE.toInt() else 0xFF455A64.toInt())
        return g
    }

    const val GRID_DP = 8               // every space is a multiple of this (or half of it)
    const val MIN_TOUCH_DP = 48         // nothing you tap is smaller than this

    private val numberFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val smallNumberFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    /** The numbers of the HUD: a condensed bold font and digits of equal width, so values do not jitter and take less room. */
    fun styleNumbers(p: Paint) {
        p.typeface = numberFont
        p.fontFeatureSettings = "tnum"
    }

    /** The same for small figures (the ends of an axis, a range in metres): condensed, not bold, digits of equal width. */
    fun styleSmallNumbers(p: Paint) {
        p.typeface = smallNumberFont
        p.fontFeatureSettings = "tnum"
    }

    /** Lets a text shrink to fit its box (down to minSp) instead of being cut off, and wrap to two lines first. Android 8 and newer. */
    fun fitText(tv: TextView, minSp: Int, maxSp: Int) {
        tv.maxLines = 2
        if (Build.VERSION.SDK_INT >= 26) tv.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    /** A flat square block with an edge, in two states (normal, pressed). */
    fun stateBackground(density: Float, normal: Int, pressed: Int, edge: Int, edgePressed: Int = ACCENT): StateListDrawable {
        fun box(fill: Int, line: Int): GradientDrawable {
            val g = GradientDrawable()
            g.setColor(fill)
            g.setStroke((1f * density).toInt().coerceAtLeast(1), line)
            return g
        }
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), box(pressed, edgePressed))
        s.addState(intArrayOf(), box(normal, edge))
        return s
    }
}
