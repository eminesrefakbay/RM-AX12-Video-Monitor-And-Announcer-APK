// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton

/** What a screen needs after it is built: its fonts and the shape style of its buttons. Call it again after a screen is rebuilt. */
object Look {
    fun apply(view: View?) {
        Fonts.apply(view)
        ShapeSkin.apply(view)
    }
}

/**
 * The buttons of a screen in the shape style that is chosen. A plain Android button is a sharp, flat rectangle with a shadow that no cut
 * corner can follow, so it is given the same frame as a tile or a row. Switches and check boxes keep their own look.
 */
object ShapeSkin {
    fun apply(view: View?) {
        if (view == null) return
        if (view is Button && view !is CompoundButton) style(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
    }

    private fun style(b: Button) {
        b.background = CardStyle.buttonBackground(b.resources.displayMetrics.density)
        b.stateListAnimator = null                      // the lift of a Material button is a rectangle shadow
        b.elevation = 0f
        val on = Sunlight.c(0xFFFFFFFF.toInt())
        b.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf((on and 0x00FFFFFF) or 0x80000000.toInt(), on)))
    }
}
