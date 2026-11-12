// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Everything the settings screen needs from the main screen. */
interface SettingsHost {
    fun cameraId(): String
    fun chooseCamera()
    fun rotation(): Int
    fun rotateVideo()
    fun fill(): Boolean
    fun toggleFill()
    fun speechOn(): Boolean
    fun setSpeechOn(on: Boolean)
    fun speakTest()
    fun exportRules()
    fun importRules()
    fun hasRulesBackup(): Boolean
    fun restoreRules()
    fun resetRules()
    fun openRulesText()
    fun splashOn(): Boolean
    fun setSplashOn(on: Boolean)
    fun menuAlways(): Boolean
    fun sunlightOn(): Boolean
    fun shapeLabel(): String
    fun cycleShape()
    // antenna tracker
    fun trackerOn(): Boolean
    fun setTrackerOn(on: Boolean)
    fun trackerDeviceLabel(): String
    fun trackerStatusLine(): String
    fun trackerFind()
    fun trackerCommand(kind: Int)
    fun trackerDeclinationDialog()
    fun trackerHeightDialog()
    fun setSunlightOn(on: Boolean)
    fun setMenuAlways(on: Boolean)
    fun variablesOn(): Boolean
    fun setVariablesOn(on: Boolean)
    fun testChannelsOn(): Boolean
    fun setTestChannelsOn(on: Boolean)
    fun about(): String
    fun openAbout()
    fun openGuide()

    // languages
    fun languageLabel(): String
    fun cycleLanguage()
    fun speechLanguageLabel(): String
    fun cycleSpeechLanguage()

    // OSD overlays
    fun sticksOn(): Boolean
    fun setSticksOn(on: Boolean)
    fun sticksSizeLabel(): String
    fun cycleSticksSize()
    fun sticksPositionLabel(): String
    fun cycleSticksPosition()
    fun sticksOpacityLabel(): String
    fun cycleSticksOpacity()
    fun editStickChannels()
    fun channelBarsOn(): Boolean
    fun setChannelBarsOn(on: Boolean)
    fun editChannelBars()
    fun warningBarOn(): Boolean
    fun setWarningBarOn(on: Boolean)
    fun warningBarPositionLabel(): String
    fun cycleWarningBarPosition()
    fun editWarningRules()
    fun previewWarnings()

    // flight overlays: statistics, radar, checklist
    fun tileOn(tile: String): Boolean
    fun setTileOn(tile: String, on: Boolean)
    fun tileSizeLabel(tile: String): String
    fun cycleTileSize(tile: String)
    fun tilePositionLabel(tile: String): String
    fun cycleTilePosition(tile: String)
    fun tileOpacityLabel(tile: String): String
    fun cycleTileOpacity(tile: String)
    fun editCharts()

    // dashboard
    fun editDashboard()
    fun resetDashboard()
    fun saveDashboardLayout()
    fun loadDashboardLayout()
    fun deleteDashboardLayout()
    fun chooseDashboardPreset()
    fun horizonInvertPitch(): Boolean
    fun setHorizonInvertPitch(on: Boolean)
    fun horizonInvertRoll(): Boolean
    fun setHorizonInvertRoll(on: Boolean)

    // flight logs
    fun logOn(): Boolean
    fun setLogOn(on: Boolean)
    fun logRateLabel(): String
    fun cycleLogRate()
    fun logFieldsLabel(): String
    fun editLogFields()
    fun logKeepLabel(): String
    fun cycleLogKeep()
    fun logStatus(): String
    fun logFolder(): String
    fun openLogViewer()
    fun editLayout()
    // approval window, landing, link loss and packs
    fun windowOn(): Boolean
    fun setWindowOn(on: Boolean)
    fun windowStartLabel(): String
    fun pickWindowStart()
    fun windowEndLabel(): String
    fun pickWindowEnd()
    fun windowTwoHours()
    fun windowWarnLabel(): String
    fun editWindowWarn()
    fun windowClockNote(): String
    fun windowSpeechOn(): Boolean
    fun setWindowSpeechOn(on: Boolean)
    fun debriefOn(): Boolean
    fun setDebriefOn(on: Boolean)
    fun lossSpeechOn(): Boolean
    fun setLossSpeechOn(on: Boolean)
    fun openPackBook()
    fun openPosition()
    fun openDiagnostics()
    fun showStartupTimings()
    fun resetLayout()
    fun editArmDetection()
    fun editStatsRows()
    fun editCheckRules()

