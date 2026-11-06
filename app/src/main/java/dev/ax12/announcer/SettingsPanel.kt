package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
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
    fun menuAlways(): Boolean
    fun setMenuAlways(on: Boolean)
    fun variablesOn(): Boolean
    fun setVariablesOn(on: Boolean)
    fun testChannelsOn(): Boolean
    fun setTestChannelsOn(on: Boolean)
    fun about(): String

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
    fun warningBarOn(): Boolean
    fun setWarningBarOn(on: Boolean)
    fun warningBarPositionLabel(): String
    fun cycleWarningBarPosition()
    fun editWarningRules()
    fun previewWarnings()
}

/**
 * The settings screen: everything that is not a daily action. Video, speech, rules files,
 * diagnostics. It opens over the video from MENU > Settings.
 */
class SettingsPanel(private val activity: Activity, private val host: SettingsHost) {
    val root = LinearLayout(activity)
    private val list = LinearLayout(activity)

    val isShowing: Boolean get() = root.visibility == View.VISIBLE

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xF01E1E1E.toInt())
        root.setPadding(dp(16), dp(10), dp(16), dp(10))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(24).toFloat()          // above the MENU button and every other view

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(activity)
        title.text = "Settings"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(activity)
        close.text = "Close"
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
        root.visibility = View.VISIBLE
    }

    fun hide() {
        root.visibility = View.GONE
    }

    // ------------------------------------------------------------------ building blocks

    private fun section(title: String) {
        val t = TextView(activity)
        t.text = title
        t.textSize = 14f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(0xFF9CDCFE.toInt())
        t.setPadding(0, dp(18), 0, dp(4))
        list.addView(t)
    }

    private fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.isEnabled = enabled
        b.setOnClickListener { onClick() }
        list.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(activity)
        sw.text = label
        sw.setTextColor(Color.WHITE)
        sw.textSize = 15f
        sw.isChecked = checked
        sw.setPadding(dp(4), dp(8), dp(4), dp(8))
        sw.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        list.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun note(text: String) {
        val t = TextView(activity)
        t.text = text
        t.textSize = 12f
        t.setTextColor(0xFFBDBDBD.toInt())
        t.setPadding(dp(4), dp(2), dp(4), dp(2))
        list.addView(t)
    }

    private fun confirm(message: String, positive: String, onYes: () -> Unit) {
        Immersive.show(
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(positive) { _, _ -> onYes() }
                .setNegativeButton("Cancel", null)
                .create()
        )
    }

    // ------------------------------------------------------------------ the screen

    fun refresh() {
        list.removeAllViews()

        section("Display")
        toggle("Always show the MENU button", host.menuAlways()) { host.setMenuAlways(it) }
        note("Off: the button hides and appears when you touch the screen.")

        section("OSD overlays (drawn over the video)")
        toggle("Stick view: roll, pitch, yaw and throttle", host.sticksOn()) {
            host.setSticksOn(it)
            refresh()
        }
        if (host.sticksOn()) {
            action("Stick size: " + host.sticksSizeLabel() + "  (tap to change)") {
                host.cycleSticksSize()
                refresh()
            }
            action("Stick position: " + host.sticksPositionLabel() + "  (tap to change)") {
                host.cycleSticksPosition()
                refresh()
            }
            action("Stick opacity: " + host.sticksOpacityLabel() + "  (tap to change)") {
                host.cycleSticksOpacity()
                refresh()
            }
        }
        action("Stick mode, channels and direction...") { host.editStickChannels() }
        toggle("Warning status bar", host.warningBarOn()) { host.setWarningBarOn(it) }
        action("Warning bar position: " + host.warningBarPositionLabel() + "  (tap to change)") {
            host.cycleWarningBarPosition()
            refresh()
        }
        action("Edit the warning rules (thresholds and logic)...") { host.editWarningRules() }
        action("Preview the warning bar (4 seconds)") { host.previewWarnings() }
        note("Warnings show while their condition is true, so thresholds and logic are yours to set.")

        section("Video")
        action("Camera: " + host.cameraId() + "  (tap to choose the HDMI input)") { host.chooseCamera() }
        action("Rotate video: now " + host.rotation() + " degrees  (tap to turn 90)") {
            host.rotateVideo()
            refresh()
        }
        action("Scale: " + (if (host.fill()) "fill (stretch)" else "fit") + "  (tap to change)") {
            host.toggleFill()
            refresh()
        }

        section("Speech")
        toggle("Speech on", host.speechOn()) { host.setSpeechOn(it) }
        action("Speak a test sentence") { host.speakTest() }

        section("Sound rules")
        action("Export sound rules to a file...") { host.exportRules() }
        action("Import sound rules from a file...") { host.importRules() }
        action("Restore the rules from before the last import, reset or restore", host.hasRulesBackup()) {
            confirm("Swap back to the previous rules? The current rules are kept, so you can swap again.", "Restore") {
                host.restoreRules()
                refresh()
            }
        }
        action("Reset the rules to the defaults") {
            confirm("Replace your rules with the defaults? Your current rules are kept as a backup.", "Reset") {
                host.resetRules()
                refresh()
            }
        }
        action("Edit the rules as text (advanced)") { host.openRulesText() }

        section("Diagnostics")
        toggle("Show the variables panel (every value the radio sends)", host.variablesOn()) { host.setVariablesOn(it) }
        toggle("Test channels: sliders instead of the radio", host.testChannelsOn()) { host.setTestChannelsOn(it) }
        note("Close Settings to see the panels over the video.")

        section("About")
        note(host.about())
    }
}
