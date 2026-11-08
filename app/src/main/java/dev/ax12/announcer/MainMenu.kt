// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The main menu as a panel inside the screen, not a popup window. A popup window takes the
 * keyboard focus, and Android answers that by showing its navigation buttons; a view does not.
 * Eight large square tiles in two rows of four: an icon, a name, and, where there is something to show, the state (speech on or off,
 * the pack in use, the aircraft in use) in words and in a coloured bar. Tapping outside the panel closes it.
 */
class MainMenu(
    private val activity: Activity,
    private val speechOn: () -> Boolean,
    private val onRules: () -> Unit,
    private val onToggleSpeech: () -> Unit,
    private val onSettings: () -> Unit,
    private val batteryName: () -> String,
    private val onBattery: () -> Unit,
    private val aircraftName: () -> String,
    private val onAircraft: () -> Unit,
    private val dashboardOn: () -> Boolean,
    private val onDashboard: () -> Unit,
    private val onLogs: () -> Unit,
    private val onExit: () -> Unit
) {
    val root = FrameLayout(activity)
    private var shown = false
    private val density = activity.resources.displayMetrics.density

    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** One tile: icon, name, state, and a bar along the bottom edge that is coloured when the tile has a state. */
    private inner class Tile(private val label: String, private val closeMenu: Boolean, private val onTap: () -> Unit) {
        val view = FrameLayout(activity)
        private val icon = ImageView(activity)
        private val name = TextView(activity)
        private val state = TextView(activity)
        private val bar = View(activity)

        init {
            view.isClickable = true
            view.background = CardStyle.stateBackground(density, 0xE6232C36.toInt(), 0xF24F6272.toInt(), 0xFF546E7A.toInt())
            val column = LinearLayout(activity)
            column.orientation = LinearLayout.VERTICAL
            column.gravity = Gravity.CENTER_HORIZONTAL
            column.setPadding(dp(8), dp(12), dp(8), dp(12))
            column.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
            name.text = label
            name.textSize = 13f
            name.setTypeface(null, Typeface.BOLD)
            name.setTextColor(Color.WHITE)
            name.gravity = Gravity.CENTER
            CardStyle.fitText(name, 10, 13)
            column.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
            state.textSize = 11f
            state.setTextColor(CardStyle.ACCENT)
            state.gravity = Gravity.CENTER
            CardStyle.fitText(state, 9, 11)
            column.addView(state, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4) })
            view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.BOTTOM))
            view.setOnClickListener {
                if (closeMenu) hide()
                onTap()
                refresh()
            }
            set("rules", "", 0)
        }

        /** @param barColor the colour of the bar along the bottom (0 = none) */
        fun set(iconName: String, stateText: String, barColor: Int, iconColor: Int = CardStyle.ICON, nameColor: Int = Color.WHITE, nameText: String? = null) {
            if (nameText != null) name.text = nameText
            icon.setImageDrawable(Icons.drawable(activity, iconName, iconColor, 32))
            name.setTextColor(nameColor)
            state.text = stateText
            state.visibility = if (stateText.isEmpty()) View.GONE else View.VISIBLE
            bar.setBackgroundColor(if (barColor == 0) Color.TRANSPARENT else barColor)
        }
    }

    private val rulesTile = Tile(tr("Sound rules"), true) { onRules() }
    private val speechTile = Tile(tr("Speech"), false) { onToggleSpeech() }          // stays open, so the new state can be seen
    private val batteryTile = Tile(tr("Battery"), false) { onBattery() }              // the same: the new pack shows on the tile
    private val aircraftTile = Tile(tr("Aircraft"), true) { onAircraft() }
    private val dashboardTile = Tile(tr("Dashboard"), true) { onDashboard() }       // the other page: cards only, no video
    private val logsTile = Tile(tr("Flight logs"), true) { onLogs() }
    private val settingsTile = Tile(tr("Settings"), true) { onSettings() }
    private val exitTile = Tile(tr("Exit"), true) { onExit() }

    init {
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(32).toFloat()
        root.setOnClickListener { hide() }

        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.background = CardStyle.panelBackground(density, CardStyle.MENU)     // the video shows through
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.isClickable = true
        for (row in listOf(listOf(rulesTile, speechTile, batteryTile, aircraftTile), listOf(dashboardTile, logsTile, settingsTile, exitTile))) {
            val line = LinearLayout(activity)
            line.orientation = LinearLayout.HORIZONTAL
            for (t in row) line.addView(t.view, LinearLayout.LayoutParams(dp(104), dp(112)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            box.addView(line)
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.topMargin = dp(56)
        lp.rightMargin = dp(8)
        root.addView(box, lp)
        refresh()
    }

    /** Brings every tile's state up to date. */
    private fun refresh() {
        rulesTile.set("rules", "", 0)
        speechTile.set(if (speechOn()) "speaker" else "speaker_off", if (speechOn()) tr("On") else tr("Off"), if (speechOn()) CardStyle.OK else CardStyle.BAD)
        batteryTile.set("battery", batteryName(), CardStyle.ACCENT)
        aircraftTile.set("aircraft", aircraftName(), CardStyle.ACCENT)
        dashboardTile.set(if (dashboardOn()) "video" else "dashboard", "", 0, nameText = if (dashboardOn()) tr("Video") else tr("Dashboard"))
        logsTile.set("logs", "", 0)
        settingsTile.set("gear", "", 0)
        exitTile.set("exit", "", 0, iconColor = 0xFFFF8A80.toInt(), nameColor = 0xFFFF8A80.toInt())          // apart from the others: a warning colour
    }

    fun show() {
        refresh()
        shown = true
        Anim.menuIn(root)
    }

    fun hide() {
        shown = false
        Anim.menuOut(root)
    }
}
