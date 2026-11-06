package dev.ax12.announcer

/**
 * Starting warning rules for the status bar. A warning shows while its condition is true.
 * The text is in assets/i18n/warnings.<language>.txt, in the language of the app.
 */
object DefaultWarnings {
    fun text(): String = I18n.text("warnings", I18n.language())
}
