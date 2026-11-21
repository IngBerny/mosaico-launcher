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

    /** 0 = tinta (oscuro), 1 = papel (claro), 2 = negro puro. */
    var themeMode: Int get() = i("themeMode", if (b("dark", true)) 0 else 1); set(v) = put("themeMode", v)
    var dark: Boolean get() = themeMode != 1; set(v) = put("themeMode", if (v) 0 else 1)
    var accent: Int get() = i("accent", SAGE); set(v) = put("accent", v)
    /** Fuente: 0 fina, 1 normal, 2 monoespaciada, 3 condensada, 4 serif. */
    var font: Int get() = i("font", 0); set(v) = put("font", v)

    // ---------- inicio minimalista ----------
    /** 0 = fondo de Android, 1 = foto propia, 2 = color sólido, 3 = montañas (Mirador). */
    var homeBg: Int get() = i("homeBg", 3); set(v) = put("homeBg", v)
    /** Oscurecido sobre el fondo, en %. */
    var homeDim: Int get() = i("homeDim", 15); set(v) = put("homeDim", v)
    /** Reloj: 0 grande y fino, 1 mediano, 2 apilado (hora sobre minutos), 3 oculto. */
    var clockStyle: Int get() = i("clockStyle", 0); set(v) = put("clockStyle", v)
    /** Alineación de los widgets y favoritos: 0 izquierda, 1 centro, 2 derecha. */
    var homeAlign: Int get() = i("homeAlign", 1); set(v) = put("homeAlign", v)
    var favAlign: Int get() = i("favAlign", 1); set(v) = put("favAlign", v)
    /** Posición de los favoritos: 0 debajo del reloj, 1 al centro, 2 abajo (cerca del pulgar). */
    var favPos: Int get() = i("favPos", 2); set(v) = put("favPos", v)
    var showDate: Boolean get() = b("showDate", true); set(v) = put("showDate", v)
    var showGreeting: Boolean get() = b("showGreeting", true); set(v) = put("showGreeting", v)
    var greetName: String get() = s("greetName") ?: "Berny"; set(v) = put("greetName", v)
    var showWeather: Boolean get() = b("showWeather", true); set(v) = put("showWeather", v)
    var showBattery: Boolean get() = b("showBattery", true); set(v) = put("showBattery", v)
    var showEvent: Boolean get() = b("showEvent", true); set(v) = put("showEvent", v)
    var showMedia: Boolean get() = b("showMedia", true); set(v) = put("showMedia", v)
    /** Favoritos en Inicio (claves de app, en orden). */
    var favorites: List<String>
        get() = (s("favorites") ?: "").split("|").filter { it.isNotBlank() }
        set(v) = put("favorites", v.joinToString("|"))
    /** 0 = solo texto, 1 = ícono y texto, 2 = solo íconos. */
    /** 0 = solo texto, 1 = ícono y texto, 2 = solo íconos, 3 = cuadrícula con nombres (estilo POCO). */
    var favStyle: Int get() = i("favStyle", 3); set(v) = put("favStyle", v)
    /** Cuadrícula de Inicio: columnas, tamaño del ícono (dp), tamaño del nombre (sp), nombres y sombra. */
    var homeCols: Int get() = i("homeCols", 4); set(v) = put("homeCols", v)
    var homeIconSize: Int get() = i("homeIconSize", 56); set(v) = put("homeIconSize", v)
    var homeLabelSize: Int get() = i("homeLabelSize", 12); set(v) = put("homeLabelSize", v)
    var homeLabels: Boolean get() = b("homeLabels", true); set(v) = put("homeLabels", v)
    var labelShadow: Boolean get() = b("labelShadow", true); set(v) = put("labelShadow", v)
    /** Fuente de los nombres de las apps (independiente de la del reloj): -1 = la del tema. */
    var labelFont: Int get() = i("labelFont", 1); set(v) = put("labelFont", v)
    var dockIconSize: Int get() = i("dockIconSize", 54); set(v) = put("dockIconSize", v)
    var dockLabels: Boolean get() = b("dockLabels", false); set(v) = put("dockLabels", v)
    /** Fondo del dock: 0 ninguno, 1 tarjeta translúcida. */
    var dockBg: Int get() = i("dockBg", 1); set(v) = put("dockBg", v)
    /** Tamaño del texto de los favoritos en sp. */
    var favSize: Int get() = i("favSize", 24); set(v) = put("favSize", v)
    var favSpacing: Int get() = i("favSpacing", 14); set(v) = put("favSpacing", v)
    /** Barra de apps fija abajo. */
    var dockOn: Boolean get() = b("dockOn", true); set(v) = put("dockOn", v)
    var dock: List<String>
        get() = (s("dock") ?: "").split("|").filter { it.isNotBlank() }
        set(v) = put("dock", v.joinToString("|"))
    /** Nombres propios de las apps (clave → nombre), en JSON. */
    var renamesJson: String? get() = s("renames"); set(v) = put("renames", v)
    /** Forma detrás de los íconos: 0 ninguna, 1 círculo, 2 cuadrado suave, 3 cuadrado. */
    var iconShape: Int get() = i("iconShape", 0); set(v) = put("iconShape", v)
    /** Color de la forma: 0 = del tema, 1 = del énfasis, 2 = personalizado. */
    var shapeColorMode: Int get() = i("shapeColorMode", 0); set(v) = put("shapeColorMode", v)
    var shapeColor: Int get() = i("shapeColor", 0xFF1B2127.toInt()); set(v) = put("shapeColor", v)
    /** Color de los íconos de silueta o monocromáticos: 0 = del tema, 1 = énfasis, 2 = personalizado. */
    var glyphColorMode: Int get() = i("glyphColorMode", 0); set(v) = put("glyphColorMode", v)
    var glyphColor: Int get() = i("glyphColor", 0xFFE9E4DA.toInt()); set(v) = put("glyphColor", v)

    // ---------- cajón de apps ----------
    /** 0 = lista, 1 = cuadrícula. */
    var drawerStyle: Int get() = i("drawerStyle", 1); set(v) = put("drawerStyle", v)
    var drawerColumns: Int get() = i("drawerColumns", 5); set(v) = put("drawerColumns", v)
    var drawerIconSize: Int get() = i("drawerIconSize", 50); set(v) = put("drawerIconSize", v)
    var drawerLabelSize: Int get() = i("drawerLabelSize", 12); set(v) = put("drawerLabelSize", v)
    var drawerLabels: Boolean get() = b("drawerLabels", true); set(v) = put("drawerLabels", v)
    /** Espacio entre filas del cajón en dp. */
    var drawerRowGap: Int get() = i("drawerRowGap", 10); set(v) = put("drawerRowGap", v)
    /** Alto del cajón en % de la pantalla (los más pequeños quedan como una tarjeta abajo). */
    var drawerHeight: Int get() = i("drawerHeight", 100); set(v) = put("drawerHeight", v)
    var drawerCategories: Boolean get() = b("drawerCategories", true); set(v) = put("drawerCategories", v)
    /** 0 = alfabético, 1 = instaladas recientemente primero, 2 = por color. */
    var drawerSort: Int get() = i("drawerSort", 0); set(v) = put("drawerSort", v)
    var drawerKeyboard: Boolean get() = b("drawerKeyboard", false); set(v) = put("drawerKeyboard", v)
    var drawerLetters: Boolean get() = b("drawerLetters", true); set(v) = put("drawerLetters", v)
    var drawerIcons: Boolean get() = b("drawerIcons", true); set(v) = put("drawerIcons", v)
    /** Opacidad del fondo del cajón en %. */
    var drawerOpacity: Int get() = i("drawerOpacity", 92); set(v) = put("drawerOpacity", v)
    /** Abre la app sola cuando la búsqueda deja un único resultado. */
    var drawerAutoLaunch: Boolean get() = b("drawerAutoLaunch", false); set(v) = put("drawerAutoLaunch", v)

    // ---------- gestos extra ----------
    var gestureUp: Int get() = i("gestureUp", SysActions.APPS); set(v) = put("gestureUp", v)
    var gestureLeft: Int get() = i("gestureLeft", SysActions.SEARCH); set(v) = put("gestureLeft", v)
    var gestureRight: Int get() = i("gestureRight", SysActions.NONE); set(v) = put("gestureRight", v)
    var gestureLong: Int get() = i("gestureLong", SysActions.MENU); set(v) = put("gestureLong", v)
    /** Ya se aplicaron los valores por defecto del launcher minimalista (2.0). */
    var minimalMigrated: Boolean get() = b("minimalMigrated", false); set(v) = put("minimalMigrated", v)

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
    var iconStyle: Int get() = i("iconStyle", 0); set(v) = put("iconStyle", v)
    /** Color de íconos y texto de los mosaicos (ARGB) o TileSpec.COLOR_ACCENT. */
    var tileFg: Int get() = i("tileFg", 0xFFFFFFFF.toInt()); set(v) = put("tileFg", v)
    /** Radio de las esquinas de los mosaicos en dp (0 = cuadrados, como el original). */
    var tileRadius: Int get() = i("tileRadius", 0); set(v) = put("tileRadius", v)
    /** Tamaño del texto de los mosaicos en %. */
    var textScale: Int get() = i("textScale", 100); set(v) = put("textScale", v)

    /** Barra de navegación propia dentro de Mosaico (oculta la de Android en Inicio). */
    var navInLauncher: Boolean get() = b("navInLauncher", true); set(v) = put("navInLauncher", v)
    /** 0 = negra, 1 = igual al fondo, 2 = color de énfasis. */
    var navColor: Int get() = i("navColor", 1); set(v) = put("navColor", v)

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
    var lockClockPos: Int get() = i("lockClockPos", 1); set(v) = put("lockClockPos", v)
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
    var acRadius: Int get() = i("acRadius", 18); set(v) = put("acRadius", v)
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

    fun palette() = Palette(themeMode, accent)

    /** Al pasar de la versión de mosaicos al launcher minimalista, se adoptan los nuevos valores por defecto. */
    fun migrateToMinimal() {
        if (!sp.getBoolean("pocoMigrated", false)) {
            // 2.1: inicio en cuadrícula, íconos a color y cajón en cuadrícula por defecto.
            val e = sp.edit()
            for (k in listOf("favStyle", "iconStyle", "iconShape", "drawerStyle", "drawerColumns", "drawerKeyboard"))
                e.remove(k)
            e.putBoolean("pocoMigrated", true).apply()
            bump()
        }
        if (minimalMigrated) return
        val e = sp.edit()
        for (k in listOf("accent", "dark", "bgMode", "acRadius", "lockClockPos", "navColor", "iconStyle", "statusBg"))
            e.remove(k)
        e.putBoolean("minimalMigrated", true).apply()
        bump()
    }

    companion object {
        const val SAGE = 0xFF9CC5A8.toInt()

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
            "Salvia" to SAGE,
            "Crema" to 0xFFE9E4DA.toInt(),
            "Atardecer" to 0xFFE0A85B.toInt(),
            "Cielo" to 0xFF8FB3D9.toInt(),
            "Cancha" to 0xFF5FAF6E.toInt(),
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
