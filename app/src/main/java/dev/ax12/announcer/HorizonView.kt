// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import java.util.Locale
import kotlin.math.abs

/**
 * An artificial horizon: sky above, ground below, a ladder every 10 degrees, and a fixed aircraft symbol. The picture follows the
 * pitch and roll angles of the flight controller (smoothed a little, because telemetry arrives a few times a second).
 * INAV counts a nose down pitch as positive; the two switches in Settings turn a direction round if a flight controller does not.
 */
class HorizonView(context: Context) : View(context) {
    /** Whether the card is drawn in the sunlight style (dark on light); set by the dashboard page. */
    var light = false

    private val density = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sky = Paint()
    private val ground = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ladder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val symbol = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var pitch = Double.NaN          // degrees, nose down positive as the flight controller reports it
    private var roll = Double.NaN           // degrees, right wing down positive
    private var lastMs = 0L
    private var lastValidMs = 0L
    private var invertPitch = false
    private var invertRoll = false
    private var drawnPitch = Double.NaN
    private var drawnRoll = Double.NaN
    private var drawnDataAvailable = false
    private var expiryScheduled = false
    private val sizePx = (168 * density).toInt()
    private val dataExpiry = object : Runnable {
        override fun run() {
            expiryScheduled = false
            val remaining = lastValidMs + 2000L - SystemClock.uptimeMillis()
            if (remaining >= 0) {
                expiryScheduled = true
                postDelayed(this, remaining + 1L)
            } else if (drawnDataAvailable) {
                drawnDataAvailable = false
                invalidate()
            }
        }
    }

    init {
        bg.color = CardStyle.BG
        sky.color = 0xFF2F6FA3.toInt()
        ground.color = 0xFF8A5A2B.toInt()
        line.color = 0xFFFFFFFF.toInt()
        line.strokeWidth = 2f * density
        ladder.color = 0xCCFFFFFF.toInt()
        ladder.strokeWidth = 1.5f * density
        text.color = 0xFFFFFFFF.toInt()
        CardStyle.styleSmallNumbers(text)
        text.textSize = 11f * density
        text.setShadowLayer(2f * density, 0f, 0f, 0xFF000000.toInt())
        symbol.color = 0xFFFFC107.toInt()
        symbol.strokeWidth = 3f * density
        symbol.style = Paint.Style.STROKE
        edge.color = CardStyle.EDGE
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
    }
    /** The sunlight look: dark text on a light card (and back). */
    fun applyLight(on: Boolean) {
        light = on
        bg.color = Sunlight.cIf(on, CardStyle.BG)
        edge.color = Sunlight.cIf(on, CardStyle.EDGE)
        invalidate()
    }


    fun configure(invertPitch: Boolean, invertRoll: Boolean) {
        this.invertPitch = invertPitch
        this.invertRoll = invertRoll
        invalidate()
    }

    /** The newest angles in degrees; NaN where the flight controller sends none. */
    fun set(pitchDeg: Double, rollDeg: Double) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastMs == 0L) 0.0 else (now - lastMs).toDouble()
        lastMs = now
        if (!pitchDeg.isNaN() || !rollDeg.isNaN()) {
            lastValidMs = now
            if (!expiryScheduled) {
                expiryScheduled = true
                postDelayed(dataExpiry, 2001L)
            }
        }
        pitch = Attitude.smooth(pitch, pitchDeg, dt, 90.0)
        roll = Attitude.wrap180(Attitude.smoothAngle(if (roll.isNaN()) Double.NaN else Attitude.wrap360(roll), if (rollDeg.isNaN()) Double.NaN else Attitude.wrap360(rollDeg), dt, 90.0))
        val hasData = hasDrawableData(now)
        if (hasData != drawnDataAvailable ||
            (hasData && (abs(pitch - drawnPitch) >= 0.1 || abs(Attitude.wrap180(roll - drawnRoll)) >= 0.1))) {
            drawnDataAvailable = hasData
            drawnPitch = pitch
            drawnRoll = roll
            invalidate()
        }
    }

    private fun hasDrawableData(now: Long): Boolean =
        !pitch.isNaN() && !roll.isNaN() && now - lastValidMs <= 2000L

    override fun onDetachedFromWindow() {
        removeCallbacks(dataExpiry)
        expiryScheduled = false
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(1f, 1f, w - 1f, h - 1f)
        CardStyle.drawFill(canvas, rect, bg, density)
        val noData = !hasDrawableData(SystemClock.uptimeMillis())
        val cx = w / 2f
        val cy = h / 2f
        if (!noData) {
            val perDeg = h / 60f                                         // 30 degrees up and down are in view
            val noseUp = if (invertPitch) pitch else -pitch
            val rollShown = if (invertRoll) -roll else roll
            canvas.save()
            CardStyle.clipFrame(canvas, rect, density)
            canvas.translate(cx, cy)
            canvas.rotate((-rollShown).toFloat())
            val off = (noseUp * perDeg).toFloat()
            val big = w * 2f
            canvas.drawRect(-big, -big, big, off, sky)
            canvas.drawRect(-big, off, big, big, ground)
            canvas.drawLine(-big, off, big, off, line)
            for (d in intArrayOf(10, 20, 30)) {
                for (sign in intArrayOf(1, -1)) {
                    val y = off - sign * d * perDeg
                    val half = if (d == 20) w * 0.10f else w * 0.16f
                    canvas.drawLine(-half, y, half, y, ladder)
                    if (d != 20) canvas.drawText(d.toString(), half + 4f * density, y + 4f * density, text)
                }
            }
            canvas.restore()
            val rs = String.format(Locale.US, "R %+.0f\u00B0", rollShown)
            val ps = String.format(Locale.US, "P %+.0f\u00B0", noseUp)
            canvas.drawText(rs, 6f * density, h - 6f * density, text)
            canvas.drawText(ps, w - 6f * density - text.measureText(ps), h - 6f * density, text)
        } else {
            val msg = "-"
            canvas.drawText(msg, cx - text.measureText(msg) / 2f, cy + 4f * density, text)
        }
        // the aircraft: two wings and a dot, fixed in the middle
        canvas.drawLine(cx - w * 0.30f, cy, cx - w * 0.10f, cy, symbol)
        canvas.drawLine(cx + w * 0.10f, cy, cx + w * 0.30f, cy, symbol)
        canvas.drawLine(cx - w * 0.10f, cy, cx - w * 0.10f, cy + 8f * density, symbol)
        canvas.drawLine(cx + w * 0.10f, cy, cx + w * 0.10f, cy + 8f * density, symbol)
        canvas.drawCircle(cx, cy, 2.5f * density, symbol)
        CardStyle.drawEdge(canvas, rect, edge, density, Sunlight.cIf(light, CardStyle.ACCENT))
    }
}
