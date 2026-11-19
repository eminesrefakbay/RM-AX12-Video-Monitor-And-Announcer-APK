// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * The page of one flight: eight tiles with the figures that matter, the voltage and the current over the flight, the track seen from
 * above, and the buttons (replay, export, copy the text). It opens from the Summary button of a log.
 */
class SummaryPage(
    private val activity: Activity,
    private val log: FlightLog,
    private val summary: FlightSummary,
    private val moves: List<Manoeuvres.Item>,
    private val actions: Actions,
) {
    interface Actions {
        fun replay()
        fun export()
        fun copyText()
    }

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    private fun ok(v: Double) = !v.isNaN() && !v.isInfinite()
    private lateinit var dialog: Dialog

    fun show() {
        dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(build())
        Immersive.show(dialog)
    }

    private fun n0(v: Double, unit: String) = if (!ok(v)) "-" else Math.round(v).toString() + " " + unit

    private fun build(): View {
        val s = summary
        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(12), dp(20), dp(12))
        root.setBackgroundColor(0xFF0B1016.toInt())

        // ---- head: the title, the flight, and the approval window as a pill on the right
        val head = LinearLayout(activity)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(activity)
        titles.orientation = LinearLayout.VERTICAL
        val title = TextView(activity)
        title.text = tr("Flight summary")
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        titles.addView(title)
        val sub = TextView(activity)
        sub.text = s.started + (if (s.aircraft.isEmpty()) "" else "  ·  " + s.aircraft) + (if (s.pack.isEmpty()) "" else "  ·  " + s.pack)
        sub.textSize = 13f
        sub.setTextColor(CardStyle.LABEL)
        titles.addView(sub)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (ok(s.windowLeftMin)) {
            val pill = TextView(activity)
            val over = s.windowLeftMin < 0
            pill.text = tr("Approval window at the end") + ": " + (if (!over) tr("{0} min left", Math.round(s.windowLeftMin)) else tr("Over by {0} min", Math.round(-s.windowLeftMin)))
            pill.textSize = 13f
            pill.setTextColor(if (over) CardStyle.BAD else CardStyle.ACCENT)
            pill.setPadding(dp(14), dp(6), dp(14), dp(6))
            pill.background = CardStyle.panelBackground(density, 0xFF12202B.toInt(), if (over) CardStyle.BAD else CardStyle.ACCENT, solid = true)
            head.addView(pill)
        }
        root.addView(head)

        // ---- two rows of four tiles
        val limit = 120.0
        val mins = Math.round(s.aboveLimitS)
        val tiles = ArrayList<View>()
        tiles.add(tile(tr("Flight time"), FlightSummary.clock(s.durationMs), tr("Armed to disarmed"), Color.WHITE))
        tiles.add(tile(tr("Highest"), n0(s.maxHeight, "m"), if (s.aboveLimitS >= 1) tr("Above 120 m for {0} s", mins) else "", if (s.aboveLimitS >= 1) CardStyle.WARN else Color.WHITE))
        tiles.add(tile(tr("Farthest"), if (ok(s.maxDist)) (if (s.maxDist >= 1000) String.format(Locale.US, "%.2f km", s.maxDist / 1000.0) else Math.round(s.maxDist).toString() + " m") else "-", tr("From the arming point"), Color.WHITE))
        tiles.add(tile(tr("Top speed"), n0(s.topSpeed, "km/h"), if (ok(s.avgSpeed)) tr("Average {0} km/h", Math.round(s.avgSpeed)) else "", Color.WHITE))
        tiles.add(tile(tr("Battery used"), n0(s.mah, "mAh"),
            (if (ok(s.packPct)) Math.round(s.packPct).toString() + " %" else "") + (if (ok(s.wh)) (if (ok(s.packPct)) "  ·  " else "") + String.format(Locale.US, "%.1f Wh", s.wh) else ""), Color.WHITE))
        val lowC = ok(s.minCell) && s.minCell < 3.3
        tiles.add(tile(tr("Lowest cell"), if (ok(s.minCell)) String.format(Locale.US, "%.2f V", s.minCell) else "-",
            if (s.minCellAtMs >= 0) tr("Under load, at {0}", FlightSummary.clock(s.minCellAtMs.toLong())) else "", if (lowC) CardStyle.WARN else Color.WHITE))
        tiles.add(tile(tr("Peak current"), n0(s.peakCurrent, "A"), if (ok(s.peakPowerW)) tr("Peak power {0} kW", String.format(Locale.US, "%.1f", s.peakPowerW / 1000.0)) else "", Color.WHITE))
        tiles.add(tile(tr("Landing"), if (ok(s.endCell)) String.format(Locale.US, "%.2f V", s.endCell) else "-",
            if (ok(s.packPct)) tr("{0} % left", Math.max(0L, 100L - Math.round(s.packPct))) else tr("Per cell, at the end"),
            if (!ok(s.endCell)) Color.WHITE else if (s.endCell < 3.5) CardStyle.WARN else CardStyle.OK))
        for (r in 0..1) {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            for (i in 0..3) row.addView(tiles[r * 4 + i], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.rightMargin = dp(if (i < 3) 10 else 0) })
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })
        }

        // ---- the chart, the track and the buttons
        val bottom = LinearLayout(activity)
        bottom.orientation = LinearLayout.HORIZONTAL
        val chartBox = box(tr("VOLTAGE AND CURRENT"), ChartView(activity, log, s.cells))
        val trackBox = box(tr("TRACK"), TrackView(activity, FlightPath.build(log), moves))
        bottom.addView(chartBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f).also { it.rightMargin = dp(10) })
        bottom.addView(trackBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also { it.rightMargin = dp(10) })
        val buttons = LinearLayout(activity)
        buttons.orientation = LinearLayout.VERTICAL
        fun button(text: String, primary: Boolean, click: () -> Unit) {
            val b = Button(activity)
            b.text = text
            b.isAllCaps = false
            b.textSize = 15f
            b.setTypeface(null, Typeface.BOLD)
            b.setTextColor(if (primary) 0xFF0B1016.toInt() else Color.WHITE)
            b.background = if (primary) CardStyle.panelBackground(density, CardStyle.ACCENT, CardStyle.ACCENT, solid = true) else CardStyle.buttonBackground(density)
            b.setOnClickListener { click() }
            buttons.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CardStyle.MIN_TOUCH_DP)).also { it.bottomMargin = dp(8) })
        }
        button(tr("Replay in 3D"), true) { dialog.dismiss(); actions.replay() }
        button(tr("Export..."), false) { actions.export() }
        button(tr("Copy the report text"), false) { actions.copyText() }
        buttons.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        val mv = TextView(activity)
        mv.text = tr("{0} dives, {1} rolls, {2} flips", Manoeuvres.count(moves, Manoeuvres.DIVE), Manoeuvres.count(moves, Manoeuvres.ROLL), Manoeuvres.count(moves, Manoeuvres.FLIP))
        mv.textSize = 12f
        mv.setTextColor(CardStyle.LABEL)
        mv.setPadding(0, 0, 0, dp(8))
        buttons.addView(mv)
        button(tr("Close"), false) { dialog.dismiss() }
        bottom.addView(buttons, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also { it.topMargin = dp(10) })
        val scroll = ScrollView(activity)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(0xFF0B1016.toInt())
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return scroll
    }

    private fun tile(label: String, value: String, note: String, valueColor: Int): View {
        val t = LinearLayout(activity)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        t.background = CardStyle.panelBackground(density, 0xFF151D26.toInt(), CardStyle.ACCENT, solid = true)
        val l = TextView(activity)
        l.text = label.uppercase(Locale.getDefault())
        l.textSize = 11f
        l.setTextColor(CardStyle.LABEL)
        t.addView(l)
        val v = TextView(activity)
        v.text = value
        v.textSize = 30f
        v.setTypeface(null, Typeface.BOLD)
        v.setTextColor(valueColor)
        t.addView(v)
        val n = TextView(activity)
        n.text = if (note.isEmpty()) " " else note
        n.textSize = 12f
        n.setTextColor(CardStyle.LABEL)
        t.addView(n)
        return t
    }

    private fun box(title: String, content: View): View {
        val b = LinearLayout(activity)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(dp(14), dp(10), dp(14), dp(10))
        b.background = CardStyle.panelBackground(density, 0xFF151D26.toInt(), CardStyle.ACCENT, solid = true)
        val l = TextView(activity)
        l.text = title
        l.textSize = 11f
        l.setTextColor(CardStyle.LABEL)
        b.addView(l)
        b.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also { it.topMargin = dp(6) })
        return b
    }

    /** Volts per cell (green) and current (orange) over the flight, with the 3.30 V line. */
    private class ChartView(context: Context, private val log: FlightLog, cells: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val t = ArrayList<Long>()
        private val volt = ArrayList<Double>()
        private val amp = ArrayList<Double>()

        init {
            val cCell = log.indexOf("cell")
            val cV = log.indexOf("vbat")
            val cI = log.indexOf("curr")
            val step = Math.max(1, log.rows / 300)
            var r = 0
            while (r < log.rows) {
                val v = if (cCell >= 0) log.value(r, cCell) else if (cV >= 0 && cells > 0) log.value(r, cV) / cells else Double.NaN
                t.add(log.time[r])
                volt.add(if (!v.isNaN() && v > 1.0) v else Double.NaN)
                amp.add(if (cI >= 0) log.value(r, cI) else Double.NaN)
                r += step
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (t.size < 2) return
            val d = resources.displayMetrics.density
            val t0 = t[0]
            val t1 = t[t.size - 1]
            fun x(ms: Long): Float = ((ms - t0).toDouble() / Math.max(1L, t1 - t0) * w).toFloat()
            var vMin = Double.MAX_VALUE
            var vMax = -Double.MAX_VALUE
            var aMax = 1.0
            for (i in t.indices) {
                if (!volt[i].isNaN()) { vMin = Math.min(vMin, volt[i]); vMax = Math.max(vMax, volt[i]) }
                if (!amp[i].isNaN()) aMax = Math.max(aMax, amp[i])
            }
            vMin = Math.min(vMin, 3.3) - 0.05
            if (vMax <= vMin) vMax = vMin + 1.0
            fun yv(v: Double): Float = (h - 4f - ((v - vMin) / (vMax - vMin) * (h - 8f))).toFloat()
            fun ya(a: Double): Float = (h - 4f - (a / aMax * (h - 8f))).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * d
            paint.color = 0xFFEF5350.toInt()
            paint.pathEffect = DashPathEffect(floatArrayOf(6f * d, 4f * d), 0f)
            canvas.drawLine(0f, yv(3.3), w, yv(3.3), paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.textSize = 11f * d
            canvas.drawText("3.30 V", 4f * d, yv(3.3) - 3f * d, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * d
            for (series in 0..1) {
                paint.color = if (series == 0) CardStyle.OK else 0xFFEF7C4F.toInt()
                var px = 0f
                var py = 0f
                var have = false
                for (i in t.indices) {
                    val v = if (series == 0) volt[i] else amp[i]
                    if (v.isNaN()) { have = false; continue }
                    val cx = x(t[i])
                    val cy = if (series == 0) yv(v) else ya(v)
                    if (have) canvas.drawLine(px, py, cx, cy, paint)
                    px = cx; py = cy; have = true
                }
            }
            paint.style = Paint.Style.FILL
            paint.textSize = 11f * d
            paint.color = CardStyle.OK
            canvas.drawText(String.format(Locale.US, "%.2f V", vMax), w - 60f * d, 12f * d, paint)
            paint.color = 0xFFEF7C4F.toInt()
            canvas.drawText(String.format(Locale.US, "%.0f A", aMax), w - 60f * d, 26f * d, paint)
        }
    }

    /** The path seen from above, the arming point as a green dot, and the places of the dives marked. */
    private class TrackView(context: Context, private val path: FlightPath, private val moves: List<Manoeuvres.Item>) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (path.n < 2) return
            val d = resources.displayMetrics.density
            var r = 10.0
            for (i in 0 until path.n) r = Math.max(r, Math.max(Math.abs(path.x[i]), Math.abs(path.y[i])))
            val scale = (Math.min(w, h) / 2f - 8f * d) / r
            val cx = w / 2f
            val cy = h / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * d
            paint.color = 0xFF37474F.toInt()
            canvas.drawCircle(cx, cy, (r * scale).toFloat(), paint)
            canvas.drawCircle(cx, cy, (r * scale / 2).toFloat(), paint)
            paint.color = CardStyle.ACCENT
            paint.strokeWidth = 2f * d
            for (i in 1 until path.n) {
                canvas.drawLine((cx + path.x[i - 1] * scale).toFloat(), (cy - path.y[i - 1] * scale).toFloat(), (cx + path.x[i] * scale).toFloat(), (cy - path.y[i] * scale).toFloat(), paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = CardStyle.OK
            canvas.drawCircle(cx, cy, 5f * d, paint)
            paint.color = 0xFF90A4AE.toInt()
            paint.textSize = 11f * d
            canvas.drawText(Math.round(r).toString() + " m", cx + 4f * d, cy - (r * scale).toFloat() + 12f * d, paint)
        }
    }
}
