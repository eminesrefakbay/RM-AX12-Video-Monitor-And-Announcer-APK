// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Edit mode for the cards on the video: drag a card to move it, drag the round handle at its bottom right corner to resize it.
 * The cards themselves are drawn by their own views; this overlay only draws a frame, a name and a handle around each,
 * and turns finger movement into a place and a scale (see TileLayout). Done and Reset sit in a bar at the top.
 */
class LayoutEditor(private val activity: Activity, private val host: Host) : FrameLayout(activity) {

    class Tile(val name: String, val label: String, val view: View)

    interface Host {
        /** The cards that can be edited right now, the topmost last. */
        fun tiles(): List<Tile>
        /** Does the card have a place of its own (not a corner)? Then its place is its offset alone: it sits at 0,0 in its parent. */
        fun isFree(tile: Tile): Boolean
        /** A card is about to be moved for the first time: leave its corner and take a place of its own. */
        fun enterFree(tile: Tile)
        /** A move or a resize ended: keep this place (0..1000) and size (percent). */
        fun placed(tile: Tile, fx: Int, fy: Int, percent: Int)
        fun done()
        fun reset()
    }

    private val density = activity.resources.displayMetrics.density
    private val extraBox = LinearLayout(activity)
    private val bar = LinearLayout(activity)
    private var snapButton: ImageButton? = null
    private var snapOn = Prefs.bool(activity, "layoutSnap", true)
    private val gridPaint = Paint()
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var active: Tile? = null
    private var resizing = false
    private var downX = 0f
    private var downY = 0f
    private var startLeft = 0.0
    private var startTop = 0.0

    /** True while a finger is moving or resizing a card (the cards then do not re-place themselves). */
    var dragging = false
        private set

