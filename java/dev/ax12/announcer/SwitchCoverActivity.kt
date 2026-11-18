// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The loading screen of an aircraft switch. The main screen is built again for the new aircraft and, while that
 * happens, Android shows nothing but black. This small screen sits in front (see-through window, solid content, so the
 * main screen stays "visible" behind it and is rebuilt at once), and goes away when the new main screen is ready.
 */
class SwitchCoverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(COVER_COLOR)
        val small = TextView(this)
        small.text = tr("SWITCHING AIRCRAFT")
        small.textSize = 13f
        small.setTextColor(CardStyle.LABEL)
        root.addView(small)
        val big = TextView(this)
        big.text = name
        big.textSize = 30f
        big.setTypeface(null, Typeface.BOLD)
        big.setTextColor(Color.WHITE)
        big.setPadding(0, dp(6), 0, dp(14))
        root.addView(big)
        val spin = ProgressBar(this)
        spin.isIndeterminate = true
        root.addView(spin, LinearLayout.LayoutParams(dp(40), dp(40)))
        val note = TextView(this)
        note.text = tr("Loading its settings, rules and layout...")
        note.textSize = 13f
        note.setTextColor(CardStyle.LABEL)
        note.setPadding(0, dp(14), 0, 0)
        root.addView(note)
        setContentView(root)
        root.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        current = this
        Diagnostics.event("aircraft", "loading screen created")
        Handler(Looper.getMainLooper()).postDelayed({ finishCover() }, SAFETY_MS)      // never stays for ever
    }

    override fun onBackPressed() { /* the switch is under way */ }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        const val COVER_COLOR = 0xFF10171F.toInt()
        private const val EXTRA_NAME = "name"
        private const val SAFETY_MS = 10_000L
        @Volatile private var current: SwitchCoverActivity? = null

        fun show(context: Context, name: String) {
            val i = Intent(context, SwitchCoverActivity::class.java)
            i.putExtra(EXTRA_NAME, name)
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(i)
        }

        /** The new main screen is ready: the cover goes away. */
        fun finishCover() {
            val c = current ?: return
            current = null
            c.finish()
            c.overridePendingTransition(0, 0)
        }

        val showing: Boolean get() = current != null
    }
}
