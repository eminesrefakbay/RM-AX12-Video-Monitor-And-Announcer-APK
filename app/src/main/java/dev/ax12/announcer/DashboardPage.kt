// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * The dashboard: a second page with cards only and no video, for goggles or for a second pair of eyes. It has its own set of cards
 * (horizon, compass, statistics, radar, charts, sticks and any number of value cards), its own places for them, and the same editor
 * for moving and resizing as the video page. The cards read the same values as the ones on the video, and in a replay too.
 */
class DashboardPage(private val activity: Activity, private val host: Host) {

    interface Host {
        fun stats(): FlightStats
        fun chart(): ChartModel
        fun statsContent(fs: FlightStats, now: Long): Pair<String, List<FlightStatsView.Line>>
        fun pickVariable(onPicked: (String) -> Unit)
        fun editEnded()
    }

    val root = FrameLayout(activity)
    private val density = activity.resources.displayMetrics.density
    private var tiles: MutableList<DashLayout.Tile> = ArrayList()
    private val views = LinkedHashMap<String, View>()
    private val specs = HashMap<String, ValueSpec>()
    private val editor: LayoutEditor
    private var shown = false
    private var holdX = 0f
    private var holdY = 0f
    private val holdFire = Runnable {
        if (shown && !isEditing) {
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startEdit()
        }
    }

