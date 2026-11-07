package dev.ax12.announcer

import android.graphics.drawable.GradientDrawable

/** One look for every card and panel on the video: the same background, edge, corners and colours. */
object CardStyle {
    val BG: Int = 0xD91A222C.toInt()            // cards over the video: dark navy-grey, a little see-through
    val PANEL: Int = 0xF21A222C.toInt()         // full panels and menus: nearly solid
    val EDGE: Int = 0xFF90A4AE.toInt()
    val TITLE: Int = 0xFF9CDCFE.toInt()
    val LABEL: Int = 0xFFB0BEC5.toInt()
    val VALUE: Int = 0xFFFFFFFF.toInt()
    val ACCENT: Int = 0xFF4FC3F7.toInt()
    val OK: Int = 0xFF66BB6A.toInt()
    val WARN: Int = 0xFFFFB300.toInt()
    val BAD: Int = 0xFFEF5350.toInt()
    val ICON: Int = 0xFFE2E8EE.toInt()

    const val RADIUS_DP = 10f
    const val EDGE_DP = 1.2f

    fun radius(density: Float): Float = RADIUS_DP * density

    /** The rounded background of a menu or a block of settings. */
    fun panelBackground(density: Float, color: Int = PANEL): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius(density)
        g.setStroke((1f * density).toInt().coerceAtLeast(1), 0xFF455A64.toInt())
        return g
    }
}
