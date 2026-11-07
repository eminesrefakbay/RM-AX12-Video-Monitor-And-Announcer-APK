package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * A small card of flight numbers: a title and rows of "label  now / maximum". A row can carry a bar
 * (the current) with a marker for the highest value reached and a line for a limit.
 */
class FlightStatsView(context: Context) : View(context) {

    class Line(
        val label: String,
        val value: String,
        val bar: Double = Double.NaN,      // 0..1, the value now
        val peak: Double = Double.NaN,     // 0..1, the highest so far
        val limit: Double = Double.NaN     // 0..1, a line to stay under
    )

    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var title = ""
    private var lines: List<Line> = emptyList()
    private var signature = ""
    private var textPx = 15f * density

    init {
        bg.color = CardStyle.BG
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        labelPaint.color = 0xFFB0BEC5.toInt()
        valuePaint.color = 0xFFFFFFFF.toInt()
        valuePaint.isFakeBoldText = true
        titlePaint.color = CardStyle.TITLE
        titlePaint.isFakeBoldText = true
        barBg.color = 0xFF37474F.toInt()
        barFill.color = 0xFF66BB6A.toInt()
        mark.style = Paint.Style.STROKE
        mark.strokeWidth = 2f * density
        applyTextSize()
    }

    fun setTextSp(sp: Float) {
        textPx = sp * density
        applyTextSize()
        requestLayout()
        invalidate()
    }

    private fun applyTextSize() {
        labelPaint.textSize = textPx * 0.8f
        valuePaint.textSize = textPx
        titlePaint.textSize = textPx * 0.85f
    }

    fun setContent(newTitle: String, newLines: List<Line>) {
        val sb = StringBuilder(newTitle)
        for (l in newLines) sb.append('|').append(l.label).append(':').append(l.value).append(':').append(Math.round(l.bar * 100)).append(':').append(Math.round(l.peak * 100))
        val sig = sb.toString()
        if (sig == signature) return
        signature = sig
        title = newTitle
        lines = newLines
        requestLayout()
        invalidate()
    }

    private fun pad() = textPx * 0.6f
    private fun rowH() = textPx * 1.45f
    private fun barH() = textPx * 0.45f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = titlePaint.measureText(title)
        for (l in lines) w = maxOf(w, labelPaint.measureText(l.label) + textPx * 0.9f + valuePaint.measureText(l.value))
        var h = pad() * 2 + rowH()
        for (l in lines) h += rowH() + (if (l.bar.isNaN()) 0f else barH() + textPx * 0.25f)
        setMeasuredDimension((w + pad() * 2).toInt() + 2, h.toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(1f, 1f, width - 1f, height - 1f)
        canvas.drawRect(rect, bg)
        canvas.drawRect(rect, edge)
        var y = pad() + textPx * 0.9f
        canvas.drawText(title, pad(), y, titlePaint)
        for (l in lines) {
            y += rowH()
            canvas.drawText(l.label, pad(), y, labelPaint)
            canvas.drawText(l.value, width - pad() - valuePaint.measureText(l.value), y, valuePaint)
            if (!l.bar.isNaN()) {
                val top = y + textPx * 0.25f
                val left = pad()
                val right = width - pad()
                rect.set(left, top, right, top + barH())
                canvas.drawRect(rect, barBg)
                val f = l.bar.coerceIn(0.0, 1.0).toFloat()
                val over = !l.limit.isNaN() && l.bar > l.limit
                barFill.color = if (over) 0xFFEF5350.toInt() else 0xFF66BB6A.toInt()
                if (f > 0f) {
                    rect.set(left, top, left + (right - left) * f, top + barH())
                    canvas.drawRect(rect, barFill)
                }
                if (!l.peak.isNaN()) {
                    val px = left + (right - left) * l.peak.coerceIn(0.0, 1.0).toFloat()
                    mark.color = 0xFFFFFFFF.toInt()
                    canvas.drawLine(px, top - 2 * density, px, top + barH() + 2 * density, mark)
                }
                if (!l.limit.isNaN()) {
                    val lx = left + (right - left) * l.limit.coerceIn(0.0, 1.0).toFloat()
                    mark.color = 0xFFFFB300.toInt()
                    canvas.drawLine(lx, top - 3 * density, lx, top + barH() + 3 * density, mark)
                }
                y += barH() + textPx * 0.25f
            }
        }
    }
}
