// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * The 3D flight replay: the flight drawn as a ribbon over a flat ground with a horizon, the aircraft on it, and flags at the events.
 * Everything is smooth: the aircraft is sampled on a curve through the logged points (FlightPath), the camera follows with a half-life
 * (Cam3D), and the view keeps its own clock that moves on every screen frame and is only nudged towards the replay's position.
 * It is drawn with the canvas of the view, which the screen draws on the GPU; one frame is a few thousand lines.
 */
class Flight3DView(context: Context) : View(context) {
    private var path: FlightPath? = null
    /** What the aircraft is drawn as: MODEL_QUAD or MODEL_PLANE. */
    var model = MODEL_QUAD
    private val cam = Cam3D()
    private val pose = FlightPath.Pose()
    private val density = context.resources.displayMetrics.density

    // the view's own clock
    private var renderMs = 0.0
    private var engineMs = 0.0
    private var engineNano = 0L
    private var playing = false
    private var speed = 1
    private var lastNano = 0L
    private var snapNext = true

    // the ribbon, as x, y, z, speed per point every RIBBON_STEP_MS
    private var ribbon = FloatArray(0)
    private val bands = Array(BAND_COUNT) { FloatArray(0) }
    private var futureLines = FloatArray(0)
    private val bandCount = IntArray(BAND_COUNT)
    private var futureCount = 0

