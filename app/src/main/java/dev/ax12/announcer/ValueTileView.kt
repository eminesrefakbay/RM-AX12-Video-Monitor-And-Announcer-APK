// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** A value card: a label, one big number (amber and red past its limits) and the unit. A text value, such as the flight mode, is shown as text. */
class ValueTileView(context: Context) : View(context) {
    /** Whether the card is drawn in the sunlight style (dark on light); set by the dashboard page. */
    var light = false

    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var spec = ValueSpec()
    private var shown = "-"
    private var level = 0
    private val wPx = (150 * density).toInt()
    private val hPx = (84 * density).toInt()

    init {
        bg.color = CardStyle.BG
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        labelPaint.color = CardStyle.LABEL
        labelPaint.textSize = 12f * density
        valuePaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        unitPaint.color = CardStyle.LABEL
        unitPaint.textSize = 14f * density
    }
    /** The sunlight look: dark text on a light card (and back). */
    fun applyLight(on: Boolean) {
        light = on
        bg.color = Sunlight.cIf(on, CardStyle.BG)
        edge.color = Sunlight.cIf(on, CardStyle.EDGE)
        labelPaint.color = Sunlight.cIf(on, CardStyle.LABEL)
        unitPaint.color = Sunlight.cIf(on, CardStyle.LABEL)
        invalidate()
    }


    /** The number (or NaN) and the text (or null) of the value this card shows. */
    fun set(newSpec: ValueSpec, number: Double, text: String?) {
        val s = text ?: newSpec.shown(number)
        val l = if (text != null) 0 else newSpec.level(number)
        if (s == shown && l == level && newSpec.serialize() == spec.serialize()) return
        spec = newSpec
        shown = s
        level = l
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(wPx, hPx)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(1f, 1f, w - 1f, h - 1f)
        canvas.drawRect(rect, bg)
        canvas.drawRect(rect, edge)
        levelPaint.color = when (level) { 2 -> Sunlight.cIf(light, CardStyle.BAD); 1 -> Sunlight.cIf(light, CardStyle.WARN); else -> Sunlight.cIf(light, CardStyle.ACCENT) }
        canvas.drawRect(1f, 1f, 5f * density, h - 1f, levelPaint)
        val pad = 12f * density
        var title = spec.title()
        while (title.length > 1 && labelPaint.measureText(title) > w - pad * 2) title = title.substring(0, title.length - 1)
        canvas.drawText(title, pad, 20f * density, labelPaint)
        valuePaint.color = when (level) { 2 -> Sunlight.cIf(light, CardStyle.BAD); 1 -> Sunlight.cIf(light, CardStyle.WARN); else -> Sunlight.cIf(light, 0xFFFFFFFF.toInt()) }
        val unit = spec.unit
        val unitW = if (unit.isEmpty()) 0f else unitPaint.measureText(unit) + 4f * density
        var size = 38f * density
        valuePaint.textSize = size
        while (size > 12f * density && valuePaint.measureText(shown) + unitW > w - pad * 2) {
            size -= 1f * density
            valuePaint.textSize = size
        }
        val baseline = h - 14f * density
        canvas.drawText(shown, pad, baseline, valuePaint)
        if (unit.isNotEmpty()) canvas.drawText(unit, pad + valuePaint.measureText(shown) + 4f * density, baseline, unitPaint)
    }
}