    init {
        setWillNotDraw(false)
        visibility = View.GONE
        isClickable = true
        elevation = 20f * density
        scrim.color = 0x55000000
        frame.color = 0xFF4FC3F7.toInt()
        frame.style = Paint.Style.STROKE
        frame.strokeWidth = 2f * density
        chip.color = 0xFF4FC3F7.toInt()
        chipText.color = 0xFF10202A.toInt()
        chipText.textSize = 12f * density
        chipText.isFakeBoldText = true
        handleFill.color = 0xFFFFFFFF.toInt()

        // a small see-through strip of icon buttons at the top centre: it takes as little of the screen as it can
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(0x99101820.toInt())
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))
        bar.addView(extraBox)
        bar.addView(iconButton("grid", tr("Snap to grid")) { setSnap(!snapOn) }.also { snapButton = it })
        bar.addView(iconButton("reset", tr("Reset the layout")) { host.reset() })
        bar.addView(iconButton("check", tr("Done"), CardStyle.OK) { host.done() })
        addView(bar, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(4) })
        paintSnapButton()
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** A button of the strip: an icon, and a name for the screen reader. */
    class Action(val icon: String, val label: String, val onClick: () -> Unit)

    private fun iconButton(icon: String, label: String, color: Int = CardStyle.ICON, onClick: () -> Unit): ImageButton {
        val b = ImageButton(activity)
        b.setImageDrawable(Icons.drawable(activity, icon, color, 24))
        b.contentDescription = label
        b.background = CardStyle.stateBackground(density, 0xE6232C36.toInt(), 0xF24F6272.toInt(), 0xFF546E7A.toInt())
        b.scaleType = android.widget.ImageView.ScaleType.CENTER
        b.setOnClickListener { onClick() }
        val side = dp(CardStyle.MIN_TOUCH_DP)
        b.layoutParams = LinearLayout.LayoutParams(side, side).also { it.setMargins(dp(4), 0, 0, 0) }
        return b
    }

    /** Extra buttons in the strip, before the grid, Reset and Done (the dashboard adds, removes and edits cards). */
    fun setActions(actions: List<Action>) {
        extraBox.removeAllViews()
        extraBox.orientation = LinearLayout.HORIZONTAL
        for (a in actions) extraBox.addView(iconButton(a.icon, a.label, onClick = a.onClick))
    }

    private fun setSnap(on: Boolean) {
        snapOn = on
        Prefs.setBool(activity, "layoutSnap", on)
        paintSnapButton()
        invalidate()
    }

    /** The grid button is lit while snapping is on. */
    private fun paintSnapButton() {
        val b = snapButton ?: return
        b.setImageDrawable(Icons.drawable(activity, "grid", if (snapOn) CardStyle.ACCENT else CardStyle.ICON, 24))
    }

    /** The grid: a line every 16 dp, a little stronger every fourth. */
    private fun gridPx(): Double = 16.0 * density

    private fun snapToGrid(v: Double): Double = Math.rint(v / gridPx()) * gridPx()

    fun show() {
        Anim.show(this)
        invalidate()
    }

    fun hide() {
        dragging = false
        active = null
        Anim.hide(this)
    }

    private fun rectOf(t: Tile): TileLayout.Rect {
        val v = t.view
        val free = host.isFree(t)
        val left = if (free) v.translationX.toDouble() else (v.left + v.translationX).toDouble()
        val top = if (free) v.translationY.toDouble() else (v.top + v.translationY).toDouble()
        return TileLayout.Rect(left, top, v.width * v.scaleX.toDouble(), v.height * v.scaleY.toDouble())
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        if (snapOn) {
            val g = gridPx().toFloat()
            var i = 0
            var x = 0f
            while (x <= width) { gridPaint.color = if (i % 4 == 0) 0x40FFFFFF else 0x1CFFFFFF; canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += g; i++ }
            i = 0
            var y = 0f
            while (y <= height) { gridPaint.color = if (i % 4 == 0) 0x40FFFFFF else 0x1CFFFFFF; canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += g; i++ }
        }
        for (t in host.tiles()) {
            val r = rectOf(t)
            rect.set(r.left.toFloat(), r.top.toFloat(), r.right().toFloat(), r.bottom().toFloat())
            frame.strokeWidth = (if (t === active) 3.5f else 2f) * density
            canvas.drawRect(rect, frame)
            val w = chipText.measureText(t.label) + 12 * density
            // the name tag sits above the card, or inside its corner when the card is too close to the top
            val tagTop = if (rect.top >= 24 * density) rect.top - 18 * density else rect.top + 2 * density
            canvas.drawRect(rect.left, tagTop, rect.left + w, tagTop + 18 * density, chip)
            canvas.drawText(t.label, rect.left + 6 * density, tagTop + 13 * density, chipText)
            val h = 11f * density                                   // the square handle at the bottom right corner
            canvas.drawRect(rect.right - h, rect.bottom - h, rect.right + h, rect.bottom + h, handleFill)
            canvas.drawRect(rect.right - h, rect.bottom - h, rect.right + h, rect.bottom + h, frame)
        }
        if (visibility == View.VISIBLE) postInvalidateDelayed(200)        // follow cards whose size changes by itself
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val rw = width.toDouble()
        val rh = height.toDouble()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = null
                for (t in host.tiles().reversed()) {
                    val r = rectOf(t)
                    if (TileLayout.onHandle(r, ev.x.toDouble(), ev.y.toDouble(), 28.0 * density)) {
                        active = t
                        resizing = true
                        break
                    }
                    if (TileLayout.contains(r, ev.x.toDouble(), ev.y.toDouble())) {
                        active = t
                        resizing = false
                        break
                    }
                }
                val t = active
                if (t != null) {
                    host.enterFree(t)                              // a card in its corner takes a place of its own now
                    val r = rectOf(t)
                    startLeft = r.left
                    startTop = r.top
                    downX = ev.x
                    downY = ev.y
                    dragging = true
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val t = active ?: return true
                val v = t.view
                if (v.width <= 0 || v.height <= 0) return true
                if (resizing) {
                    var s = TileLayout.scaleFromDrag(rw, rh, v.width.toDouble(), v.height.toDouble(), startLeft, ev.x.toDouble())
                    if (snapOn) {                                  // the right edge goes to the grid: the width is a whole number of cells
                        val baseW = v.width.toDouble()
                        val cells = Math.max(1.0, Math.rint(baseW * s / gridPx()))
                        s = TileLayout.clampScale(cells * gridPx() / baseW, rw, rh, baseW, v.height.toDouble())
                    }
                    v.scaleX = s.toFloat()
                    v.scaleY = s.toFloat()
                } else {
                    val w = v.width * v.scaleX.toDouble()
                    val h = v.height * v.scaleY.toDouble()
                    var pos = TileLayout.keepOnScreen(startLeft + (ev.x - downX), startTop + (ev.y - downY), w, h, rw, rh)
                    pos = TileLayout.snap(pos[0], pos[1], w, h, rw, rh, 14.0 * density)
                    if (snapOn) {                                  // the top left corner goes to the grid (the screen's edges still win when close)
                        val gx = snapToGrid(pos[0])
                        val gy = snapToGrid(pos[1])
                        val nearEdgeX = pos[0] + w >= rw - 1.0 || pos[0] <= 1.0
                        val nearEdgeY = pos[1] + h >= rh - 1.0 || pos[1] <= 1.0
                        pos = doubleArrayOf(if (nearEdgeX) pos[0] else gx, if (nearEdgeY) pos[1] else gy)
                    }
                    v.translationX = pos[0].toFloat()
                    v.translationY = pos[1].toFloat()
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = active
                if (t != null && dragging) {
                    val v = t.view
                    val r = rectOf(t)
                    val s = if (v.width > 0) v.scaleX.toDouble() else 1.0
                    val f = TileLayout.fractions(rw, rh, v.width.toDouble(), v.height.toDouble(), s, r.left, r.top)
                    dragging = false
                    host.placed(t, f[0], f[1], TileLayout.toPercent(s))
                }
                dragging = false
                invalidate()
            }
        }
        return true
    }
}
