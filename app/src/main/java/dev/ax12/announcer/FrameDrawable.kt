// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The background of a panel, tile or row in the shape style that is chosen (cut corners, brackets, rounded or plain). The style is read each
 * time it draws, so a change in Settings shows at once. A solid frame (a warning) never uses brackets, because a warning keeps its fill.
 */
class FrameDrawable(
    fill: Int,
    private val stroke: Int,
    private val strokePx: Float,
    private val density: Float,
    private val accent: Int,
    private val solid: Boolean = false
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = fill
        edgePaint.style = Paint.Style.STROKE
        edgePaint.strokeWidth = strokePx
        edgePaint.color = stroke
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val half = strokePx / 2f
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)
        CardStyle.drawFill(canvas, rect, fillPaint, density, solid)
        if (strokePx > 0f && Color.alpha(stroke) > 0) CardStyle.drawEdge(canvas, rect, edgePaint, density, accent, solid)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        edgePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        edgePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
