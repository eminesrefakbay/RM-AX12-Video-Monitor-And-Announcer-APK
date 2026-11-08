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
import android.os.SystemClock
import android.view.View
import java.util.Locale

/**
 * A compass rose that turns with the heading of the aircraft (the nose is always at the top), the heading in degrees in the middle,
 * and a green marker that points the way home with the distance below it.
 */
class CompassView(context: Context) : View(context) {
    /** Whether the card is drawn in the sunlight style (dark on light); set by the dashboard page. */
    var light = false

    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nose = Paint(Paint.ANTI_ALIAS_FLAG)
    private val home = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private var heading = Double.NaN
    private var homeBearing = Double.NaN
    private var homeDistance = Double.NaN
    private var lastMs = 0L
    private val sizePx = (168 * density).toInt()

    init {
        bg.color = CardStyle.BG
        ring.color = 0xFF90A4AE.toInt()
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 1.5f * density
        tick.color = 0xFFB0BEC5.toInt()
        tick.strokeWidth = 1.2f * density
        label.color = 0xFFFFFFFF.toInt()
        label.textSize = 13f * density
        CardStyle.styleTitle(label)
        big.color = 0xFFFFFFFF.toInt()
        big.textSize = 26f * density * CardStyle.numberScale
        CardStyle.styleNumbers(big)
        small.color = CardStyle.LABEL
        small.textSize = 12f * density
        CardStyle.styleLabel(small)
        nose.color = 0xFFFFC107.toInt()
        home.color = 0xFF66BB6A.toInt()
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
    }
    /** The sunlight look: dark text on a light card (and back). */
    fun applyLight(on: Boolean) {
        light = on
        bg.color = Sunlight.cIf(on, CardStyle.BG)
        ring.color = Sunlight.cIf(on, 0xFF90A4AE.toInt())
        tick.color = Sunlight.cIf(on, 0xFFB0BEC5.toInt())
        label.color = Sunlight.cIf(on, 0xFFFFFFFF.toInt())
        big.color = Sunlight.cIf(on, 0xFFFFFFFF.toInt())
        small.color = Sunlight.cIf(on, CardStyle.LABEL)
        nose.color = Sunlight.cIf(on, 0xFFFFC107.toInt())
        home.color = Sunlight.cIf(on, 0xFF66BB6A.toInt())
        edge.color = Sunlight.cIf(on, CardStyle.EDGE)
        invalidate()
    }


    /** headingDeg and homeBearingDeg from north; NaN where not known; homeDistanceM in metres. */
    fun set(headingDeg: Double, homeBearingDeg: Double, homeDistanceM: Double) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastMs == 0L) 0.0 else (now - lastMs).toDouble()
        lastMs = now
        heading = Attitude.smoothAngle(heading, headingDeg, dt, 110.0)
        homeBearing = homeBearingDeg
        homeDistance = homeDistanceM
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(1f, 1f, w - 1f, h - 1f)
        CardStyle.drawFill(canvas, rect, bg, density)
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.38f
        canvas.drawCircle(cx, cy, r, ring)
        val hd = if (heading.isNaN()) 0.0 else heading
        canvas.save()
        canvas.rotate((-hd).toFloat(), cx, cy)
        for (deg in 0 until 360 step 10) {
            val long = deg % 30 == 0
            canvas.save()
            canvas.rotate(deg.toFloat(), cx, cy)
            canvas.drawLine(cx, cy - r, cx, cy - r + (if (long) 10f else 5f) * density, tick)
            canvas.restore()
        }
        val names = arrayOf("N", "E", "S", "W")
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0)
            val rr = r - 22f * density
            val px = cx + (rr * Math.sin(a)).toFloat()
            val py = cy - (rr * Math.cos(a)).toFloat()
            label.color = if (i == 0) Sunlight.cIf(light, 0xFFEF5350.toInt()) else Sunlight.cIf(light, 0xFFFFFFFF.toInt())
            canvas.save()
            canvas.rotate(hd.toFloat(), px, py)            // undo the turn of the rose, so that the letter stands upright
            canvas.drawText(names[i], px - label.measureText(names[i]) / 2f, py + 5f * density, label)
            canvas.restore()
        }
        // the way home: a green triangle on the ring
        if (!homeBearing.isNaN()) {
            canvas.save()
            canvas.rotate(homeBearing.toFloat(), cx, cy)
            path.reset()
            path.moveTo(cx, cy - r - 1f * density)
            path.lineTo(cx - 7f * density, cy - r - 12f * density)
            path.lineTo(cx + 7f * density, cy - r - 12f * density)
            path.close()
            canvas.drawPath(path, home)
            canvas.restore()
        }
        canvas.restore()
        // the nose of the aircraft: a fixed yellow marker at the top
        path.reset()
        path.moveTo(cx, cy - r + 2f * density)
        path.lineTo(cx - 6f * density, cy - r - 9f * density)
        path.lineTo(cx + 6f * density, cy - r - 9f * density)
        path.close()
        canvas.drawPath(path, nose)
        val hs = if (heading.isNaN()) "-" else String.format(Locale.US, "%03.0f\u00B0", heading)
        CardStyle.drawNumber(canvas, hs, cx - CardStyle.measureNumber(hs, big) / 2f, cy + 4f * density, big)
        val card = Attitude.cardinal(heading)
        if (card.isNotEmpty()) canvas.drawText(card, cx - small.measureText(card) / 2f, cy + 22f * density, small)
        if (!homeBearing.isNaN() && !homeDistance.isNaN()) {
            val d = "H " + Math.round(homeDistance) + " m"
            home.textSize = 12f * density
            canvas.drawText(d, cx - home.measureText(d) / 2f, h - 5f * density, home)
        }
        CardStyle.drawEdge(canvas, rect, edge, density, Sunlight.cIf(light, CardStyle.ACCENT))
    }
}
