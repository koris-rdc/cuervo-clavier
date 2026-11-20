package co.cuervo.clavier

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable

/**
 * Design system « Encre & soleil » : encre profonde, vert racines, jaune soleil.
 * Clavier sombre ; application sur papier chaud.
 */
object Ui {
    // Clavier
    val INK = Color.parseColor("#101319")
    val KEY = Color.parseColor("#1D2230")
    val KEY_ALT = Color.parseColor("#2A3042")
    val KEY_EDGE = Color.parseColor("#0A0C11")
    val KEY_PRESSED = Color.parseColor("#3A4360")
    val TEXT = Color.parseColor("#F4F6FA")
    val TEXT_DIM = Color.parseColor("#C5CBDA")
    val MUTED = Color.parseColor("#8C94A8")
    val OUTLINE = Color.parseColor("#2E3547")
    val GREEN = Color.parseColor("#1DB45A")
    val GREEN_LIGHT = Color.parseColor("#2FD472")
    val GREEN_DEEP = Color.parseColor("#0F7A3A")
    val SUN = Color.parseColor("#F5C518")
    val SUN_DEEP = Color.parseColor("#B88A06")

    // Application
    val PAPER = Color.parseColor("#F6F4EE")
    val CARD = Color.WHITE
    val LINE = Color.parseColor("#E7E3D7")
    val INK_TEXT = Color.parseColor("#14171F")
    val GREY_TEXT = Color.parseColor("#5C6372")
    val SOFT_GREEN = Color.parseColor("#E3F5EA")
    val SOFT_SUN = Color.parseColor("#FFF3C4")
    val RED = Color.parseColor("#C0392B")

    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius.toFloat()
        return g
    }

    fun outline(fill: Int, stroke: Int, radius: Int, strokePx: Int): GradientDrawable {
        val g = round(fill, radius)
        g.setStroke(strokePx, stroke)
        return g
    }

    private fun layer(face: Int, edge: Int, r: Int, depth: Int): LayerDrawable {
        val ld = LayerDrawable(arrayOf<Drawable>(round(edge, r), round(face, r)))
        ld.setLayerInset(1, 0, 0, 0, depth)
        return ld
    }

    /** Touche avec un léger relief (arête sombre en bas) et un état « appuyée ». */
    fun keyBg(ctx: Context, face: Int, pressed: Int = KEY_PRESSED, edge: Int = KEY_EDGE): StateListDrawable {
        val r = dp(ctx, 9)
        val depth = dp(ctx, 2)
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), layer(pressed, edge, r, depth))
        s.addState(intArrayOf(), layer(face, edge, r, depth))
        return s
    }
}
