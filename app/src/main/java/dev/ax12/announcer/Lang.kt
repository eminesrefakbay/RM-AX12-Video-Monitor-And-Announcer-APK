package dev.ax12.announcer

import android.content.Context
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

    /** Loads the language chosen in Settings. Call once at start and again after a change. */
    fun apply(c: Context) {
        val app = c.applicationContext
        val code = I18n.resolve(Prefs.language(app), Locale.getDefault().language)
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
