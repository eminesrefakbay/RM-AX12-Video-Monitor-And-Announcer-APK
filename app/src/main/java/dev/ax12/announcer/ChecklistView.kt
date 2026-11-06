package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The pre-flight card: one row per check with a green tick, a red cross or a grey dash (no value yet),
 * and a line at the bottom: READY or NOT READY. The marks are drawn, not typed, so they look the same
 * on any Android font.
 */
class ChecklistView(context: Context) : View(context) {

    class Row(val label: String, val state: Int)      // 0 unknown, 1 pass, 2 fail

    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markLine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var rows: List<Row> = emptyList()
    private var footer = ""
    private var footerColor = 0xFFB0BEC5.toInt()
    private var signature = ""
    private var textPx = 16f * density

    init {
        bg.color = 0xDD1E1E1E.toInt()
        edge.color = 0xFFE0E0E0.toInt()
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        labelPaint.color = 0xFFFFFFFF.toInt()
        titlePaint.color = 0xFF9CDCFE.toInt()
        titlePaint.isFakeBoldText = true
        footPaint.isFakeBoldText = true
        markLine.style = Paint.Style.STROKE
        markLine.strokeCap = Paint.Cap.ROUND
        applyTextSize()
    }

    fun setTextSp(sp: Float) {
        textPx = sp * density
        applyTextSize()
        requestLayout()
        invalidate()
    }

    private fun applyTextSize() {
        labelPaint.textSize = textPx
        titlePaint.textSize = textPx * 0.85f
        footPaint.textSize = textPx * 1.1f
        markLine.strokeWidth = textPx * 0.13f
    }

    private var title = ""
    private var subtitle = ""

    fun setContent(newTitle: String, newSubtitle: String, newRows: List<Row>, newFooter: String, color: Int) {
        val sb = StringBuilder(newTitle).append('#').append(newSubtitle).append('#').append(newFooter)
        for (r in newRows) sb.append('|').append(r.label).append(':').append(r.state)
        val sig = sb.toString()
        if (sig == signature) return
        signature = sig
        title = newTitle
        subtitle = newSubtitle
        rows = newRows
        footer = newFooter
        footerColor = color
        requestLayout()
        invalidate()
    }

    private fun pad() = textPx * 0.7f
    private fun rowH() = textPx * 1.5f
    private fun markW() = textPx * 1.2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = maxOf(titlePaint.measureText(title), footPaint.measureText(footer), labelPaint.measureText(subtitle) * 0.8f)
        for (r in rows) w = maxOf(w, markW() + labelPaint.measureText(r.label))
        val h = pad() * 2 + rowH() * (rows.size + 2) + (if (subtitle.isEmpty()) 0f else rowH() * 0.8f)
        setMeasuredDimension((w + pad() * 2).toInt() + 2, h.toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(1f, 1f, width - 1f, height - 1f)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, bg)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, edge)
        var y = pad() + textPx
        canvas.drawText(title, pad(), y, titlePaint)
        if (subtitle.isNotEmpty()) {
            y += rowH() * 0.8f
            val old = labelPaint.textSize
            labelPaint.textSize = old * 0.8f
            labelPaint.color = 0xFFB0BEC5.toInt()
            canvas.drawText(subtitle, pad(), y, labelPaint)
            labelPaint.textSize = old
            labelPaint.color = 0xFFFFFFFF.toInt()
        }
        for (r in rows) {
            y += rowH()
            drawMark(canvas, pad() + markW() * 0.4f, y - textPx * 0.33f, r.state)
            canvas.drawText(r.label, pad() + markW(), y, labelPaint)
        }
        y += rowH() * 1.1f
        footPaint.color = footerColor
        canvas.drawText(footer, pad(), y, footPaint)
    }

    private fun drawMark(canvas: Canvas, cx: Float, cy: Float, state: Int) {
        val r = textPx * 0.42f
        when (state) {
            1 -> {
                markFill.color = 0xFF2E7D32.toInt()
                canvas.drawCircle(cx, cy, r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.5f, cy, cx - r * 0.1f, cy + r * 0.45f, markLine)
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.45f, cx + r * 0.55f, cy - r * 0.4f, markLine)
            }
            2 -> {
                markFill.color = 0xFFC62828.toInt()
                canvas.drawCircle(cx, cy, r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.42f, cy - r * 0.42f, cx + r * 0.42f, cy + r * 0.42f, markLine)
                canvas.drawLine(cx - r * 0.42f, cy + r * 0.42f, cx + r * 0.42f, cy - r * 0.42f, markLine)
            }
            else -> {
                markFill.color = 0xFF546E7A.toInt()
                canvas.drawCircle(cx, cy, r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, markLine)
            }
        }
    }
}