    // aircraft, packs, battery profiles and backups
    fun aircraftName(): String
    fun chooseAircraft()
    fun manageAircraft()
    fun editPacks()
    fun batteryLabel(): String
    fun cycleBattery()
    fun editBatteries()
    fun resetWarningRules()
    fun resetCheckRules()
    fun exportSettings()
    fun importSettings()
    fun hasSettingsBackup(): Boolean
    fun restoreSettings()

    // updates from GitHub
    fun updateLine(): String
    fun checkUpdatesNow()
    fun updateChannelLabel(): String
    fun cycleUpdateChannel()
    fun updateAuto(): Boolean
    fun setUpdateAuto(on: Boolean)
}

/**
 * The settings screen: everything that is not a daily action. A rail of tabs on the left (General, Aircraft and flight, Video and overlays,
 * Dashboard, Antenna tracker, Flight logs, Rules and backup, System), the rows of the chosen tab on the right, and a search field in the
 * title bar that finds a setting by its words in every tab. It opens over the video from MENU > Settings.
 */
class SettingsPanel(private val activity: Activity, private val host: SettingsHost) {
    val root = LinearLayout(activity)
    private val list = LinearLayout(activity)
    private val rail = LinearLayout(activity)
    private val search = EditText(activity)
    private var target: LinearLayout = list          // where building blocks add their rows: the list, or a hidden container while searching
    private var barView: LinearLayout? = null
    private var titleView: TextView? = null
    private var selectedTab = "general"             // kept while the app runs
    private var query = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private val runSearch = Runnable { refresh() }

    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    private class Part(val title: String?, val build: () -> Unit)
    private class Tab(val id: String, val title: String, val icon: String, val parts: List<Part>)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))     // the video stays visible behind the settings, so you can see it is there
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true
        root.isFocusableInTouchMode = true          // so that the search field does not take the focus (and the keyboard) when the screen opens
        root.elevation = dp(24).toFloat()          // above the MENU button and every other view

        val bar = LinearLayout(activity)
        barView = bar
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        bar.setPadding(dp(8), dp(0), dp(8), dp(0))
        val title = TextView(activity)
        titleView = title
        title.text = tr("Settings")
        title.textSize = 22f
        title.setTextColor(Sunlight.c(Color.WHITE))
        title.setTypeface(null, Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        search.hint = tr("Search settings")
        search.setSingleLine(true)
        search.textSize = 15f
        search.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        search.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN     // no full-screen keyboard in landscape
        search.setPadding(dp(12), dp(4), dp(12), dp(4))
        search.minHeight = dp(40)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (q == query) return
                query = q
                searchHandler.removeCallbacks(runSearch)
                searchHandler.postDelayed(runSearch, 220L)                       // search a moment after the last key
            }
        })
        search.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }
        bar.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
            it.marginStart = dp(16)
            it.marginEnd = dp(8)
        })
        val close = Button(activity)
        close.text = tr("Close")
        close.setOnClickListener { hide() }
        bar.addView(close)
        root.addView(bar)

        val body = LinearLayout(activity)
        body.orientation = LinearLayout.HORIZONTAL
        val railScroll = ScrollView(activity)
        rail.orientation = LinearLayout.VERTICAL
        railScroll.addView(rail)
        body.addView(railScroll, LinearLayout.LayoutParams(dp(RAIL_DP), ViewGroup.LayoutParams.MATCH_PARENT).also { it.topMargin = dp(8) })
        val scroll = ScrollView(activity)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        body.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also { it.marginStart = dp(8) })
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        skin()
    }

    /** The colours of the frame of the screen, set again each time it opens, because the theme may have changed. */
    fun skin() {
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        barView?.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        titleView?.setTextColor(Sunlight.c(Color.WHITE))
        search.setTextColor(Sunlight.c(Color.WHITE))
        search.setHintTextColor(Sunlight.c(0xFF90A4AE.toInt()))
        search.background = CardStyle.panelBackground(activity.resources.displayMetrics.density, Sunlight.c(0xE6232C36.toInt()))
    }

    fun show() {
        skin()
        query = ""
        search.setText("")
        refresh()
        shown = true
        hideKeyboard()
        Anim.panelIn(root)
    }

    fun hide() {
        hideKeyboard()
        shown = false
        Anim.panelOut(root)
    }

    private fun hideKeyboard() {
        searchHandler.removeCallbacks(runSearch)
        search.clearFocus()
        root.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // ------------------------------------------------------------------ building blocks

    private fun section(title: String) {
        val t = TextView(activity)
        t.text = title
        t.textSize = 14f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Sunlight.c(0xFF9CDCFE.toInt()))
        t.setPadding(0, dp(16), 0, dp(8))
        t.tag = SECTION_TAG
        target.addView(t)
    }

    private fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setPaddingRelative(dp(16), dp(8), dp(16), dp(8))
        b.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)
        b.isEnabled = enabled
        b.setOnClickListener { onClick() }
        target.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(4)
            it.bottomMargin = dp(4)
        })
    }

    /**
     * A setting that cycles through its values when tapped: the label on the left, the value on the right in blue. The row looks
     * like the buttons (the themed button's own background), is at least 48 dp tall, and both texts wrap when they are long.
     */
    private fun valueRow(label: String, value: String, indent: Boolean = false, onClick: () -> Unit) {
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)
        row.setPadding(dp(if (indent) 24 else 16), dp(8), dp(16), dp(8))
        row.background = CardStyle.buttonBackground(activity.resources.displayMetrics.density)
        row.isClickable = true
        if (indent) row.tag = INDENT_TAG
        val l = TextView(activity)
        l.text = label
        l.textSize = 15f
        l.setTextColor(Sunlight.c(Color.WHITE))
        val v = TextView(activity)
        v.text = value
        v.textSize = 15f
        v.setTextColor(Sunlight.c(CardStyle.ACCENT))
        v.setTypeface(null, Typeface.BOLD)
        v.gravity = Gravity.END
        v.maxLines = 2
        row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f))
        row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
        row.setOnClickListener { onClick() }
        target.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(4) })
    }

    private fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(activity)
        sw.text = label
        sw.setTextColor(Sunlight.c(Color.WHITE))
        sw.textSize = 15f
        sw.isChecked = checked
        sw.setPadding(dp(8), dp(8), dp(8), dp(8))
        sw.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)
        squareSwitch(sw)
        sw.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        target.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** The system switch is round; this one is a square thumb in a square track, to match everything else. */
    private fun squareSwitch(sw: Switch) {
        val d = activity.resources.displayMetrics.density
        fun box(fill: Int, edge: Int, w: Float, h: Float): GradientDrawable {
            val g = GradientDrawable()
            g.setColor(fill)
            g.setStroke((1f * d).toInt().coerceAtLeast(1), edge)
            g.setSize((w * d).toInt(), (h * d).toInt())
            return g
        }
        val thumb = StateListDrawable()
        thumb.addState(intArrayOf(android.R.attr.state_checked), box(Sunlight.c(CardStyle.ACCENT), Sunlight.c(0xFFFFFFFF.toInt()), 22f, 22f))
        thumb.addState(intArrayOf(), box(Sunlight.c(0xFFB0BEC5.toInt()), Sunlight.c(0xFF78909C.toInt()), 22f, 22f))
        val track = StateListDrawable()
        track.addState(intArrayOf(android.R.attr.state_checked), box(Sunlight.c(0x884FC3F7.toInt()), Sunlight.c(CardStyle.ACCENT), 46f, 26f))
        track.addState(intArrayOf(), box(Sunlight.c(0x66263238), Sunlight.c(0xFF546E7A.toInt()), 46f, 26f))
        sw.thumbDrawable = thumb
        sw.trackDrawable = track
    }

    /** A switch for one overlay tile and, when it is on, its size, position and opacity. */
    private fun tileControls(tile: String, title: String, extra: () -> Unit) {
        toggle(title, host.tileOn(tile)) {
            host.setTileOn(tile, it)
            refresh()
        }
        if (host.tileOn(tile)) {
            valueRow(tr("Size"), host.tileSizeLabel(tile), indent = true) {
                host.cycleTileSize(tile)
                refresh()
            }
            valueRow(tr("Position"), host.tilePositionLabel(tile), indent = true) {
                host.cycleTilePosition(tile)
                refresh()
            }
            valueRow(tr("Opacity"), host.tileOpacityLabel(tile), indent = true) {
                host.cycleTileOpacity(tile)
                refresh()
            }
            extra()
        }
    }

    private fun note(text: String) {
        val t = TextView(activity)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Sunlight.c(0xFFBDBDBD.toInt()))
        t.setPadding(dp(8), dp(4), dp(8), dp(4))
        t.tag = NOTE_TAG
        target.addView(t)
    }

    private fun confirm(message: String, positive: String, onYes: () -> Unit) {
        Immersive.show(
            Sunlight.builder(activity)
                .setMessage(message)
                .setPositiveButton(positive) { _, _ -> onYes() }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

    // ------------------------------------------------------------------ the tabs and the search

    private fun tabs(): List<Tab> = listOf(
        Tab("general", tr("General"), "language", listOf(Part("Language / Dil / Sprache / \u8BED\u8A00") { languageContent() })),
        Tab("flight", tr("Aircraft and flight"), "aircraft", listOf(
            Part(null) { aircraftContent() },
            Part(tr("Approval window")) { windowContent() },
            Part(tr("Landing, link loss and packs")) { afterContent() })),
        Tab("video", tr("Video and overlays"), "video", listOf(Part(null) { videoContent() }, Part(null) { overlaysContent() })),
        Tab("dashboard", tr("Dashboard"), "dashboard", listOf(Part(null) { dashboardContent() })),
        Tab("tracker", tr("Antenna tracker"), "tracker", listOf(Part(null) { trackerContent() })),
        Tab("logs", tr("Flight logs"), "logs", listOf(Part(null) { logsContent() })),
        Tab("rules", tr("Rules and backup"), "rules", listOf(Part(null) { rulesContent() })),
        Tab("system", tr("System"), "diagnostics", listOf(
            Part(tr("Diagnostics")) { diagnosticsContent() },
            Part(tr("Updates")) { updatesContent() },
            Part(tr("About")) { aboutContent() })))

    private fun selectTab(id: String) {
        selectedTab = id
        hideKeyboard()
        if (query.isNotEmpty()) {
            query = ""
            search.setText("")
        }
        refresh()
    }

    /** One entry of the rail: an icon and the name of the tab; the open tab is lit. */
    private fun railItem(tab: Tab, selected: Boolean) {
        val t = TextView(activity)
        t.text = tab.title
        t.textSize = 15f
        t.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        t.setTextColor(Sunlight.c(if (selected) Color.WHITE else CardStyle.LABEL))
        t.gravity = Gravity.CENTER_VERTICAL
        t.minHeight = dp(RAIL_ITEM_DP)
        t.setPaddingRelative(dp(12), dp(4), dp(8), dp(4))
        t.compoundDrawablePadding = dp(12)
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(
            Icons.drawable(activity, tab.icon, if (selected) Sunlight.c(CardStyle.ACCENT) else Sunlight.c(CardStyle.ICON), 24), null, null, null
        )
        t.background = CardStyle.panelBackground(activity.resources.displayMetrics.density, Sunlight.c(if (selected) 0xF22C3844.toInt() else 0xE6232C36.toInt()))
        CardStyle.fitText(t, 11, 15)
        t.isClickable = true
        t.setOnClickListener { selectTab(tab.id) }
        rail.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(4) })
    }

    /** Builds the rows of a tab into the current target, with the heading of each part. */
    private fun buildParts(tab: Tab) {
        for (p in tab.parts) {
            if (p.title != null) section(p.title)
            p.build()
        }
    }

    /** The text that names a row, for the search; null for headings and notes. */
    private fun labelOf(v: View): String? = when {
        v.tag == SECTION_TAG || v.tag == NOTE_TAG -> null
        v is Switch -> v.text.toString()
        v is Button -> v.text.toString()
        v is LinearLayout && v.childCount > 0 && v.getChildAt(0) is TextView -> (v.getChildAt(0) as TextView).text.toString()
        else -> null
    }

    private fun resultHeader(text: String) {
        val t = TextView(activity)
        t.text = text
        t.textSize = 13f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Sunlight.c(0xFF9CDCFE.toInt()))
        t.setPadding(dp(4), dp(12), 0, dp(4))
        list.addView(t)
    }

    /**
     * The settings that match the words, from every tab. Every tab is built into a hidden container, the rows whose label matches are moved
     * into the list (they are the real rows, so they work right there), grouped under "Tab > Heading". The size, position and opacity rows
     * of an overlay follow their switch when it matches.
     */
    private fun buildSearchResults() {
        val words = SettingsIndex.words(query)
        var found = 0
        for (tab in tabs()) {
            val temp = LinearLayout(activity)
            temp.orientation = LinearLayout.VERTICAL
            target = temp
            buildParts(tab)
            target = list
            val rows = ArrayList<View>()
            for (i in 0 until temp.childCount) rows.add(temp.getChildAt(i))
            var heading = ""
            var parentMatched = false
            var lastHeader: String? = null
            for (v in rows) {
                if (v.tag == SECTION_TAG) {
                    heading = (v as TextView).text.toString()
                    parentMatched = false
                    continue
                }
                val label = labelOf(v) ?: continue
                val hit = if (v.tag == INDENT_TAG) parentMatched else SettingsIndex.matches(label, words)
                if (v.tag != INDENT_TAG) parentMatched = hit
                if (!hit) continue
                val header = if (heading.isEmpty()) tab.title else tab.title + " > " + heading
                if (header != lastHeader) {
                    resultHeader(header)
                    lastHeader = header
                }
                temp.removeView(v)
                list.addView(v)
                found++
            }
        }
        if (found == 0) note(tr("Nothing found."))
    }

    // ------------------------------------------------------------------ the screen

    fun refresh() {
        refreshRows()
        Look.apply(root)
    }

    private fun refreshRows() {
        list.removeAllViews()
        rail.removeAllViews()
        target = list
        val searching = query.isNotBlank()
        val all = tabs()
        for (t in all) railItem(t, !searching && t.id == selectedTab)
        if (searching) buildSearchResults() else buildParts(all.firstOrNull { it.id == selectedTab } ?: all.first())
    }

    // ------------------------------------------------------------------ the rows of each block (the old groups)

    private fun languageContent() {
        valueRow(tr("Language"), host.languageLabel()) { host.cycleLanguage() }
        valueRow(tr("Speech language"), host.speechLanguageLabel()) {
            host.cycleSpeechLanguage()
            refresh()
        }
        note(tr("The screen and the voice can use different languages. Your rules keep their own words: \"Reset the rules to the defaults\" loads the starting rules in the voice's language."))

        section(tr("Display"))
        toggle(tr("Sunlight theme"), host.sunlightOn()) {
            host.setSunlightOn(it)
            refresh()
        }
        valueRow(tr("Shape"), host.shapeLabel()) {
            host.cycleShape()
            refresh()
        }
        note(tr("The shape of cards, tiles and rows: two cut corners (the default), four cut corners, corner brackets, soft corners or plain rectangles. Warnings keep a solid fill."))
        note(tr("Dark text on light backgrounds for the menu, the settings, the other screens and the dashboard, and stronger warnings. In direct sun this reads better than light on dark. The MENU has a Sunlight tile as a quick switch."))
        toggle(tr("Always show the MENU button"), host.menuAlways()) { host.setMenuAlways(it) }
        note(tr("Off: the button hides and appears when you touch the screen."))
        toggle(tr("Start-up splash screen (animated, about 3 seconds)"), host.splashOn()) { host.setSplashOn(it) }
        note(tr("Tap the top edge three times, away from MENU, to save a screenshot to Pictures/AX-HUD."))
    }

    private fun aircraftContent() {
        section(tr("Aircraft"))
        valueRow(tr("Active aircraft"), host.aircraftName()) { host.chooseAircraft() }
        action(tr("Manage the aircraft (add, rename, delete)...")) { host.manageAircraft() }
        note(tr("Each aircraft keeps its own rules, warning rules, checks, battery profiles and packs, channel bars, stick mapping and arm switch. Overlay looks, language and updates are shared."))

        section(tr("Battery"))
        valueRow(tr("Active battery profile"), host.batteryLabel()) {
            host.cycleBattery()
            refresh()
        }
        action(tr("Edit the packs of this aircraft...")) { host.editPacks() }
        action(tr("Edit the battery profiles (cells and voltage limits)...")) { host.editBatteries() }
        note(tr("Rules and checks can use the value cell (volts per cell) and the limits \$low, \$crit and \$full of the active profile, for example: cell < \$low -> battery low. Switching the profile changes the numbers, not the rules."))
    }

    private fun windowContent() {
        note(tr("The time you are approved to fly, for example the two hours the authority gave you. The radio shows the time left, speaks warnings while the aircraft is armed, and warns when you arm outside the window. It repeats every day and uses the clock of this radio, which says {0} now.", host.windowClockNote()))
        toggle(tr("Use an approval window"), host.windowOn()) {
            host.setWindowOn(it)
            refresh()
        }
        if (host.windowOn()) {
            valueRow(tr("Opens at"), host.windowStartLabel()) { host.pickWindowStart() }
            valueRow(tr("Closes at"), host.windowEndLabel()) { host.pickWindowEnd() }
            action(tr("Two hours from now")) {
                host.windowTwoHours()
                refresh()
            }
            valueRow(tr("Warn at (minutes left)"), host.windowWarnLabel()) { host.editWindowWarn() }
            toggle(tr("Speak the warnings"), host.windowSpeechOn()) { host.setWindowSpeechOn(it) }
            note(tr("The value win_left (minutes left, negative after the window has ended) can be used in value cards, charts, rules and logs."))
        }
    }

    private fun afterContent() {
        toggle(tr("Speak a debrief after landing"), host.debriefOn()) { host.setDebriefOn(it) }
        note(tr("After a flight of 20 seconds or more: flight time, distance, capacity used, efficiency, lowest cell and the voltage the pack rests at."))
        toggle(tr("Speak the last position when the link is lost"), host.lossSpeechOn()) { host.setLossSpeechOn(it) }
        note(tr("When the link is lost in flight, a card shows the last position with its distance and bearing from home, and the position is saved on this radio."))
        action(tr("Last known position...")) { host.openPosition() }
        action(tr("Packs: totals per pack...")) { host.openPackBook() }
    }

    private fun videoContent() {
        section(tr("Video"))
        valueRow(tr("Camera (HDMI input)"), host.cameraId()) { host.chooseCamera() }
        valueRow(tr("Rotate video"), host.rotation().toString() + "\u00B0") {
            host.rotateVideo()
            refresh()
        }
        valueRow(tr("Scale"), if (host.fill()) tr("Fill (stretch)") else tr("Fit")) {
            host.toggleFill()
            refresh()
        }

        section(tr("Speech"))
        toggle(tr("Speech on"), host.speechOn()) { host.setSpeechOn(it) }
        action(tr("Speak a test sentence")) { host.speakTest() }
    }

    private fun overlaysContent() {
        section(tr("OSD overlays (drawn over the video)"))
        toggle(tr("Stick view: roll, pitch, yaw and throttle"), host.sticksOn()) {
            host.setSticksOn(it)
            refresh()
        }
        if (host.sticksOn()) {
            valueRow(tr("Stick size"), host.sticksSizeLabel()) {
                host.cycleSticksSize()
                refresh()
            }
            valueRow(tr("Stick position"), host.sticksPositionLabel()) {
                host.cycleSticksPosition()
                refresh()
            }
            valueRow(tr("Stick opacity"), host.sticksOpacityLabel()) {
                host.cycleSticksOpacity()
                refresh()
            }
        }
        action(tr("Stick mode, channels and direction...")) { host.editStickChannels() }
        toggle(tr("Channel bars beside the sticks (aux switches)"), host.channelBarsOn()) { host.setChannelBarsOn(it) }
        action(tr("Channel bars: names, channels, on and off...")) { host.editChannelBars() }
        note(tr("Tip: press and hold the stick view on the video to open the channel bars."))
        toggle(tr("Warning status bar"), host.warningBarOn()) { host.setWarningBarOn(it) }
        valueRow(tr("Warning bar position"), host.warningBarPositionLabel()) {
            host.cycleWarningBarPosition()
            refresh()
        }
        action(tr("Edit the warning rules (thresholds and logic)...")) { host.editWarningRules() }
        action(tr("Reset the warning rules to the defaults")) {
            confirm(tr("Replace your warning rules with the defaults? Export all settings first if you want to keep the current ones."), tr("Reset")) { host.resetWarningRules() }
        }
        action(tr("Preview the warning bar (4 seconds)")) { host.previewWarnings() }
        note(tr("Warnings show while their condition is true, so thresholds and logic are yours to set."))

        section(tr("Flight overlays (statistics, radar, checklist)"))
        note(tr("The arm switch starts and stops a flight: the statistics start over when you arm and stay readable after landing."))
        action(tr("Arm detection: channel, thresholds, cells in the pack...")) { host.editArmDetection() }
        tileControls("stats", tr("Flight statistics: max speed, max height, peak current")) {
            action(tr("Rows and current limit...")) { host.editStatsRows() }
        }
        tileControls("radar", tr("Radar map: home in the center, your track and range rings")) {}
        tileControls("chart", tr("Live charts: rolling graphs of telemetry values")) {
            action(tr("Charts: choose the values and the time window...")) { host.editCharts() }
        }
        tileControls("check", tr("Pre-flight checklist (shown while disarmed)")) {
            action(tr("Edit the checks (thresholds and logic)...")) { host.editCheckRules() }
            action(tr("Reset the checks to the defaults")) {
                confirm(tr("Replace your checks with the defaults? Export all settings first if you want to keep the current ones."), tr("Reset")) { host.resetCheckRules() }
            }
        }

        section(tr("Layout of the cards"))
        action(tr("Move and resize the cards with your finger...")) { host.editLayout() }
        action(tr("Put every card back in its corner at normal size")) {
            confirm(tr("Put every card back in its corner at normal size? The places and sizes you chose will be forgotten."), tr("Reset")) { host.resetLayout() }
        }
        note(tr("Drag a card to move it and drag its square handle to resize it. Cards that are switched off are not shown. The Size setting of a card is its starting size; resizing multiplies it."))
    }

    private fun dashboardContent() {
        note(tr("A second page with cards only and no video, for goggles or for a second pair of eyes. Switch pages with the Dashboard tile in the MENU."))
        action(tr("Edit the dashboard layout...")) { host.editDashboard() }
        action(tr("Save current dashboard layout")) { host.saveDashboardLayout() }
        action(tr("Load a saved dashboard layout")) { host.loadDashboardLayout() }
        action(tr("Delete a saved dashboard layout")) { host.deleteDashboardLayout() }
        action(tr("Choose a dashboard preset...")) { host.chooseDashboardPreset() }
        action(tr("Reset the dashboard layout")) {
            confirm(tr("Replace the dashboard layout with the first one?"), tr("Reset")) { host.resetDashboard() }
        }
        toggle(tr("Horizon: invert pitch"), host.horizonInvertPitch()) { host.setHorizonInvertPitch(it) }
        toggle(tr("Horizon: invert roll"), host.horizonInvertRoll()) { host.setHorizonInvertRoll(it) }
        note(tr("Tilt the aircraft nose down: the horizon should show more ground. If it shows the opposite, switch the pitch inversion; the same for roll."))
    }

    private fun trackerContent() {
        note(tr("An antenna tracker points your antennas at the aircraft: an ESP32 with two servos, a GPS and a compass. The radio sends the aircraft's position over Bluetooth; the tracker does the pointing. Build instructions: the tracker folder of the project."))
        toggle(tr("Use an antenna tracker"), host.trackerOn()) {
            host.setTrackerOn(it)
            refresh()
        }
        if (host.trackerOn()) {
            valueRow(tr("Tracker"), host.trackerDeviceLabel()) { host.trackerFind() }
            note(tr("Android asks for the location permission to search for Bluetooth devices. AX-HUD does not read your location."))
            note(host.trackerStatusLine())
            action(tr("Follow the aircraft")) { host.trackerCommand(TrackerKind.AUTO) }
            action(tr("Hold the antennas where they are")) { host.trackerCommand(TrackerKind.HOLD) }
            action(tr("Center the antennas (straight ahead)")) { host.trackerCommand(TrackerKind.CENTRE) }
            action(tr("Test sweep (30 seconds)")) { host.trackerCommand(TrackerKind.SWEEP) }
            action(tr("Compass calibration: start (turn the tracker through a full circle)")) { host.trackerCommand(TrackerKind.CAL_START) }
            action(tr("Compass calibration: save")) { host.trackerCommand(TrackerKind.CAL_STOP) }
            action(tr("Set the magnetic declination...")) { host.trackerDeclinationDialog() }
            action(tr("Set the antenna height above the launch point...")) { host.trackerHeightDialog() }
        }
    }

    private fun logsContent() {
        note(tr("Writes a log of every flight, from arming to disarming: the values you choose, several rows per second. The files are plain CSV that a spreadsheet opens, and the log viewer can replay them on the radio."))
        toggle(tr("Log every flight"), host.logOn()) {
            host.setLogOn(it)
            refresh()
        }
        if (host.logOn()) {
            valueRow(tr("Rows per second"), host.logRateLabel()) {
                host.cycleLogRate()
                refresh()
            }
            note(tr("Rows are written up to 20 times a second. The sticks change that often; the values from the aircraft (battery, GPS, attitude) change only as often as your link sends them, about once a second at a slow ELRS telemetry ratio (1:32 at 150 Hz). A faster ratio, such as 1:16 or 1:8, gives a sharper log."))
            valueRow(tr("Values in the log"), host.logFieldsLabel()) { host.editLogFields() }
            valueRow(tr("Flights to keep"), host.logKeepLabel()) {
                host.cycleLogKeep()
                refresh()
            }
        }
        action(tr("Flight logs: list, replay and delete...")) { host.openLogViewer() }
        val status = host.logStatus()
        if (status.isNotEmpty()) note(status)
        note(tr("The logs hold GPS positions and stay on this radio, in the folder {0}", host.logFolder()))
    }

    private fun rulesContent() {
        section(tr("Sound rules"))
        action(tr("Export sound rules to a file...")) { host.exportRules() }
        action(tr("Import sound rules from a file...")) { host.importRules() }
        action(tr("Restore the rules from before the last import, reset or restore"), host.hasRulesBackup()) {
            confirm(tr("Swap back to the previous rules? The current rules are kept, so you can swap again."), tr("Restore")) {
                host.restoreRules()
                refresh()
            }
        }
        action(tr("Reset the rules to the defaults")) {
            confirm(tr("Replace your rules with the defaults? Your current rules are kept as a backup."), tr("Reset")) {
                host.resetRules()
                refresh()
            }
        }
        action(tr("Edit the rules as text (advanced)")) { host.openRulesText() }

        section(tr("Backup of all settings"))
        action(tr("Export all settings to a file...")) { host.exportSettings() }
        action(tr("Import all settings from a file...")) { host.importSettings() }
        action(tr("Restore the settings from before the last import"), host.hasSettingsBackup()) {
            confirm(tr("Swap back to the settings from before the last import? The current settings are kept, so you can swap again."), tr("Restore")) { host.restoreSettings() }
        }
        note(tr("One file with everything: sound rules, warning rules, checks, overlays, channel bars, battery profiles and language. The sound rules also have their own export above."))
    }

    private fun diagnosticsContent() {
        note(tr("If something goes wrong, this page collects what a bug report needs: versions, the device, the state of the radio link and its start-up timing, a digest of the settings and the last events. It holds no GPS positions, no aircraft or pack names and nothing personal. Read it before you send it."))
        action(tr("Diagnostics and bug report...")) { host.openDiagnostics() }
        toggle(tr("Show the variables panel (every value the radio sends)"), host.variablesOn()) { host.setVariablesOn(it) }
        toggle(tr("Test channels: sliders instead of the radio"), host.testChannelsOn()) { host.setTestChannelsOn(it) }
        action(tr("Show the start-up timings of the last launches...")) { host.showStartupTimings() }
        note(tr("The radio link starts on a background thread and keeps running while the app's own screens, file pickers and rebuilds come and go; it stops when you leave the app. The SDK itself needs about 12 seconds to open the radio's serial port; the timings show where the time goes."))
        note(tr("Close Settings to see the panels over the video."))
    }

    private fun updatesContent() {
        note(host.updateLine())
        action(tr("Check for updates now")) { host.checkUpdatesNow() }
        valueRow(tr("Update channel"), host.updateChannelLabel()) {
            host.cycleUpdateChannel()
            refresh()
        }
        toggle(tr("Check at start-up (needs internet)"), host.updateAuto()) { host.setUpdateAuto(it) }
        note(tr("Android asks you to confirm every update. The first time it also asks you to allow installs from AX-HUD. Never update during a flight."))
    }

    private fun aboutContent() {
        note(host.about())
        action(tr("Show the welcome guide...")) { host.openGuide() }
        action(tr("About AX-HUD, licenses and thanks...")) { host.openAbout() }
    }

    private companion object {
        const val RAIL_DP = 168
        const val RAIL_ITEM_DP = 40            // full width; eight of them (40 dp and 4 dp between) must fit the height of the 416 dp screen
        const val SECTION_TAG = "section"
        const val NOTE_TAG = "note"
        const val INDENT_TAG = "indent"
    }
}
