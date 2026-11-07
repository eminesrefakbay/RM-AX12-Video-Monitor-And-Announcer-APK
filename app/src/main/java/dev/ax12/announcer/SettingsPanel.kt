// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
 * The settings screen: everything that is not a daily action. Video, speech, rules files,
 * diagnostics. It opens over the video from MENU > Settings.
 */
class SettingsPanel(private val activity: Activity, private val host: SettingsHost) {
    val root = LinearLayout(activity)
    private val list = LinearLayout(activity)
    private var target: LinearLayout = list          // where building blocks add their rows: the list, or the open group
    private var openGroup = ""                      // which group is open (one at a time); kept while the app runs

    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(CardStyle.SEE_THROUGH)     // the video stays visible behind the settings, so you can see it is there
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(24).toFloat()          // above the MENU button and every other view

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(CardStyle.BLOCK)
        bar.setPadding(dp(8), dp(0), dp(8), dp(0))
        val title = TextView(activity)
        title.text = tr("Settings")
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(activity)
        close.text = tr("Close")
        close.setOnClickListener { hide() }
        bar.addView(close)
        root.addView(bar)

        val scroll = ScrollView(activity)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show() {
        refresh()
        shown = true
        Anim.panelIn(root)
    }

    fun hide() {
        shown = false
        Anim.panelOut(root)
    }

    // ------------------------------------------------------------------ building blocks

    private fun section(title: String) {
        val t = TextView(activity)
        t.text = title
        t.textSize = 14f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(0xFF9CDCFE.toInt())
        t.setPadding(0, dp(16), 0, dp(8))
        target.addView(t)
    }

    private fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.isEnabled = enabled
        b.setOnClickListener { onClick() }
        target.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        row.background = Button(activity).background?.constantState?.newDrawable()?.mutate()
        row.isClickable = true
        val l = TextView(activity)
        l.text = label
        l.textSize = 15f
        l.setTextColor(Color.WHITE)
        val v = TextView(activity)
        v.text = value
        v.textSize = 15f
        v.setTextColor(CardStyle.ACCENT)
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
        sw.setTextColor(Color.WHITE)
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
        thumb.addState(intArrayOf(android.R.attr.state_checked), box(CardStyle.ACCENT, 0xFFFFFFFF.toInt(), 22f, 22f))
        thumb.addState(intArrayOf(), box(0xFFB0BEC5.toInt(), 0xFF78909C.toInt(), 22f, 22f))
        val track = StateListDrawable()
        track.addState(intArrayOf(android.R.attr.state_checked), box(0x884FC3F7.toInt(), CardStyle.ACCENT, 46f, 26f))
        track.addState(intArrayOf(), box(0x66263238, 0xFF546E7A.toInt(), 46f, 26f))
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
        t.setTextColor(0xFFBDBDBD.toInt())
        t.setPadding(dp(8), dp(4), dp(8), dp(4))
        target.addView(t)
    }

    private fun confirm(message: String, positive: String, onYes: () -> Unit) {
        Immersive.show(
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(positive) { _, _ -> onYes() }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }


    /** A tappable group header with an icon; its rows are built only while it is open, and only one group is open at a time. */
    private fun group(id: String, title: String, icon: String, content: () -> Unit) {
        val open = openGroup == id
        val header = TextView(activity)
        header.text = title
        header.textSize = 17f
        header.setTypeface(null, Typeface.BOLD)
        header.setTextColor(Color.WHITE)
        header.gravity = Gravity.CENTER_VERTICAL
        header.minHeight = dp(56)
        header.setPadding(dp(16), dp(8), dp(16), dp(8))
        header.compoundDrawablePadding = dp(16)
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(
            Icons.drawable(activity, icon, if (open) CardStyle.ACCENT else CardStyle.ICON, 26), null,
            Icons.drawable(activity, if (open) "chevron_down" else "chevron_right", CardStyle.LABEL, 22), null
        )
        header.background = CardStyle.panelBackground(activity.resources.displayMetrics.density, if (open) 0xF22C3844.toInt() else 0xE6232C36.toInt())
        CardStyle.fitText(header, 13, 17)
        header.isClickable = true
        header.setOnClickListener {
            openGroup = if (open) "" else id
            refresh()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        list.addView(header, lp)
        if (open) {
            val box = LinearLayout(activity)
            box.orientation = LinearLayout.VERTICAL
            box.background = CardStyle.panelBackground(activity.resources.displayMetrics.density, CardStyle.BLOCK)     // rows and notes stay readable over moving video
            box.setPadding(dp(8), dp(8), dp(8), dp(8))
            list.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            target = box
            content()
            target = list
        }
    }

    // ------------------------------------------------------------------ the screen

    fun refresh() {
        list.removeAllViews()
        target = list

        group("language", "Language / Dil / Sprache / \u8BED\u8A00", "language") {
            valueRow(tr("Language"), host.languageLabel()) { host.cycleLanguage() }
            valueRow(tr("Speech language"), host.speechLanguageLabel()) {
                host.cycleSpeechLanguage()
                refresh()
            }
            note(tr("The screen and the voice can use different languages. Your rules keep their own words: \"Reset the rules to the defaults\" loads the starting rules in the voice's language."))

            section(tr("Display"))
            toggle(tr("Always show the MENU button"), host.menuAlways()) { host.setMenuAlways(it) }
            note(tr("Off: the button hides and appears when you touch the screen."))
            toggle(tr("Start-up splash screen (logo and version, 2 seconds)"), host.splashOn()) { host.setSplashOn(it) }

        }
        group("aircraft", tr("Aircraft and battery"), "aircraft") {
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
        group("overlays", tr("Overlays on the video"), "overlays") {
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
        group("video", tr("Video and speech"), "video") {
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
        group("logs", tr("Flight logs"), "logs") {
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
        group("rules", tr("Rules and backup"), "rules") {
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
        group("updates", tr("Updates"), "update") {
            note(host.updateLine())
            action(tr("Check for updates now")) { host.checkUpdatesNow() }
            valueRow(tr("Update channel"), host.updateChannelLabel()) {
                host.cycleUpdateChannel()
                refresh()
            }
            toggle(tr("Check at start-up (needs internet)"), host.updateAuto()) { host.setUpdateAuto(it) }
            note(tr("Android asks you to confirm every update. The first time it also asks you to allow installs from AX-HUD. Never update during a flight."))

        }
        group("diagnostics", tr("Diagnostics"), "diagnostics") {
            toggle(tr("Show the variables panel (every value the radio sends)"), host.variablesOn()) { host.setVariablesOn(it) }
            toggle(tr("Test channels: sliders instead of the radio"), host.testChannelsOn()) { host.setTestChannelsOn(it) }
            action(tr("Show the start-up timings of the last launches...")) { host.showStartupTimings() }
            note(tr("The radio link starts on a background thread and keeps running while the app's own screens, file pickers and rebuilds come and go; it stops when you leave the app. The SDK itself needs about 12 seconds to open the radio's serial port; the timings show where the time goes."))
            note(tr("Close Settings to see the panels over the video."))

        }
        group("about", tr("About"), "about") {
            note(host.about())
            action(tr("Show the welcome guide...")) { host.openGuide() }
            action(tr("About AX-HUD, licenses and thanks...")) { host.openAbout() }
        }
    }
}
