// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * A small radar: home in the centre, north up, three range rings, the track of the flight and the aircraft
 * with its heading. It shows only positions relative to home, never coordinates. The scale grows as you fly
 * farther and does not shrink again during the flight.
 */
class RadarView(context: Context) : View(context) {
    /** Whether the card is drawn in the sunlight style (dark on light); set by the dashboard page. */
    var light = false


    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plane = Paint(Paint.ANTI_ALIAS_FLAG)
    private val homePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val tri = Path()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)

    private var stats: FlightStats? = null
    private var sizePx = (150 * density).toInt()

    init {
        bg.color = CardStyle.BG
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = CardStyle.EDGE_DP * density
        ring.color = 0xFF78909C.toInt()
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 1f * density
        trail.color = 0xFF4FC3F7.toInt()
        trail.style = Paint.Style.STROKE
        trail.strokeWidth = 2f * density
        trail.strokeJoin = Paint.Join.ROUND
        plane.color = 0xFF66BB6A.toInt()
        homePaint.color = 0xFFFFFFFF.toInt()
        text.color = 0xFFCFD8DC.toInt()
        text.textAlign = Paint.Align.CENTER
        CardStyle.styleSmallNumbers(text)
        text.textSize = 10f * density
    }
    /** The sunlight look: dark text on a light card (and back). */
    fun applyLight(on: Boolean) {
        light = on
        bg.color = Sunlight.cIf(on, CardStyle.BG)
        edge.color = Sunlight.cIf(on, CardStyle.EDGE)
        ring.color = Sunlight.cIf(on, 0xFF78909C.toInt())
        trail.color = Sunlight.cIf(on, 0xFF4FC3F7.toInt())
        plane.color = Sunlight.cIf(on, 0xFF66BB6A.toInt())
        homePaint.color = Sunlight.cIf(on, 0xFFFFFFFF.toInt())
        text.color = Sunlight.cIf(on, 0xFFCFD8DC.toInt())
        invalidate()
    }


    fun bind(s: FlightStats) { stats = s }

    fun setSizeDp(dp: Int) {
        sizePx = (dp * density).toInt()
        text.textSize = (dp * 0.075f).coerceIn(9f, 14f) * density
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val rpx = sizePx / 2f - 2 * density
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bg)           // a square card like the others; the range rings stay round
        canvas.drawRect(1f, 1f, sizePx - 1f, sizePx - 1f, edge)
        val s = stats
        val radiusM = s?.radarRadiusM() ?: 150.0
        for (k in 1..3) canvas.drawCircle(cx, cy, rpx * k / 3f, ring)
        canvas.drawText("N", cx, cy - rpx + text.textSize, text)
        canvas.drawText(Math.round(radiusM).toString() + " m", cx, cy + rpx - 4 * density, text)
        if (s == null || !s.hasHome()) return

        val k = (rpx / radiusM).toFloat()
        path.reset()
        for (i in 0 until s.trailSize()) {
            val x = cx + (s.trailEast(i) * k).toFloat()
            val y = cy - (s.trailNorth(i) * k).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, trail)
        canvas.drawCircle(cx, cy, 3.5f * density, homePaint)

        if (!s.eastM().isNaN()) {
            var px = cx + (s.eastM() * k).toFloat()
            var py = cy - (s.northM() * k).toFloat()
            // keep the aircraft inside the circle
            val dx = px - cx
            val dy = py - cy
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (d > rpx - 4 * density) {
                px = cx + dx * (rpx - 4 * density) / d
                py = cy + dy * (rpx - 4 * density) / d
            }
            val h = s.headingDeg()
            if (h.isNaN()) {
                canvas.drawCircle(px, py, 4.5f * density, plane)
            } else {
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(h.toFloat())
                tri.reset()
                tri.moveTo(0f, -7f * density)
                tri.lineTo(5f * density, 6f * density)
                tri.lineTo(0f, 3f * density)
                tri.lineTo(-5f * density, 6f * density)
                tri.close()
                canvas.drawPath(tri, plane)
                canvas.restore()
            }
        }
    }
}
