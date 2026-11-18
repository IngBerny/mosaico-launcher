package com.ingberny.mosaico

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

interface TileHost {
    val repo: AppRepo
    val prefs: Prefs
    val palette: Palette
    val live: LiveState
    fun backdrop(): Bitmap?
}

class TileView(ctx: Context, val spec: TileSpec, private val host: TileHost) : View(ctx) {

    var face = 0; private set
    private var flipping = false
    private var photoIndex = 0
    private var prevPhoto = -1
    private var photoFade = 1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val loc = IntArray(2)
    private val src = Rect()
    private val dst = RectF()

    init {
        cameraDistance = 9000 * resources.displayMetrics.density
    }

    // ---------- color de fondo ----------

    private fun appColor(): Int? = host.repo.find(spec)?.let { host.repo.color(it) }

    fun baseColor(): Int = when (spec.color) {
        TileSpec.COLOR_DEFAULT ->
            (if (host.prefs.appColors && spec.kind == TileKind.APP) appColor() else null) ?: host.palette.accent
        TileSpec.COLOR_APP -> appColor() ?: host.palette.accent
        TileSpec.COLOR_ACCENT -> host.palette.accent
        TileSpec.COLOR_TRANSPARENT -> Color.TRANSPARENT
        else -> spec.color
    }

