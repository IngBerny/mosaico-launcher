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
    val mono: Typeface = Typeface.MONOSPACE
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    val serif: Typeface = Typeface.create("serif", Typeface.NORMAL)

    val NAMES = listOf("Fina", "Normal", "Monoespaciada", "Condensada", "Serif")

    /** Fuente elegida en Personalizar → Tema. [big] = para la hora y títulos grandes. */
    fun pick(style: Int, big: Boolean = false): Typeface = when (style) {
        1 -> regular
        2 -> mono
        3 -> condensed
        4 -> serif
        else -> if (big) thin else light
    }
}

/**
 * Colores del tema "Mirador": tinta, crema y salvia.
 * [mode]: 0 = tinta (oscuro), 1 = papel (claro), 2 = negro puro (AMOLED).
 */
class Palette(val mode: Int, val accent: Int) {
    val dark: Boolean = mode != 1
    val bg: Int = when (mode) { 1 -> 0xFFF4F1EA.toInt(); 2 -> Color.BLACK; else -> 0xFF0E1317.toInt() }
    val fg: Int = if (dark) 0xFFE9E4DA.toInt() else 0xFF1B2127.toInt()
    val sub: Int = if (dark) 0xFF8E949A.toInt() else 0xFF6E7378.toInt()
    val chrome: Int = when (mode) { 1 -> 0xFFE6E1D7.toInt(); 2 -> 0xFF151515.toInt(); else -> 0xFF1B2127.toInt() }
    val toggleOff: Int = when (mode) { 1 -> 0xFFD9D4CA.toInt(); 2 -> 0xFF222222.toInt(); else -> 0xFF262E35.toInt() }
    val line: Int = when (mode) { 1 -> 0xFFCFC9BD.toInt(); 2 -> 0xFF2A2A2A.toInt(); else -> 0xFF2C353C.toInt() }
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


/**
 * Pide a la pantalla su modo de mayor frecuencia (por ejemplo 120 o 144 Hz) con la misma resolución,
 * para que las animaciones y el desplazamiento se vean fluidos. El sistema puede bajarla para ahorrar batería.
 */
fun android.app.Activity.preferMaxRefreshRate() {
    try {
        @Suppress("DEPRECATION")
        val d = (if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        lp.preferredDisplayModeId = best.modeId
        lp.preferredRefreshRate = best.refreshRate
        window.attributes = lp
    } catch (e: Throwable) { }
}
