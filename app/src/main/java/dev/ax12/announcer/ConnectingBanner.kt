package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A card at the top of the video while the radio link is coming up: "Connecting to the radio... 7 s", a strip that fills towards
 * the usual 12 seconds, and, when it takes much longer, what to check. It goes when the radio answers.
 */
class ConnectingBanner(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val title = TextView(context)
    private val hint = TextView(context)
    private val strip = Strip(context)
    val view = LinearLayout(context)

    private class Strip(context: Context) : View(context) {
        var percent = 0
        private val track = Paint()
        private val fill = Paint()

        init {
            track.color = 0x33FFFFFF
            fill.color = CardStyle.ACCENT
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), track)
            canvas.drawRect(0f, 0f, width * percent / 100f, height.toFloat(), fill)
        }
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        view.orientation = LinearLayout.VERTICAL
        view.setPadding(dp(16), dp(8), dp(16), dp(8))
        view.background = CardStyle.panelBackground(density, CardStyle.BG)
        view.elevation = dp(4).toFloat()
        view.visibility = View.GONE
        title.textSize = 16f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        view.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        view.addView(strip, LinearLayout.LayoutParams(dp(240), dp(4)).also { it.topMargin = dp(8); it.gravity = Gravity.CENTER_HORIZONTAL })
        hint.textSize = 13f
        hint.setTextColor(CardStyle.WARN)
        hint.gravity = Gravity.CENTER
        hint.visibility = View.GONE
        view.addView(hint, LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8); it.gravity = Gravity.CENTER_HORIZONTAL })
    }

    fun update(seconds: Double) {
        title.text = tr("Connecting to the radio... {0} s", ConnectingText.wholeSeconds(seconds))
        strip.percent = ConnectingText.percent(seconds)
        strip.invalidate()
        if (ConnectingText.stuck(seconds)) {
            hint.text = tr("Still waiting. Close the RadioMaster app and check that the radio is on.")
            hint.visibility = View.VISIBLE
        } else {
            hint.visibility = View.GONE
        }
    }
}
