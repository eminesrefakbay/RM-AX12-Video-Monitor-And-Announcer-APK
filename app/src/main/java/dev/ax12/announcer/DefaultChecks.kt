// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

/**
 * Starting pre-flight checks. The text is in assets/i18n/checks.<language>.txt, in the language of the app.
 */
object DefaultChecks {
    fun text(): String = I18n.text("checks", I18n.language())
}
