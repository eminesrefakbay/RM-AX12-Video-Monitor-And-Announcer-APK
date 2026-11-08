// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

/** The shape style in use (see [ShapeGeometry]); read when the app starts and set when the choice in Settings changes. */
object Shapes {
    @JvmField var style: Int = ShapeGeometry.CHAMFER2

    /** The names for the setting, in the order of the styles. */
    fun label(i: Int): String = when (ShapeGeometry.clampStyle(i)) {
        ShapeGeometry.CHAMFER2 -> tr("Chamfer, two corners")
        ShapeGeometry.CHAMFER4 -> tr("Chamfer, four corners")
        ShapeGeometry.BRACKETS -> tr("Corner brackets")
        ShapeGeometry.SOFT -> tr("Soft corners")
        else -> tr("Plain rectangles")
    }
}
