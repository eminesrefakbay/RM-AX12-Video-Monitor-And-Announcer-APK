package dev.ax12.announcer

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The main menu as a panel inside the screen, not a popup window. A popup window takes the
 * keyboard focus, and Android answers that by showing its navigation buttons; a view does not.
 * Tapping outside the panel closes it.
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
    private val onAircraft: () -> Unit
) {
    val root = FrameLayout(activity)
    private val speechButton = Button(activity)
    private val batteryButton = Button(activity)
    private val aircraftButton = Button(activity)

    val isShowing: Boolean get() = root.visibility == View.VISIBLE

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(30).toFloat()
        root.setOnClickListener { hide() }

        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.background = CardStyle.panelBackground(activity.resources.displayMetrics.density)
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.isClickable = true

        box.addView(item(tr("Sound rules"), "rules") { onRules() })
        box.addView(speechButton.also { b ->
            b.isAllCaps = false
            b.textSize = 16f
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setOnClickListener {
                hide()
                onToggleSpeech()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(batteryButton.also { b ->
            style(b, "battery")
            b.isAllCaps = false
            b.textSize = 16f
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setOnClickListener {
                hide()
                onBattery()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(aircraftButton.also { b ->
            style(b, "aircraft")
            b.isAllCaps = false
            b.textSize = 16f
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setOnClickListener {
                hide()
                onAircraft()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(item(tr("Settings"), "gear") { onSettings() })

        val lp = FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.topMargin = dp(56)
        lp.rightMargin = dp(8)
        root.addView(box, lp)
    }

    /** The look every menu line shares: an icon, then the words. */
    private fun style(b: Button, icon: String) {
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.drawable(activity, icon), null, null, null)
        b.compoundDrawablePadding = dp(12)
    }

    private fun item(label: String, icon: String, action: () -> Unit): Button {
        val b = Button(activity)
        style(b, icon)
        b.text = label
        b.isAllCaps = false
        b.textSize = 16f
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setOnClickListener {
            hide()
            action()
        }
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return b
    }

    fun show() {
        speechButton.text = if (speechOn()) tr("Mute speech") else tr("Speech is OFF - tap to turn on")
        style(speechButton, if (speechOn()) "speaker" else "speaker_off")
        batteryButton.text = tr("Battery: {0}  (tap to switch)", batteryName())
        aircraftButton.text = tr("Aircraft: {0}  (tap to choose)", aircraftName())
        root.animate().cancel()
        root.alpha = 0f
        root.visibility = View.VISIBLE
        root.animate().alpha(1f).setDuration(140).start()
    }

    fun hide() {
        root.animate().cancel()
        root.alpha = 1f
        root.visibility = View.GONE
    }
}
