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
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
