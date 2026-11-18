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
 * The scrub bar of the replay: a full-width track like a video player's, the played part in the accent colour, a small mark for every
 * event of the flight (take-off, mode changes, the highest point, landing) and a round handle. A touch anywhere on it jumps there and a
 * drag scrubs. It has no padding for buttons: the buttons sit below it, so it keeps the whole width.
 */
class ScrubView(context: Context) : View(context) {
    /** Called while the finger is on the bar: the position as a fraction 0..1, and whether the finger has just lifted. */
    var onScrub: (Double, Boolean) -> Unit = { _, _ -> }
    /** Called when the finger goes down or up (the bar of the replay must not hide while it is held). */
    var onHold: (Boolean) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var fraction = 0.0
    private var held = false
    private var marks = DoubleArray(0)
    private var markColors = IntArray(0)

    private fun dp(v: Float): Float = v * density

    fun setMarks(fractions: DoubleArray, colors: IntArray) {
        marks = fractions
        markColors = colors
        invalidate()
    }

    /** Moves the handle; ignored while the finger holds it. */
    fun setProgress(f: Double) {
        if (held) return
        val v = f.coerceIn(0.0, 1.0)
        if (v == fraction) return
        fraction = v
        invalidate()
    }

    private fun trackLeft(): Float = dp(14f)
    private fun trackRight(): Float = width - dp(14f)

    private fun fractionAt(x: Float): Double {
        val l = trackLeft()
        val w = (trackRight() - l).coerceAtLeast(1f)
        return ((x - l) / w).toDouble().coerceIn(0.0, 1.0)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                held = true
                onHold(true)
                fraction = fractionAt(e.x)
                invalidate()
                onScrub(fraction, false)
            }
            MotionEvent.ACTION_MOVE -> {
                fraction = fractionAt(e.x)
                invalidate()
                onScrub(fraction, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                held = false
                fraction = fractionAt(e.x)
                invalidate()
                onScrub(fraction, true)
                onHold(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val l = trackLeft()
        val r = trackRight()
        val cy = height / 2f
        val half = dp(if (held) 4f else 3f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF37424F.toInt()
        rect.set(l, cy - half, r, cy + half)
        canvas.drawRect(rect, paint)
        val x = l + (r - l) * fraction.toFloat()
        paint.color = CardStyle.ACCENT
        rect.set(l, cy - half, x, cy + half)
        canvas.drawRect(rect, paint)
        for (i in marks.indices) {
            val mx = l + (r - l) * marks[i].toFloat()
            paint.color = markColors[i]
            rect.set(mx - dp(1.5f), cy - dp(9f), mx + dp(1.5f), cy - half - dp(1f))
            canvas.drawRect(rect, paint)
        }
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, cy, dp(if (held) 11f else 8f), paint)
    }
}
