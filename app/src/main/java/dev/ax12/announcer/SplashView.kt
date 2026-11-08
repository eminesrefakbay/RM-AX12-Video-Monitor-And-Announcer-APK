// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The splash screen, drawn and animated in one view: the frame around the logo draws itself, the logo grows in under a passing light, the
 * title appears letter by letter and catches a light, the tagline follows, and the version chip draws itself last. It is laid out in a
 * 1280 by 720 design (the radio's screen) and scaled to the view. The timing is in [SplashTimeline].
 */
class SplashView(context: Context, private val versionName: String, private val build: Long) : View(context) {
    private val glyph: Bitmap? = try {
        context.assets.open("splash/glyph.png").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
    private var t = 0L
    private val animator = ValueAnimator.ofInt(0, SplashTimeline.TOTAL_MS)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dst = Path()
    private val rect = RectF()
    private val framePath: Path
    private val wordPath = Path()
    private val letters = arrayOf("A", "X", "-", "H", "U", "D")
    private val letterColors = intArrayOf(LIME, LIME, CYAN, Color.WHITE, Color.WHITE, Color.WHITE)
    private val letterX = FloatArray(6)

    init {
        val f = ShapeGeometry.outline(ShapeGeometry.CHAMFER2, 90f, 120f, 570f, 600f, 46f)
        framePath = Path()
        framePath.moveTo(f[0], f[1])
        var i = 2
        while (i < f.size) { framePath.lineTo(f[i], f[i + 1]); i += 2 }
        framePath.close()
        animator.duration = SplashTimeline.TOTAL_MS.toLong()
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            t = (it.animatedValue as Int).toLong()
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt() shl 24)

    private fun wordFont(): Typeface = Fonts.numbers(Typeface.DEFAULT_BOLD) ?: Typeface.DEFAULT_BOLD

    private fun setText(size: Float, weight: Int, color: Int, spacingPx: Float) {
        text.typeface = Fonts.text(weight, if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
        text.textSize = size
        text.color = color
        text.letterSpacing = if (spacingPx == 0f) 0f else spacingPx / size
    }

    /** The title letters at their final places, measured once. */
    private fun layoutWord() {
        text.typeface = wordFont()
        text.textSize = 178f
        text.letterSpacing = 0f
        wordPath.reset()
        var x = 636f
        for (k in 0 until 6) {
            letterX[k] = x
            val p = Path()
            text.getTextPath(letters[k], 0, 1, x, 407f, p)
            wordPath.addPath(p)
            x += text.measureText(letters[k])
        }
    }

    private fun chip(x: Float, y: Float, w: Float, h: Float, cut: Float) {
        val p = ShapeGeometry.outline(ShapeGeometry.CHAMFER2, x, y, x + w, y + h, cut)
        path.reset()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        val s = minOf(width / 1280f, height / 720f)
        canvas.save()
        canvas.translate((width - 1280f * s) / 2f, (height - 720f * s) / 2f)
        canvas.scale(s, s)
        val ms = t

        // the background: a faint grid, a breathing glow behind the logo, and dark corners
        val bg = SplashTimeline.easeOut(SplashTimeline.seg(ms, 0, 500))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = alpha(Color.WHITE, 0.035f * bg)
        var gx = 0f
        while (gx <= 1280f) { canvas.drawLine(gx, 0f, gx, 720f, paint); gx += 64f }
        var gy = 0f
        while (gy <= 720f) { canvas.drawLine(0f, gy, 1280f, gy, paint); gy += 64f }
        paint.style = Paint.Style.FILL
        val glow = (0.55f + 0.45f * SplashTimeline.breathe(ms)) * SplashTimeline.seg(ms, 0, 700)
        paint.shader = RadialGradient(330f, 370f, 470f, alpha(CYAN, 0.16f * glow), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1280f, 720f, paint)
        paint.shader = RadialGradient(640f, 360f, 820f, intArrayOf(0x00000000, 0x00000000, alpha(Color.BLACK, 0.45f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1280f, 720f, paint)
        paint.shader = null

        // the frame around the logo draws itself; two corner brackets follow (the cut corners stay as they are)
        val fp = SplashTimeline.seg(ms, SplashTimeline.FRAME_START, SplashTimeline.FRAME_END)
        paint.style = Paint.Style.FILL
        paint.color = alpha(Color.BLACK, 0.27f * fp)
        canvas.drawPath(framePath, paint)
        if (fp > 0f) {
            val pm = PathMeasure(framePath, true)
            dst.reset()
            pm.getSegment(0f, pm.length * SplashTimeline.easeInOut(fp), dst, true)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = alpha(CYAN, 0.6f)
            canvas.drawPath(dst, paint)
        }
        val bp = SplashTimeline.easeOut(SplashTimeline.seg(ms, SplashTimeline.BRACKET_START, SplashTimeline.BRACKET_END))
        if (bp > 0f) {
            paint.strokeWidth = 5f
            paint.strokeCap = Paint.Cap.SQUARE
            paint.color = alpha(CYAN, bp)
            val a = 38f * bp
            canvas.drawLine(570f, 120f + a, 570f, 120f, paint); canvas.drawLine(570f, 120f, 570f - a, 120f, paint)
            canvas.drawLine(90f, 600f - a, 90f, 600f, paint); canvas.drawLine(90f, 600f, 90f + a, 600f, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }

        // the logo, with a light passing over it
        val gp = SplashTimeline.easeOut(SplashTimeline.seg(ms, SplashTimeline.GLYPH_START, SplashTimeline.GLYPH_END))
        val gl = glyph
        if (gl != null && gp > 0f) {
            val k = 0.72f * (0.94f + 0.06f * gp)
            val w = gl.width * k
            val h = gl.height * k
            rect.set(330f - w / 2f, 362f - h / 2f, 330f + w / 2f, 362f + h / 2f)
            paint.style = Paint.Style.FILL
            paint.alpha = (255 * gp).toInt()
            canvas.drawBitmap(gl, null, rect, paint)
            paint.alpha = 255
        }
        val sp = SplashTimeline.seg(ms, SplashTimeline.SCAN_START, SplashTimeline.SCAN_END)
        if (sp > 0f && sp < 1f) {
            val y = 120f + 480f * SplashTimeline.easeInOut(sp)
            canvas.save()
            canvas.clipPath(framePath)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, y - 46f, 0f, y + 46f, intArrayOf(0x00000000, alpha(CYAN, 0.36f * SplashTimeline.bell(sp)), 0x00000000), null, Shader.TileMode.CLAMP)
            canvas.drawRect(90f, y - 46f, 570f, y + 46f, paint)
            paint.shader = null
            canvas.restore()
        }

        // the tag
        val cp = SplashTimeline.easeOut(SplashTimeline.seg(ms, SplashTimeline.CHIP_START, SplashTimeline.CHIP_END))
        if (cp > 0f) {
            canvas.save()
            canvas.translate(-30f * (1f - cp), 0f)
            chip(640f, 176f, 232f, 40f, 9f)
            paint.style = Paint.Style.FILL
            paint.color = alpha(LIME, cp)
            canvas.drawPath(path, paint)
            setText(17f, 700, alpha(0xFF141E14.toInt(), cp), 3f)
            canvas.drawText("RADIOMASTER AX12", 660f, 203f, text)
            canvas.restore()
        }

        // the title, letter by letter
        if (letterX[0] == 0f) layoutWord()
        text.typeface = wordFont()
        text.textSize = 178f
        text.letterSpacing = 0f
        for (k in 0 until 6) {
            val e = SplashTimeline.easeOut(SplashTimeline.letter(k, ms))
            if (e <= 0f) continue
            text.color = alpha(letterColors[k], e)
            canvas.drawText(letters[k], letterX[k], 407f + 46f * (1f - e), text)
        }
        val shp = SplashTimeline.seg(ms, SplashTimeline.SHINE_START, SplashTimeline.SHINE_END)
        if (shp > 0f && shp < 1f) {                                   // a light across the letters only
            canvas.save()
            canvas.clipPath(wordPath)
            canvas.rotate(-18f, 900f, 300f)
            val x = 520f + 760f * SplashTimeline.easeInOut(shp)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(x - 90f, 0f, x + 90f, 0f, intArrayOf(0x00FFFFFF, alpha(Color.WHITE, 0.55f * SplashTimeline.bell(shp)), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawRect(x - 90f, 100f, x + 90f, 500f, paint)
            paint.shader = null
            canvas.restore()
        }

        // the line under the title
        val rp = SplashTimeline.easeInOut(SplashTimeline.seg(ms, SplashTimeline.RULE_START, SplashTimeline.RULE_END))
        if (rp > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = alpha(Color.WHITE, 0.2f)
            canvas.drawLine(640f, 470f, 640f + 550f * rp, 470f, paint)
            paint.style = Paint.Style.FILL
            paint.color = LIME
            canvas.drawRect(640f, 466f, 640f + 120f * rp, 474f, paint)
        }

        // the tagline
        val t1 = SplashTimeline.easeOut(SplashTimeline.seg(ms, SplashTimeline.TAG1_START, SplashTimeline.TAG1_END))
        if (t1 > 0f) {
            setText(24f, 700, alpha(Color.WHITE, t1), 4f)
            canvas.drawText(CardStyle.upper(tr("Video monitor")), 640f, 520f + 14f * (1f - t1), text)
        }
        val t2 = SplashTimeline.easeOut(SplashTimeline.seg(ms, SplashTimeline.TAG2_START, SplashTimeline.TAG2_END))
        if (t2 > 0f) {
            setText(21f, 500, alpha(LAB, t2), 4f)
            canvas.drawText(CardStyle.upper(tr("HUD  \u00B7  voice announcer")), 640f, 553f + 14f * (1f - t2), text)
        }

        // the version chip draws itself
        val vp = SplashTimeline.seg(ms, SplashTimeline.VERSION_START, SplashTimeline.VERSION_END)
        if (vp > 0f) {
            val label = CardStyle.upper(tr("Version {0}  \u00B7  Build {1}", versionName, build))
            setText(18f, 600, Color.WHITE, 2f)
            val w = text.measureText(label) + 44f
            chip(640f, 606f, w, 44f, 11f)
            paint.style = Paint.Style.FILL
            paint.color = alpha(CYAN, 0.14f * vp)
            canvas.drawPath(path, paint)
            val pm = PathMeasure(path, true)
            dst.reset()
            pm.getSegment(0f, pm.length * SplashTimeline.easeInOut(vp), dst, true)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = CYAN
            canvas.drawPath(dst, paint)
            val fa = SplashTimeline.easeOut(((vp - 0.45f) / 0.55f).coerceIn(0f, 1f))
            if (fa > 0f) {
                setText(18f, 600, alpha(Color.WHITE, fa), 2f)
                canvas.drawText(label, 662f, 636f, text)
            }
        }
        canvas.restore()
    }

    companion object {
        const val BG = 0xFF212D3D.toInt()
        const val LIME = 0xFF9CFF00.toInt()
        const val CYAN = 0xFF00E5FF.toInt()
        const val LAB = 0xFFB0BEC5.toInt()
    }
}
