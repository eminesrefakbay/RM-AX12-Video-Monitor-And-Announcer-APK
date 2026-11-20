// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * What a replay adds to the screen: a dark backdrop where the video was, a small REPLAY tag, a player bar along the bottom and a rail of
 * buttons on the right. The bar is a video player's: a scrub bar over the whole width with a mark for every event, then Stop, the previous
 * and next event, 10 seconds back and forward, Play or Pause, the time, the speed and Loop. The rail holds what is not about time: Close,
 * the camera, the aircraft, the colour of the trail, the map under the flight, the cards and a snapshot. The cards themselves are the ones of the live flight.
 */
class ReplayOverlay(private val activity: Activity, private val actions: Actions) {
    /** What the buttons do; the screen implements it. */
    interface Actions {
        fun toggle()
        fun stop()
        fun speed(index: Int)
        fun seek(fraction: Double)
        fun step(deltaMs: Long)
        fun previousMarker()
        fun nextMarker()
        fun loop(on: Boolean)
        fun close()
        fun camera(mode: Int)
        fun model(model: Int)
        fun trail(mode: Int)
        fun cards(show: Boolean)
        fun map(index: Int)
        fun snapshot()
        fun jumpTo(ms: Long)
    }

    val backdrop = View(activity)
    val tag = TextView(activity)
    val bar = LinearLayout(activity)
    private val rail = LinearLayout(activity)
    private val railA = LinearLayout(activity)          // the left column, used only when the buttons do not fit in the height above the bar
    private val railB = LinearLayout(activity)          // the right column: all of the buttons, or the first half
    private val railButtons = ArrayList<View>()
    private var railSig = ""
    private var frame: FrameLayout? = null
    private val menu = LinearLayout(activity)
    private val scrub = ScrubView(activity)
    private val timeView = TextView(activity)
    private val playButton = ImageView(activity)
    private val loopButton = ImageView(activity)
    private val speedViews = ArrayList<TextView>()
    private val cameraRail = ImageView(activity)
    private val modelRail = ImageView(activity)
    private val trailRail = ImageView(activity)
    private val cardsRail = ImageView(activity)
    private val mapRail = ImageView(activity)
    private var mapIndex = 1                 // 0 off, 1 street map, 2 satellite, 3 terrain, 4 the custom address
    private var mapCustom = false
    private val movesRail = ImageView(activity)
    private val movesPanel = LinearLayout(activity)
    private val movesChips = LinearLayout(activity)
    private val diveCard = LinearLayout(activity)
    private var moves: List<Manoeuvres.Item> = emptyList()
    private var logTimes = LongArray(0)
    private var logHeights = DoubleArray(0)
    private var movesFilter: String? = null
    private var selectedMove = -1
    private var movesMissing = ""
    private var movesScrollY = 0
    private val rowViews = ArrayList<Pair<Int, View>>()
    private val density = activity.resources.displayMetrics.density
    private var held = false
    private var lastSeekAt = 0L
    private var loopOn = false
    private var cardsOn = true
    private var playing = true
    private var speedIndex = ReplayEngine.NORMAL_SPEED
    private var camera = 0
    private var model = 0
    private var trailMode = 0
    private var has3d = false

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private fun icon(name: String, color: Int = Color.WHITE, size: Int = 24) = Icons.drawable(activity, name, color, size)

    /** A square button with an icon, in the look of the other buttons of the app. */
    private fun iconButton(v: ImageView, name: String, size: Int = CardStyle.MIN_TOUCH_DP, click: () -> Unit): ImageView {
        v.setImageDrawable(icon(name))
        v.scaleType = ImageView.ScaleType.CENTER
        v.background = CardStyle.buttonBackground(density)
        v.isClickable = true
        v.setOnClickListener { touch(); click() }
        return v
    }

