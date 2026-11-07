package dev.ax12.announcer

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** The icons of the app. They are described in assets/icons.txt (see IconSet) and drawn here, so they have any size and colour. */
object Icons {
    private var set: Map<String, List<IconSet.Op>> = emptyMap()

    fun load(context: Context) {
        if (set.isNotEmpty()) return
        context.assets.open("icons.txt").use { set = IconSet.parse(String(it.readBytes(), Charsets.UTF_8), null) }
    }

    /** @param sizeDp the size of the icon on the screen */
    fun drawable(context: Context, name: String, color: Int = CardStyle.ICON, sizeDp: Int = 24): Drawable {
        val px = (sizeDp * context.resources.displayMetrics.density + 0.5f).toInt()
        return IconDrawable(set[name] ?: emptyList(), color, px)
    }
}

class IconDrawable(private val ops: List<IconSet.Op>, color: Int, private val sizePx: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    init {
        paint.color = color
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val u = minOf(b.width(), b.height()) / IconSet.GRID.toFloat()
        if (u <= 0f) return
        canvas.save()
        canvas.translate(b.left + (b.width() - IconSet.GRID.toFloat() * u) / 2f, b.top + (b.height() - IconSet.GRID.toFloat() * u) / 2f)
        canvas.scale(u, u)
        for (op in ops) {
            val n = op.n
            paint.style = if (op.fill) Paint.Style.FILL else Paint.Style.STROKE
            when (op.type) {
                "rect" -> {
                    rect.set(n[0].toFloat(), n[1].toFloat(), n[2].toFloat(), n[3].toFloat())
                    canvas.drawRoundRect(rect, n[4].toFloat(), n[4].toFloat(), paint)
                }
                "circle" -> canvas.drawCircle(n[0].toFloat(), n[1].toFloat(), n[2].toFloat(), paint)
                "line" -> canvas.drawLine(n[0].toFloat(), n[1].toFloat(), n[2].toFloat(), n[3].toFloat(), paint)
                "polyline", "poly" -> {
                    path.reset()
                    var i = 0
                    while (i < n.size) {
                        if (i == 0) path.moveTo(n[0].toFloat(), n[1].toFloat()) else path.lineTo(n[i].toFloat(), n[i + 1].toFloat())
                        i += 2
                    }
                    if (op.type == "poly") path.close()
                    canvas.drawPath(path, paint)
                }
                "arc" -> {
                    rect.set(n[0].toFloat(), n[1].toFloat(), n[2].toFloat(), n[3].toFloat())
                    canvas.drawArc(rect, n[4].toFloat(), n[5].toFloat(), false, paint)
                }
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Fades cards in and out instead of switching them on and off. */
class CardFader {
    private val wanted = HashMap<android.view.View, Boolean>()
    private val target = HashMap<android.view.View, Float>()

    /** How opaque a card is once it is shown (the opacity setting). */
    fun setOpacity(view: android.view.View, alpha: Float) {
        target[view] = alpha
        if (wanted[view] == true) {
            view.animate().cancel()
            view.alpha = alpha
        }
    }

    /** Show or hide a card; does nothing when it is already so. */
    fun set(view: android.view.View, show: Boolean) {
        if (wanted[view] == show) return
        wanted[view] = show
        view.animate().cancel()
        if (show) {
            if (view.visibility != android.view.View.VISIBLE) {
                view.alpha = 0f
                view.visibility = android.view.View.VISIBLE
            }
            view.animate().alpha(target[view] ?: 1f).setDuration(260).start()
        } else {
            view.animate().alpha(0f).setDuration(180).withEndAction {
                if (wanted[view] == false) view.visibility = android.view.View.GONE
            }.start()
        }
    }
}
