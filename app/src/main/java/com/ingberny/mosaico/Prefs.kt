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

    /** Acción (SysActions) al deslizar hacia abajo en Inicio. 0 y 1 coinciden con las versiones anteriores. */
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

    /** Barra de navegación propia dentro de Mosaico (oculta la de Android en Inicio). */
    var navInLauncher: Boolean get() = b("navInLauncher", true); set(v) = put("navInLauncher", v)
    /** 0 = negra, 1 = igual al fondo, 2 = color de énfasis. */
    var navColor: Int get() = i("navColor", 0); set(v) = put("navColor", v)

    /** Barra de estado propia dentro de Mosaico (oculta la de Android en Inicio). */
    var statusInLauncher: Boolean get() = b("statusInLauncher", true); set(v) = put("statusInLauncher", v)
    /** Con el servicio de accesibilidad: barra de estado y de navegación en todo el teléfono. */
    var statusSystem: Boolean get() = b("statusSystem", true); set(v) = put("statusSystem", v)
    var navSystem: Boolean get() = b("navSystem", true); set(v) = put("navSystem", v)
    var lockEnabled: Boolean get() = b("lockEnabled", false); set(v) = put("lockEnabled", v)
    /** 0 = foto de Inicio, 1 = foto propia, 2 = color de énfasis, 3 = negro. */
    var lockBg: Int get() = i("lockBg", 2); set(v) = put("lockBg", v)
    var lockNotifs: Boolean get() = b("lockNotifs", true); set(v) = put("lockNotifs", v)
    /** 0 = pequeña, 1 = normal, 2 = grande. */
    var lockClockSize: Int get() = i("lockClockSize", 1); set(v) = put("lockClockSize", v)
    /** 0 = abajo a la izquierda (W10M), 1 = centrada arriba. */
    var lockClockPos: Int get() = i("lockClockPos", 0); set(v) = put("lockClockPos", v)
    var lockShowDate: Boolean get() = b("lockShowDate", true); set(v) = put("lockShowDate", v)
    var lockShowEvent: Boolean get() = b("lockShowEvent", true); set(v) = put("lockShowEvent", v)
    /** Oscurecido del fondo de bloqueo en % (0 a 80). */
    var lockDim: Int get() = i("lockDim", 0); set(v) = put("lockDim", v)

    // ---------- barra de estado ----------
    /** -1 = automático, 0 = transparente, 1 = negra, 2 = igual al fondo, 3 = énfasis, 4 = personalizado. */
    var statusBg: Int get() = i("statusBg", -1); set(v) = put("statusBg", v)
    var statusBgCustom: Int get() = i("statusBgCustom", 0xFF000000.toInt()); set(v) = put("statusBgCustom", v)
    /** 0 = automático, 1 = blanco, 2 = negro, 3 = énfasis, 4 = personalizado. */
    var statusFg: Int get() = i("statusFg", 0); set(v) = put("statusFg", v)
    var statusFgCustom: Int get() = i("statusFgCustom", 0xFFFFFFFF.toInt()); set(v) = put("statusFgCustom", v)
    var sbSignal: Boolean get() = b("sbSignal", true); set(v) = put("sbSignal", v)
    var sbWifi: Boolean get() = b("sbWifi", true); set(v) = put("sbWifi", v)
    var sbModes: Boolean get() = b("sbModes", true); set(v) = put("sbModes", v)
    var sbNotifCount: Boolean get() = b("sbNotifCount", true); set(v) = put("sbNotifCount", v)
    var sbBatteryIcon: Boolean get() = b("sbBatteryIcon", true); set(v) = put("sbBatteryIcon", v)
    var sbBatteryPct: Boolean get() = b("sbBatteryPct", false); set(v) = put("sbBatteryPct", v)
    var sbClock: Boolean get() = b("sbClock", true); set(v) = put("sbClock", v)
    /** 0 = derecha, 1 = centro, 2 = izquierda. */
    var sbClockPos: Int get() = i("sbClockPos", 0); set(v) = put("sbClockPos", v)
    /** Tamaño del texto de la barra en %. */
    var sbTextScale: Int get() = i("sbTextScale", 100); set(v) = put("sbTextScale", v)
    /** Acciones (SysActions) al tocar y al deslizar la barra de estado. */
    var statusTap: Int get() = i("statusTap", SysActions.ACTION_CENTER); set(v) = put("statusTap", v)
    var statusSwipe: Int get() = i("statusSwipe", SysActions.ACTION_CENTER); set(v) = put("statusSwipe", v)

    // ---------- gestos en Inicio ----------
    var gestureDoubleTap: Int get() = i("gestureDoubleTap", SysActions.NONE); set(v) = put("gestureDoubleTap", v)

    // ---------- Centro de actividades ----------
    /** Botones de acción rápida visibles, en orden (ids separados por comas). */
    var acToggles: List<String>
        get() = (s("acToggles") ?: DEFAULT_TOGGLES.joinToString(",")).split(",").filter { it.isNotBlank() }
        set(v) = put("acToggles", v.joinToString(","))
    var acColumns: Int get() = i("acColumns", 4); set(v) = put("acColumns", v)
    /** Filas visibles con el panel contraído. */
    var acRows: Int get() = i("acRows", 1); set(v) = put("acRows", v)
    var acStartExpanded: Boolean get() = b("acStartExpanded", false); set(v) = put("acStartExpanded", v)
    /** Radio de las esquinas de los botones en dp. */
    var acRadius: Int get() = i("acRadius", 0); set(v) = put("acRadius", v)
    var acLabels: Boolean get() = b("acLabels", true); set(v) = put("acLabels", v)
    /** 0 = igual al fondo, 1 = negro, 2 = personalizado. */
    var acBg: Int get() = i("acBg", 0); set(v) = put("acBg", v)
    var acBgCustom: Int get() = i("acBgCustom", 0xFF1F1F1F.toInt()); set(v) = put("acBgCustom", v)
    /** Opacidad del panel en %. */
    var acOpacity: Int get() = i("acOpacity", 96); set(v) = put("acOpacity", v)
    /** Color de los botones encendidos: 0 = énfasis, 1 = personalizado. */
    var acOnMode: Int get() = i("acOnMode", 0); set(v) = put("acOnMode", v)
    var acOnCustom: Int get() = i("acOnCustom", 0xFF1BA1E2.toInt()); set(v) = put("acOnCustom", v)
    var acBrightness: Boolean get() = b("acBrightness", true); set(v) = put("acBrightness", v)
    var acHeader: Boolean get() = b("acHeader", true); set(v) = put("acHeader", v)
    var acGroupNotifs: Boolean get() = b("acGroupNotifs", true); set(v) = put("acGroupNotifs", v)
    var acAndroidLink: Boolean get() = b("acAndroidLink", true); set(v) = put("acAndroidLink", v)
    /** Tamaño del texto de las notificaciones en %. */
    var acTextScale: Int get() = i("acTextScale", 100); set(v) = put("acTextScale", v)

    /** Muestra el contenido de las notificaciones (mensajes) en los mosaicos medianos, anchos y grandes. */
    var tilePreviews: Boolean get() = b("tilePreviews", true); set(v) = put("tilePreviews", v)

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
        /** Todos los botones de acción rápida, con su nombre, en el orden por defecto. */
        val ALL_TOGGLES: List<Pair<String, String>> = listOf(
            "wifi" to "Wi-Fi", "bt" to "Bluetooth", "torch" to "Linterna", "rotation" to "Rotación",
            "location" to "Ubicación", "airplane" to "Modo avión", "quiet" to "Horas de silencio",
            "saver" to "Ahorro de batería", "data" to "Datos móviles", "hotspot" to "Zona Wi-Fi",
            "camera" to "Cámara", "settings" to "Toda la configuración",
            "lock" to "Bloquear pantalla", "screenshot" to "Captura de pantalla", "volume" to "Volumen",
            "autobright" to "Brillo automático",
        )
        val DEFAULT_TOGGLES: List<String> = ALL_TOGGLES.take(12).map { it.first }

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
