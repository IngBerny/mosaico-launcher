package com.ingberny.mosaico

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("mosaico", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun s(k: String): String? = sp.getString(k, null)
    private fun put(k: String, v: Any?) {
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is String -> e.putString(k, v)
            null -> e.remove(k)
        }
        e.apply()
    }

    var dark: Boolean get() = b("dark", true); set(v) = put("dark", v)
    var accent: Int get() = i("accent", 0xFF1BA1E2.toInt()); set(v) = put("accent", v)

    /** true = cada mosaico toma el color de su app; false = color de énfasis. */
    var appColors: Boolean get() = b("appColors", false); set(v) = put("appColors", v)

    /** 0 = fondo de pantalla del sistema, 1 = imagen dentro de los mosaicos, 2 = color sólido. */
    var bgMode: Int get() = i("bgMode", 0); set(v) = put("bgMode", v)

    /** Opacidad de los mosaicos en % (modo 0). */
    var tileOpacity: Int get() = i("tileOpacity", 80); set(v) = put("tileOpacity", v)
    var moreTiles: Boolean get() = b("moreTiles", false); set(v) = put("moreTiles", v)
    var monoIcons: Boolean get() = b("monoIcons", true); set(v) = put("monoIcons", v)
    var turnstile: Boolean get() = b("turnstile", true); set(v) = put("turnstile", v)

    /** 0 = Centro de actividades de Mosaico, 1 = panel de notificaciones de Android. */
    var swipeDown: Int get() = i("swipeDown", 0); set(v) = put("swipeDown", v)

    var hideLabels: Boolean get() = b("hideLabels", false); set(v) = put("hideLabels", v)
    /** 0 = juntos, 1 = normal, 2 = separados. */
    var tileGap: Int get() = i("tileGap", 1); set(v) = put("tileGap", v)
    /** Tamaño de los íconos en %, de 60 a 140. */
    var iconScale: Int get() = i("iconScale", 100); set(v) = put("iconScale", v)
    var iconPack: String get() = s("iconPack") ?: ""; set(v) = put("iconPack", v)
    /** 0 = colores originales, 1 = monocromático cuando la app lo trae, 2 = silueta para todas. */
    var iconStyle: Int get() = i("iconStyle", if (monoIcons) 1 else 0); set(v) = put("iconStyle", v)
    /** Color de íconos y texto de los mosaicos (ARGB) o TileSpec.COLOR_ACCENT. */
    var tileFg: Int get() = i("tileFg", 0xFFFFFFFF.toInt()); set(v) = put("tileFg", v)
    /** Radio de las esquinas de los mosaicos en dp (0 = cuadrados, como el original). */
    var tileRadius: Int get() = i("tileRadius", 0); set(v) = put("tileRadius", v)
    /** Tamaño del texto de los mosaicos en %. */
    var textScale: Int get() = i("textScale", 100); set(v) = put("textScale", v)

    var lockLayout: Boolean get() = b("lockLayout", false); set(v) = put("lockLayout", v)
    var showFooter: Boolean get() = b("showFooter", true); set(v) = put("showFooter", v)
    var askedDefault: Boolean get() = b("askedDefault", false); set(v) = put("askedDefault", v)
    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("hidden", v).apply() }

    var celsius: Boolean get() = b("celsius", true); set(v) = put("celsius", v)
    var city: String get() = s("city") ?: ""; set(v) = put("city", v)

    var tilesJson: String? get() = s("tiles"); set(v) = put("tiles", v)
    var weatherJson: String? get() = s("weather"); set(v) = put("weather", v)
    var weatherTime: Long get() = sp.getLong("weatherTime", 0L); set(v) = put("weatherTime", v)

    /** Se incrementa cada vez que cambia algo visual, para que el inicio se reconstruya. */
    var version: Int get() = i("version", 0); set(v) = put("version", v)
    fun bump() { version = version + 1 }

    fun palette() = Palette(dark, accent)

    companion object {
        val ACCENTS: List<Pair<String, Int>> = listOf(
            "Lima" to 0xFFA4C400.toInt(),
            "Verde" to 0xFF60A917.toInt(),
            "Esmeralda" to 0xFF008A00.toInt(),
            "Verde azulado" to 0xFF00ABA9.toInt(),
            "Cian" to 0xFF1BA1E2.toInt(),
            "Cobalto" to 0xFF0050EF.toInt(),
            "Índigo" to 0xFF6A00FF.toInt(),
            "Violeta" to 0xFFAA00FF.toInt(),
            "Rosa" to 0xFFF472D0.toInt(),
            "Magenta" to 0xFFD80073.toInt(),
            "Carmesí" to 0xFFA20025.toInt(),
            "Rojo" to 0xFFE51400.toInt(),
            "Naranja" to 0xFFFA6800.toInt(),
            "Ámbar" to 0xFFF0A30A.toInt(),
            "Amarillo" to 0xFFE3C800.toInt(),
            "Marrón" to 0xFF825A2C.toInt(),
            "Oliva" to 0xFF6D8764.toInt(),
            "Acero" to 0xFF647687.toInt(),
            "Malva" to 0xFF76608A.toInt(),
            "Topo" to 0xFF87794E.toInt(),
        )
    }
}
