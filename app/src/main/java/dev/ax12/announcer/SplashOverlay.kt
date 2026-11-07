package dev.ax12.announcer

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * The start-up splash: the logo in the centre and the version above it, over the same navy as the window
 * background Android shows at launch, so the two look like one screen. It stays for [SHOW_MS] and a tap or
 * the Back key skips it. The app starts up behind it, so it costs no extra waiting.
 */
class SplashOverlay(private val activity: Activity) {

    companion object {
        /** 2 seconds: the usual range for a splash is 1 to 3 s, long enough to read and short enough not to annoy. */
        const val SHOW_MS = 2000L
        private const val FADE_MS = 250L
    }

    val root = FrameLayout(activity)
    private var gone = false

    val isShowing: Boolean get() = !gone

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.setBackgroundColor(0xFF212D3D.toInt())
        root.isClickable = true
        root.elevation = dp(60).toFloat()
        root.setOnClickListener { dismiss() }

        // the same picture, the same size and the same place as the window background Android showed before us
        val logo = ImageView(activity)
        logo.setImageResource(R.drawable.splash_logo)
        logo.scaleType = ImageView.ScaleType.CENTER
        root.addView(logo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val version = TextView(activity)
        version.text = versionLine()
        version.textSize = 20f
        version.setTextColor(0xFF9CFF00.toInt())
        version.setTypeface(null, android.graphics.Typeface.BOLD)
        version.gravity = Gravity.CENTER
        val vp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        vp.topMargin = dp(28)
        root.addView(version, vp)

        val sub = TextView(activity)
        sub.text = tr("Video monitor, HUD and voice announcer for the RadioMaster AX12.")
        sub.textSize = 13f
        sub.setTextColor(0xFFB0BEC5.toInt())
        sub.gravity = Gravity.CENTER
        val sp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        sp.topMargin = dp(58)
        root.addView(sub, sp)
    }

    @Suppress("DEPRECATION")
    private fun versionLine(): String {
        return try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            tr("AX-HUD  version {0}  (build {1})", (info.versionName ?: "?"), build)
        } catch (e: Exception) {
            "AX-HUD"
        }
    }

    /** Fade out and take the overlay off the screen. Safe to call more than once. */
    fun dismiss() {
        if (gone) return
        gone = true
        root.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            root.visibility = View.GONE
            (root.parent as? ViewGroup)?.removeView(root)
        }.start()
    }
}