    val isShowing: Boolean get() = shown
    var isEditing = false
        private set

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        root.setBackgroundColor(Sunlight.c(0xFF0B1016.toInt()))
        root.visibility = View.GONE
        root.isClickable = true                                     // a touch on the page must not reach the video page under it
        root.setOnTouchListener { _, ev ->                         // a hold on one spot for five seconds starts editing the layout; swiping does not
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    root.removeCallbacks(holdFire)
                    holdX = ev.rawX
                    holdY = ev.rawY
                    if (shown && !isEditing) root.postDelayed(holdFire, HoldGesture.HOLD_MS)
                }
                MotionEvent.ACTION_MOVE ->
                    if (HoldGesture.moved((ev.rawX - holdX).toDouble(), (ev.rawY - holdY).toDouble(), HoldGesture.SLOP_DP * density.toDouble())) root.removeCallbacks(holdFire)
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> root.removeCallbacks(holdFire)
            }
            false
        }
        root.elevation = dp(6).toFloat()                            // above the video and its cards; the MENU button and the warnings sit above it
        editor = LayoutEditor(activity, object : LayoutEditor.Host {
            override fun tiles(): List<LayoutEditor.Tile> =
                tiles.filter { val v = views[it.id]; v != null && v.visibility == View.VISIBLE && v.width > 0 }
                    .map { LayoutEditor.Tile(it.id, DashLayout.label(it.kind), views[it.id]!!) }

            override fun isFree(tile: LayoutEditor.Tile): Boolean = true            // every card of the dashboard has its own place
            override fun enterFree(tile: LayoutEditor.Tile) {}
            override fun placed(tile: LayoutEditor.Tile, fx: Int, fy: Int, percent: Int) {
                val t = DashLayout.find(tiles, tile.name) ?: return
                t.x = fx
                t.y = fy
                t.scale = percent
                save()
                applyPosition(tile.view, t)
            }

            override fun done() = endEdit()
            override fun reset() = resetLayout()
        })
        root.addView(editor, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!editor.dragging) applyAll() }
        load()
    }

    // ------------------------------------------------------------------ the cards

    private fun load() {
        tiles = DashLayout.selection(Prefs.dashTiles(activity)).toMutableList()
        rebuild()
    }

    private fun save() { Prefs.setDashTiles(activity, DashLayout.serialize(tiles)) }

    /** Builds every card again (after a new layout, an added or removed card, or a changed setting). */
    fun rebuild() {
        for (v in views.values) root.removeView(v)
        views.clear()
        specs.clear()
        for (t in tiles) {
            val v = makeView(t)
            skinCard(v)
            views[t.id] = v
            if (t.kind == "value") specs[t.id] = ValueSpec.parse(t.setting)
            root.addView(v, root.childCount - 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            val id = t.id
            v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                if (!editor.dragging) { val tile = DashLayout.find(tiles, id); if (tile != null) applyPosition(view, tile) }
            }
        }
        root.post { applyAll() }
    }

    /** A card is drawn in the sunlight style when that is on (the horizon keeps its sky and ground, and only gets a light frame). */
    private fun skinCard(v: View) {
        when (v) {
            is HorizonView -> v.applyLight(Sunlight.on)
            is CompassView -> v.applyLight(Sunlight.on)
            is FlightStatsView -> v.applyLight(Sunlight.on)
            is RadarView -> v.applyLight(Sunlight.on)
            is ChartView -> v.applyLight(Sunlight.on)
            is SticksView -> v.applyLight(Sunlight.on)
            is ValueTileView -> v.applyLight(Sunlight.on)
        }
    }

    /** The theme was switched: the page and every card are drawn again. */
    fun applyTheme() {
        root.setBackgroundColor(Sunlight.c(0xFF0B1016.toInt()))
        for (v in views.values) skinCard(v)
    }

    private fun makeView(t: DashLayout.Tile): View = when (t.kind) {
        "horizon" -> HorizonView(activity).also { it.configure(Prefs.bool(activity, "horizonInvPitch", false), Prefs.bool(activity, "horizonInvRoll", false)) }
        "compass" -> CompassView(activity)
        "stats" -> FlightStatsView(activity).also { it.setTextSp(floatArrayOf(13f, 16f, 20f)[Prefs.int(activity, "statsSize", 1).coerceIn(0, 2)]) }
        "radar" -> RadarView(activity).also { it.setSizeDp(200); it.bind(host.stats()) }
        "chart" -> ChartView(activity).also { it.bind(host.chart()); it.setLevel(Prefs.int(activity, "chartSize", 1).coerceIn(0, 2)) }
        "sticks" -> SticksView(activity).also { configureSticks(it) }
        else -> ValueTileView(activity)
    }

    /** The stick view of the dashboard takes the same stick mode, channels, inversion and channel bars as the one on the video. */
    private fun configureSticks(v: SticksView) {
        val mode = Prefs.int(activity, "stickMode", 2)
        val roll = Prefs.int(activity, "stickRoll", 1)
        val pitch = Prefs.int(activity, "stickPitch", 2)
        val throttle = Prefs.int(activity, "stickThrottle", 3)
        val yaw = Prefs.int(activity, "stickYaw", 4)
        val layout = StickLayout.forMode(mode, roll, pitch, throttle, yaw)
        val names = arrayOf(
            StickLayout.axisName(layout, mode, true, true, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, true, false, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, false, true, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, false, false, roll, pitch, throttle, yaw)
        )
        val boxDp = intArrayOf(64, 96, 132)[Prefs.int(activity, "sticksSize", 1).coerceIn(0, 2)]
        v.configure(layout, Prefs.int(activity, "stickInvert", 0), names, boxDp)
        v.setBars(AuxBars.decode(Prefs.barsConfig(activity)), Prefs.bool(activity, "barsOn", true))
    }

    private fun applyAll() {
        for (t in tiles) { val v = views[t.id]; if (v != null) applyPosition(v, t) }
    }

    /** The place is a fraction of the free space, so it is right on any screen: the card sits at 0,0 and its offset is its place. */
    private fun applyPosition(view: View, t: DashLayout.Tile) {
        if (root.width <= 0 || view.width <= 0 || view.height <= 0) return
        val r = TileLayout.rect(root.width.toDouble(), root.height.toDouble(), view.width.toDouble(), view.height.toDouble(), t.x, t.y, TileLayout.fromPercent(t.scale))
        view.pivotX = 0f
        view.pivotY = 0f
        view.scaleX = (r.width / view.width).toFloat()
        view.scaleY = (r.height / view.height).toFloat()
        view.translationX = r.left.toFloat()
        view.translationY = r.top.toFloat()
    }

    // ------------------------------------------------------------------ showing, updating

    fun show() {
        shown = true
        Anim.show(root)
        root.post { applyAll() }
    }

    /** Without the fade: when the app starts on the dashboard. */
    fun showNow() {
        shown = true
        root.visibility = View.VISIBLE
        root.post { applyAll() }
    }

    fun hide() {
        shown = false
        root.removeCallbacks(holdFire)
        if (isEditing) { isEditing = false; editor.hide() }
        Anim.hide(root)
    }

    /** About five times a second: the cards that are drawn from statistics, the radar and the charts. */
    fun update(now: Long) {
        if (!shown) return
        val fs = host.stats()
        for (v in views.values) {
            when (v) {
                is FlightStatsView -> { val c = host.statsContent(fs, now); v.setContent(c.first, c.second) }
                is RadarView -> { v.bind(fs); v.invalidate() }
                is ChartView -> { v.bind(host.chart()); v.refresh(now) }
                else -> {}
            }
        }
    }

    /** On every tick: the cards that move smoothly. */
    fun updateFast(numbers: Map<String, Double>, texts: Map<String, String>) {
        if (!shown) return
        val fs = host.stats()
        for (t in tiles) {
            when (val v = views[t.id]) {
                is HorizonView -> v.set(numbers["pitch"] ?: Double.NaN, numbers["roll"] ?: Double.NaN)
                is CompassView -> {
                    val home = fs.hasHome()
                    v.set(Attitude.heading(numbers), if (home) Attitude.bearingToHome(fs.eastM(), fs.northM()) else Double.NaN, if (home) fs.distanceNow() else Double.NaN)
                }
                is SticksView -> v.update(numbers)
                is ValueTileView -> {
                    val spec = specs[t.id] ?: ValueSpec()
                    v.set(spec, numbers[spec.variable] ?: Double.NaN, texts[spec.variable])
                }
                else -> {}
            }
        }
    }

    // ------------------------------------------------------------------ editing

    fun startEdit() {
        isEditing = true
        editor.setActions(listOf(
            LayoutEditor.Action("plus", tr("Add a card...")) { addCard() },
            LayoutEditor.Action("minus", tr("Remove a card...")) { removeCard() },
            LayoutEditor.Action("pencil", tr("Edit a value card...")) { editValueCard() }
        ))
        editor.show()
    }

    fun endEdit() {
        if (!isEditing) return
        isEditing = false
        editor.hide()
        host.editEnded()
    }

    fun resetLayout() {
        tiles = DashLayout.defaults().toMutableList()
        save()
        rebuild()
    }

    private fun addCard() {
        val kinds = DashLayout.KINDS
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Add a card"))
            .setItems(Array(kinds.size) { DashLayout.label(kinds[it]) }) { _, i -> addOfKind(kinds[i]) }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }

    private fun addOfKind(kind: String) {
        if (tiles.size >= DashLayout.MAX_TILES) {
            Toast.makeText(activity, tr("The dashboard has the most cards it can hold."), Toast.LENGTH_SHORT).show()
            return
        }
        if (kind == "value") {
            host.pickVariable { name -> valueForm(ValueSpec(name, "", "", 1, Double.NaN, Double.NaN, "-")) { spec -> insert(kind, spec.serialize()) } }
        } else {
            insert(kind, "")
        }
    }

    private fun insert(kind: String, setting: String) {
        tiles.add(DashLayout.Tile(DashLayout.newId(tiles, kind), kind, 500, 500, 100, setting))
        save()
        rebuild()
    }

    private fun describe(t: DashLayout.Tile): String =
        if (t.kind == "value") DashLayout.label(t.kind) + ": " + ValueSpec.parse(t.setting).title() else DashLayout.label(t.kind)

    private fun removeCard() {
        if (tiles.isEmpty()) return
        val snapshot = ArrayList(tiles)
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Remove which card?"))
            .setItems(Array(snapshot.size) { describe(snapshot[it]) }) { _, i ->
                tiles.remove(snapshot[i])
                save()
                rebuild()
            }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }

    private fun editValueCard() {
        val values = tiles.filter { it.kind == "value" }
        if (values.isEmpty()) {
            Toast.makeText(activity, tr("There is no value card to edit."), Toast.LENGTH_SHORT).show()
            return
        }
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Edit which value card?"))
            .setItems(Array(values.size) { describe(values[it]) }) { _, i ->
                val t = values[i]
                valueForm(ValueSpec.parse(t.setting)) { spec ->
                    t.setting = spec.serialize()
                    save()
                    rebuild()
                }
            }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }

    /** The form of a value card: the value, a label, the unit, the decimals and the two limits with the side that is bad. */
    private fun valueForm(initial: ValueSpec, onOk: (ValueSpec) -> Unit) {
        val body = LinearLayout(activity)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(20), dp(8), dp(20), dp(8))
        var variable = initial.variable
        var decimals = initial.decimals
        var direction = initial.direction

        val pick = Button(activity)
        fun showVariable() { pick.text = tr("Value: {0}", variable) }
        showVariable()
        pick.setOnClickListener { host.pickVariable { name -> variable = name; showVariable() } }
        body.addView(pick)

        fun field(hint: String, text: String, numeric: Boolean): EditText {
            val e = EditText(activity)
            e.hint = hint
            e.setText(text)
            e.setSingleLine(true)
            if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            body.addView(e)
            return e
        }
        val label = field(tr("Label (optional)"), initial.label, false)
        val unit = field(tr("Unit"), initial.unit, false)

        val dec = Button(activity)
        fun showDecimals() { dec.text = tr("Decimals: {0}", decimals) }
        showDecimals()
        dec.setOnClickListener { decimals = (decimals + 1) % 4; showDecimals() }
        body.addView(dec)

        val dirButton = Button(activity)
        fun showDirection() { dirButton.text = when (direction) { "<" -> tr("Low is bad"); ">" -> tr("High is bad"); else -> tr("No limits") } }
        showDirection()
        dirButton.setOnClickListener { direction = when (direction) { "-" -> "<"; "<" -> ">"; else -> "-" }; showDirection() }
        body.addView(dirButton)

        fun lim(v: Double) = if (v.isNaN()) "" else (if (v == Math.rint(v)) v.toLong().toString() else v.toString())
        val warn = field(tr("Amber limit"), lim(initial.warn), true)
        val crit = field(tr("Red limit"), lim(initial.crit), true)

        fun num(e: EditText): Double = e.text.toString().trim().toDoubleOrNull() ?: Double.NaN
        val scroll = ScrollView(activity)
        scroll.addView(body)
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Value card"))
            .setView(scroll)
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Done")) { _, _ ->
                onOk(ValueSpec(variable, label.text.toString(), unit.text.toString(), decimals, num(warn), num(crit), direction))
            }
            .create()
        Immersive.show(dlg)
    }
}
