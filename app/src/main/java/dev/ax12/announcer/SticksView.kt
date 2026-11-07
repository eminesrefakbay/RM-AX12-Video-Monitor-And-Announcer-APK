// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The two sticks drawn as boxes with a dot, from the channel values (microseconds), with up to four
 * channel bars on each side (the aux switches). Which channel is which stick axis is set by StickLayout;
 * which channels get a bar, and their names, by AuxBars.
 *
 * A bar fills from the bottom with the channel value. Its colour tells a switch position:
 * low = grey-blue, middle = amber, high = green. Its name is written along the bar.
 */
class SticksView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nameFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nameOutline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val level = RectF()

    private var boxPx = (96 * density).toInt()
    private var gapPx = (14 * density).toInt()
    private var labelPx = (18 * density).toInt()
    private var barPx = (16 * density).toInt()
    private val barGapPx = (3 * density).toInt()
    private val groupGapPx = (8 * density).toInt()

    private var layoutInfo: StickLayout = StickLayout.forMode(2, 1, 2, 3, 4)
    private var invert = 0
    private var names = arrayOf("T", "Y", "P", "R")      // left vertical, left horizontal, right vertical, right horizontal

    // the four stick axes, -1..1, or NaN when the radio has not sent that channel
    private val axes = DoubleArray(4) { Double.NaN }
    private var drawn = DoubleArray(4) { 2.0 }

    private var leftBars: List<AuxBars.Bar> = emptyList()
    private var rightBars: List<AuxBars.Bar> = emptyList()
    private var leftFrac = DoubleArray(0)
    private var rightFrac = DoubleArray(0)
    private var leftDrawn = DoubleArray(0)
    private var rightDrawn = DoubleArray(0)

    /** Called when the overlay is held down: the app opens the bars editor. */
    var onLongPress: (() -> Unit)? = null

    init {
        fill.color = CardStyle.BG
        line.color = CardStyle.EDGE
        line.style = Paint.Style.STROKE
        line.strokeWidth = 1.5f * density
        dot.color = 0xFF66BB6A.toInt()
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = 11f * density
        text.textAlign = Paint.Align.CENTER
        nameFill.color = 0xFFFFFFFF.toInt()
        nameFill.textAlign = Paint.Align.CENTER
        nameOutline.color = 0xE0000000.toInt()
        nameOutline.style = Paint.Style.STROKE
        nameOutline.strokeWidth = 2.5f * density
        nameOutline.textAlign = Paint.Align.CENTER
        isLongClickable = true
        setOnLongClickListener {
            onLongPress?.invoke()
            true
        }
    }

    /** @param boxDp the side of one stick box; the bars scale with it */
    fun configure(layout: StickLayout, invertMask: Int, axisNames: Array<String>, boxDp: Int) {
        layoutInfo = layout
        invert = invertMask
        names = axisNames
        boxPx = (boxDp * density).toInt()
        barPx = (boxDp * 0.17f * density).toInt().coerceAtLeast((11 * density).toInt())
        labelPx = (boxDp * 0.2f * density).toInt().coerceIn((14 * density).toInt(), (22 * density).toInt())
        text.textSize = (boxDp * 0.14f).coerceIn(10f, 16f) * density
        requestLayout()
        invalidate()
    }

    /** @param all the eight bars in order (slots 1 to 4 left, 5 to 8 right); @param show false hides all of them */
    fun setBars(all: List<AuxBars.Bar>, show: Boolean) {
        leftBars = if (show) AuxBars.side(all, true) else emptyList()
        rightBars = if (show) AuxBars.side(all, false) else emptyList()
        leftFrac = DoubleArray(leftBars.size) { Double.NaN }
        rightFrac = DoubleArray(rightBars.size) { Double.NaN }
        leftDrawn = DoubleArray(leftBars.size) { 2.0 }
        rightDrawn = DoubleArray(rightBars.size) { 2.0 }
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
            if (differs(v, drawn[i], 0.004)) changed = true
        }
        if (changed) for (i in 0 until 4) drawn[i] = axes[i]
        if (updateBars(leftBars, leftFrac, leftDrawn, numbers)) changed = true
        if (updateBars(rightBars, rightFrac, rightDrawn, numbers)) changed = true
        if (changed) invalidate()
    }

    private fun updateBars(bars: List<AuxBars.Bar>, frac: DoubleArray, shown: DoubleArray, numbers: Map<String, Double>): Boolean {
        var changed = false
        for (i in bars.indices) {
            val us = numbers["ch" + bars[i].channel]
            val f = if (us == null) Double.NaN else AuxBars.fraction(us, bars[i].invert)
            frac[i] = f
            if (differs(f, shown[i], 0.01)) {
                shown[i] = f
                changed = true
            }
        }
        return changed
    }

    private fun differs(a: Double, b: Double, eps: Double): Boolean =
        a.isNaN() != b.isNaN() || (!a.isNaN() && Math.abs(a - b) > eps)

    // ------------------------------------------------------------------ layout and drawing

    private fun groupWidth(n: Int): Int = if (n == 0) 0 else n * barPx + (n - 1) * barGapPx + groupGapPx

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(groupWidth(leftBars.size) + 2 * boxPx + gapPx + groupWidth(rightBars.size), boxPx + labelPx)
    }

    override fun onDraw(canvas: Canvas) {
        var x = 0f
        for (i in leftBars.indices) {
            drawBar(canvas, x, leftBars[i].name, leftDrawn[i])
            x += (barPx + barGapPx).toFloat()
        }
        val stickLeft = groupWidth(leftBars.size).toFloat()
        drawStick(canvas, stickLeft, drawn[1], drawn[0], names[1], names[0])
        val stickRight = stickLeft + boxPx + gapPx
        drawStick(canvas, stickRight, drawn[3], drawn[2], names[3], names[2])
        x = stickRight + boxPx + (if (rightBars.isEmpty()) 0 else groupGapPx)
        for (i in rightBars.indices) {
            drawBar(canvas, x, rightBars[i].name, rightDrawn[i])
            x += (barPx + barGapPx).toFloat()
        }
    }

    private fun drawBar(canvas: Canvas, left: Float, name: String, frac: Double) {
        rect.set(left, 0f, left + barPx, boxPx.toFloat())
        canvas.drawRect(rect, fill)
        if (!frac.isNaN() && frac <= 1.0) {
            val h = (rect.height() * frac).toFloat().coerceAtLeast(5f * density)      // a low switch still shows a stub
            barFill.color = when (AuxBars.level(frac)) {
                0 -> 0xFF607D8B.toInt()
                1 -> 0xFFFFB300.toInt()
                else -> 0xFF66BB6A.toInt()
            }
            level.set(rect.left + 2 * density, rect.bottom - h, rect.right - 2 * density, rect.bottom - 2 * density)
            canvas.drawRect(level, barFill)
        }
        canvas.drawRect(rect, line)

        // the name, written upwards along the bar and shrunk to fit its length
        var size = (barPx * 0.62f).coerceIn(8f * density, 14f * density)
        val maxLen = boxPx - 8 * density
        nameFill.textSize = size
        val w = nameFill.measureText(name)
        if (w > maxLen) {
            size *= maxLen / w
            nameFill.textSize = size
        }
        nameOutline.textSize = size
        val alphaText = if (frac.isNaN() || frac > 1.0) 0.5f else 1f
        nameFill.alpha = (255 * alphaText).toInt()
        canvas.save()
        canvas.rotate(-90f, rect.centerX(), rect.centerY())
        canvas.drawText(name, rect.centerX(), rect.centerY() + size * 0.34f, nameOutline)
        canvas.drawText(name, rect.centerX(), rect.centerY() + size * 0.34f, nameFill)
        canvas.restore()
    }

    private fun drawStick(canvas: Canvas, left: Float, h: Double, v: Double, hName: String, vName: String) {
        rect.set(left, 0f, left + boxPx, boxPx.toFloat())
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, line)
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
