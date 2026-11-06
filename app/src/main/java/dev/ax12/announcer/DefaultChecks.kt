package dev.ax12.announcer

/**
 * Starting pre-flight checks. The text is in assets/i18n/checks.<language>.txt, in the language of the app.
 */
object DefaultChecks {
    fun text(): String = I18n.text("checks", I18n.language())
}
