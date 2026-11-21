package com.ingberny.mosaico

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/** Mezcla dos colores: t = 0 da [a], t = 1 da [b]. */
fun blend(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
    )
}

/**
 * Fondo "Mirador": cielo en degradado, luna fina, algunas estrellas y tres cordilleras,
 * todo con los colores del tema. Se dibuja en vectores, así que se ve nítido en cualquier pantalla.
 */
class MountainsView(ctx: Context, private val pal: Palette, private val seed: Int = 7) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var sky: LinearGradient? = null
    private val stars = ArrayList<FloatArray>()

    private val horizon = if (pal.dark) blend(pal.bg, pal.accent, 0.22f) else blend(pal.bg, pal.accent, 0.35f)
    private val layers = if (pal.dark) listOf(
        blend(pal.bg, pal.accent, 0.16f), blend(pal.bg, pal.accent, 0.09f), blend(pal.bg, Color.BLACK, 0.15f),
    ) else listOf(
        blend(pal.bg, pal.accent, 0.45f), blend(pal.bg, pal.accent, 0.65f), blend(pal.fg, pal.accent, 0.35f),
    )

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sky = LinearGradient(0f, 0f, 0f, h * 0.72f, pal.bg, horizon, Shader.TileMode.CLAMP)
        stars.clear()
        val r = Random(seed)
        if (pal.dark) repeat(26) { stars += floatArrayOf(r.nextFloat() * w, h * (0.04f + r.nextFloat() * 0.45f), 40f + r.nextFloat() * 110f) }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = sky
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (s in stars) { p.color = withAlpha(pal.fg, s[2].toInt()); c.drawCircle(s[0], s[1], dp(1.1f), p) }
        // Luna fina (o sol, en el modo claro).
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = withAlpha(pal.fg, 190)
        c.drawCircle(w * 0.76f, h * 0.24f, w * 0.03f, p)
        p.style = Paint.Style.FILL
        val r = Random(seed)
        val specs = listOf(
            Triple(0.66f, 0.058f, floatArrayOf(1.1f, 0.6f, 2.7f, 0.3f, 5.3f, 0.1f)),
            Triple(0.72f, 0.05f, floatArrayOf(0.8f, 0.7f, 3.1f, 0.25f, 7.0f, 0.05f)),
            Triple(0.79f, 0.038f, floatArrayOf(1.4f, 0.6f, 4.2f, 0.3f, 9.0f, 0.1f)),
        )
        specs.forEachIndexed { i, (base, amp, fr) ->
            val phases = FloatArray(3) { r.nextFloat() * 6.28f }
            path.reset(); path.moveTo(0f, h)
            var x = 0f
            val step = dp(4f)
            while (x <= w + step) {
                var y = 0f
                for (k in 0 until 3) y += fr[k * 2 + 1] * sin(x / w * fr[k * 2] * 6.283f + phases[k])
                path.lineTo(x, h * base - y * h * amp)
                x += step
            }
            path.lineTo(w, h); path.close()
            p.color = layers[i]
            c.drawPath(path, p)
        }
    }
}
