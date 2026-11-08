// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.TextView

/** One look for every card and panel on the video: the same background, edge, corners and colours. */
object CardStyle {
    val BG: Int = 0xD91A222C.toInt()            // cards over the video: dark navy-grey, a little see-through
    val PANEL: Int = 0xF21A222C.toInt()         // panels that need to be read closely: nearly solid
    val SEE_THROUGH: Int = 0x8C1A222C.toInt()   // the backdrop of Settings, About and the rules: the video stays visible behind it
    val BLOCK: Int = 0xCC1A222C.toInt()         // a block of text or rows on that backdrop: dark enough to read over moving video
    val MENU: Int = 0xBA1A222C.toInt()          // the MENU box
    val EDGE: Int = 0xFF90A4AE.toInt()
    val TITLE: Int = 0xFF9CDCFE.toInt()
    val LABEL: Int = 0xFFB0BEC5.toInt()
    val VALUE: Int = 0xFFFFFFFF.toInt()
    val ACCENT: Int = 0xFF4FC3F7.toInt()
    val OK: Int = 0xFF66BB6A.toInt()
    val WARN: Int = 0xFFFFB300.toInt()
    val BAD: Int = 0xFFEF5350.toInt()
    val ICON: Int = 0xFFE2E8EE.toInt()

    const val EDGE_DP = 1.2f
    private const val EDGE_PLAIN = 0xFF455A64.toInt()
    private val scratch = Path()
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A solid frame (a warning) keeps its fill: brackets would leave it nearly empty, so it is cut at two corners instead. */
    private fun effective(solid: Boolean): Int = if (solid && Shapes.style == ShapeGeometry.BRACKETS) ShapeGeometry.CHAMFER2 else Shapes.style

    private fun polygon(p: FloatArray): Path {
        scratch.reset()
        scratch.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { scratch.lineTo(p[i], p[i + 1]); i += 2 }
        scratch.close()
        return scratch
    }

    private fun outlinePath(style: Int, rect: RectF, density: Float): Path? {
        val w = rect.width()
        val h = rect.height()
        if (ShapeGeometry.cutsCorners(style)) {
            return polygon(ShapeGeometry.outline(style, rect.left, rect.top, rect.right, rect.bottom, ShapeGeometry.cut(w, h, density)))
        }
        if (style == ShapeGeometry.SOFT) {
            scratch.reset()
            val r = ShapeGeometry.radius(w, h, density)
            scratch.addRoundRect(rect, r, r, Path.Direction.CW)
            return scratch
        }
        return null
    }

    /** The fill of a card, tile or row in the style that is chosen. */
    fun drawFill(canvas: Canvas, rect: RectF, fill: Paint, density: Float, solid: Boolean = false) {
        val style = effective(solid)
        if (style == ShapeGeometry.BRACKETS) {                      // a quiet fill: the brackets carry the frame
            val a = fill.alpha
            fill.alpha = (a * 0.62f).toInt()
            canvas.drawRect(rect, fill)
            fill.alpha = a
            return
        }
        val path = outlinePath(style, rect, density)
        if (path != null) canvas.drawPath(path, fill) else canvas.drawRect(rect, fill)
    }

    /** The edge of a card, tile or row; with brackets, a faint edge and bright corner brackets in the accent colour. */
    fun drawEdge(canvas: Canvas, rect: RectF, edge: Paint, density: Float, accent: Int, solid: Boolean = false) {
        val style = effective(solid)
        if (style == ShapeGeometry.BRACKETS) {
            val a = edge.alpha
            edge.alpha = 70
            canvas.drawRect(rect, edge)
            edge.alpha = a
            bracketPaint.style = Paint.Style.STROKE
            bracketPaint.strokeCap = Paint.Cap.SQUARE
            bracketPaint.color = accent
            bracketPaint.strokeWidth = maxOf(edge.strokeWidth * 2.2f, 2.5f * density)
            val p = ShapeGeometry.brackets(rect.left, rect.top, rect.right, rect.bottom, ShapeGeometry.bracket(rect.width(), rect.height(), density))
            scratch.reset()
            for (c in 0 until 4) {
                scratch.moveTo(p[c * 6], p[c * 6 + 1])
                scratch.lineTo(p[c * 6 + 2], p[c * 6 + 3])
                scratch.lineTo(p[c * 6 + 4], p[c * 6 + 5])
            }
            canvas.drawPath(scratch, bracketPaint)
            return
        }
        val path = outlinePath(style, rect, density)
        if (path != null) canvas.drawPath(path, edge) else canvas.drawRect(rect, edge)
    }

