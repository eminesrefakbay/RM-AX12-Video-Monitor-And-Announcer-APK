// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** The values being charted: one rolling record per name, filled about five times a second. */
class ChartModel {
    val names = ArrayList<String>()
    private val series = HashMap<String, TimeSeries>()
    var windowMs = 60000L

    /** Keeps the records of names that stay, so changing the list or the window does not clear the graphs. */
    fun configure(newNames: List<String>, windowSeconds: Int) {
        names.clear()
        names.addAll(newNames)
        windowMs = windowSeconds * 1000L
        series.keys.retainAll(names.toSet())
        for (n in names) if (!series.containsKey(n)) series[n] = TimeSeries(700)       // 120 s at five a second, and some spare
    }

    fun sample(numbers: Map<String, Double>, now: Long) {
        for (n in names) series[n]?.add(now, numbers[n] ?: Double.NaN)
    }

    fun series(name: String): TimeSeries? = series[name]
}

/**
 * Small graphs, one per value, each with its own scale: a label and the value now on top, the last seconds as a line,
 * and the ends of the axis in small figures. Known values keep a fixed or generous scale (see ChartScale).
 */
class ChartView(context: Context) : View(context) {
    /** Whether the card is drawn in the sunlight style (dark on light); set by the dashboard page. */
    var light = false


    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private var model: ChartModel? = null
    private var widthDp = 230
    private var rowDp = 58
    private var shownRows = 0
    private var now = 0L

    private val colors = intArrayOf(Sunlight.cIf(light, 0xFF4FC3F7.toInt()), Sunlight.cIf(light, 0xFF66BB6A.toInt()), Sunlight.cIf(light, 0xFFFFB300.toInt()), Sunlight.cIf(light, 0xFFEF5350.toInt()))

    init {
        bg.color = CardStyle.BG
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        label.color = 0xFFB0BEC5.toInt()
        valuePaint.color = 0xFFFFFFFF.toInt()
        CardStyle.styleNumbers(valuePaint)          // condensed bold, digits of equal width
        valuePaint.textAlign = Paint.Align.RIGHT
        axis.color = 0xAA90A4AE.toInt()
        CardStyle.styleSmallNumbers(axis)
        grid.color = 0x44FFFFFF
        grid.style = Paint.Style.STROKE
        grid.strokeWidth = 1f * density
        line.style = Paint.Style.STROKE
        line.strokeWidth = 1.8f * density
        line.strokeJoin = Paint.Join.ROUND
        applyText(1)
    }
    /** The sunlight look: dark text on a light card (and back). */
    fun applyLight(on: Boolean) {
        light = on
        bg.color = Sunlight.cIf(on, CardStyle.BG)
        edge.color = Sunlight.cIf(on, CardStyle.EDGE)
        label.color = Sunlight.cIf(on, 0xFFB0BEC5.toInt())
        valuePaint.color = Sunlight.cIf(on, 0xFFFFFFFF.toInt())
        axis.color = Sunlight.cIf(on, 0xAA90A4AE.toInt())
        grid.color = Sunlight.cIf(on, 0x44FFFFFF)
        invalidate()
    }


    fun bind(m: ChartModel) { model = m }

    /** @param level 0 small, 1 medium, 2 large */
    fun setLevel(level: Int) {
        widthDp = intArrayOf(170, 230, 300)[level.coerceIn(0, 2)]
        rowDp = intArrayOf(46, 58, 74)[level.coerceIn(0, 2)]
        applyText(level)
        requestLayout()
        invalidate()
    }

    private fun applyText(level: Int) {
        val sp = floatArrayOf(10f, 12f, 14f)[level.coerceIn(0, 2)]
        label.textSize = sp * density
        valuePaint.textSize = sp * 1.1f * density
        axis.textSize = sp * 0.75f * density
    }

    /** Draw the newest data; rebuilds the size when the number of charts changed. */
    fun refresh(nowMs: Long) {
        now = nowMs
        val n = model?.names?.size ?: 0
        if (n != shownRows) {
            shownRows = n
            requestLayout()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = model?.names?.size ?: 0
        val foot = if (rows > 0) label.textSize * 1.6f else 0f
        setMeasuredDimension((widthDp * density).toInt(), (rows * rowDp * density + foot + 6 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val m = model ?: return
        if (m.names.isEmpty()) return
        rect.set(1f, 1f, width - 1f, height - 1f)
        canvas.drawRect(rect, bg)
        canvas.drawRect(rect, edge)
        val rowH = rowDp * density
        val pad = 6 * density
        for ((i, name) in m.names.withIndex()) {
            val top = 3 * density + i * rowH
            drawRow(canvas, m, name, Sunlight.cIf(light, colors[i % colors.size]), pad, top, width - pad, top + rowH)
        }
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText(tr("Last {0} s", m.windowMs / 1000), width - pad, height - 6 * density, label)
        label.textAlign = Paint.Align.LEFT
    }

    private fun drawRow(canvas: Canvas, m: ChartModel, name: String, color: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val s = m.series(name)
        val textH = label.textSize * 1.25f
        label.textAlign = Paint.Align.LEFT
        label.color = color
        canvas.drawText(VariableCatalog.chartLabel(name), left, top + textH, label)
        label.color = Sunlight.cIf(light, 0xFFB0BEC5.toInt())
        val last = s?.last() ?: Double.NaN
        canvas.drawText(ChartScale.format(name, last), right, top + textH, valuePaint)

        val gTop = top + textH + 3 * density
        val gBottom = bottom - 3 * density
        val h = gBottom - gTop
        if (h < 8 * density) return
        canvas.drawLine(left, gBottom, right, gBottom, grid)
        val mm = s?.minMax(now, m.windowMs) ?: return
        val range = ChartScale.range(name, mm[0], mm[1])
        val lo = range[0]
        val hi = range[1]
        val first = s.firstInWindow(now, m.windowMs)
        val w = right - left
        line.color = color
        dot.color = color
        path.reset()
        var segment = 0
        var lastX = 0f
        var lastY = 0f
        for (k in first until s.count()) {
            val v = s.valueAt(k)
            if (v.isNaN() || v.isInfinite()) {
                if (segment == 1) canvas.drawCircle(lastX, lastY, 1.6f * density, dot)
                segment = 0
                continue
            }
            val x = left + (w * (s.timeAt(k) - (now - m.windowMs)) / m.windowMs).toFloat()
            val y = gBottom - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * h
            if (segment == 0) path.moveTo(x, y) else path.lineTo(x, y)
            segment++
            lastX = x
            lastY = y
        }
        if (segment == 1) canvas.drawCircle(lastX, lastY, 1.6f * density, dot)
        canvas.drawPath(path, line)
        // the ends of the axis
        axis.textAlign = Paint.Align.LEFT
        canvas.drawText(ChartScale.format(name, hi), left + 2 * density, gTop + axis.textSize, axis)
        canvas.drawText(ChartScale.format(name, lo), left + 2 * density, gBottom - 2 * density, axis)
    }
}