    /** true si este mosaico tiene un color elegido a mano (no el automático). */
    private val customColor get() = spec.color != TileSpec.COLOR_DEFAULT

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        val color = baseColor()
        if (spec.color == TileSpec.COLOR_TRANSPARENT) {
            if (host.prefs.bgMode == 1) host.backdrop()?.let { drawBackdrop(c, it, w, h) }
            return
        }
        when (host.prefs.bgMode) {
            1 -> {
                val b = host.backdrop()
                if (b != null) {
                    drawBackdrop(c, b, w, h)
                    val tinted = customColor || (host.prefs.appColors && spec.kind == TileKind.APP)
                    fill.color = if (tinted) withAlpha(color, 150) else withAlpha(Color.BLACK, 40)
                    c.drawRect(0f, 0f, w, h, fill)
                } else {
                    fill.color = color; c.drawRect(0f, 0f, w, h, fill)
                }
            }
            2 -> { fill.color = color; c.drawRect(0f, 0f, w, h, fill) }
            else -> {
                fill.color = withAlpha(color, host.prefs.tileOpacity * 255 / 100)
                c.drawRect(0f, 0f, w, h, fill)
            }
        }
    }

    private fun drawBackdrop(c: Canvas, b: Bitmap, w: Float, h: Float) {
        getLocationOnScreen(loc)
        val sw = resources.displayMetrics.widthPixels.toFloat()
        val k = b.width / max(1f, sw)
        src.set((loc[0] * k).toInt(), (loc[1] * k).toInt(), ((loc[0] + w) * k).toInt(), ((loc[1] + h) * k).toInt())
        dst.set(0f, 0f, w, h)
        c.drawBitmap(b, src, dst, bmpPaint)
    }

    // ---------- texto ----------

    private fun text(c: Canvas, s: String, x: Float, y: Float, sizeSp: Float, face: Typeface = Fonts.regular,
                     align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f, alpha: Int = 255) {
        tp.textSize = sp(sizeSp); tp.typeface = face; tp.textAlign = align; tp.alpha = alpha
        val str = if (maxW > 0) TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString() else s
        c.drawText(str, x, y, tp)
        tp.alpha = 255
    }

    private fun multiline(c: Canvas, s: String, x: Float, y: Float, w: Float, sizeSp: Float, lines: Int,
                          face: Typeface = Fonts.regular, alpha: Int = 255): Float {
        tp.textSize = sp(sizeSp); tp.typeface = face; tp.textAlign = Paint.Align.LEFT; tp.alpha = alpha
        val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, max(1, w.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        tp.alpha = 255
        return l.height.toFloat()
    }

    private val pad get() = dp(8f)

    private val labelHidden get() = spec.hideLabel || host.prefs.hideLabels

    private fun bottomLabel(c: Canvas, s: String, w: Float, h: Float, right: String? = null) {
        if (spec.size == TileSize.SMALL || labelHidden) return
        val maxW = if (right != null) w - pad * 2 - dp(36f) else w - pad * 2
        text(c, s, pad, h - dp(8f), 12f, Fonts.regular, maxW = maxW)
        if (right != null) text(c, right, w - pad, h - dp(8f), 12f, Fonts.regular, Paint.Align.RIGHT)
    }

    // ---------- dibujo ----------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        drawBackground(canvas, w, h)
        when (spec.kind) {
            TileKind.APP -> drawApp(canvas, w, h)
            TileKind.CALENDAR -> drawCalendar(canvas, w, h)
            TileKind.WEATHER -> drawWeather(canvas, w, h)
            TileKind.PHOTOS -> drawPhotos(canvas, w, h)
            TileKind.BATTERY -> drawBattery(canvas, w, h)
        }
    }

    private fun drawIcon(c: Canvas, e: AppEntry, cx: Float, cy: Float, size: Float) {
        val mono: Drawable? = if (host.prefs.monoIcons) host.repo.mono(e) else null
        val d = mono ?: host.repo.icon(e)
        // La capa monocromática ocupa el lienzo completo de 108dp; lo visible son los 72dp centrales.
        val s = if (mono != null) size * 1.5f else size
        d.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
        d.draw(c)
    }

    private fun drawApp(c: Canvas, w: Float, h: Float) {
        val e = host.repo.find(spec)
        if (e == null) {
            text(c, "?", w / 2, h / 2 + sp(10f), 28f, Fonts.light, Paint.Align.CENTER); return
        }
        val scale = host.prefs.iconScale.coerceIn(60, 140) / 100f
        val size = scale * when (spec.size) {
            TileSize.SMALL -> min(w, h) * 0.56f
            TileSize.MEDIUM -> w * 0.38f
            TileSize.WIDE -> h * 0.38f
            TileSize.LARGE -> w * 0.24f
        }
        val cx = w / 2
        val cy = if (spec.size == TileSize.SMALL || labelHidden) h / 2 else h / 2 - dp(4f)
        drawIcon(c, e, cx, cy, size)
        val count = host.live.badges[e.pkg] ?: 0
        val badge = if (count > 99) "99+" else count.toString()
        if (count > 0) {
            if (spec.size == TileSize.SMALL) {
                text(c, badge, w - dp(4f), sp(13f), 11f, Fonts.medium, Paint.Align.RIGHT)
            } else {
                text(c, badge, cx + size / 2 + dp(6f), cy - size / 2 + sp(20f), 22f, Fonts.light)
            }
        }
        bottomLabel(c, spec.label ?: e.label, w, h)
    }

    private fun drawCalendar(c: Canvas, w: Float, h: Float) {
        val now = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(now.time).capFirst()
        val dayNum = now.get(Calendar.DAY_OF_MONTH).toString()
        val ev = host.live.nextEvent
        // Mediano: cara 0 = fecha, cara 1 = evento. Ancho/grande: cara 0 = evento, cara 1 = fecha.
        val showEvent = ev != null && when (spec.size) {
            TileSize.MEDIUM -> face == 1
            TileSize.WIDE, TileSize.LARGE -> face == 0
            TileSize.SMALL -> false
        }

        if (spec.size == TileSize.SMALL) {
            text(c, dayNum, w / 2, h / 2 + sp(11f), 30f, Fonts.light, Paint.Align.CENTER); return
        }
        if (ev != null && showEvent) {
            var y = pad
            y += multiline(c, ev.title, pad, y, w - pad * 2, if (spec.size == TileSize.MEDIUM) 15f else 17f,
                if (spec.size == TileSize.LARGE) 4 else 2, Fonts.medium)
            val whenTxt = eventTime(ev)
            multiline(c, whenTxt, pad, y + dp(2f), w - pad * 2, 13f, 1, Fonts.regular, 200)
            bottomLabel(c, "$dayName $dayNum", w, h)
            if (spec.size != TileSize.MEDIUM) Glyphs.draw(c, G.CALENDAR, w - pad - dp(12f), h - dp(14f), dp(22f), Color.WHITE)
            return
        }
        if (spec.size == TileSize.MEDIUM) {
            text(c, dayName, w / 2, h * 0.32f, 14f, Fonts.regular, Paint.Align.CENTER, w - pad * 2)
            text(c, dayNum, w / 2, h * 0.32f + sp(52f), 52f, Fonts.light, Paint.Align.CENTER)
        } else {
            text(c, dayNum, pad, h / 2 + sp(18f), 56f, Fonts.light)
            tp.textSize = sp(56f); tp.typeface = Fonts.light
            val nw = tp.measureText(dayNum)
            text(c, dayName, pad + nw + dp(10f), h / 2 + sp(18f), 20f, Fonts.light, maxW = w - nw - pad * 3)
            if (ev == null) text(c, "Sin eventos próximos", pad, h - dp(10f), 12f, alpha = 200)
        }
    }

    private fun eventTime(ev: CalEvent): String {
        if (ev.allDay) return "Todo el día"
        val cal = Calendar.getInstance(); val today = cal.get(Calendar.DAY_OF_YEAR); val year = cal.get(Calendar.YEAR)
        cal.timeInMillis = ev.begin
        val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(ev.begin))
        return if (cal.get(Calendar.DAY_OF_YEAR) == today && cal.get(Calendar.YEAR) == year) time
        else SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(ev.begin)).capFirst() + ", " + time
    }

    private fun deg(t: Int) = "$t°"

    private fun drawWeather(c: Canvas, w: Float, h: Float) {
        val wd = host.live.weather
        if (wd == null) {
            Glyphs.draw(c, G.PARTLY_DAY, w / 2, h / 2 - dp(6f), min(w, h) * 0.45f, Color.WHITE)
            if (spec.size != TileSize.SMALL) {
                val msg = host.live.weatherError ?: "Clima"
                bottomLabel(c, msg, w, h)
            }
            return
        }
        when (spec.size) {
            TileSize.SMALL -> {
                Glyphs.draw(c, wd.glyph, w / 2, h / 2 - dp(5f), h * 0.5f, Color.WHITE)
                text(c, deg(wd.temp), w / 2, h - dp(6f), 11f, Fonts.regular, Paint.Align.CENTER)
            }
            TileSize.MEDIUM -> if (face == 0) {
                Glyphs.draw(c, wd.glyph, w / 2, h * 0.32f, w * 0.38f, Color.WHITE)
                text(c, deg(wd.temp), w / 2, h * 0.32f + w * 0.2f + sp(30f), 30f, Fonts.light, Paint.Align.CENTER)
                bottomLabel(c, (spec.label ?: wd.city.ifEmpty { "Clima" }), w, h)
            } else {
                var y = pad
                y += multiline(c, (spec.label ?: wd.city.ifEmpty { "Clima" }), pad, y, w - pad * 2, 15f, 1, Fonts.medium)
                y += multiline(c, wd.description, pad, y + dp(2f), w - pad * 2, 13f, 2, Fonts.regular, 220) + dp(6f)
                text(c, "↑ ${deg(wd.hi)}   ↓ ${deg(wd.lo)}", pad, y + sp(14f), 14f, Fonts.light)
            }
            TileSize.WIDE -> if (face == 0) drawWeatherNow(c, wd, w, h, h * 0.42f) else drawForecast(c, wd, w, h, pad, h - dp(28f))
            TileSize.LARGE -> {
                drawWeatherNow(c, wd, w, h * 0.55f, h * 0.24f)
                drawForecast(c, wd, w, h, h * 0.55f, h - dp(28f))
            }
        }
    }

    private fun drawWeatherNow(c: Canvas, wd: WeatherData, w: Float, h: Float, glyph: Float) {
        val gx = pad + glyph / 2 + dp(4f)
        Glyphs.draw(c, wd.glyph, gx, h / 2 - dp(6f), glyph, Color.WHITE)
        val tx = gx + glyph / 2 + dp(10f)
        text(c, deg(wd.temp), tx, h / 2 + sp(10f), 42f, Fonts.light)
        tp.textSize = sp(42f); tp.typeface = Fonts.light
        val rx = tx + tp.measureText(deg(wd.temp)) + dp(14f)
        val rw = w - rx - pad
        if (rw > dp(40f)) {
            multiline(c, wd.description, rx, h / 2 - sp(22f), rw, 14f, 2, Fonts.regular)
            text(c, "↑ ${deg(wd.hi)}  ↓ ${deg(wd.lo)}", rx, h / 2 + sp(22f), 13f, Fonts.regular, maxW = rw, alpha = 220)
        }
        if (spec.size == TileSize.WIDE) bottomLabel(c, (spec.label ?: wd.city.ifEmpty { "Clima" }), w, h)
        else text(c, (spec.label ?: wd.city.ifEmpty { "Clima" }), pad, pad + sp(16f), 16f, Fonts.medium, maxW = w - pad * 2)
    }

    private fun drawForecast(c: Canvas, wd: WeatherData, w: Float, h: Float, top: Float, bottom: Float) {
        val days = wd.days.take(3)
        if (days.isEmpty()) return
        val colW = (w - pad * 2) / days.size
        val gSize = min(colW * 0.45f, (bottom - top) * 0.38f)
        days.forEachIndexed { i, d ->
            val cx = pad + colW * i + colW / 2
            text(c, d.name, cx, top + sp(16f), 14f, Fonts.regular, Paint.Align.CENTER)
            Glyphs.draw(c, Glyphs.forWeather(d.code, true), cx, top + sp(22f) + gSize / 2 + dp(4f), gSize, Color.WHITE)
            text(c, "${deg(d.hi)} ${deg(d.lo)}", cx, top + sp(22f) + gSize + dp(8f) + sp(13f), 13f, Fonts.light, Paint.Align.CENTER)
        }
        if (spec.size == TileSize.WIDE) bottomLabel(c, (spec.label ?: wd.city.ifEmpty { "Clima" }), w, h)
    }

    private fun drawPhotos(c: Canvas, w: Float, h: Float) {
        val photos = host.live.photos
        if (photos.isEmpty()) {
            Glyphs.draw(c, G.PHOTO, w / 2, h / 2 - dp(4f), min(w, h) * 0.42f, Color.WHITE)
            bottomLabel(c, spec.label ?: "Fotos", w, h)
            return
        }
        if (prevPhoto in photos.indices && photoFade < 1f) drawCrop(c, photos[prevPhoto], w, h, 255)
        drawCrop(c, photos[photoIndex % photos.size], w, h, (photoFade * 255).toInt())
        if (spec.size != TileSize.SMALL) {
            fill.color = withAlpha(Color.BLACK, 70)
            c.drawRect(0f, h - dp(26f), w, h, fill)
        }
        bottomLabel(c, spec.label ?: "Fotos", w, h)
    }

    private fun drawCrop(c: Canvas, b: Bitmap, w: Float, h: Float, alpha: Int) {
        val scale = max(w / b.width, h / b.height)
        val sw = w / scale; val sh = h / scale
        val sx = (b.width - sw) / 2; val sy = (b.height - sh) / 2
        src.set(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        dst.set(0f, 0f, w, h)
        bmpPaint.alpha = alpha
        c.drawBitmap(b, src, dst, bmpPaint)
        bmpPaint.alpha = 255
    }

    private fun drawBattery(c: Canvas, w: Float, h: Float) {
        val pct = host.live.batteryPct
        val txt = if (pct >= 0) "$pct%" else "—"
        if (spec.size == TileSize.SMALL) {
            text(c, txt, w / 2, h / 2 + sp(7f), 18f, Fonts.light, Paint.Align.CENTER)
            if (host.live.charging) Glyphs.draw(c, G.BOLT, w / 2, h - dp(9f), dp(12f), Color.WHITE)
            return
        }
        val g = min(w, h) * 0.3f
        Glyphs.draw(c, if (host.live.charging) G.BOLT else G.BATTERY, pad + g / 2, pad + g / 2, g, Color.WHITE)
        text(c, txt, pad, h / 2 + sp(22f), 40f, Fonts.light)
        bottomLabel(c, spec.label ?: if (host.live.charging) "Cargando" else "Batería", w, h)
    }

    // ---------- animaciones vivas ----------

    fun faces(): Int = when (spec.kind) {
        TileKind.CALENDAR -> if (host.live.nextEvent != null && spec.size != TileSize.SMALL) 2 else 1
        TileKind.WEATHER -> if (host.live.weather != null && (spec.size == TileSize.MEDIUM || spec.size == TileSize.WIDE)) 2 else 1
        else -> 1
    }

    fun canFlip() = faces() > 1 && !flipping

    /** Volteo vertical como el de los Live Tiles. */
    fun flip() {
        if (!canFlip()) return
        flipping = true
        pivotX = width / 2f; pivotY = height / 2f
        anim().rotationX(90f).setDuration(260).withEndAction {
            face = (face + 1) % faces()
            rotationX = -90f
            invalidate()
            anim().rotationX(0f).setDuration(260).withEndAction { flipping = false }.start()
        }.start()
    }

    fun nextPhoto() {
        val n = host.live.photos.size
        if (n < 2) { photoIndex = 0; invalidate(); return }
        prevPhoto = photoIndex % n
        photoIndex = (photoIndex + 1) % n
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            addUpdateListener { photoFade = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun resetFace() { if (face >= faces()) face = 0 }
}
