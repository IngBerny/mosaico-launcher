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
    val shapeColor = when (prefs.shapeColorMode) { 1 -> pal.accent; 2 -> prefs.shapeColor; else -> pal.chrome }
    val glyphColor = when (prefs.glyphColorMode) { 1 -> pal.accent; 2 -> prefs.glyphColor; else -> pal.fg }
    val face = Fonts.pick(prefs.font)
    val bigFace = Fonts.pick(prefs.font, big = true)
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

/** Ícono de una app, con un punto de énfasis si tiene notificaciones. */
class AppIconView(
    ctx: Context,
    private val look: AppLook,
    private val repo: AppRepo,
    val entry: AppEntry,
    private val badge: () -> Int,
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        look.draw(c, repo, entry, width / 2f, height / 2f, s * 0.9f)
        if (badge() > 0) {
            p.color = look.pal.accent
            c.drawCircle(width / 2f + s * 0.36f, height / 2f - s * 0.36f, s * 0.07f, p)
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
