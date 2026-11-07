// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Paint
import java.util.Locale

/** The text in the current language. The English text is the key; see I18n. */
fun tr(en: String, vararg args: Any?): String = I18n.t(en, *args)

/** Marks a text for translation without translating it yet (for tables built when the program starts). */
fun tn(en: String): String = en

/** Which language the app uses, and which one it speaks. */
object Lang {
    /** "auto" follows Android; the others are the four we have. */
    val choices = arrayOf("auto", "en", "tr", "de", "zh")

    fun nativeName(code: String): String = when (code) {
        "tr" -> "T\u00FCrk\u00E7e"
        "de" -> "Deutsch"
        "zh" -> "\u4E2D\u6587"
        "en" -> "English"
        else -> "Auto"
    }

    /**
     * True when this device can draw Chinese characters. Some Android builds for radios ship without a Chinese font, and then every
     * Chinese text is a row of empty boxes. A sample of common characters is asked of the system font.
     */
    fun hasChineseFont(): Boolean {
        val paint = Paint()
        return paint.hasGlyph("\u4E2D\u6587") && paint.hasGlyph("\u8BED") && paint.hasGlyph("\u8BBE\u7F6E")
    }

    /** Loads the language chosen in Settings. Call once at start and again after a change. */
    fun apply(c: Context) {
        val app = c.applicationContext
        var code = I18n.resolve(Prefs.language(app), Locale.getDefault().language)
        if (code == "zh" && !hasChineseFont()) code = "en"       // never show a screen of empty boxes
        I18n.load(I18n.Source { path -> app.assets.open(path) }, code)
    }

    /** The language the voice speaks: the app's, or one chosen on its own. */
    fun speechCode(c: Context): String {
        val pref = Prefs.speechLanguage(c)
        return if (pref == "same" || !I18n.isSupported(pref)) I18n.language() else pref
    }

    fun locale(code: String): Locale = when (code) {
        "tr" -> Locale("tr", "TR")
        "de" -> Locale.GERMANY
        "zh" -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.US
    }
}
