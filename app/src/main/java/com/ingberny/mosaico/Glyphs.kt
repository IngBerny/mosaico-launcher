package com.ingberny.mosaico

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Iconos de línea dibujados a mano, en el estilo fino de Windows 10 Mobile. */
enum class G {
    SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM,
    WIFI, BLUETOOTH, AIRPLANE, FLASHLIGHT, LOCATION, ROTATION, QUIET, BATTERY, DATA,
    HOTSPOT, CAMERA, SETTINGS, SEARCH, PIN, UNPIN, RESIZE, CLOSE, CHEVRON_DOWN,
    CHEVRON_UP, ARROW_RIGHT, PHOTO, CALENDAR, BOLT, CHECK, MORE, PALETTE, ARROW_LEFT, START,
    LOCK, SCREENSHOT, VOLUME, BRIGHTNESS
}

object Glyphs {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    fun draw(c: Canvas, g: G, cx: Float, cy: Float, s: Float, color: Int, weight: Float = 1f) {
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, s * 0.055f * weight)

        fun X(x: Float) = cx + x * s
        fun Y(y: Float) = cy + y * s
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(X(x1), Y(y1), X(x2), Y(y2), p)
        fun circ(x: Float, y: Float, r: Float) = c.drawCircle(X(x), Y(y), r * s, p)
        fun dot(x: Float, y: Float, r: Float) {
            p.style = Paint.Style.FILL; c.drawCircle(X(x), Y(y), r * s, p); p.style = Paint.Style.STROKE
        }
        fun poly(close: Boolean, vararg pts: Float) {
            val path = Path()
            path.moveTo(X(pts[0]), Y(pts[1]))
            var i = 2
            while (i < pts.size) { path.lineTo(X(pts[i]), Y(pts[i + 1])); i += 2 }
            if (close) path.close()
            c.drawPath(path, p)
        }
        fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float) {
            rect.set(X(x - r), Y(y - r), X(x + r), Y(y + r))
            c.drawArc(rect, start, sweep, false, p)
        }
        fun box(l: Float, t: Float, r: Float, b: Float, rad: Float = 0.04f) {
            rect.set(X(l), Y(t), X(r), Y(b)); c.drawRoundRect(rect, rad * s, rad * s, p)
        }
        fun sun(ox: Float, oy: Float, k: Float) {
            circ(ox, oy, 0.17f * k)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                ln(ox + ca * 0.26f * k, oy + sa * 0.26f * k, ox + ca * 0.37f * k, oy + sa * 0.37f * k)
            }
        }
        fun moonPath(ox: Float, oy: Float, k: Float): Path {
            val a = Path(); a.addCircle(X(ox), Y(oy), 0.28f * k * s, Path.Direction.CW)
            val b = Path(); b.addCircle(X(ox + 0.15f * k), Y(oy - 0.13f * k), 0.24f * k * s, Path.Direction.CW)
            a.op(b, Path.Op.DIFFERENCE)
            return a
        }
        fun cloud(dx: Float, dy: Float, k: Float): Path {
            val a = Path(); a.addCircle(X(dx - 0.17f * k), Y(dy + 0.04f * k), 0.15f * k * s, Path.Direction.CW)
            val b = Path(); b.addCircle(X(dx + 0.03f * k), Y(dy - 0.08f * k), 0.2f * k * s, Path.Direction.CW)
            val d = Path(); d.addCircle(X(dx + 0.2f * k), Y(dy + 0.06f * k), 0.13f * k * s, Path.Direction.CW)
            val r = Path(); r.addRect(X(dx - 0.17f * k), Y(dy + 0.04f * k), X(dx + 0.2f * k), Y(dy + 0.19f * k), Path.Direction.CW)
            a.op(b, Path.Op.UNION); a.op(d, Path.Op.UNION); a.op(r, Path.Op.UNION)
            return a
        }

        when (g) {
            G.SUN -> sun(0f, 0f, 1.1f)
            G.MOON -> c.drawPath(moonPath(0f, 0f, 1.1f), p)
            G.PARTLY_DAY, G.PARTLY_NIGHT -> {
                val cl = cloud(0.08f, 0.1f, 1f)
                c.save(); c.clipOutPath(cl)
                if (g == G.PARTLY_DAY) sun(-0.14f, -0.13f, 0.8f) else c.drawPath(moonPath(-0.13f, -0.12f, 0.75f), p)
                c.restore()
                c.drawPath(cl, p)
            }
            G.CLOUD -> c.drawPath(cloud(0f, 0f, 1.2f), p)
            G.FOG -> { ln(-0.36f, -0.16f, 0.3f, -0.16f); ln(-0.3f, 0f, 0.36f, 0f); ln(-0.36f, 0.16f, 0.3f, 0.16f) }
            G.DRIZZLE -> {
                c.drawPath(cloud(0f, -0.1f, 1.1f), p)
                for (x in floatArrayOf(-0.14f, 0f, 0.14f)) ln(x, 0.22f, x - 0.03f, 0.28f)
                for (x in floatArrayOf(-0.07f, 0.07f)) ln(x, 0.34f, x - 0.03f, 0.4f)
            }
            G.RAIN -> {
                c.drawPath(cloud(0f, -0.1f, 1.1f), p)
                for (x in floatArrayOf(-0.14f, 0f, 0.14f)) ln(x, 0.21f, x - 0.07f, 0.4f)
            }
            G.SNOW -> {
                c.drawPath(cloud(0f, -0.1f, 1.1f), p)
                for ((x, y) in listOf(-0.14f to 0.26f, 0f to 0.3f, 0.14f to 0.26f, -0.07f to 0.4f, 0.07f to 0.4f)) dot(x, y, 0.03f)
            }
            G.STORM -> {
                c.drawPath(cloud(0f, -0.12f, 1.1f), p)
                poly(false, 0.04f, 0.16f, -0.06f, 0.3f, 0.05f, 0.3f, -0.05f, 0.45f)
            }
            G.WIFI -> {
                arc(0f, 0.3f, 0.16f, 225f, 90f); arc(0f, 0.3f, 0.32f, 225f, 90f); arc(0f, 0.3f, 0.48f, 225f, 90f)
                dot(0f, 0.3f, 0.04f)
            }
            G.BLUETOOTH -> poly(false, -0.17f, -0.17f, 0.17f, 0.17f, 0f, 0.36f, 0f, -0.36f, 0.17f, -0.17f, -0.17f, 0.17f)
            G.AIRPLANE -> {
                ln(0f, -0.4f, 0f, 0.34f)
                poly(false, -0.38f, 0.08f, 0f, -0.12f, 0.38f, 0.08f)
                poly(false, -0.15f, 0.4f, 0f, 0.3f, 0.15f, 0.4f)
            }
            G.FLASHLIGHT -> {
                poly(true, -0.2f, -0.4f, 0.2f, -0.4f, 0.12f, -0.15f, -0.12f, -0.15f)
                box(-0.12f, -0.15f, 0.12f, 0.4f, 0.02f)
                ln(0f, -0.02f, 0f, 0.1f)
            }
            G.LOCATION -> {
                val path = Path()
                rect.set(X(-0.25f), Y(-0.38f), X(0.25f), Y(0.12f))
                path.arcTo(rect, 150f, 240f, true)
                path.lineTo(X(0f), Y(0.42f)); path.close()
                c.drawPath(path, p)
                circ(0f, -0.13f, 0.08f)
            }
            G.ROTATION -> {
                box(-0.14f, -0.26f, 0.14f, 0.26f)
                arc(0f, 0f, 0.42f, 200f, 70f); arc(0f, 0f, 0.42f, 20f, 70f)
                poly(false, 0.36f, 0.1f, 0.4f, 0.24f, 0.28f, 0.27f)
            }
            G.QUIET -> c.drawPath(moonPath(0f, 0f, 1.1f), p)
            G.BATTERY -> {
                box(-0.34f, -0.16f, 0.26f, 0.16f)
                box(0.26f, -0.06f, 0.32f, 0.06f, 0.01f)
                p.style = Paint.Style.FILL
                rect.set(X(-0.28f), Y(-0.1f), X(0.02f), Y(0.1f)); c.drawRect(rect, p)
                p.style = Paint.Style.STROKE
            }
            G.DATA -> {
                val xs = floatArrayOf(-0.27f, -0.09f, 0.09f, 0.27f)
                val tops = floatArrayOf(0.18f, 0.04f, -0.12f, -0.3f)
                for (i in 0 until 4) ln(xs[i], 0.32f, xs[i], tops[i])
            }
            G.HOTSPOT -> {
                dot(0f, 0f, 0.05f)
                arc(0f, 0f, 0.2f, 135f, 90f); arc(0f, 0f, 0.2f, -45f, 90f)
                arc(0f, 0f, 0.38f, 135f, 90f); arc(0f, 0f, 0.38f, -45f, 90f)
            }
            G.CAMERA -> {
                box(-0.38f, -0.2f, 0.38f, 0.3f)
                poly(false, -0.14f, -0.2f, -0.1f, -0.3f, 0.1f, -0.3f, 0.14f, -0.2f)
                circ(0f, 0.05f, 0.14f)
            }
            G.SETTINGS -> {
                circ(0f, 0f, 0.13f); circ(0f, 0f, 0.28f)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 + 22.5)
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    ln(ca * 0.28f, sa * 0.28f, ca * 0.4f, sa * 0.4f)
                }
            }
            G.SEARCH -> { circ(-0.06f, -0.06f, 0.24f); ln(0.11f, 0.11f, 0.38f, 0.38f) }
            G.PIN -> {
                poly(true, -0.12f, -0.38f, 0.12f, -0.38f, 0.1f, -0.06f, 0.22f, 0.1f, -0.22f, 0.1f, -0.1f, -0.06f)
                ln(0f, 0.1f, 0f, 0.42f)
            }
            G.UNPIN -> {
                poly(true, -0.12f, -0.38f, 0.12f, -0.38f, 0.1f, -0.06f, 0.22f, 0.1f, -0.22f, 0.1f, -0.1f, -0.06f)
                ln(0f, 0.1f, 0f, 0.42f)
                ln(-0.36f, -0.36f, 0.36f, 0.36f)
            }
            G.RESIZE -> {
                ln(-0.3f, 0.3f, 0.3f, -0.3f)
                poly(false, 0.04f, -0.3f, 0.3f, -0.3f, 0.3f, -0.04f)
                poly(false, -0.3f, 0.04f, -0.3f, 0.3f, -0.04f, 0.3f)
            }
            G.CLOSE -> { ln(-0.28f, -0.28f, 0.28f, 0.28f); ln(-0.28f, 0.28f, 0.28f, -0.28f) }
            G.CHEVRON_DOWN -> poly(false, -0.28f, -0.12f, 0f, 0.16f, 0.28f, -0.12f)
            G.CHEVRON_UP -> poly(false, -0.28f, 0.12f, 0f, -0.16f, 0.28f, 0.12f)
            G.ARROW_RIGHT -> { ln(-0.28f, 0f, 0.28f, 0f); poly(false, 0.1f, -0.18f, 0.28f, 0f, 0.1f, 0.18f) }
            G.PHOTO -> {
                box(-0.38f, -0.3f, 0.38f, 0.3f, 0.02f)
                poly(false, -0.38f, 0.3f, -0.1f, 0f, 0.08f, 0.18f, 0.2f, 0.06f, 0.38f, 0.24f)
                circ(0.18f, -0.13f, 0.06f)
            }
            G.CALENDAR -> {
                box(-0.36f, -0.28f, 0.36f, 0.34f, 0.02f)
                ln(-0.36f, -0.12f, 0.36f, -0.12f)
                ln(-0.18f, -0.38f, -0.18f, -0.2f); ln(0.18f, -0.38f, 0.18f, -0.2f)
            }
            G.MORE -> { dot(-0.26f, 0f, 0.06f); dot(0f, 0f, 0.06f); dot(0.26f, 0f, 0.06f) }
            G.PALETTE -> {
                circ(0f, 0f, 0.36f)
                dot(-0.15f, -0.14f, 0.06f); dot(0.08f, -0.2f, 0.06f); dot(0.2f, 0.02f, 0.06f); dot(-0.18f, 0.1f, 0.06f)
            }
            G.ARROW_LEFT -> { ln(0.3f, 0f, -0.3f, 0f); poly(false, -0.1f, -0.2f, -0.3f, 0f, -0.1f, 0.2f) }
            G.START -> {
                // Botón Inicio propio de Mosaico: un mosaico mediano, dos pequeños y uno ancho.
                p.style = Paint.Style.FILL
                rect.set(X(-0.34f), Y(-0.34f), X(0.02f), Y(0.02f)); c.drawRect(rect, p)
                rect.set(X(0.08f), Y(-0.34f), X(0.34f), Y(-0.08f)); c.drawRect(rect, p)
                rect.set(X(0.08f), Y(-0.02f), X(0.34f), Y(0.02f)); c.drawRect(rect, p)
                rect.set(X(-0.34f), Y(0.08f), X(0.34f), Y(0.34f)); c.drawRect(rect, p)
                p.style = Paint.Style.STROKE
            }
            G.CHECK -> poly(false, -0.3f, 0.02f, -0.08f, 0.24f, 0.32f, -0.2f)
            G.LOCK -> {
                box(-0.28f, -0.04f, 0.28f, 0.38f)
                arc(0f, -0.12f, 0.17f, 180f, 180f)
                ln(-0.17f, -0.12f, -0.17f, -0.04f); ln(0.17f, -0.12f, 0.17f, -0.04f)
                ln(0f, 0.1f, 0f, 0.22f)
            }
            G.SCREENSHOT -> {
                poly(false, -0.38f, -0.18f, -0.38f, -0.38f, -0.18f, -0.38f)
                poly(false, 0.18f, -0.38f, 0.38f, -0.38f, 0.38f, -0.18f)
                poly(false, 0.38f, 0.18f, 0.38f, 0.38f, 0.18f, 0.38f)
                poly(false, -0.18f, 0.38f, -0.38f, 0.38f, -0.38f, 0.18f)
                circ(0f, 0f, 0.1f)
            }
            G.VOLUME -> {
                poly(true, -0.38f, -0.1f, -0.22f, -0.1f, 0f, -0.3f, 0f, 0.3f, -0.22f, 0.1f, -0.38f, 0.1f)
                arc(0f, 0f, 0.18f, -45f, 90f)
                arc(0f, 0f, 0.32f, -45f, 90f)
            }
            G.BRIGHTNESS -> sun(0f, 0f, 1.1f)
            G.BOLT -> poly(true, 0.06f, -0.4f, -0.2f, 0.06f, 0f, 0.06f, -0.06f, 0.4f, 0.2f, -0.06f, 0f, -0.06f)
        }
    }

    fun forWeather(code: Int, isDay: Boolean): G = when (code) {
        0 -> if (isDay) G.SUN else G.MOON
        1, 2 -> if (isDay) G.PARTLY_DAY else G.PARTLY_NIGHT
        3 -> G.CLOUD
        45, 48 -> G.FOG
        in 51..57 -> G.DRIZZLE
        in 61..67, in 80..82 -> G.RAIN
        in 71..77, 85, 86 -> G.SNOW
        in 95..99 -> G.STORM
        else -> G.CLOUD
    }
}

/** Vista simple que muestra un glifo. */
class GlyphView(ctx: Context, var glyph: G, var color: Int, private val scale: Float = 0.8f) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) * scale
        Glyphs.draw(canvas, glyph, width / 2f, height / 2f, s, color, 1.3f)
    }
}
