// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context

/**
 * Starting rules (spoken), written for the AKB7 INAV mode ranges (AUX1 = ch5 ... AUX8 = ch12).
 * The text is in assets/i18n/rules.<language>.txt, in the language the voice speaks.
 */
object DefaultRules {
    fun text(c: Context): String = I18n.text("rules", Lang.speechCode(c))
}