    /** What is drawn after this is cut to the shape (a bar that runs along an edge, the sky and ground of the horizon). */
    fun clipFrame(canvas: Canvas, rect: RectF, density: Float) {
        val path = outlinePath(effective(false), rect, density)
        if (path != null) canvas.clipPath(path) else canvas.clipRect(rect)
    }

    /** The background of a panel or row: [color] with an edge that is light on a light panel and dark on a dark one. */
    fun panelBackground(density: Float, color: Int = PANEL, accent: Int = Sunlight.c(ACCENT), solid: Boolean = false): Drawable {
        val lightFill = SunlightPalette.luminance(SunlightPalette.over(color, 0xFF808080.toInt())) > 0.5      // a light panel gets a light edge
        return FrameDrawable(color, if (lightFill) 0xFF90A4AE.toInt() else EDGE_PLAIN, maxOf(1f, density), density, accent, solid)
    }

    const val GRID_DP = 8               // every space is a multiple of this (or half of it)
    const val MIN_TOUCH_DP = 48         // nothing you tap is smaller than this

    private val numberFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val smallNumberFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    /** The big numbers are set in a narrow face that looks small, so they are drawn a little bigger. */
    val numberScale: Float get() = if (Fonts.ready) Fonts.DISPLAY_SCALE else 1f

    /** The numbers of the HUD: a condensed bold font and digits of equal width, so values do not jitter and take less room. */
    fun styleNumbers(p: Paint) {
        p.typeface = Fonts.display(true, numberFont)
        p.fontFeatureSettings = "tnum"
    }

    /** The same for small figures (the ends of an axis, a range in metres): condensed, not bold, digits of equal width. */
    fun styleSmallNumbers(p: Paint) {
        p.typeface = Fonts.text(500, smallNumberFont)
        p.fontFeatureSettings = "tnum"
    }

    /** The small titles of the cards: medium weight with a little air between the letters. */
    fun styleLabel(p: Paint) {
        p.typeface = Fonts.text(500, p.typeface)
        p.letterSpacing = 0.04f
        p.fontFeatureSettings = "tnum"
    }

    /** Card titles and names: bold, with more air. */
    fun styleTitle(p: Paint) {
        p.typeface = Fonts.text(700, p.typeface)
        p.letterSpacing = 0.08f
        p.fontFeatureSettings = "tnum"
    }

    /** A title in capitals, in the language of the screen (a Turkish i becomes İ). */
    fun upper(s: String): String = s.uppercase(Lang.locale(I18n.language()))

    /** A number whose every digit has a slot of the same width, whatever the font: it does not shift sideways while it counts. */
    private fun advance(p: Paint) = NumberLayout.Advance { c -> p.measureText(c.toString()) }

    fun measureNumber(text: String, p: Paint): Float = NumberLayout.width(text, NumberLayout.slot(advance(p)), advance(p))

    /** Draws a number at x (left, centre or right of x as the paint's alignment says), baseline y. */
    fun drawNumber(canvas: Canvas, text: String, x: Float, y: Float, p: Paint) {
        val adv = advance(p)
        val slot = NumberLayout.slot(adv)
        val width = NumberLayout.width(text, slot, adv)
        val align = when (p.textAlign) { Paint.Align.RIGHT -> 1; Paint.Align.CENTER -> 0; else -> -1 }
        val saved = p.textAlign
        p.textAlign = Paint.Align.LEFT
        var cx = NumberLayout.left(align, x, width)
        for (ch in text) {
            val a = NumberLayout.advance(ch, slot, adv)
            val own = adv.of(ch)
            canvas.drawText(ch.toString(), if (NumberLayout.isDigit(ch)) cx + (a - own) / 2f else cx, y, p)       // a digit sits in the middle of its slot
            cx += a
        }
        p.textAlign = saved
    }

    /** Lets a text shrink to fit its box (down to minSp) instead of being cut off, and wrap to two lines first. Android 8 and newer. */
    fun fitText(tv: TextView, minSp: Int, maxSp: Int) {
        tv.maxLines = 2
        if (Build.VERSION.SDK_INT >= 26) tv.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    /** A flat square block with an edge, in two states (normal, pressed). */
    fun stateBackground(density: Float, normal: Int, pressed: Int, edge: Int, edgePressed: Int = ACCENT): StateListDrawable {
        val line = maxOf(1f, density)
        val accent = Sunlight.c(ACCENT)
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), FrameDrawable(pressed, edgePressed, line, density, accent))
        s.addState(intArrayOf(), FrameDrawable(normal, edge, line, density, accent))
        return s
    }
}