    private val seg = DoubleArray(4)
    private val pt = DoubleArray(3)
    private val pt2 = DoubleArray(3)
    private val tmpPath = Path()
    private var skyShader: Shader? = null

    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val futurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandPaints = Array(BAND_COUNT) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rotorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val homePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hazePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noseFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val placed = FloatArray(MAX_LABELS * 4)
    private var placedCount = 0

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            cam.userDistance = (cam.userDistance / d.scaleFactor).coerceIn(8.0, 600.0)
            return true
        }
    })
    private var lastX = 0f
    private var lastY = 0f

    init {
        skyPaint.style = Paint.Style.FILL
        groundPaint.style = Paint.Style.FILL
        groundPaint.color = 0xFF17222D.toInt()
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = dp(1f)
        gridPaint.color = 0x4090A4AE
        axisPaint.style = Paint.Style.STROKE
        axisPaint.strokeWidth = dp(1.4f)
        axisPaint.color = 0x804FC3F7.toInt()
        shadowPaint.style = Paint.Style.STROKE
        shadowPaint.strokeWidth = dp(1.5f)
        shadowPaint.color = 0x50FFFFFF
        dropPaint.style = Paint.Style.STROKE
        dropPaint.strokeWidth = dp(1f)
        dropPaint.color = 0x30B0BEC5
        futurePaint.style = Paint.Style.STROKE
        futurePaint.strokeWidth = dp(2f)
        futurePaint.color = 0x509CDCFE
        for (i in 0 until BAND_COUNT) {
            val p = bandPaints[i]
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(3.2f)
            p.strokeCap = Paint.Cap.ROUND
            p.color = rampColor(i * 110.0 / (BAND_COUNT - 1))
        }
        bodyPaint.style = Paint.Style.STROKE
        bodyPaint.strokeWidth = dp(3f)
        bodyPaint.strokeCap = Paint.Cap.ROUND
        bodyPaint.color = 0xFFE2E8EE.toInt()
        nosePaint.style = Paint.Style.STROKE
        nosePaint.strokeWidth = dp(3.4f)
        nosePaint.strokeCap = Paint.Cap.ROUND
        nosePaint.color = 0xFFFFB300.toInt()
        rotorPaint.style = Paint.Style.FILL
        rotorPaint.color = 0x904FC3F7.toInt()
        flagPaint.style = Paint.Style.STROKE
        flagPaint.strokeWidth = dp(1.4f)
        textPaint.textSize = dp(11f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        chipPaint.style = Paint.Style.FILL
        homePaint.style = Paint.Style.STROKE
        homePaint.strokeWidth = dp(2f)
        homePaint.color = 0xFF66BB6A.toInt()
        wingPaint.style = Paint.Style.FILL
        wingPaint.color = 0xE6DDE6EE.toInt()
        tailPaint.style = Paint.Style.FILL
        tailPaint.color = 0xE69FB2C4.toInt()
        bodyFill.style = Paint.Style.FILL
        bodyFill.color = 0xFFF4F7FA.toInt()
        noseFill.style = Paint.Style.FILL
        noseFill.color = 0xFFFFB300.toInt()
        headPaint.style = Paint.Style.STROKE
        headPaint.strokeWidth = dp(2f)
        headPaint.strokeCap = Paint.Cap.ROUND
        headPaint.color = 0xCCFFB300.toInt()
        arrowPaint.style = Paint.Style.FILL
        arrowPaint.color = 0x70FFB300
        tipPaint.style = Paint.Style.STROKE
        tipPaint.strokeWidth = dp(4f)
        tipPaint.strokeCap = Paint.Cap.ROUND
        hazePaint.style = Paint.Style.STROKE
        hazePaint.strokeWidth = dp(1.5f)
        hazePaint.color = 0x80BBD6EC.toInt()
    }

    private fun dp(v: Float): Float = v * density

    // ------------------------------------------------------------------ what the screen tells it

    /** The flight to show; null when the log holds no position. */
    fun bind(p: FlightPath?) {
        path = if (p != null && p.n >= 2) p else null
        val pp = path
        if (pp != null) {
            ribbon = pp.polyline(RIBBON_STEP_MS)
            val points = ribbon.size / 4
            for (i in 0 until BAND_COUNT) bands[i] = FloatArray(points * 4)
            futureLines = FloatArray(points * 4)
            cam.userDistance = (pp.maxRangeM * 0.25).coerceIn(30.0, 150.0)
            renderMs = pp.startMs().toDouble()
        } else {
            ribbon = FloatArray(0)
        }
        cam.mode = Cam3D.CHASE
        cam.autoOrbit = true
        snapNext = true
        playing = false
        postInvalidateOnAnimation()
    }

    /** Called on every tick of the replay: where the replay is, and whether it runs. */
    fun setClock(positionMs: Double, isPlaying: Boolean, speedFactor: Int) {
        engineMs = positionMs
        engineNano = System.nanoTime()
        playing = isPlaying
        speed = speedFactor
    }

    /** After a seek: the picture and the camera go straight to the new place. */
    fun jump(positionMs: Double) {
        renderMs = positionMs
        engineMs = positionMs
        engineNano = System.nanoTime()
        snapNext = true
        cam.reset()
    }

    fun cameraMode(): Int = cam.mode

    /** Chase, then orbit, then pilot, then chase again. */
    fun cycleCamera(): Int {
        cam.mode = (cam.mode + 1) % 3
        cam.autoOrbit = true
        cam.reset()
        return cam.mode
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        skyShader = LinearGradient(0f, 0f, 0f, h * 0.5f, intArrayOf(0xFF0E2A4D.toInt(), 0xFF2B6396.toInt(), 0xFF9CC3E0.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        skyPaint.shader = skyShader
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (scaler.isInProgress) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && cam.mode == Cam3D.ORBIT) {
                    cam.autoOrbit = false
                    cam.userAzimuth -= (e.x - lastX) * 0.008
                    cam.userElevation = (cam.userElevation + (e.y - lastY) * 0.006).coerceIn(0.08, 1.45)
                }
                lastX = e.x; lastY = e.y
            }
        }
        return true
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNano == 0L) 0.016 else ((now - lastNano) / 1e9).coerceIn(0.0, 0.25)
        lastNano = now
        val w = width.toFloat()
        val h = height.toFloat()
        val p = path
        if (p == null) {
            canvas.drawRect(0f, 0f, w, h, groundPaint)
            textPaint.color = 0xFFB0BEC5.toInt()
            textPaint.textSize = dp(16f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(tr("No position in this log, so there is no 3D view."), w / 2, h / 2, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = dp(11f)
            return
        }

        advanceClock(p, dt)
        p.sample(renderMs, pose)
        cam.setViewport(w.toDouble(), h.toDouble())
        cam.homeX = 0.0; cam.homeY = 0.0
        if (snapNext) { cam.reset(); snapNext = false }
        cam.update(pose, dt)

        drawSkyAndGround(canvas, w, h)
        drawGrid(canvas)
        drawRibbon(canvas, p)
        drawHome(canvas)
        drawFlags(canvas, p)
        drawAircraft(canvas)
        drawLegend(canvas, w, h)

        if (isAttachedToWindow) postInvalidateOnAnimation()          // the next screen frame: this is what keeps it smooth
    }

    /** The view's own clock: it moves with the screen frames and is eased towards the replay's position, never jerked. */
    private fun advanceClock(p: FlightPath, dt: Double) {
        val sinceTick = if (playing) ((System.nanoTime() - engineNano) / 1e6).coerceIn(0.0, 150.0) * speed else 0.0
        val target = engineMs + sinceTick
        if (!playing) {
            renderMs = target
        } else {
            renderMs += dt * 1000.0 * speed
            val err = target - renderMs
            if (Math.abs(err) > 2500.0 * speed) { renderMs = target; cam.reset() }
            else renderMs += err * Cam3D.alpha(dt, 0.25)
        }
        renderMs = renderMs.coerceIn(p.startMs().toDouble(), p.endMs().toDouble())
    }

    private fun drawSkyAndGround(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        val horizon = (h / 2 + cam.focal() * Math.tan(cam.pitchRad())).toFloat().coerceIn(-h, 2 * h)
        if (horizon < h) canvas.drawRect(0f, horizon.coerceAtLeast(0f), w, h, groundPaint)
        if (horizon > 0f && horizon < h) canvas.drawLine(0f, horizon, w, horizon, hazePaint)
    }

    private fun niceStep(v: Double): Double {
        val steps = doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
        for (s in steps) if (s >= v) return s
        return 1000.0
    }

    private fun drawGrid(canvas: Canvas) {
        val step = niceStep(Math.max(8.0, Math.abs(cam.ez - cam.tz) * 1.2 + Math.hypot(cam.ex - cam.tx, cam.ey - cam.ty) * 0.25))
        val cx = Math.floor(cam.tx / step) * step
        val cy = Math.floor(cam.ty / step) * step
        val n = 14
        val reach = n * step
        for (i in -n..n) {
            val gx = cx + i * step
            val onAxis = Math.abs(gx) < 1e-6
            if (cam.projectSegment(gx, cy - reach, 0.0, gx, cy + reach, 0.0, seg))
                canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), if (onAxis) axisPaint else gridPaint)
            val gy = cy + i * step
            val onAxisY = Math.abs(gy) < 1e-6
            if (cam.projectSegment(cx - reach, gy, 0.0, cx + reach, gy, 0.0, seg))
                canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), if (onAxisY) axisPaint else gridPaint)
        }
    }

    private fun drawRibbon(canvas: Canvas, p: FlightPath) {
        val points = ribbon.size / 4
        if (points < 2) return
        val cur = (((renderMs - p.startMs()) / RIBBON_STEP_MS).toInt()).coerceIn(0, points - 1)
        java.util.Arrays.fill(bandCount, 0)
        futureCount = 0
        val chase = cam.mode == Cam3D.CHASE
        val from = if (chase) Math.max(0, cur - TRAIL_POINTS) else 0               // the chase view shows the last minute and the next 20 s, not the whole flight
        val to = if (chase) Math.min(points - 1, cur + AHEAD_POINTS) else points - 1
        for (i in from until to) {
            val a = i * 4
            val b = a + 4
            if (!cam.projectSegment(ribbon[a].toDouble(), ribbon[a + 1].toDouble(), ribbon[a + 2].toDouble(),
                    ribbon[b].toDouble(), ribbon[b + 1].toDouble(), ribbon[b + 2].toDouble(), seg)) continue
            if (i >= cur) {
                val o = futureCount
                futureLines[o] = seg[0].toFloat(); futureLines[o + 1] = seg[1].toFloat(); futureLines[o + 2] = seg[2].toFloat(); futureLines[o + 3] = seg[3].toFloat()
                futureCount += 4
            } else {
                val band = bandOf(ribbon[a + 3].toDouble())
                val arr = bands[band]
                val o = bandCount[band]
                arr[o] = seg[0].toFloat(); arr[o + 1] = seg[1].toFloat(); arr[o + 2] = seg[2].toFloat(); arr[o + 3] = seg[3].toFloat()
                bandCount[band] = o + 4
            }
            if (i % 8 == 0) {                                           // a thin drop line to the ground every two seconds: height at a glance
                if (cam.projectSegment(ribbon[a].toDouble(), ribbon[a + 1].toDouble(), ribbon[a + 2].toDouble(), ribbon[a].toDouble(), ribbon[a + 1].toDouble(), 0.0, seg))
                    canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), dropPaint)
            }
        }
        if (futureCount > 0) canvas.drawLines(futureLines, 0, futureCount, futurePaint)
        for (i in 0 until BAND_COUNT) if (bandCount[i] > 0) canvas.drawLines(bands[i], 0, bandCount[i], bandPaints[i])
    }

    private fun drawHome(canvas: Canvas) {
        val r = 3.0
        var first = true
        tmpPath.reset()
        for (i in 0..20) {
            val a = i * Math.PI * 2 / 20
            if (!cam.project(Math.cos(a) * r, Math.sin(a) * r, 0.0, pt)) { first = true; continue }
            if (first) { tmpPath.moveTo(pt[0].toFloat(), pt[1].toFloat()); first = false } else tmpPath.lineTo(pt[0].toFloat(), pt[1].toFloat())
        }
        canvas.drawPath(tmpPath, homePaint)
        if (cam.project(0.0, 0.0, 6.0, pt2) && cam.project(0.0, 0.0, 0.0, pt)) {
            canvas.drawLine(pt[0].toFloat(), pt[1].toFloat(), pt2[0].toFloat(), pt2[1].toFloat(), homePaint)
            label(canvas, "HOME", pt2[0].toFloat(), pt2[1].toFloat(), 0xFF66BB6A.toInt())
        }
    }

    private fun eventColor(kind: String): Int = when (kind) {
        "mode" -> 0xFFFFB300.toInt()
        "window" -> 0xFFEF5350.toInt()
        "takeoff", "landing" -> 0xFF66BB6A.toInt()
        else -> 0xFF4FC3F7.toInt()
    }

    private fun drawFlags(canvas: Canvas, p: FlightPath) {
        placedCount = 0
        for (e in p.events) {
            val top = Math.max(e.z, 4.0) + 6.0
            if (!cam.project(e.x, e.y, 0.0, pt) || !cam.project(e.x, e.y, top, pt2)) continue
            if (pt[2] > 1500.0) continue
            val col = eventColor(e.kind)
            flagPaint.color = (col and 0x00FFFFFF) or (if (e.ms <= renderMs) 0xB0000000.toInt() else 0x50000000)
            canvas.drawLine(pt[0].toFloat(), pt[1].toFloat(), pt2[0].toFloat(), pt2[1].toFloat(), flagPaint)
            label(canvas, e.label, pt2[0].toFloat(), pt2[1].toFloat(), col)
        }
    }

    /** A small text chip; skipped when it would land on one that is already drawn (a crowded view stays readable). */
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, color: Int) {
        val tw = textPaint.measureText(text)
        val padX = dp(6f)
        val hgt = dp(18f)
        if (x < -dp(40f) || x > width + dp(40f) || y < -dp(40f) || y > height + dp(40f)) return
        val l = (x - tw / 2 - padX).coerceIn(dp(4f), Math.max(dp(4f), width - tw - padX * 2 - dp(4f)))
        val t = (y - hgt - dp(2f)).coerceIn(dp(4f), Math.max(dp(4f), height - hgt - dp(60f)))
        val r = l + tw + padX * 2
        val b = t + hgt
        for (i in 0 until placedCount) {
            val o = i * 4
            if (l < placed[o + 2] && r > placed[o] && t < placed[o + 3] && b > placed[o + 1]) return
        }
        if (placedCount < MAX_LABELS) {
            val o = placedCount * 4
            placed[o] = l; placed[o + 1] = t; placed[o + 2] = r; placed[o + 3] = b
            placedCount++
        }
        chipPaint.color = 0xCC0B1016.toInt()
        canvas.drawRect(l, t, r, b, chipPaint)
        textPaint.color = color
        canvas.drawText(text, l + padX, b - dp(5f), textPaint)
    }

    private fun drawAircraft(canvas: Canvas) {
        if (model == MODEL_PLANE) { drawPlane(canvas); return }
        val q = pose.q
        if (!cam.project(pose.x, pose.y, pose.z, pt)) return
        val depth = pt[2]
        // the size: true size when it is near, never smaller than a mark you can see
        val metre = Math.max(0.6, dp(12f) / cam.scaleAt(depth))
        val arm = metre
        val cxp = pt[0].toFloat()
        val cyp = pt[1].toFloat()
        drawHeadingMark(canvas, arm * 1.2)
        drawForwardArrow(canvas, arm)
        if (cam.project(pose.x, pose.y, 0.0, pt2)) {
            canvas.drawLine(cxp, cyp, pt2[0].toFloat(), pt2[1].toFloat(), shadowPaint)
            canvas.drawCircle(pt2[0].toFloat(), pt2[1].toFloat(), dp(5f), shadowPaint)
        }
        val corners = arrayOf(doubleArrayOf(-1.0, 1.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(-1.0, -1.0), doubleArrayOf(1.0, -1.0))
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (i in 0 until 4) {
            val v = Quat.rotate(q, corners[i][0] * arm, corners[i][1] * arm, 0.0)
            if (cam.project(pose.x + v[0], pose.y + v[1], pose.z + v[2], pt2)) {
                xs[i] = pt2[0].toFloat(); ys[i] = pt2[1].toFloat()
                canvas.drawLine(cxp, cyp, xs[i], ys[i], if (corners[i][1] > 0) nosePaint else bodyPaint)
                canvas.drawCircle(xs[i], ys[i], Math.max(dp(3f), (cam.scaleAt(pt2[2]) * arm * 0.55).toFloat()), rotorPaint)
            }
        }
        val nose = Quat.rotate(q, 0.0, arm * 2.4, 0.0)
        if (cam.project(pose.x + nose[0], pose.y + nose[1], pose.z + nose[2], pt2))
            canvas.drawLine(cxp, cyp, pt2[0].toFloat(), pt2[1].toFloat(), nosePaint)
    }

    /** A line of the aircraft's body, from one point to another in its own axes (x right, y forward, z up), turned by its attitude. */
    private fun bodyLine(canvas: Canvas, a: Double, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, paint: Paint) {
        val p = Quat.rotate(pose.q, ax * a, ay * a, az * a)
        val r = Quat.rotate(pose.q, bx * a, by * a, bz * a)
        if (cam.projectSegment(pose.x + p[0], pose.y + p[1], pose.z + p[2], pose.x + r[0], pose.y + r[1], pose.z + r[2], seg))
            canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), paint)
    }

    /** A flat shape of the aircraft's body (points x, y, z in its own axes, in order), filled. */
    private fun bodyPoly(canvas: Canvas, a: Double, pts: DoubleArray, paint: Paint) {
        tmpPath.reset()
        var n = 0
        var i = 0
        while (i < pts.size) {
            val r = Quat.rotate(pose.q, pts[i] * a, pts[i + 1] * a, pts[i + 2] * a)
            if (!cam.project(pose.x + r[0], pose.y + r[1], pose.z + r[2], pt2)) return
            if (n == 0) tmpPath.moveTo(pt2[0].toFloat(), pt2[1].toFloat()) else tmpPath.lineTo(pt2[0].toFloat(), pt2[1].toFloat())
            n++
            i += 3
        }
        tmpPath.close()
        canvas.drawPath(tmpPath, paint)
    }

    /** A level arrow ahead of the aircraft along its heading, at its own height: it shows which way the nose points whatever the pitch and the camera. */
    private fun drawForwardArrow(canvas: Canvas, size: Double) {
        val hd = Math.toRadians(pose.headingDeg)
        val fx = Math.sin(hd)
        val fy = Math.cos(hd)
        val len = size * 5.0
        val tx = pose.x + fx * len
        val ty = pose.y + fy * len
        if (cam.projectSegment(pose.x, pose.y, pose.z, tx, ty, pose.z, seg))
            canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), headPaint)
        val bx = tx - fx * size * 1.2
        val by = ty - fy * size * 1.2
        val sx = -fy * size * 0.7
        val sy = fx * size * 0.7
        if (cam.projectSegment(tx, ty, pose.z, bx + sx, by + sy, pose.z, seg))
            canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), headPaint)
        if (cam.projectSegment(tx, ty, pose.z, bx - sx, by - sy, pose.z, seg))
            canvas.drawLine(seg[0].toFloat(), seg[1].toFloat(), seg[2].toFloat(), seg[3].toFloat(), headPaint)
    }

    /** On the ground under the aircraft: a triangle that points the way its nose points. It reads the same from every camera and at every pitch. */
    private fun drawHeadingMark(canvas: Canvas, size: Double) {
        val hd = Math.toRadians(pose.headingDeg)
        val fx = Math.sin(hd)
        val fy = Math.cos(hd)
        val pts = doubleArrayOf(fx * size * 1.6, fy * size * 1.6, -fx * size - fy * size * 0.8, -fy * size + fx * size * 0.8, -fx * size + fy * size * 0.8, -fy * size - fx * size * 0.8)
        tmpPath.reset()
        for (k in 0 until 3) {
            if (!cam.project(pose.x + pts[k * 2], pose.y + pts[k * 2 + 1], 0.0, pt2)) return
            if (k == 0) tmpPath.moveTo(pt2[0].toFloat(), pt2[1].toFloat()) else tmpPath.lineTo(pt2[0].toFloat(), pt2[1].toFloat())
        }
        tmpPath.close()
        canvas.drawPath(tmpPath, arrowPaint)
    }

    /** A fixed-wing: a tapered fuselage with an orange nose cone, a swept wing with a red and a green tip, a tail plane and a fin. */
    private fun drawPlane(canvas: Canvas) {
        if (!cam.project(pose.x, pose.y, pose.z, pt)) return
        val a = Math.max(0.9, dp(15f) / cam.scaleAt(pt[2]))
        drawHeadingMark(canvas, a * 1.2)
        drawForwardArrow(canvas, a)
        if (cam.project(pose.x, pose.y, 0.0, pt2)) canvas.drawLine(pt[0].toFloat(), pt[1].toFloat(), pt2[0].toFloat(), pt2[1].toFloat(), shadowPaint)
        // the tail first, so that the wing and the nose are drawn over it
        bodyPoly(canvas, a, doubleArrayOf(0.0, -1.4, 0.0, 0.0, -1.1, 0.7, 0.0, -0.8, 0.0), tailPaint)                       // fin
        bodyPoly(canvas, a, doubleArrayOf(-0.75, -1.5, 0.0, 0.75, -1.5, 0.0, 0.5, -0.95, 0.0, -0.5, -0.95, 0.0), tailPaint) // tail plane
        bodyPoly(canvas, a, doubleArrayOf(-0.12, -1.5, 0.0, 0.12, -1.5, 0.0, 0.18, 0.7, 0.0, -0.18, 0.7, 0.0), bodyFill)    // fuselage
        bodyPoly(canvas, a, doubleArrayOf(-1.9, 0.0, 0.0, 1.9, 0.0, 0.0, 1.9, 0.45, 0.0, 0.2, 0.75, 0.0, -0.2, 0.75, 0.0, -1.9, 0.45, 0.0), wingPaint)  // wing
        bodyPoly(canvas, a, doubleArrayOf(-0.18, 0.7, 0.0, 0.18, 0.7, 0.0, 0.0, 1.7, 0.0), noseFill)                         // nose cone
        tipPaint.color = 0xFFEF5350.toInt()
        bodyLine(canvas, a, -1.9, 0.2, 0.0, -1.5, 0.2, 0.0, tipPaint)
        tipPaint.color = 0xFF66BB6A.toInt()
        bodyLine(canvas, a, 1.5, 0.2, 0.0, 1.9, 0.2, 0.0, tipPaint)
    }

    private fun drawLegend(canvas: Canvas, w: Float, h: Float) {
        val barW = dp(150f)
        val barH = dp(6f)
        val x = w - barW - dp(12f)
        val y = h - dp(CardStyle.MIN_TOUCH_DP.toFloat()) - dp(26f)
        for (i in 0 until BAND_COUNT) {
            chipPaint.color = rampColor(i * 110.0 / (BAND_COUNT - 1))
            canvas.drawRect(x + barW * i / BAND_COUNT, y, x + barW * (i + 1) / BAND_COUNT, y + barH, chipPaint)
        }
        textPaint.color = 0xFFB0BEC5.toInt()
        textPaint.textSize = dp(10f)
        canvas.drawText("0", x, y - dp(3f), textPaint)
        canvas.drawText("km/h  110+", x + barW - dp(54f), y - dp(3f), textPaint)
        textPaint.textSize = dp(11f)
    }

    companion object {
        const val MODEL_QUAD = 0
        const val MODEL_PLANE = 1
        const val RIBBON_STEP_MS = 250L
        const val BAND_COUNT = 8
        const val MAX_LABELS = 24
        const val TRAIL_POINTS = 240          // 60 s of the ribbon behind the aircraft in the chase view
        const val AHEAD_POINTS = 80           // 20 s in front of it

        private val STOPS = doubleArrayOf(0.0, 35.0, 60.0, 85.0, 110.0)
        private val COLORS = intArrayOf(0xFF4FC3F7.toInt(), 0xFF66BB6A.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF9800.toInt(), 0xFFEF5350.toInt())

        /** Speed in km/h to a colour: blue when slow, green, yellow, orange and red when fast. */
        fun rampColor(kmh: Double): Int {
            val v = kmh.coerceIn(0.0, STOPS[STOPS.size - 1])
            for (i in 1 until STOPS.size) {
                if (v <= STOPS[i]) {
                    val u = ((v - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1])).toFloat()
                    val a = COLORS[i - 1]
                    val b = COLORS[i]
                    return Color.rgb(
                        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
                        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
                        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt())
                }
            }
            return COLORS[COLORS.size - 1]
        }

        fun bandOf(kmh: Double): Int = Math.round(kmh.coerceIn(0.0, 110.0) / 110.0 * (BAND_COUNT - 1)).toInt()
    }
}
