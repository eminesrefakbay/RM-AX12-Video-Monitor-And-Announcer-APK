// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.AlertDialog
import android.content.Context

/**
 * The sunlight theme: the menu, the settings and the other panels, the dashboard page and the warning bar in dark text on light
 * backgrounds, with stronger warnings. One switch for all of it (Settings > Display, or the Sunlight tile of the MENU). The cards over the
 * video keep their own look. See [SunlightPalette] for the colours.
 */
object Sunlight {
    /** Whether the theme is on; set when the screen starts and whenever the switch moves. */
    @JvmField var on = false

    /** The colour for the panels: unchanged in the dark style, the light counterpart in the sunlight style. */
    fun c(color: Int): Int = if (on) SunlightPalette.map(color) else color

    /** The same for a view that decides for itself (a dashboard card). */
    fun cIf(light: Boolean, color: Int): Int = if (light) SunlightPalette.map(color) else color

    fun dialogTheme(): Int = if (on) android.R.style.Theme_Material_Light_Dialog_Alert else 0

    /** A dialog builder that follows the theme (the system dialogs are dark otherwise). */
    fun builder(context: Context): AlertDialog.Builder =
        if (on) AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert) else AlertDialog.Builder(context)
}
