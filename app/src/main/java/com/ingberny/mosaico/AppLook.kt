package com.ingberny.mosaico

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import android.view.View
import org.json.JSONObject

/** Cómo se ven las apps en el inicio minimalista: forma, colores, nombres propios y fuente. */
class AppLook(val prefs: Prefs, val pal: Palette) {
    val shape = prefs.iconShape
    /** Cambia cuando cambian los ajustes: invalida la caché de íconos. */
    val stamp = prefs.version
    val shapeColor = when (prefs.shapeColorMode) { 1 -> pal.accent; 2 -> prefs.shapeColor; else -> pal.chrome }
    val glyphColor = when (prefs.glyphColorMode) { 1 -> pal.accent; 2 -> prefs.glyphColor; else -> pal.fg }
    val face = Fonts.pick(prefs.font)
    val bigFace = Fonts.pick(prefs.font, big = true)
    /** Fuente de los nombres de las apps. */
    val labelFace = if (prefs.labelFont < 0) face else Fonts.pick(prefs.labelFont)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val renames: MutableMap<String, String> = HashMap<String, String>().also { m ->
        try {
            val o = JSONObject(prefs.renamesJson ?: "{}")
            for (k in o.keys()) m[k] = o.getString(k)
        } catch (e: Exception) { }
    }

    fun name(e: AppEntry): String = renames[e.key] ?: e.label

    fun rename(e: AppEntry, newName: String?) {
        if (newName.isNullOrBlank() || newName == e.label) renames.remove(e.key) else renames[e.key] = newName
        prefs.renamesJson = JSONObject(renames as Map<*, *>).toString()
    }

    /** Dibuja el ícono con la forma de fondo elegida. [size] es el lado total. */
    fun draw(c: Canvas, repo: AppRepo, e: AppEntry, cx: Float, cy: Float, size: Float) {
        val scale = prefs.iconScale / 100f
        if (shape == 0) {
            repo.drawIcon(c, e, cx, cy, size * 0.92f * scale, glyphColor)
            return
        }
        p.style = Paint.Style.FILL
        p.color = shapeColor
        val h = size / 2f
        when (shape) {
            1 -> c.drawCircle(cx, cy, h, p)
            2 -> c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, size * 0.28f, size * 0.28f, p)
            else -> c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, size * 0.06f, size * 0.06f, p)
        }
        // Línea muy fina alrededor, como en el tema Mirador.
        p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.012f; p.color = withAlpha(pal.fg, 30)
        when (shape) {
            1 -> c.drawCircle(cx, cy, h, p)
            2 -> c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, size * 0.28f, size * 0.28f, p)
            else -> c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, size * 0.06f, size * 0.06f, p)
        }
        val inner = if (prefs.iconStyle == 0) 0.72f else 0.5f
        repo.drawIcon(c, e, cx, cy, size * inner * scale, glyphColor)
    }
}

/**
 * Imágenes ya dibujadas de los íconos. Dibujar un ícono adaptativo con su forma en cada cuadro es caro;
 * con la caché, desplazarse solo copia imágenes y va fluido incluso a 120/144 Hz.
 */
object IconCache {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(8 * 1024)) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount / 1024
    }
    fun get(k: String): android.graphics.Bitmap? = cache.get(k)
    fun put(k: String, b: android.graphics.Bitmap) { cache.put(k, b) }
    fun clear() = cache.evictAll()
}

