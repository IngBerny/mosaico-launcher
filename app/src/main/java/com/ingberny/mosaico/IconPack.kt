package com.ingberny.mosaico

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

class IconPackInfo(val pkg: String, val label: String)

/** Paquetes de íconos compatibles con ADW / Nova / GO (los de Google Play casi siempre lo son). */
object IconPacks {
    private val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

    fun installed(ctx: Context): List<IconPackInfo> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        val intents = actions.map { Intent(it) } +
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME")
        for (i in intents) {
            try {
                for (ri in pm.queryIntentActivities(i, 0)) {
                    val p = ri.activityInfo.packageName
                    if (p !in found) found[p] = IconPackInfo(p, ri.loadLabel(pm).toString())
                }
            } catch (e: Exception) { }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }
}

/** Lee el appfilter.xml del paquete y entrega el ícono correspondiente a cada app. */
class IconPackLoader(ctx: Context, val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()

    init {
        val parser: XmlPullParser? = try {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) res.getXml(id)
            else Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
        } catch (e: Exception) { null }
        if (parser != null) {
            try {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val draw = parser.getAttributeValue(null, "drawable")
                        if (comp != null && draw != null) key(comp)?.let { if (it !in map) map[it] = draw }
                    }
                    ev = parser.next()
                }
            } catch (e: Exception) { }
        }
    }

    /** "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main" */
    private fun key(component: String): String? {
        val s = component.substringAfter("{", "").substringBefore("}", "")
        val pkgName = s.substringBefore("/", "")
        var cls = s.substringAfter("/", "")
        if (pkgName.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith(".")) cls = pkgName + cls
        return "$pkgName/$cls"
    }

    val size: Int get() = map.size

    fun has(c: ComponentName): Boolean = map.containsKey("${c.packageName}/${c.className}")

    fun icon(c: ComponentName): Drawable? {
        val name = map["${c.packageName}/${c.className}"] ?: return null
        return try {
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else res.getDrawable(id, null)
        } catch (e: Exception) { null }
    }
}
