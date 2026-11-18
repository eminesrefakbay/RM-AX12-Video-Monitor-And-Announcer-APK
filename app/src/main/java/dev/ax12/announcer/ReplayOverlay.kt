// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.graphics.Color
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
 * the camera, the aircraft, the colour of the trail, the cards and a snapshot. The cards themselves are the ones of the live flight.
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
        fun snapshot()
    }

    val backdrop = View(activity)
    val tag = TextView(activity)
    val bar = LinearLayout(activity)
    private val rail = LinearLayout(activity)
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
        rail.addView(v, LinearLayout.LayoutParams(dp(56), dp(56)).also { it.bottomMargin = dp(8) })
    }

    private fun buildRail() {
        rail.orientation = LinearLayout.VERTICAL
        rail.setPadding(dp(4), dp(4), dp(4), dp(0))
        rail.visibility = View.GONE
        rail.elevation = dp(16).toFloat()
        railButton(ImageView(activity), "close") { actions.close() }
        railButton(cameraRail, "camera") { openMenu(cameraRail, listOf(tr("Chase"), tr("Orbit"), tr("Pilot"), tr("Top")), camera) { actions.camera(it) } }
        railButton(modelRail, "aircraft") { openMenu(modelRail, listOf(tr("Quad"), tr("Plane")), model) { actions.model(it) } }
        railButton(trailRail, "trail") { openMenu(trailRail, listOf(tr("Speed"), tr("Height")), trailMode) { actions.trail(it) } }
        railButton(cardsRail, "cards") { cardsOn = !cardsOn; paintToggles(); actions.cards(cardsOn) }
        railButton(ImageView(activity), "snapshot") { closeMenu(); actions.snapshot() }
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
        lp.topMargin = (rail.top + anchor.top).coerceAtLeast(0)
        lp.rightMargin = rail.width + dp(8)
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
        frame.addView(backdrop, backdropIndex, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(tag, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).also { it.setMargins(dp(10), dp(10), 0, 0) })
        frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        frame.addView(rail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).also { it.setMargins(0, dp(10), dp(8), dp(104)) })
        frame.addView(menu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
    }

    fun show(title: String) {
        tag.text = title
        backdrop.visibility = View.VISIBLE
        tag.visibility = View.VISIBLE
        touch()
    }

    private val hideBar = Runnable {
        if (held || menu.visibility == View.VISIBLE) touch()
        else {
            closeMenu()
            for (v in arrayOf<View>(bar, rail)) {
                val view = v
                view.animate().alpha(0f).setDuration(Motion.OUT_MS.toLong()).withEndAction { if (view.alpha == 0f) view.visibility = View.GONE }.start()
            }
        }
    }

    /** Shows the bar and the rail and starts the countdown to hide them again: they cover the picture (the sticks, the bottom cards) only while they are needed. */
    fun touch() {
        if (backdrop.visibility != View.VISIBLE) return
        tag.visibility = View.VISIBLE
        for (view in arrayOf<View>(bar, rail)) {
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
        closeMenu()
        backdrop.visibility = View.GONE
        tag.visibility = View.GONE
        bar.visibility = View.GONE
        rail.visibility = View.GONE
    }

    /** The marks on the scrub bar: the events of the flight as fractions of the log, with their colours. */
    fun setMarks(fractions: DoubleArray, colors: IntArray) = scrub.setMarks(fractions, colors)

    /** The 3D buttons (camera, aircraft, trail) are there only when the log has a 3D view. */
    fun set3d(available: Boolean, camera: Int, model: Int, trail: Int) {
        has3d = available
        this.camera = camera
        this.model = model
        this.trailMode = trail
        val v = if (available) View.VISIBLE else View.GONE
        cameraRail.visibility = v
        modelRail.visibility = v
        trailRail.visibility = v
    }

    fun setLoop(on: Boolean) { loopOn = on; paintToggles() }
    fun setCards(show: Boolean) { cardsOn = show; paintToggles() }

    private fun paintToggles() {
        loopButton.setImageDrawable(icon("loop", if (loopOn) CardStyle.ACCENT else Color.WHITE))
        cardsRail.setImageDrawable(icon("cards", if (cardsOn) Color.WHITE else CardStyle.ACCENT))
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