/** Ícono de una app, con un punto de énfasis si tiene notificaciones. Se puede reutilizar con [bind]. */
class AppIconView(
    ctx: Context,
    private val look: AppLook,
    private val repo: AppRepo,
    entry: AppEntry,
    private var badge: () -> Int,
) : View(ctx) {
    var entry: AppEntry = entry
        private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)

    fun bind(e: AppEntry, badgeOf: () -> Int) {
        if (e.key != entry.key) { entry = e; contentDescription = look.name(e) }
        badge = badgeOf
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val s = minOf(w, h).toFloat()
        val key = "${entry.key}|$w|$h|${look.stamp}|${System.identityHashCode(repo.pack)}"
        var bmp = IconCache.get(key)
        if (bmp == null) {
            bmp = try {
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also {
                    look.draw(Canvas(it), repo, entry, w / 2f, h / 2f, s * 0.9f)
                    IconCache.put(key, it)
                }
            } catch (e: Throwable) { null }
        }
        if (bmp != null) c.drawBitmap(bmp, 0f, 0f, bp) else look.draw(c, repo, entry, w / 2f, h / 2f, s * 0.9f)
        if (badge() > 0) {
            p.color = look.pal.accent
            c.drawCircle(w / 2f + s * 0.36f, h / 2f - s * 0.36f, s * 0.07f, p)
        }
    }
}

/** Favoritos y dock por defecto en el primer arranque: lo esencial y lo que más se usa. */
object DefaultApps {
    private fun resolve(ctx: Context, repo: AppRepo, i: Intent): AppEntry? = try {
        val ri = ctx.packageManager.resolveActivity(i, 0)
        val pkg = ri?.activityInfo?.packageName
        if (pkg == null || pkg == "android") null else repo.apps.firstOrNull { it.pkg == pkg }
    } catch (e: Exception) { null }

    private fun byPkg(repo: AppRepo, vararg pkgs: String): AppEntry? =
        pkgs.firstNotNullOfOrNull { p -> repo.apps.firstOrNull { it.pkg == p } }

    fun dock(ctx: Context, repo: AppRepo): List<String> = listOfNotNull(
        resolve(ctx, repo, Intent(Intent.ACTION_DIAL)),
        resolve(ctx, repo, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
        resolve(ctx, repo, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
        resolve(ctx, repo, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
    ).map { it.key }.distinct()

    fun favorites(ctx: Context, repo: AppRepo, exclude: List<String>): List<String> = listOfNotNull(
        byPkg(repo, "com.whatsapp", "org.telegram.messenger"),
        byPkg(repo, "com.spotify.music", "com.google.android.apps.youtube.music", "com.hihonor.cloudmusic", "com.hihonor.hnmusicplayer"),
        byPkg(repo, "com.google.android.youtube"),
        byPkg(repo, "com.google.android.apps.maps", "com.waze"),
        resolve(ctx, repo, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
        resolve(ctx, repo, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)),
    ).map { it.key }.distinct().filter { it !in exclude }.take(6)
}

/** Color legible sobre [bg]. */
fun readableOn(bg: Int): Int = if (Color.luminance(bg) > 0.55f) Color.BLACK else Color.WHITE

/**
 * Celda de app al estilo de los launchers Android (POCO, Nova): ícono con su nombre debajo.
 * Tamaños en dp (ícono) y sp (nombre).
 */
fun appCell(
    ctx: Context, host: HomeHost, e: AppEntry, iconDp: Int, labelSp: Int, showLabel: Boolean,
    labelColor: Int, shadow: Boolean, place: String,
): View {
    val look = host.look
    val cell = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        contentDescription = look.name(e)
    }
    cell.addView(AppIconView(ctx, look, host.repo, e) { host.live.badges[e.pkg] ?: 0 },
        android.widget.LinearLayout.LayoutParams(ctx.dpi(iconDp), ctx.dpi(iconDp)))
    if (showLabel) cell.addView(ctx.label(look.name(e), labelSp.toFloat(), labelColor, look.labelFace).apply {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = android.view.Gravity.CENTER
        setPadding(ctx.dpi(2), ctx.dpi(5), ctx.dpi(2), 0)
        if (shadow) setShadowLayer(ctx.dp(3f), 0f, ctx.dp(1f), 0x99000000.toInt())
    }, android.widget.LinearLayout.LayoutParams(-1, -2))
    cell.setOnClickListener { host.launch(e, cell) }
    cell.setOnLongClickListener { host.appMenu(e, cell, place); true }
    return cell
}