    private fun squareParams(sizeDp: Int, right: Int = 8): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(sizeDp), dp(CardStyle.MIN_TOUCH_DP)).also { it.rightMargin = dp(right) }

    init {
        backdrop.setBackgroundColor(0xFF0B1016.toInt())
        backdrop.visibility = View.GONE
        backdrop.isClickable = true                       // a touch on the backdrop must not reach anything under it
        backdrop.setOnClickListener { touch() }

        tag.setTextColor(Color.WHITE)
        tag.textSize = 13f
        tag.setTypeface(null, Typeface.BOLD)
        tag.setBackgroundColor(0xCCC62828.toInt())
        tag.setPadding(dp(12), dp(4), dp(12), dp(4))
        tag.visibility = View.GONE

        buildBar()
        buildRail()

        menu.orientation = LinearLayout.VERTICAL
        menu.background = CardStyle.panelBackground(density, CardStyle.PANEL, solid = true)
        menu.setPadding(dp(6), dp(6), dp(6), dp(6))
        menu.visibility = View.GONE
        menu.isClickable = true
        menu.elevation = dp(18).toFloat()

        movesPanel.orientation = LinearLayout.VERTICAL
        movesPanel.background = CardStyle.panelBackground(density, CardStyle.PANEL, solid = true)
        movesPanel.setPadding(dp(10), dp(8), dp(10), dp(8))
        movesPanel.visibility = View.GONE
        movesPanel.isClickable = true
        movesPanel.elevation = dp(17).toFloat()
        diveCard.orientation = LinearLayout.VERTICAL
        diveCard.background = CardStyle.panelBackground(density, CardStyle.PANEL, 0xFFEF7C4F.toInt(), solid = true)
        diveCard.setPadding(dp(12), dp(8), dp(12), dp(8))
        diveCard.visibility = View.GONE
        diveCard.isClickable = true
        diveCard.elevation = dp(17).toFloat()
    }

    // ------------------------------------------------------------------ the manoeuvres

    /** The dives, rolls and flips of the flight, and the log's heights (for the chart of a dive). The button shows only when there are some. */
    fun setMoves(items: List<Manoeuvres.Item>, times: LongArray, heights: DoubleArray, missing: String = "") {
        moves = items
        movesMissing = missing
        logTimes = times
        logHeights = heights
        movesFilter = null
        selectedMove = -1
        movesRail.alpha = if (items.isEmpty()) 0.55f else 1f
        movesPanel.visibility = View.GONE
        diveCard.visibility = View.GONE
    }

    private fun moveColor(kind: String): Int = when (kind) {
        Manoeuvres.DIVE -> 0xFFEF7C4F.toInt()
        Manoeuvres.ROLL -> 0xFF4FC3F7.toInt()
        else -> 0xFFB388FF.toInt()
    }

    private fun kindName(kind: String): String = when (kind) {
        Manoeuvres.DIVE -> tr("Dive")
        Manoeuvres.ROLL -> tr("Roll")
        else -> tr("Flip")
    }

    private fun toggleMoves() {
        if (movesPanel.visibility == View.VISIBLE) {
            movesPanel.visibility = View.GONE
            diveCard.visibility = View.GONE
            return
        }
        closeMenu()
        movesScrollY = 0
        buildMoves()
        movesPanel.visibility = View.VISIBLE
        touch()
    }

    private fun buildMoves() {
        movesPanel.removeAllViews()
        rowViews.clear()
        val head = TextView(activity)
        head.text = tr("MANOEUVRES  {0}", moves.size)
        head.textSize = 12f
        head.setTextColor(CardStyle.LABEL)
        movesPanel.addView(head)
        if (moves.isEmpty()) {
            val none = TextView(activity)
            none.text = if (movesMissing.isEmpty()) tr("No dives, rolls or flips were found in this flight.") else if (movesMissing == "height") tr("This log has no height values, so manoeuvres cannot be found. Log them (Settings > Flight logs) to see them in later flights.") else tr("This log has no pitch and roll values, so manoeuvres cannot be found. Log them (Settings > Flight logs) to see them in later flights.")
            none.textSize = 14f
            none.setTextColor(Color.WHITE)
            none.setPadding(0, dp(10), 0, dp(10))
            movesPanel.addView(none)
            return
        }
        movesChips.removeAllViews()
        movesChips.orientation = LinearLayout.HORIZONTAL
        val kinds = arrayOf<String?>(null, Manoeuvres.DIVE, Manoeuvres.ROLL, Manoeuvres.FLIP)
        for (k in kinds) {
            val c = TextView(activity)
            val n = if (k == null) moves.size else Manoeuvres.count(moves, k)
            c.text = (if (k == null) tr("All") else kindName(k)) + " " + n
            c.textSize = 13f
            c.gravity = Gravity.CENTER
            c.setPadding(dp(10), 0, dp(10), 0)
            val on = movesFilter == k
            c.setTextColor(if (on) 0xFF0B1016.toInt() else Color.WHITE)
            c.setBackgroundColor(if (on) CardStyle.ACCENT else 0xFF243447.toInt())
            c.setOnClickListener { touch(); movesFilter = k; movesScrollY = 0; buildMoves() }
            movesChips.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).also { it.rightMargin = dp(6); it.topMargin = dp(6) })
        }
        movesPanel.addView(movesChips)
        // a new list every time: the old one still sits inside the old scroll view, and a view with a parent cannot be added again
        val movesList = LinearLayout(activity)
        movesList.orientation = LinearLayout.VERTICAL
        for (i in moves.indices) {
            val m = moves[i]
            if (movesFilter != null && m.kind != movesFilter) continue
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(6), 0, dp(6), 0)
            if (i == selectedMove) row.setBackgroundColor(0x5516506F)
            val dot = View(activity)
            dot.setBackgroundColor(moveColor(m.kind))
            row.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).also { it.rightMargin = dp(10) })
            val t = TextView(activity)
            t.text = LogText.duration(m.startMs)
            t.textSize = 14f
            t.setTextColor(CardStyle.LABEL)
            row.addView(t, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
            val k = TextView(activity)
            k.text = kindName(m.kind)
            k.textSize = 14f
            k.setTypeface(null, Typeface.BOLD)
            k.setTextColor(Color.WHITE)
            row.addView(k, LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT))
            val d = TextView(activity)
            d.text = m.detail()
            d.textSize = 13f
            d.setTextColor(0xFFB7C4D1.toInt())
            row.addView(d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rowViews.add(Pair(i, row))
            row.setOnClickListener {
                touch()
                selectedMove = i
                // only the highlight changes (the list is not built again), and the seek, which rebuilds the cards from the start, waits for the next frame so the tap shows at once
                for (p in rowViews) p.second.setBackgroundColor(if (p.first == i) 0x5516506F else Color.TRANSPARENT)
                if (m.kind == Manoeuvres.DIVE) showDive(m) else diveCard.visibility = View.GONE
                movesPanel.post { actions.jumpTo(Math.max(0L, m.startMs - 2000L)) }
            }
            movesList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CardStyle.MIN_TOUCH_DP)))
        }
        val shown = if (movesFilter == null) moves.size else Manoeuvres.count(moves, movesFilter!!)
        val many = shown * CardStyle.MIN_TOUCH_DP > 240
        // a long list: the panel takes the height of the screen (between the top and the player bar) and the list scrolls inside it
        val lp = movesPanel.layoutParams as FrameLayout.LayoutParams
        lp.height = if (many) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        movesPanel.layoutParams = lp
        val scroll = android.widget.ScrollView(activity)
        scroll.addView(movesList)
        scroll.isVerticalScrollBarEnabled = true
        scroll.isScrollbarFadingEnabled = false
        scroll.scrollBarSize = dp(6)
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> movesScrollY = y; touch() }
        val step = dp(CardStyle.MIN_TOUCH_DP) * 3
        if (many) {
            // two buttons for the finger that does not want to swipe: a page up and a page down
            val pager = LinearLayout(activity)
            pager.orientation = LinearLayout.HORIZONTAL
            for (dir in intArrayOf(-1, 1)) {
                val arrow = object : View(activity) {
                    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(canvas: Canvas) {
                        p.color = Color.WHITE
                        p.style = Paint.Style.FILL
                        val w = width.toFloat(); val h = height.toFloat(); val u = Math.min(w, h) * 0.22f
                        val path = android.graphics.Path()
                        if (dir < 0) { path.moveTo(w / 2, h / 2 - u); path.lineTo(w / 2 + u * 1.4f, h / 2 + u); path.lineTo(w / 2 - u * 1.4f, h / 2 + u) }
                        else { path.moveTo(w / 2, h / 2 + u); path.lineTo(w / 2 + u * 1.4f, h / 2 - u); path.lineTo(w / 2 - u * 1.4f, h / 2 - u) }
                        path.close()
                        canvas.drawPath(path, p)
                    }
                }
                arrow.setBackgroundColor(0xFF243447.toInt())
                arrow.setOnClickListener { touch(); scroll.smoothScrollBy(0, dir * step) }
                pager.addView(arrow, LinearLayout.LayoutParams(0, dp(40), 1f).also { it.rightMargin = dp(if (dir < 0) 6 else 0); it.topMargin = dp(6) })
            }
            movesPanel.addView(pager)
        }
        movesPanel.addView(scroll, if (many) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also { it.topMargin = dp(6) }
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val keep = movesScrollY
        scroll.post { scroll.scrollTo(0, keep) }
        val note = TextView(activity)
        note.text = tr("Found from the height and the attitude in the log. A quick roll or flip in a slow log can be missed.")
        note.textSize = 11f
        note.setTextColor(CardStyle.LABEL)
        movesPanel.addView(note)
    }

    /** The height of a dive and the figures of it, on a card at the left. */
    private fun showDive(m: Manoeuvres.Item) {
        diveCard.removeAllViews()
        val title = TextView(activity)
        title.text = tr("DIVE REPORT  {0}", LogText.duration(m.startMs))
        title.textSize = 12f
        title.setTextColor(0xFFEF7C4F.toInt())
        diveCard.addView(title)
        diveCard.addView(DiveChart(activity, logTimes, logHeights, m), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)).also { it.topMargin = dp(6) })
        fun row(label: String, value: String) {
            val r = LinearLayout(activity)
            val l = TextView(activity)
            l.text = label
            l.textSize = 14f
            l.setTextColor(0xFFB7C4D1.toInt())
            val v = TextView(activity)
            v.text = value
            v.textSize = 15f
            v.setTypeface(null, Typeface.BOLD)
            v.setTextColor(Color.WHITE)
            r.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(v)
            diveCard.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))
        }
        fun n0(v: Double, unit: String) = if (v.isNaN()) "-" else Math.round(v).toString() + " " + unit
        row(tr("Start"), n0(m.startH, "m"))
        row(tr("Bottom"), n0(m.bottomH, "m"))
        row(tr("Height lost"), n0(m.startH - m.bottomH, "m"))
        row(tr("Dive angle"), if (m.angleDeg.isNaN()) "-" else Math.round(m.angleDeg).toString() + " \u00B0")
        row(tr("Top speed"), n0(m.maxSpeed, "km/h"))
        row(tr("Fall time"), String.format(java.util.Locale.US, "%.1f s", m.fallS))
        row(tr("Recovery"), String.format(java.util.Locale.US, "%.1f s", m.recoverS))
        row(tr("Peak current"), n0(m.peakCurrent, "A"))
        diveCard.visibility = View.VISIBLE
    }

    /** The height around a dive: a line, the ground, the bottom marked. */
    private class DiveChart(context: android.content.Context, private val t: LongArray, private val h: DoubleArray, private val m: Manoeuvres.Item) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val ht = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.color = 0xFF10171F.toInt()
            canvas.drawRect(0f, 0f, w, ht, paint)
            val from = m.startMs - 3000
            val to = m.endMs + 4000
            var top = 10.0
            for (i in t.indices) if (t[i] in from..to && !h[i].isNaN() && h[i] > top) top = h[i]
            top *= 1.1
            fun x(ms: Long): Float = ((ms - from).toDouble() / (to - from) * w).toFloat()
            fun y(v: Double): Float = (ht - 4f - (Math.max(0.0, v) / top * (ht - 8f))).toFloat()
            paint.color = 0xFFEF5350.toInt()
            paint.strokeWidth = 1f
            canvas.drawLine(0f, y(0.0), w, y(0.0), paint)
            paint.color = CardStyle.ACCENT
            paint.strokeWidth = 3f
            var px = 0f
            var py = 0f
            var have = false
            for (i in t.indices) {
                if (t[i] < from || t[i] > to || h[i].isNaN()) continue
                val cx = x(t[i])
                val cy = y(h[i])
                if (have) canvas.drawLine(px, py, cx, cy, paint)
                px = cx; py = cy; have = true
            }
            paint.color = 0xFFEF7C4F.toInt()
            canvas.drawCircle(x(m.endMs), y(m.bottomH), 6f, paint)
        }
    }

    private fun buildBar() {
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(CardStyle.PANEL)
        bar.visibility = View.GONE
        bar.isClickable = true
        bar.elevation = dp(16).toFloat()

        scrub.onScrub = { f, last ->
            touch()
            val now = SystemClock.elapsedRealtime()
            if (last || now - lastSeekAt >= SCRUB_MS) {          // a seek rebuilds the cards from the start: not on every pixel of a drag
                lastSeekAt = now
                actions.seek(f)
            }
        }
        scrub.onHold = { h -> held = h; touch() }
        bar.addView(scrub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))

        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), 0, dp(12), dp(6))
        bar.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun add(name: String, widthDp: Int, click: () -> Unit): ImageView {
            val b = iconButton(ImageView(activity), name, click = click)
            row.addView(b, squareParams(widthDp))
            return b
        }
        add("stop", 52) { actions.stop() }
        add("skip_prev", 52) { actions.previousMarker() }
        add("back10", 52) { actions.step(-STEP_MS) }
        iconButton(playButton, "pause") { actions.toggle() }
        playButton.setImageDrawable(icon("pause", Color.WHITE, 28))
        row.addView(playButton, squareParams(72))
        add("fwd10", 52) { actions.step(STEP_MS) }
        add("skip_next", 52) { actions.nextMarker() }

        timeView.setTextColor(Color.WHITE)
        timeView.textSize = 14f
        timeView.minWidth = dp(120)
        timeView.gravity = Gravity.CENTER_VERTICAL
        row.addView(timeView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(CardStyle.MIN_TOUCH_DP)).also { it.leftMargin = dp(4) })

        row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))        // pushes the speed and Loop to the right

        for (i in ReplayEngine.SPEEDS.indices) {
            val t = TextView(activity)
            val v = ReplayEngine.SPEEDS[i]
            t.text = tr("{0}x", if (v == Math.floor(v)) v.toInt().toString() else v.toString())
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.isClickable = true
            t.setOnClickListener { touch(); actions.speed(i) }
            speedViews.add(t)
            row.addView(t, LinearLayout.LayoutParams(dp(52), dp(CardStyle.MIN_TOUCH_DP)))
        }
        paintSpeeds()
        iconButton(loopButton, "loop") { loopOn = !loopOn; paintToggles(); actions.loop(loopOn) }
        row.addView(loopButton, squareParams(52, 0).also { it.leftMargin = dp(12) })
        paintToggles()
    }

    private fun railButton(v: ImageView, name: String, click: () -> Unit) {
        iconButton(v, name, click = click)
        railButtons.add(v)
    }

    /**
     * Puts the visible rail buttons in one column, or in two when [perColumn] of them do not fit in the height above the player bar, so the rail
     * never reaches down onto the bar.
     */
    private fun placeRail(perColumn: Int) {
        val vis = railButtons.filter { it.visibility != View.GONE }
        val two = vis.size > perColumn
        val first = if (two) (vis.size + 1) / 2 else vis.size
        val sig = vis.size.toString() + "/" + first
        if (sig == railSig) return
        railSig = sig
        railA.removeAllViews()
        railB.removeAllViews()
        for (i in vis.indices) {
            (if (i < first) railB else railA).addView(vis[i], LinearLayout.LayoutParams(dp(CardStyle.MIN_TOUCH_DP), dp(CardStyle.MIN_TOUCH_DP)).also { it.bottomMargin = dp(4) })
        }
        railA.visibility = if (two) View.VISIBLE else View.GONE
    }

    /** Fits the rail, the moves list and the dive card to the room between the top of the screen and the player bar. */
    private fun layoutRail() {
        val f = frame ?: return
        if (f.height == 0) return
        val barH = if (bar.height > 0) bar.height else dp(100)
        val avail = f.height - dp(10) - dp(4) - barH - dp(8)
        placeRail(Math.max(2, avail / dp(CardStyle.MIN_TOUCH_DP + 4)))
        val two = railA.visibility == View.VISIBLE
        val rl = rail.layoutParams as FrameLayout.LayoutParams
        if (rl.bottomMargin != barH + dp(8)) { rl.bottomMargin = barH + dp(8); rail.layoutParams = rl }
        val ml = movesPanel.layoutParams as FrameLayout.LayoutParams
        val right = dp(26 + (if (two) 108 else 56))
        val bottom = barH + dp(8)
        if (ml.rightMargin != right || ml.bottomMargin != bottom) { ml.rightMargin = right; ml.bottomMargin = bottom; movesPanel.layoutParams = ml }
    }

    private fun buildRail() {
        rail.orientation = LinearLayout.HORIZONTAL
        rail.setPadding(dp(4), dp(4), dp(4), dp(0))
        railA.orientation = LinearLayout.VERTICAL
        railB.orientation = LinearLayout.VERTICAL
        railA.visibility = View.GONE
        rail.addView(railA, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.rightMargin = dp(4) })
        rail.addView(railB, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rail.visibility = View.GONE
        rail.elevation = dp(16).toFloat()
        railButton(ImageView(activity), "close") { actions.close() }
        railButton(cameraRail, "camera") { openMenu(cameraRail, listOf(tr("Chase"), tr("Orbit"), tr("Pilot"), tr("Top")), camera) { actions.camera(it) } }
        railButton(modelRail, "aircraft") { openMenu(modelRail, listOf(tr("Quad"), tr("Plane")), model) { actions.model(it) } }
        railButton(trailRail, "trail") { openMenu(trailRail, listOf(tr("Speed"), tr("Height")), trailMode) { actions.trail(it) } }
        railButton(mapRail, "map") {
            val labels = arrayListOf(tr("Off"), tr("Street map"), tr("Satellite"), tr("Terrain"))
            if (mapCustom) labels.add(tr("Custom address"))
            openMenu(mapRail, labels, mapIndex) { actions.map(it) }
        }
        railButton(cardsRail, "cards") { cardsOn = !cardsOn; paintToggles(); actions.cards(cardsOn) }
        railButton(movesRail, "moves") { toggleMoves() }
        railButton(ImageView(activity), "snapshot") { closeMenu(); actions.snapshot() }
        placeRail(railButtons.size)
    }

    /** A short list of choices beside a rail button; a pick applies and closes it. */
    private fun openMenu(anchor: View, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val wasOpen = menu.visibility == View.VISIBLE && menu.tag === anchor
        closeMenu()
        if (wasOpen) return
        menu.tag = anchor
        menu.removeAllViews()
        for (i in labels.indices) {
            val t = TextView(activity)
            t.text = labels[i]
            t.textSize = 15f
            t.gravity = Gravity.CENTER_VERTICAL
            t.setPadding(dp(16), 0, dp(16), 0)
            t.minWidth = dp(120)
            t.setTextColor(if (i == selected) CardStyle.ACCENT else Color.WHITE)
            t.setTypeface(null, if (i == selected) Typeface.BOLD else Typeface.NORMAL)
            t.background = CardStyle.buttonBackground(density)
            t.isClickable = true
            t.setOnClickListener { touch(); closeMenu(); onPick(i) }
            menu.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CardStyle.MIN_TOUCH_DP)).also { it.bottomMargin = dp(if (i == labels.size - 1) 0 else 4) })
        }
        val lp = menu.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = (rail.top + ((anchor.parent as? View)?.top ?: 0) + anchor.top).coerceAtLeast(0)
        lp.rightMargin = rail.width + dp(16)               // the rail itself sits 8 dp from the edge
        menu.layoutParams = lp
        menu.visibility = View.VISIBLE
        touch()
    }

    private fun closeMenu() {
        menu.visibility = View.GONE
        menu.tag = null
    }

    /** Adds the views to the frame: the backdrop under everything that draws, the tag, the rail, the bar and the menu on top. */
    fun attach(frame: FrameLayout, backdropIndex: Int) {
        this.frame = frame
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> frame.post { layoutRail() } }
        frame.addView(backdrop, backdropIndex, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(tag, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).also { it.setMargins(dp(10), dp(10), 0, 0) })
        frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        frame.addView(rail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).also { it.setMargins(0, dp(10), dp(8), dp(104)) })
        frame.addView(menu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
        frame.addView(movesPanel, FrameLayout.LayoutParams(dp(400), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).also { it.setMargins(0, dp(10), dp(82), dp(112)) })
        frame.addView(diveCard, FrameLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.TOP).also { it.setMargins(dp(10), dp(52), 0, 0) })
    }

    fun show(title: String) {
        tag.text = title
        backdrop.visibility = View.VISIBLE
        tag.visibility = View.VISIBLE
        touch()
    }

    private val hideBar = Runnable {
        if (held || menu.visibility == View.VISIBLE || movesPanel.visibility == View.VISIBLE) touch()
        else {
            closeMenu()
            for (v in arrayOf<View>(bar, rail, tag)) {         // the red tag goes with the bar and the rail, so it covers nothing
                val view = v
                view.animate().alpha(0f).setDuration(Motion.OUT_MS.toLong()).withEndAction { if (view.alpha == 0f) view.visibility = View.GONE }.start()
            }
        }
    }

    /** Shows the bar and the rail and starts the countdown to hide them again: they cover the picture (the sticks, the bottom cards) only while they are needed. */
    fun touch() {
        if (backdrop.visibility != View.VISIBLE) return
        for (view in arrayOf<View>(bar, rail, tag)) {
            view.animate().cancel()
            view.alpha = 1f
            view.visibility = View.VISIBLE
        }
        bar.removeCallbacks(hideBar)
        bar.postDelayed(hideBar, HIDE_MS)
    }

    /** Takes the bar, the rail and the tag out of the picture for a snapshot; the next touch brings them back. */
    fun hideForSnapshot() {
        bar.removeCallbacks(hideBar)
        closeMenu()
        bar.visibility = View.INVISIBLE
        rail.visibility = View.INVISIBLE
        tag.visibility = View.INVISIBLE
    }

    fun hide() {
        bar.removeCallbacks(hideBar)
        bar.animate().cancel()
        rail.animate().cancel()
        tag.animate().cancel()
        closeMenu()
        backdrop.visibility = View.GONE
        tag.visibility = View.GONE
        bar.visibility = View.GONE
        rail.visibility = View.GONE
    }

    /** The marks on the scrub bar: the events of the flight as fractions of the log, with their colours. */
    fun setMarks(fractions: DoubleArray, colors: IntArray) = scrub.setMarks(fractions, colors)

    /** The 3D buttons (camera, aircraft, trail) are there only when the log has a 3D view. */
    fun set3d(available: Boolean, camera: Int, model: Int, trail: Int, mapIndex: Int, mapCustom: Boolean) {
        has3d = available
        setMap(mapIndex, mapCustom)
        this.camera = camera
        this.model = model
        this.trailMode = trail
        val v = if (available) View.VISIBLE else View.GONE
        cameraRail.visibility = v
        modelRail.visibility = v
        trailRail.visibility = v
        mapRail.visibility = v
        layoutRail()
    }

    /** The kind of map shown (0 off, 1 street, 2 satellite, 3 terrain, 4 custom) and whether the custom address is set. */
    fun setMap(index: Int, custom: Boolean) {
        mapIndex = index
        mapCustom = custom
        paintToggles()
    }

    fun setLoop(on: Boolean) { loopOn = on; paintToggles() }
    fun setCards(show: Boolean) { cardsOn = show; paintToggles() }

    private fun paintToggles() {
        loopButton.setImageDrawable(icon("loop", if (loopOn) CardStyle.ACCENT else Color.WHITE))
        cardsRail.setImageDrawable(icon("cards", if (cardsOn) Color.WHITE else CardStyle.ACCENT))
        mapRail.setImageDrawable(icon("map", if (mapIndex > 0) CardStyle.ACCENT else Color.WHITE))
    }

    private fun paintSpeeds() {
        for (i in speedViews.indices) {
            val on = i == speedIndex
            speedViews[i].setTextColor(if (on) 0xFF0B1016.toInt() else Color.WHITE)
            speedViews[i].setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            speedViews[i].setBackgroundColor(if (on) CardStyle.ACCENT else Color.TRANSPARENT)
        }
    }

    fun update(positionMs: Long, durationMs: Long, playing: Boolean, speedIndex: Int) {
        if (playing != this.playing) {
            this.playing = playing
            playButton.setImageDrawable(icon(if (playing) "pause" else "play", Color.WHITE, 28))
            playButton.contentDescription = if (playing) tr("Pause") else tr("Play")
        }
        if (speedIndex != this.speedIndex) { this.speedIndex = speedIndex; paintSpeeds() }
        timeView.text = LogText.duration(positionMs) + " / " + LogText.duration(durationMs)
        if (durationMs > 0) scrub.setProgress(positionMs.toDouble() / durationMs)
    }

    companion object {
        /** The bar and the rail fade out after this long without a touch. */
        const val HIDE_MS = 4000L
        /** The jump of the buttons 10 seconds back and forward. */
        const val STEP_MS = 10_000L
        /** The fastest rate at which a drag on the scrub bar seeks. */
        const val SCRUB_MS = 80L
    }
}
