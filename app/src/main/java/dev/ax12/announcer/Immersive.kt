package dev.ax12.announcer

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager

/**
 * Keeps the screen full-screen (no status bar, no Android navigation buttons).
 *
 * Android leaves full-screen mode whenever another window takes the keyboard focus: a popup menu,
 * a spinner list, a dialog. The flags only count for the window that has focus, so a dialog has to
 * carry them itself, and it has to get them before it is allowed to take focus.
 */
object Immersive {
    @Suppress("DEPRECATION")
    val FLAGS: Int = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        activity.window.decorView.systemUiVisibility = FLAGS
    }

    /** Show a dialog without the navigation buttons popping up. */
    @Suppress("DEPRECATION")
    fun show(dialog: Dialog) {
        val w = dialog.window
        if (w == null) {
            dialog.show()
            return
        }
        w.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        dialog.show()
        w.decorView.systemUiVisibility = FLAGS
        // An AlertDialog decides when it opens whether it can take text: it looks for a text field in its view, and if there is
        // none it sets FLAG_ALT_FOCUSABLE_IM, which keeps the keyboard away for good. A dialog that starts without text fields and
        // adds them later (the packs editor, which starts with no packs) would never show the keyboard. Clear the flag after
        // the dialog is up, and let the window shrink to make room for the keyboard.
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // The theme writes dialog buttons in ALL CAPS ("CLOSE", "CLEAR"), unlike every other button of the app.
        if (dialog is android.app.AlertDialog) {
            for (which in intArrayOf(android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE, android.app.AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(which)?.isAllCaps = false
            }
        }
    }
}
