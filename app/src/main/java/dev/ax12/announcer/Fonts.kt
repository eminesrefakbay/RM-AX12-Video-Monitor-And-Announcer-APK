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
 * The fonts of the app: Manrope for text (four weights) and Teko, a narrow techno face, for titles and the big numbers. For Chinese there
 * is a small bundled font (Noto Sans SC, cut down to the characters the app's Chinese texts use), because some radios ship without any
 * Chinese font and every Chinese text would show as empty boxes. The fonts are loaded from the app's assets once; if that fails the system
 * fonts stay and nothing breaks. [apply] walks a screen and gives every text its font by its role (title, heading, label, body), so that
 * the code that builds the screens does not have to know about fonts.
 */
object Fonts {
    /** Teko looks smaller than other fonts at the same size, so a title in it is set a little bigger. */
    const val DISPLAY_SCALE = 1.2f

    @Volatile @JvmField var ready = false

    /** True when the bundled Chinese font is loaded: Chinese can be chosen even on a radio without a Chinese system font. */
    @Volatile @JvmField var hasChinese = false

    @Volatile private var cjkMode = false
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var semiBold: Typeface? = null
    private var bold: Typeface? = null
    private var displaySemi: Typeface? = null
    private var displayBold: Typeface? = null
    private var cjkRegular: Typeface? = null
    private var cjkBold: Typeface? = null

    private enum class Role { REGULAR, MEDIUM, SEMI, DISPLAY }

    private val roles = WeakHashMap<TextView, Role>()
    private val originalSize = WeakHashMap<TextView, Float>()

    fun init(context: Context) {
        val a = context.applicationContext.assets
        if (!ready) {
            try {
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
        if (!hasChinese) {
            try {
                cjkRegular = Typeface.createFromAsset(a, "fonts/NotoSansSCRegular.ttf")
                cjkBold = Typeface.createFromAsset(a, "fonts/NotoSansSCBold.ttf")
                hasChinese = true
            } catch (e: Exception) {
                hasChinese = false
                Diagnostics.event("fonts", "the Chinese font could not be loaded: " + e.javaClass.simpleName, "warn")
            }
        }
    }

    /** Called when the language is set: in Chinese every text uses the bundled Chinese font (it holds the Latin letters and digits too). */
    fun setLanguage(code: String) {
        cjkMode = code == "zh" && hasChinese
    }

    /** A weight of the text font (400, 500, 600 or 700); the fallback when the fonts are not loaded. */
    fun text(weight: Int, fallback: Typeface? = null): Typeface? {
        if (cjkMode) return if (weight >= 600) cjkBold else cjkRegular
        return if (!ready) fallback else when {
            weight >= 700 -> bold
            weight >= 600 -> semiBold
            weight >= 500 -> medium
            else -> regular
        }
    }

    /** The title font (Teko; the Chinese font in Chinese, which Teko has no characters for). */
    fun display(boldWeight: Boolean, fallback: Typeface? = null): Typeface? {
        if (cjkMode) return cjkBold
        return if (!ready) fallback else if (boldWeight) displayBold else displaySemi
    }

    /** The font of numbers: always Teko when it is loaded, in every language (a number has no Chinese characters). */
    fun numbers(fallback: Typeface? = null): Typeface? = if (ready) displayBold else fallback

    private fun ours(t: Typeface?): Boolean =
        t != null && (t === regular || t === medium || t === semiBold || t === bold || t === displaySemi || t === displayBold || t === cjkRegular || t === cjkBold)

    /** Gives every text of the view and everything in it its font. Safe to call again after a screen was rebuilt. */
    fun apply(view: View?) {
        if (!(ready || cjkMode) || view == null) return
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
                tv.typeface = display(false)
                tv.fontFeatureSettings = null
                if (!cjkMode && !originalSize.containsKey(tv)) {
                    originalSize[tv] = tv.textSize
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.textSize * DISPLAY_SCALE)
                }
            }
            Role.SEMI -> { tv.typeface = text(600); tv.fontFeatureSettings = "tnum" }
            Role.MEDIUM -> { tv.typeface = text(500); tv.fontFeatureSettings = "tnum" }
            else -> { tv.typeface = text(400); tv.fontFeatureSettings = "tnum" }
        }
    }
}
