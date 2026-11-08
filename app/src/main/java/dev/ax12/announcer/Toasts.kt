// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.widget.Toast

/** A toast whose text has the app's fonts: the system font of a radio may have no characters for a language the app can show. */
object Toasts {
    @Suppress("DEPRECATION")
    fun make(context: Context, text: CharSequence, duration: Int): Toast {
        val t = Toast.makeText(context, text, duration)
        try {
            Look.apply(t.view)                          // on Android 9 this is the text view of the toast
        } catch (e: Exception) { /* the toast is shown with the system font */ }
        return t
    }
}
