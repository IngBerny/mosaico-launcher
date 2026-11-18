package com.ingberny.mosaico

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.view.View
import java.text.Collator
import java.text.Normalizer

class AppEntry(val info: LauncherActivityInfo) {
    val label: String = info.label?.toString() ?: info.componentName.packageName
    val component: ComponentName = info.componentName
    val user: UserHandle = info.user
    val pkg: String get() = component.packageName
    val cls: String get() = component.className
    val key: String = component.flattenToShortString()
    val installTime: Long = info.firstInstallTime
    val sortKey: String = normalize(label)

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
    }
}

class AppRepo(private val ctx: Context, private val prefs: Prefs) {
    val la: LauncherApps = ctx.getSystemService(LauncherApps::class.java)
    /** Todas las apps (incluidas las ocultas), para los mosaicos. */
    var allApps: List<AppEntry> = emptyList(); private set
    /** Apps visibles en la lista. */
    var apps: List<AppEntry> = emptyList(); private set

    /** Paquete de íconos activo (se carga en segundo plano). */
    var pack: IconPackLoader? = null
        set(v) { field = v; icons.clear(); monos.clear(); colors.clear() }

    private val icons = HashMap<String, Drawable>()
    private val monos = HashMap<String, Drawable?>()
    private val colors = HashMap<String, Int?>()

    fun clearCaches() { icons.clear(); monos.clear(); colors.clear(); fgLayers.clear() }

    fun load() {
        val collator = Collator.getInstance()
        allApps = la.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != ctx.packageName }
            .map { AppEntry(it) }
            .sortedWith(compareBy(collator) { it.sortKey })
        applyHidden()
        icons.clear(); monos.clear(); colors.clear()
    }

    fun applyHidden() {
        val hidden = prefs.hiddenApps
        apps = allApps.filter { it.key !in hidden }
    }

    fun find(pkg: String, cls: String): AppEntry? =
        allApps.firstOrNull { it.pkg == pkg && it.cls == cls } ?: allApps.firstOrNull { it.pkg == pkg }

    fun find(t: TileSpec): AppEntry? = if (t.kind == TileKind.APP) find(t.pkg, t.cls) else null

    fun icon(e: AppEntry): Drawable = icons.getOrPut(e.key) {
        pack?.icon(e.component) ?: e.info.getIcon(ctx.resources.displayMetrics.densityDpi)
    }

    /** Capa monocromática de los íconos temáticos (Android 13+), teñida de blanco. */
    fun mono(e: AppEntry): Drawable? {
        if (Build.VERSION.SDK_INT < 33) return null
        if (pack?.has(e.component) == true) return null
        return monos.getOrPut(e.key) {
            val ic = icon(e) as? AdaptiveIconDrawable
            ic?.monochrome?.constantState?.newDrawable()?.mutate()
        }
    }

    private val fgLayers = HashMap<String, Drawable?>()

    /** Capa frontal del ícono adaptable (sin el fondo), útil como silueta. */
    private fun foreground(e: AppEntry): Drawable? = fgLayers.getOrPut(e.key) {
        (icon(e) as? AdaptiveIconDrawable)?.foreground?.constantState?.newDrawable()?.mutate()
    }

    /**
     * Dibuja el ícono de una app según el estilo elegido.
     * Estilo 1: monocromático cuando existe; estilo 2: silueta de cualquier ícono, pintada de [color].
     */
    fun drawIcon(c: Canvas, e: AppEntry, cx: Float, cy: Float, size: Float, color: Int) {
        val style = prefs.iconStyle
        var d: Drawable = icon(e)
        var s = size
        var tint = false
        val m = if (style >= 1) mono(e) else null
        when {
            m != null -> { d = m; s = size * 1.5f; tint = true }
            style == 2 && pack?.has(e.component) == true -> { tint = true }
            style == 2 -> foreground(e)?.let { d = it; s = size * 1.5f; tint = true } ?: run { tint = true }
        }
        d.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
        d.colorFilter = if (tint) PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) else null
        d.draw(c)
        if (tint) d.colorFilter = null
    }

    /** Color dominante del ícono, ajustado para que funcione como fondo de mosaico. */
    fun color(e: AppEntry): Int? = colors.getOrPut(e.key) { dominant(icon(e)) }

    private fun dominant(d: Drawable): Int? {
        val n = 28
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val old = Rect(d.bounds)
        d.setBounds(0, 0, n, n); d.draw(c); d.bounds = old
        val weight = FloatArray(12); val rs = FloatArray(12); val gs = FloatArray(12); val bs = FloatArray(12)
        val hsv = FloatArray(3)
        for (y in 0 until n) for (x in 0 until n) {
            val px = bmp.getPixel(x, y)
            if (Color.alpha(px) < 160) continue
            Color.colorToHSV(px, hsv)
            if (hsv[1] < 0.28f || hsv[2] < 0.22f) continue
            val w = hsv[1] * hsv[2]
            val bin = ((hsv[0] / 30f).toInt()) % 12
            weight[bin] += w; rs[bin] += Color.red(px) * w; gs[bin] += Color.green(px) * w; bs[bin] += Color.blue(px) * w
        }
        bmp.recycle()
        var best = -1; var bw = 0f
        for (i in 0 until 12) if (weight[i] > bw) { bw = weight[i]; best = i }
        if (best < 0 || bw < 3f) return null
        val col = Color.rgb((rs[best] / bw).toInt(), (gs[best] / bw).toInt(), (bs[best] / bw).toInt())
        Color.colorToHSV(col, hsv)
        hsv[1] = hsv[1].coerceIn(0.55f, 0.9f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.78f)
        return Color.HSVToColor(hsv)
    }

    fun launch(e: AppEntry, from: View?) {
        val opts = from?.let {
            ActivityOptions.makeClipRevealAnimation(it, 0, 0, it.width, it.height).toBundle()
        }
        val bounds = from?.let {
            val l = IntArray(2); it.getLocationOnScreen(l)
            Rect(l[0], l[1], l[0] + it.width, l[1] + it.height)
        }
        try { la.startMainActivity(e.component, e.user, bounds, opts) } catch (ex: Exception) {
            ctx.packageManager.getLaunchIntentForPackage(e.pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it)
            }
        }
    }

    fun appInfo(e: AppEntry) {
        try { la.startAppDetailsActivity(e.component, e.user, null, null) } catch (ex: Exception) { }
    }

    fun uninstall(e: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (ex: Exception) { }
    }

    fun watch(onChange: (removedPkg: String?) -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange(packageName)
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange(null)
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange(null)
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange(null)
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange(null)
        }
        la.registerCallback(cb, Handler(Looper.getMainLooper()))
        return cb
    }
}
