// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * The pre-flight card: one row per check with a green tick, a red cross or a grey dash (no value yet),
 * and a line at the bottom: READY or NOT READY. The marks are drawn, not typed, so they look the same
 * on any Android font. A small X in the top right corner closes the card ([onClose]); the rest of the card lets touches through.
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

    /** Called when the X is tapped. */
    var onClose: (() -> Unit)? = null
    private var closePressed = false
    private val closeRect = RectF()

    init {
        bg.color = CardStyle.BG
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        labelPaint.color = 0xFFFFFFFF.toInt()
        titlePaint.color = CardStyle.TITLE
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
    private fun closeSide() = textPx * 1.3f

    /** The X sits in the top right corner. */
    private fun placeClose() {
        val m = pad() * 0.55f
        closeRect.set(width - m - closeSide(), m, width - m, m + closeSide())
    }

    /** The tap area is at least 48 dp square, around the drawn X. */
    private fun hit(x: Float, y: Float): Boolean {
        placeClose()
        val grow = maxOf(0f, (CardStyle.MIN_TOUCH_DP * density - closeSide()) / 2f)
        return x >= closeRect.left - grow && x <= closeRect.right + grow && y >= closeRect.top - grow && y <= closeRect.bottom + grow
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hit(e.x, e.y)) return false          // not on the X: the touch goes on to whatever is under the card
                closePressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val was = closePressed
                closePressed = false
                invalidate()
                if (was && hit(e.x, e.y)) { performClick(); onClose?.invoke() }
                return was
            }
            MotionEvent.ACTION_CANCEL -> { closePressed = false; invalidate(); return true }
        }
        return closePressed
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = maxOf(titlePaint.measureText(title) + closeSide() + pad(), footPaint.measureText(footer), labelPaint.measureText(subtitle) * 0.8f)
        for (r in rows) w = maxOf(w, markW() + labelPaint.measureText(r.label))
        val h = pad() * 2 + rowH() * (rows.size + 2) + (if (subtitle.isEmpty()) 0f else rowH() * 0.8f)
        setMeasuredDimension((w + pad() * 2).toInt() + 2, h.toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(1f, 1f, width - 1f, height - 1f)
        canvas.drawRect(rect, bg)
        canvas.drawRect(rect, edge)
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
        drawClose(canvas)
    }

    /** A plain square with an X, like the marks of the rows. */
    private fun drawClose(canvas: Canvas) {
        placeClose()
        markFill.color = if (closePressed) 0xFF546E7A.toInt() else 0x99000000.toInt()
        canvas.drawRect(closeRect, markFill)
        canvas.drawRect(closeRect, edge)
        markLine.color = 0xFFFFFFFF.toInt()
        val c = closeRect.centerX()
        val d = closeRect.centerY()
        val r = closeSide() * 0.26f
        canvas.drawLine(c - r, d - r, c + r, d + r, markLine)
        canvas.drawLine(c - r, d + r, c + r, d - r, markLine)
    }

    private fun drawMark(canvas: Canvas, cx: Float, cy: Float, state: Int) {
        val r = textPx * 0.42f
        when (state) {
            1 -> {
                markFill.color = 0xFF2E7D32.toInt()
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.5f, cy, cx - r * 0.1f, cy + r * 0.45f, markLine)
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.45f, cx + r * 0.55f, cy - r * 0.4f, markLine)
            }
            2 -> {
                markFill.color = 0xFFC62828.toInt()
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.42f, cy - r * 0.42f, cx + r * 0.42f, cy + r * 0.42f, markLine)
                canvas.drawLine(cx - r * 0.42f, cy + r * 0.42f, cx + r * 0.42f, cy - r * 0.42f, markLine)
            }
            else -> {
                markFill.color = 0xFF546E7A.toInt()
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, markFill)
                markLine.color = 0xFFFFFFFF.toInt()
                canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, markLine)
            }
        }
    }
}
