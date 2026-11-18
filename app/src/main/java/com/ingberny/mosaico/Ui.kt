package com.ingberny.mosaico

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Int): Int = context.dpi(v)
fun View.sp(v: Float): Float = context.sp(v)

/** Animador con valores por defecto limpios (ViewPropertyAnimator recuerda delay e interpolador). */
fun View.anim(): ViewPropertyAnimator =
    animate().setStartDelay(0).setInterpolator(DecelerateInterpolator())

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

fun String.capFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

object Fonts {
    val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Colores del tema estilo "Metro". */
class Palette(val dark: Boolean, val accent: Int) {
    val bg: Int = if (dark) Color.BLACK else Color.WHITE
    val fg: Int = if (dark) Color.WHITE else Color.BLACK
    val sub: Int = if (dark) 0xFF9A9A9A.toInt() else 0xFF6B6B6B.toInt()
    val chrome: Int = if (dark) 0xFF1F1F1F.toInt() else 0xFFEDEDED.toInt()
    val toggleOff: Int = if (dark) 0xFF333333.toInt() else 0xFFD6D6D6.toInt()
    val line: Int = if (dark) 0xFF3A3A3A.toInt() else 0xFFCCCCCC.toInt()
}

fun Context.label(
    s: CharSequence,
    sizeSp: Float,
    color: Int,
    face: Typeface = Fonts.regular
): TextView = TextView(this).apply {
    text = s
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    typeface = face
    includeFontPadding = false
}
