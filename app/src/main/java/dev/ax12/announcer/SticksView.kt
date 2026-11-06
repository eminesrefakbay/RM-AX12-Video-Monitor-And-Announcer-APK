package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The two sticks drawn as boxes with a dot, from the channel values (microseconds).
 * Which channel is which is set by StickLayout (mode 1 to 4, channel numbers, inversion).
 */
class SticksView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var boxPx = (96 * density).toInt()
    private var gapPx = (14 * density).toInt()
    private var labelPx = (18 * density).toInt()

    private var layoutInfo: StickLayout = StickLayout.forMode(2, 1, 2, 3, 4)
    private var invert = 0
    private var names = arrayOf("T", "Y", "P", "R")      // left vertical, left horizontal, right vertical, right horizontal

    // the four axes, -1..1, or NaN when the radio has not sent that channel
    private val axes = DoubleArray(4) { Double.NaN }
    private var drawn = DoubleArray(4) { 2.0 }

    init {
        fill.color = 0xCC1E1E1E.toInt()
        line.color = 0xFFE0E0E0.toInt()
        line.style = Paint.Style.STROKE
        line.strokeWidth = 1.5f * density
        dot.color = 0xFF66BB6A.toInt()
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = 11f * density
        text.textAlign = Paint.Align.CENTER
    }

    /** @param boxDp the side of one box */
    fun configure(layout: StickLayout, invertMask: Int, axisNames: Array<String>, boxDp: Int) {
        layoutInfo = layout
        invert = invertMask
        names = axisNames
        boxPx = (boxDp * density).toInt()
        labelPx = (boxDp * 0.2f * density / 1.0f).toInt().coerceIn((14 * density).toInt(), (22 * density).toInt())
        text.textSize = (boxDp * 0.14f).coerceIn(10f, 16f) * density
        requestLayout()
        invalidate()
    }

    fun update(numbers: Map<String, Double>) {
        val l = layoutInfo
        val chans = intArrayOf(l.leftVertical, l.leftHorizontal, l.rightVertical, l.rightHorizontal)
        val flags = intArrayOf(StickLayout.INV_LEFT_V, StickLayout.INV_LEFT_H, StickLayout.INV_RIGHT_V, StickLayout.INV_RIGHT_H)
        var changed = false
        for (i in 0 until 4) {
            val us = numbers["ch" + chans[i]]
            val v = if (us == null) Double.NaN else StickLayout.axis(us, (invert and flags[i]) != 0)
            axes[i] = v
            if (v.isNaN() != drawn[i].isNaN() || (!v.isNaN() && Math.abs(v - drawn[i]) > 0.004)) changed = true
        }
        if (changed) {
            for (i in 0 until 4) drawn[i] = axes[i]
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(2 * boxPx + gapPx, boxPx + labelPx)
    }

    override fun onDraw(canvas: Canvas) {
        drawStick(canvas, 0f, drawn[1], drawn[0], names[1], names[0])
        drawStick(canvas, (boxPx + gapPx).toFloat(), drawn[3], drawn[2], names[3], names[2])
    }

    private fun drawStick(canvas: Canvas, left: Float, h: Double, v: Double, hName: String, vName: String) {
        rect.set(left, 0f, left + boxPx, boxPx.toFloat())
        canvas.drawRoundRect(rect, 8f * density, 8f * density, fill)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, line)
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawLine(rect.left + 4 * density, cy, rect.right - 4 * density, cy, line)
        canvas.drawLine(cx, rect.top + 4 * density, cx, rect.bottom - 4 * density, line)
        if (!h.isNaN() && !v.isNaN() && h <= 1.5 && v <= 1.5) {
            val travel = boxPx / 2f - 8 * density
            val x = cx + (h * travel).toFloat()
            val y = cy - (v * travel).toFloat()
            canvas.drawCircle(x, y, boxPx * 0.075f, dot)
        }
        val hp = if (h.isNaN() || h > 1.5) "-" else Math.round(h * 100).toString()
        val vp = if (v.isNaN() || v > 1.5) "-" else Math.round(v * 100).toString()
        canvas.drawText(vName + " " + vp + "   " + hName + " " + hp, cx, boxPx + labelPx * 0.8f, text)
    }
}
