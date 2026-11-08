// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.util.WeakHashMap

/**
 * The fonts of the app: Manrope for text (four weights) and Teko, a narrow techno face, for titles and the big numbers. They are
 * loaded from the app's assets once; if that fails the system fonts stay and nothing breaks. [apply] walks a screen and gives every text
 * its font by its role (title, heading, label, body), so that the code that builds the screens does not have to know about fonts.
 */
object Fonts {
    /** Teko looks smaller than other fonts at the same size, so a title in it is set a little bigger. */
    const val DISPLAY_SCALE = 1.2f

    @Volatile @JvmField var ready = false
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var semiBold: Typeface? = null
    private var bold: Typeface? = null
    private var displaySemi: Typeface? = null
    private var displayBold: Typeface? = null

    private enum class Role { REGULAR, MEDIUM, SEMI, DISPLAY }

    private val roles = WeakHashMap<TextView, Role>()
    private val originalSize = WeakHashMap<TextView, Float>()

    fun init(context: Context) {
        if (ready) return
        try {
            val a = context.applicationContext.assets
            regular = Typeface.createFromAsset(a, "fonts/ManropeRegular.ttf")
            medium = Typeface.createFromAsset(a, "fonts/ManropeMedium.ttf")
            semiBold = Typeface.createFromAsset(a, "fonts/ManropeSemiBold.ttf")
            bold = Typeface.createFromAsset(a, "fonts/ManropeBold.ttf")
            displaySemi = Typeface.createFromAsset(a, "fonts/TekoSemiBold.ttf")
            displayBold = Typeface.createFromAsset(a, "fonts/TekoBold.ttf")
            ready = true
        } catch (e: Exception) {
            ready = false
            Diagnostics.event("fonts", "the app fonts could not be loaded, the system fonts stay: " + e.javaClass.simpleName, "warn")
        }
    }

    /** A weight of the text font (400, 500, 600 or 700); the fallback when the fonts are not loaded. */
    fun text(weight: Int, fallback: Typeface? = null): Typeface? =
        if (!ready) fallback else when {
            weight >= 700 -> bold
            weight >= 600 -> semiBold
            weight >= 500 -> medium
            else -> regular
        }

    /** The title and number font. */
    fun display(boldWeight: Boolean, fallback: Typeface? = null): Typeface? =
        if (!ready) fallback else if (boldWeight) displayBold else displaySemi

    private fun ours(t: Typeface?): Boolean =
        t != null && (t === regular || t === medium || t === semiBold || t === bold || t === displaySemi || t === displayBold)

    /** Gives every text of the view and everything in it its font. Safe to call again after a screen was rebuilt. */
    fun apply(view: View?) {
        if (!ready || view == null) return
        if (view is TextView) style(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
    }

    private fun style(tv: TextView) {
        val cur = tv.typeface
        if (cur === Typeface.MONOSPACE) return                 // text that is meant to be monospace stays so
        var role = roles[tv]
        if (role == null || !ours(cur)) {                      // a new text, or one whose font the code that built it set again
            val sp = (originalSize[tv] ?: tv.textSize) / tv.resources.displayMetrics.scaledDensity
            val isBold = cur != null && cur.isBold
            role = when {
                isBold && sp >= 20f -> Role.DISPLAY
                isBold -> Role.SEMI
                tv is Button || sp <= 12.5f -> Role.MEDIUM
                else -> Role.REGULAR
            }
            roles[tv] = role
        }
        when (role) {
            Role.DISPLAY -> {
                tv.typeface = displaySemi
                tv.fontFeatureSettings = null
                if (!originalSize.containsKey(tv)) {
                    originalSize[tv] = tv.textSize
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.textSize * DISPLAY_SCALE)
                }
            }
            Role.SEMI -> { tv.typeface = semiBold; tv.fontFeatureSettings = "tnum" }
            Role.MEDIUM -> { tv.typeface = medium; tv.fontFeatureSettings = "tnum" }
            else -> { tv.typeface = regular; tv.fontFeatureSettings = "tnum" }
        }
    }
}
