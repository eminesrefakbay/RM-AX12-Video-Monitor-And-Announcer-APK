package dev.ax12.announcer

import android.content.Context

/**
 * Starting rules (spoken), written for the AKB7 INAV mode ranges (AUX1 = ch5 ... AUX8 = ch12).
 * The text is in assets/i18n/rules.<language>.txt, in the language the voice speaks.
 */
object DefaultRules {
    fun text(c: Context): String = I18n.text("rules", Lang.speechCode(c))
}
