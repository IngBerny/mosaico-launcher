package com.ingberny.mosaico

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max

/** Pantalla "personalizar" construida en código, con el estilo de los ajustes del teléfono original. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var pal: Palette
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private var topInset = 0
    private var bottomInset = 0

    companion object {
        private const val REQ_IMAGE = 21
        private const val REQ_PERM = 22
        private const val REQ_EXPORT = 23
        private const val REQ_IMPORT = 24
        const val EXTRA_PAGE = "page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        pal = prefs.palette()
        setTheme(if (pal.dark) android.R.style.Theme_DeviceDefault_NoActionBar else android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.decorView // fuerza la creación del decor
            window.insetsController?.setSystemBarsAppearance(if (pal.dark) 0 else mask, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            @Suppress("DEPRECATION")
            if (!pal.dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
        scroll = ScrollView(this).apply { setBackgroundColor(pal.bg); isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                topInset = b.top; bottomInset = b.bottom
            } else {
                @Suppress("DEPRECATION") run { topInset = ins.systemWindowInsetTop; bottomInset = ins.systemWindowInsetBottom }
            }
            body.setPadding(dpi(20), topInset + dpi(12), dpi(20), bottomInset + dpi(32))
            ins
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val y = scroll.scrollY
        build()
        scroll.post { scroll.scrollTo(0, y) }
    }

    override fun onDestroy() { super.onDestroy(); io.shutdown() }

    private fun changed(rebuildSelf: Boolean = false) {
        prefs.bump()
        if (rebuildSelf) recreate() else { val y = scroll.scrollY; build(); scroll.post { scroll.scrollTo(0, y) } }
    }

    // ---------- piezas de interfaz ----------

    private fun add(v: View, top: Int = 0) {
        body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(top) })
    }

    private fun section(title: String) = add(label(title, 22f, pal.fg, Fonts.light), 30)
    private fun caption(t: String) = add(label(t, 14f, pal.sub, Fonts.regular).apply { setLineSpacing(0f, 1.15f) }, 6)

    private fun choice(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        options.forEachIndexed { i, o ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(9), 0, dpi(9))
                addView(Radio(i == selected), LinearLayout.LayoutParams(dpi(26), dpi(26)))
                addView(label(o, 17f, pal.fg, Fonts.regular).apply { setPadding(dpi(14), 0, 0, 0) })
                setOnClickListener { if (i != selected) onPick(i) }
            }
            add(row, if (i == 0) 6 else 0)
        }
    }

    private inner class Radio(val on: Boolean) : View(this@SettingsActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = width / 2f
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = pal.fg
            c.drawCircle(r, r, r - dp(1.5f), p)
            if (on) { p.style = Paint.Style.FILL; p.color = pal.accent; c.drawCircle(r, r, r * 0.45f, p) }
        }
    }

    private fun switchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            isChecked = checked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, pal.fg))
            trackTintList = ColorStateList(states, intArrayOf(pal.accent, pal.toggleOff))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 17f, pal.fg, Fonts.regular))
            if (desc != null) addView(label(desc, 13f, pal.sub, Fonts.regular).apply { setPadding(0, dpi(3), dpi(8), 0) })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(10), 0, dpi(10))
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
        add(row, 4)
    }

    private fun button(text: String, onClick: () -> Unit): TextView = label(text, 16f, pal.fg, Fonts.regular).apply {
        gravity = Gravity.CENTER
        setPadding(dpi(18), dpi(11), dpi(18), dpi(11))
        background = GradientDrawable().apply { setStroke(dpi(2), pal.fg); setColor(Color.TRANSPARENT) }
        setOnClickListener { onClick() }
    }

    private fun addButton(text: String, onClick: () -> Unit) {
        val wrap = LinearLayout(this).apply { addView(button(text, onClick)) }
        add(wrap, 10)
    }

    private fun link(text: String, onClick: () -> Unit) =
        add(label(text, 16f, pal.accent, Fonts.regular).apply { setPadding(0, dpi(8), 0, dpi(8)); setOnClickListener { onClick() } }, 2)

    private fun statusRow(title: String, ok: Boolean, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(10), 0, dpi(10))
            addView(label(title, 17f, pal.fg, Fonts.regular), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(if (ok) "Activado" else "Activar", 15f, if (ok) pal.sub else pal.accent, Fonts.regular))
            if (!ok) setOnClickListener { onClick() }
        }
        add(row)
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun go(i: Intent) = try { startActivity(i) } catch (e: Exception) {
        Toast.makeText(this, "No disponible en este teléfono", Toast.LENGTH_SHORT).show()
    }

    // ---------- navegación entre secciones (estilo Nova) ----------

    private class Page(val id: String, val title: String, val desc: String)

    private val pages = listOf(
        Page("tema", "Tema, colores y fuente", "Tinta, papel o negro puro, color de énfasis, fuente"),
        Page("inicio", "Pantalla de inicio", "Fondo, reloj, clima, música, cuadrícula de apps y dock"),
        Page("iconos", "Íconos", "Estilo, forma, color, tamaño, paquetes de íconos, apps ocultas"),
        Page("cajon", "Cajón de apps", "Columnas, tamaño de íconos y nombres, alto, categorías y orden"),
        Page("barra", "Barra de estado", "Colores, qué íconos mostrar, posición de la hora"),
        Page("centro", "Centro de actividades", "Botones, orden, columnas, brillo y notificaciones"),
        Page("bloqueo", "Pantalla de bloqueo", "Fondo, hora, fecha, evento y notificaciones"),
        Page("navegacion", "Barra de navegación", "Atrás, Inicio y buscar"),
        Page("gestos", "Gestos", "Deslizar en las 4 direcciones, doble toque, mantener presionado"),
        Page("sistema", "Tema para todo el teléfono", "Usar las barras y el bloqueo de Mosaico en todas las apps"),
        Page("clima", "Clima", "Unidades y ciudad"),
        Page("permisos", "Permisos", "Notificaciones, calendario, ubicación, fotos y ajustes"),
        Page("respaldo", "Copia de seguridad", "Exportar e importar tus ajustes, restablecer"),
    )

    private val page: String by lazy { intent.getStringExtra(EXTRA_PAGE) ?: "" }

    private fun openPage(id: String) = startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, id))

    private fun navRow(title: String, desc: String, onClick: () -> Unit) {
        add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpi(12), 0, dpi(12))
            addView(label(title, 20f, pal.fg, Fonts.regular))
            addView(label(desc, 13f, pal.sub, Fonts.regular).apply { setPadding(0, dpi(3), 0, 0) })
            setOnClickListener { onClick() }
        })
    }

    private fun slider(title: (Int) -> String, min: Int, max: Int, value: Int, onStop: (Int) -> Unit) {
        val t = label(title(value), 15f, pal.fg, Fonts.regular)
        add(t, 12)
        add(SeekBar(this).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(pal.accent)
            thumbTintList = ColorStateList.valueOf(pal.fg)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { t.text = title(v + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { onStop((s?.progress ?: 0) + min) }
            })
        }, 4)
    }

    private fun subtitle(t: String) = add(label(t, 17f, pal.fg, Fonts.regular), 14)

    /** Fila que muestra la acción elegida para un gesto; al tocarla abre la lista de acciones. */
    private fun actionRow(title: String, current: Int, onPick: (Int) -> Unit) {
        navRow(title, SysActions.NAMES.getOrElse(current) { SysActions.NAMES[SysActions.NONE] }) {
            val theme = if (pal.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            android.app.AlertDialog.Builder(this, theme)
                .setTitle(title)
                .setSingleChoiceItems(SysActions.NAMES.toTypedArray(), current) { d, which -> d.dismiss(); onPick(which) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun serviceHint() {
        if (NavService.isEnabled(this)) return
        add(label("Para usar esto fuera de Mosaico, activa el Tema para todo el teléfono.", 14f, pal.accent, Fonts.regular).apply {
            setPadding(0, dpi(8), 0, dpi(4))
            setOnClickListener { openPage("sistema") }
        }, 4)
    }

    // ---------- contenido ----------

    private fun build() {
        body.removeAllViews()
        body.setPadding(dpi(20), topInset + dpi(12), dpi(20), bottomInset + dpi(32))

        val current = pages.firstOrNull { it.id == page }
        add(label("MOSAICO", 14f, pal.fg, Fonts.medium))
        add(label((current?.title ?: "personalizar").lowercase(), if (current == null) 46f else 38f, pal.fg, Fonts.light).apply {
            setPadding(0, dpi(2), 0, 0)
        })

        when (page) {
            "tema" -> pageTheme()
            "inicio" -> pageStart()
            "iconos" -> pageIcons()
            "cajon" -> pageDrawer()
            "barra" -> pageStatus()
            "centro" -> pageCenter()
            "bloqueo" -> pageLock()
            "navegacion" -> pageNav()
            "gestos" -> pageGestures()
            "sistema" -> pageSystem()
            "clima" -> pageWeather()
            "permisos" -> pagePermissions()
            "respaldo" -> pageBackup()
            else -> pageMain()
        }
    }

    private fun pageMain() {
        if (DefaultHome.isDefault(this)) {
            add(label("✓  Mosaico es tu pantalla de inicio", 15f, pal.sub, Fonts.regular), 10)
        } else {
            add(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(pal.accent)
                setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
                addView(label("Mosaico no es tu launcher predeterminado", 16f, Color.WHITE, Fonts.medium))
                addView(label("Toca aquí para que el botón Inicio abra siempre Mosaico.", 14f, Color.WHITE).apply {
                    setPadding(0, dpi(4), 0, 0)
                })
                setOnClickListener { DefaultHome.request(this@SettingsActivity) }
            }, 14)
        }
        add(View(this), 10)
        pages.forEach { p -> navRow(p.title, p.desc) { openPage(p.id) } }

        section("Acerca de")
        caption("Mosaico 2.1 · Launcher para Android, hecho en Heredia, Costa Rica.\n" +
            "Proyecto independiente. Clima: Open-Meteo.")
    }

    private fun pageTheme() {
        section("Modo")
        choice(listOf("Tinta (oscuro)", "Papel (claro)", "Negro puro (AMOLED)"), prefs.themeMode.coerceIn(0, 2)) {
            prefs.themeMode = it; changed(true)
        }

        section("Color de énfasis")
        caption("Se usa en detalles: puntos de notificación, letras del cajón, botones encendidos y montañas.")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Prefs.ACCENTS.chunked(5).forEach { chunk ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (name, col) ->
                val sw = object : View(this) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(c: Canvas) {
                        p.style = Paint.Style.FILL; p.color = col
                        val rad = height / 2f
                        c.drawRoundRect(dp(4f), dp(4f), width - dp(4f), height - dp(4f), rad, rad, p)
                        if (col == prefs.accent) {
                            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = pal.fg
                            c.drawRoundRect(dp(1f), dp(1f), width - dp(1f), height - dp(1f), rad, rad, p)
                        }
                    }
                }
                sw.contentDescription = name
                sw.setOnClickListener { prefs.accent = col; changed(true) }
                r.addView(sw, LinearLayout.LayoutParams(0, dpi(44), 1f).apply { setMargins(dpi(3), dpi(3), dpi(3), dpi(3)) })
            }
            grid.addView(r)
        }
        add(grid, 8)
        caption("Actual: " + (Prefs.ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: "personalizado"))
        link("Color personalizado…") {
            Pickers.askColor(this, pal, "Color de énfasis", prefs.accent) { prefs.accent = it; changed(true) }
        }

        section("Fuente")
        subtitle("Reloj, saludo y fecha")
        choice(Fonts.NAMES, prefs.font.coerceIn(0, Fonts.NAMES.size - 1)) { prefs.font = it; changed() }
        subtitle("Nombres de las apps")
        choice(listOf("Igual que el reloj") + Fonts.NAMES, (prefs.labelFont + 1).coerceIn(0, Fonts.NAMES.size)) {
            prefs.labelFont = it - 1; changed()
        }
    }

    private fun pageStart() {
        section("Fondo")
        choice(listOf("Montañas (cambian con tu color de énfasis)", "Fondo de pantalla de Android", "Foto propia", "Color sólido del tema"),
            when (prefs.homeBg) { 3 -> 0; 0 -> 1; 1 -> 2; else -> 3 }) { i ->
            when (i) {
                0 -> { prefs.homeBg = 3; changed() }
                1 -> { prefs.homeBg = 0; changed() }
                2 -> if (!File(filesDir, "backdrop.jpg").exists()) pickImage() else { prefs.homeBg = 1; changed() }
                else -> { prefs.homeBg = 2; changed() }
            }
        }
        if (prefs.homeBg == 1) link("Elegir otra foto") { pickImage() }
        if (prefs.homeBg == 0) link("Cambiar el fondo de pantalla de Android") {
            go(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
        }
        slider({ "Oscurecer el fondo: $it%" }, 0, 80, prefs.homeDim) { prefs.homeDim = it; changed() }

        section("Saludo, hora y fecha")
        switchRow("Saludo", "\"Buenos días\", \"Buenas tardes\" o \"Buenas noches\" con tu nombre", prefs.showGreeting) {
            prefs.showGreeting = it; changed()
        }
        if (prefs.showGreeting) {
            val name = EditText(this).apply {
                setText(prefs.greetName); hint = "Tu nombre (opcional)"; setHintTextColor(pal.sub); setTextColor(pal.fg)
                isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                backgroundTintList = ColorStateList.valueOf(pal.sub)
            }
            add(name, 2)
            addButton("Guardar nombre") { prefs.greetName = name.text.toString().trim(); prefs.bump()
                Toast.makeText(this, "Listo", Toast.LENGTH_SHORT).show() }
        }
        subtitle("Reloj")
        choice(listOf("Grande y fino", "Mediano", "Apilado (hora sobre minutos)", "Sin reloj"), prefs.clockStyle.coerceIn(0, 3)) {
            prefs.clockStyle = it; changed()
        }
        switchRow("Fecha", "Tócala para abrir el calendario", prefs.showDate) { prefs.showDate = it; prefs.bump() }
        subtitle("Alineación")
        choice(listOf("Izquierda", "Centro", "Derecha"), prefs.homeAlign.coerceIn(0, 2)) { prefs.homeAlign = it; changed() }

        section("Línea de información")
        switchRow("Clima", "Temperatura y ciudad. Configúralo en Clima.", prefs.showWeather) { prefs.showWeather = it; prefs.bump() }
        switchRow("Batería", null, prefs.showBattery) { prefs.showBattery = it; prefs.bump() }
        switchRow("Próximo evento", "Necesita el permiso de calendario", prefs.showEvent) {
            prefs.showEvent = it; prefs.bump()
            if (it && !has(Manifest.permission.READ_CALENDAR)) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_PERM)
        }
        switchRow("Lo que está sonando", "Canción, artista y controles de Spotify, YouTube Music, etc. Necesita el acceso a notificaciones.",
            prefs.showMedia) { prefs.showMedia = it; prefs.bump() }

        section("Apps en Inicio")
        caption("Mantén presionada una app en el cajón → Agregar a Inicio. Mantén presionada una app de Inicio para moverla, cambiarle el nombre o quitarla.")
        subtitle("Estilo")
        choice(listOf("Cuadrícula con nombres (como POCO)", "Lista de texto", "Lista con íconos", "Solo íconos"),
            when (prefs.favStyle) { 3 -> 0; 0 -> 1; 1 -> 2; else -> 3 }) { i ->
            prefs.favStyle = when (i) { 0 -> 3; 1 -> 0; 2 -> 1; else -> 2 }; changed()
        }
        if (prefs.favStyle == 3) {
            subtitle("Columnas")
            val cols = listOf(3, 4, 5, 6, 7)
            choice(cols.map { "$it" }, cols.indexOf(prefs.homeCols).let { if (it < 0) 1 else it }) { prefs.homeCols = cols[it]; changed() }
            slider({ "Tamaño de los íconos: $it dp" }, 36, 84, prefs.homeIconSize) { prefs.homeIconSize = it; changed() }
            switchRow("Mostrar nombres", null, prefs.homeLabels) { prefs.homeLabels = it; changed() }
            if (prefs.homeLabels) slider({ "Tamaño de los nombres: $it" }, 9, 18, prefs.homeLabelSize) { prefs.homeLabelSize = it; changed() }
            switchRow("Sombra en los nombres", "Para leerlos sobre cualquier fondo", prefs.labelShadow) { prefs.labelShadow = it; prefs.bump() }
        } else {
            slider({ "Tamaño del texto: $it" }, 14, 40, prefs.favSize) { prefs.favSize = it; changed() }
            subtitle("Alineación")
            choice(listOf("Izquierda", "Centro", "Derecha"), prefs.favAlign.coerceIn(0, 2)) { prefs.favAlign = it; changed() }
        }
        subtitle("Posición")
        choice(listOf("Debajo del reloj", "Al centro", "Abajo, cerca del pulgar"), prefs.favPos.coerceIn(0, 2)) { prefs.favPos = it; changed() }
        slider({ "Espacio entre filas: $it" }, 0, 40, prefs.favSpacing) { prefs.favSpacing = it; changed() }
        link("Quitar todas las apps de Inicio") { prefs.favorites = emptyList(); changed() }

        section("Dock")
        switchRow("Mostrar el dock", "Hasta 6 apps fijas abajo", prefs.dockOn) { prefs.dockOn = it; changed() }
        if (prefs.dockOn) {
            slider({ "Tamaño de los íconos del dock: $it dp" }, 36, 76, prefs.dockIconSize) { prefs.dockIconSize = it; changed() }
            switchRow("Nombres en el dock", null, prefs.dockLabels) { prefs.dockLabels = it; prefs.bump() }
            subtitle("Fondo del dock")
            choice(listOf("Sin fondo", "Tarjeta translúcida"), prefs.dockBg.coerceIn(0, 1)) { prefs.dockBg = it; changed() }
            caption("Para agregar o quitar: mantén presionada la app → Agregar al dock.")
        }
    }

    private fun pageIcons() {
        val packs = IconPacks.installed(this)
        section("Estilo")
        choice(listOf(
            "Colores originales de cada app",
            "Monocromáticos cuando la app lo permite (Android 13+)",
            "Silueta de un solo color para todas (minimalista)",
        ), prefs.iconStyle) { prefs.iconStyle = it; changed() }

        section("Forma de fondo")
        choice(listOf("Ninguna", "Círculo", "Cuadrado suave", "Cuadrado"), prefs.iconShape.coerceIn(0, 3)) { prefs.iconShape = it; changed() }
        if (prefs.iconShape != 0) {
            subtitle("Color de la forma")
            choice(listOf("Del tema", "Color de énfasis", "Personalizado…"), prefs.shapeColorMode.coerceIn(0, 2)) { i ->
                if (i == 2) Pickers.askColor(this, pal, "Color de la forma", prefs.shapeColor) { prefs.shapeColor = it; prefs.shapeColorMode = 2; changed() }
                else { prefs.shapeColorMode = i; changed() }
            }
        }
        if (prefs.iconStyle != 0) {
            subtitle("Color de los íconos")
            choice(listOf("Del tema", "Color de énfasis", "Personalizado…"), prefs.glyphColorMode.coerceIn(0, 2)) { i ->
                if (i == 2) Pickers.askColor(this, pal, "Color de los íconos", prefs.glyphColor) { prefs.glyphColor = it; prefs.glyphColorMode = 2; changed() }
                else { prefs.glyphColorMode = i; changed() }
            }
        }
        slider({ "Tamaño de los íconos: $it%" }, 60, 140, prefs.iconScale) { prefs.iconScale = it; changed() }

        section("Paquete de íconos")
        if (packs.isEmpty()) {
            caption("No tienes paquetes de íconos instalados. Busca \"icon pack\" en Google Play (por ejemplo, alguno minimalista o de línea) y vuelve aquí para elegirlo.")
        } else {
            val names = listOf("Íconos del sistema") + packs.map { it.label }
            val sel = packs.indexOfFirst { it.pkg == prefs.iconPack } + 1
            choice(names, sel) { i -> prefs.iconPack = if (i == 0) "" else packs[i - 1].pkg; changed() }
        }

        val hidden = prefs.hiddenApps
        section("Apps ocultas")
        if (hidden.isEmpty()) caption("No hay apps ocultas. Para ocultar una, mantenla presionada en el cajón.")
        else {
            val repo = AppRepo(this, prefs).also { it.load() }
            for (key in hidden.sorted()) {
                val name = repo.allApps.firstOrNull { it.key == key }?.label ?: key.substringBefore("/")
                add(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dpi(6), 0, dpi(6))
                    addView(label(name, 17f, pal.fg, Fonts.regular), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button("Mostrar") { prefs.hiddenApps = prefs.hiddenApps - key; changed() })
                })
            }
        }
    }

    private fun pageDrawer() {
        section("Vista")
        choice(listOf("Cuadrícula", "Lista"), if (prefs.drawerStyle == 1) 0 else 1) { prefs.drawerStyle = if (it == 0) 1 else 0; changed() }
        if (prefs.drawerStyle == 1) {
            subtitle("Columnas")
            val cols = listOf(3, 4, 5, 6, 7)
            choice(cols.map { "$it" }, cols.indexOf(prefs.drawerColumns).let { if (it < 0) 2 else it }) { prefs.drawerColumns = cols[it]; changed() }
            switchRow("Mostrar nombres", null, prefs.drawerLabels) { prefs.drawerLabels = it; changed() }
        } else {
            switchRow("Letras de sección", "A, B, C… entre las apps", prefs.drawerLetters) { prefs.drawerLetters = it; prefs.bump() }
            switchRow("Íconos en la lista", "Apágalo para una lista solo de nombres", prefs.drawerIcons) { prefs.drawerIcons = it; prefs.bump() }
        }

        section("Tamaño")
        slider({ "Tamaño de los íconos: $it dp" }, 32, 76, prefs.drawerIconSize) { prefs.drawerIconSize = it; changed() }
        slider({ "Tamaño de los nombres: $it" }, 9, 18, prefs.drawerLabelSize) { prefs.drawerLabelSize = it; changed() }
        slider({ "Espacio entre filas: $it" }, 0, 40, prefs.drawerRowGap) { prefs.drawerRowGap = it; changed() }
        slider({ "Alto del cajón: ${if (it >= 100) "pantalla completa" else "$it% de la pantalla"}" }, 40, 100, prefs.drawerHeight) {
            prefs.drawerHeight = it; changed()
        }
        slider({ "Opacidad del fondo: $it%" }, 40, 100, prefs.drawerOpacity) { prefs.drawerOpacity = it; changed() }

        section("Organización")
        switchRow("Categorías", "Pestañas Social, Juegos, Música, Video, Fotos… arriba del cajón", prefs.drawerCategories) {
            prefs.drawerCategories = it; prefs.bump()
        }
        subtitle("Orden")
        choice(listOf("Alfabético", "Instaladas recientemente primero", "Por color del ícono"), prefs.drawerSort.coerceIn(0, 2)) {
            prefs.drawerSort = it; changed()
        }

        section("Búsqueda")
        switchRow("Abrir el teclado al abrir el cajón", null, prefs.drawerKeyboard) { prefs.drawerKeyboard = it; prefs.bump() }
        switchRow("Abrir la app sola", "Cuando la búsqueda deja un único resultado", prefs.drawerAutoLaunch) { prefs.drawerAutoLaunch = it; prefs.bump() }
        caption("Si no hay apps con ese nombre, puedes buscarlo en la web con un toque.")
    }

    private fun pageGestures() {
        caption("Elige qué hace cada gesto. \"Bloquear pantalla\" y \"Apps recientes\" necesitan el Tema para todo el teléfono.")
        section("En Inicio")
        actionRow("Deslizar hacia arriba", prefs.gestureUp) { prefs.gestureUp = it; changed() }
        actionRow("Deslizar hacia abajo", prefs.swipeDown) { prefs.swipeDown = it; changed() }
        actionRow("Deslizar a la izquierda", prefs.gestureLeft) { prefs.gestureLeft = it; changed() }
        actionRow("Deslizar a la derecha", prefs.gestureRight) { prefs.gestureRight = it; changed() }
        actionRow("Doble toque", prefs.gestureDoubleTap) { prefs.gestureDoubleTap = it; changed() }
        actionRow("Mantener presionado", prefs.gestureLong) { prefs.gestureLong = it; changed() }
        section("En la barra de estado")
        actionRow("Tocar", prefs.statusTap) { prefs.statusTap = it; changed() }
        actionRow("Deslizar hacia abajo", prefs.statusSwipe) { prefs.statusSwipe = it; changed() }
        serviceHint()
    }

    private fun pageStatus() {
        caption("La barra de estado de Mosaico reemplaza a la de Android dentro de Mosaico y, con el Tema para todo el teléfono, en todas las apps.")
        switchRow("Usarla en Inicio", "Oculta la barra de Android mientras estás en Mosaico.",
            prefs.statusInLauncher) { prefs.statusInLauncher = it; prefs.bump() }
        if (NavService.isEnabled(this)) switchRow("Usarla en todo el teléfono", "Tapa la barra de Android en todas las apps.",
            prefs.statusSystem) { prefs.statusSystem = it; prefs.bump() }
        else serviceHint()

        section("Fondo")
        val bgNames = listOf("Automático", "Transparente", "Negro", "Igual al fondo del tema", "Color de énfasis", "Personalizado…")
        choice(bgNames, (prefs.statusBg + 1).coerceIn(0, 5)) { i ->
            if (i == 5) Pickers.askColor(this, pal, "Fondo de la barra", prefs.statusBgCustom) {
                prefs.statusBgCustom = it; prefs.statusBg = 4; changed()
            } else { prefs.statusBg = i - 1; changed() }
        }
        caption("En todo el teléfono, \"Transparente\" deja ver la barra de Android debajo; úsalo solo dentro de Mosaico.")

        section("Color de íconos y texto")
        choice(listOf("Automático", "Blanco", "Negro", "Color de énfasis", "Personalizado…"), prefs.statusFg.coerceIn(0, 4)) { i ->
            if (i == 4) Pickers.askColor(this, pal, "Íconos y texto de la barra", prefs.statusFgCustom) {
                prefs.statusFgCustom = it; prefs.statusFg = 4; changed()
            } else { prefs.statusFg = i; changed() }
        }

        section("Qué mostrar")
        switchRow("Señal del celular", null, prefs.sbSignal) { prefs.sbSignal = it; prefs.bump() }
        switchRow("Wi-Fi", null, prefs.sbWifi) { prefs.sbWifi = it; prefs.bump() }
        switchRow("Modo avión y horas de silencio", null, prefs.sbModes) { prefs.sbModes = it; prefs.bump() }
        switchRow("Contador de notificaciones", null, prefs.sbNotifCount) { prefs.sbNotifCount = it; prefs.bump() }
        switchRow("Ícono de batería", null, prefs.sbBatteryIcon) { prefs.sbBatteryIcon = it; prefs.bump() }
        switchRow("Porcentaje de batería", null, prefs.sbBatteryPct) { prefs.sbBatteryPct = it; prefs.bump() }
        switchRow("Hora", null, prefs.sbClock) { prefs.sbClock = it; changed() }
        if (prefs.sbClock) {
            subtitle("Posición de la hora")
            choice(listOf("Derecha (como el original)", "Centro", "Izquierda"), prefs.sbClockPos) { prefs.sbClockPos = it; changed() }
        }
        subtitle("Tamaño del texto")
        val scales = listOf(85, 100, 120)
        choice(listOf("Pequeño", "Normal", "Grande"), scales.indexOf(prefs.sbTextScale).let { if (it < 0) 1 else it }) {
            prefs.sbTextScale = scales[it]; changed()
        }

        section("Al usar la barra")
        actionRow("Al tocarla", prefs.statusTap) { prefs.statusTap = it; changed() }
        actionRow("Al deslizarla hacia abajo", prefs.statusSwipe) { prefs.statusSwipe = it; changed() }
    }

    private fun pageCenter() {
        caption("El Centro de actividades de Mosaico es propio: no usa el panel de Android. Se abre al deslizar hacia abajo en Inicio o desde la barra de estado (cámbialo en Gestos).")
        serviceHint()

        section("Acciones rápidas")
        caption("Activa los botones que quieras y ordénalos con las flechas.")
        val enabled = prefs.acToggles
        val names = Prefs.ALL_TOGGLES.toMap()
        val ordered = enabled.filter { it in names } + Prefs.ALL_TOGGLES.map { it.first }.filter { it !in enabled }
        ordered.forEach { id ->
            val on = id in enabled
            val idx = enabled.indexOf(id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(4), 0, dpi(4))
            }
            row.addView(label(names[id] ?: id, 17f, if (on) pal.fg else pal.sub, Fonts.regular), LinearLayout.LayoutParams(0, -2, 1f))
            fun arrow(text: String, enabledArrow: Boolean, onClick: () -> Unit) = label(text, 20f,
                if (enabledArrow) pal.accent else withAlpha(pal.sub, 60), Fonts.regular).apply {
                setPadding(dpi(12), dpi(6), dpi(12), dpi(6))
                if (enabledArrow) setOnClickListener { onClick() }
            }
            if (on) {
                row.addView(arrow("↑", idx > 0) {
                    val l = enabled.toMutableList(); java.util.Collections.swap(l, idx, idx - 1); prefs.acToggles = l; changed()
                })
                row.addView(arrow("↓", idx < enabled.size - 1) {
                    val l = enabled.toMutableList(); java.util.Collections.swap(l, idx, idx + 1); prefs.acToggles = l; changed()
                })
            }
            row.addView(Switch(this).apply {
                isChecked = on
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, pal.fg))
                trackTintList = ColorStateList(states, intArrayOf(pal.accent, pal.toggleOff))
                setOnCheckedChangeListener { _, v ->
                    prefs.acToggles = if (v) enabled + id else enabled - id
                    changed()
                }
            })
            add(row)
        }
        link("Restablecer botones") { prefs.acToggles = Prefs.DEFAULT_TOGGLES; changed() }

        section("Distribución")
        subtitle("Columnas")
        val cols = listOf(3, 4, 5, 6)
        choice(cols.map { "$it" }, cols.indexOf(prefs.acColumns).let { if (it < 0) 1 else it }) { prefs.acColumns = cols[it]; changed() }
        subtitle("Filas visibles con el panel contraído")
        choice(listOf("1", "2", "3"), (prefs.acRows - 1).coerceIn(0, 2)) { prefs.acRows = it + 1; changed() }
        switchRow("Abrir siempre expandido", "Muestra todos los botones al abrir el panel", prefs.acStartExpanded) {
            prefs.acStartExpanded = it; prefs.bump()
        }
        switchRow("Mostrar nombres de los botones", "Apágalo para ver solo los íconos, más compactos", prefs.acLabels) {
            prefs.acLabels = it; prefs.bump()
        }
        slider({ "Esquinas de los botones: ${if (it == 0) "cuadradas" else "$it dp"}" }, 0, 28, prefs.acRadius) {
            prefs.acRadius = it; changed()
        }

        section("Colores")
        subtitle("Fondo del panel")
        choice(listOf("Igual al fondo del tema", "Negro", "Personalizado…"), prefs.acBg.coerceIn(0, 2)) { i ->
            if (i == 2) Pickers.askColor(this, pal, "Fondo del panel", prefs.acBgCustom) { prefs.acBgCustom = it; prefs.acBg = 2; changed() }
            else { prefs.acBg = i; changed() }
        }
        slider({ "Opacidad del panel: $it%" }, 30, 100, prefs.acOpacity) { prefs.acOpacity = it; changed() }
        subtitle("Botones encendidos")
        choice(listOf("Color de énfasis", "Personalizado…"), prefs.acOnMode.coerceIn(0, 1)) { i ->
            if (i == 1) Pickers.askColor(this, pal, "Botones encendidos", prefs.acOnCustom) { prefs.acOnCustom = it; prefs.acOnMode = 1; changed() }
            else { prefs.acOnMode = 0; changed() }
        }

        section("Contenido")
        switchRow("Barra de brillo", "Necesita el permiso de modificar ajustes", prefs.acBrightness) { prefs.acBrightness = it; prefs.bump() }
        switchRow("Hora, fecha y batería arriba", null, prefs.acHeader) { prefs.acHeader = it; prefs.bump() }
        switchRow("Enlace \"Panel de Android\"", "Para abrir el panel original cuando lo necesites", prefs.acAndroidLink) {
            prefs.acAndroidLink = it; prefs.bump()
        }
        switchRow("Agrupar notificaciones por app", "Apágalo para verlas en orden de llegada.",
            prefs.acGroupNotifs) { prefs.acGroupNotifs = it; prefs.bump() }
        subtitle("Tamaño del texto de las notificaciones")
        val scales = listOf(85, 100, 120)
        choice(listOf("Pequeño", "Normal", "Grande"), scales.indexOf(prefs.acTextScale).let { if (it < 0) 1 else it }) {
            prefs.acTextScale = scales[it]; changed()
        }
    }

    private fun pageLock() {
        caption("Aparece al encender la pantalla. Al deslizarla hacia arriba aparece el bloqueo real de Android (PIN, patrón o huella), así que la seguridad no cambia.")
        if (NavService.isEnabled(this)) {
            switchRow("Usar la pantalla de bloqueo de Mosaico", null, prefs.lockEnabled) { prefs.lockEnabled = it; changed() }
        } else serviceHint()

        section("Fondo")
        choice(listOf("La misma foto de Inicio", "Otra foto", "Color de énfasis", "Negro"), prefs.lockBg) { m ->
            if (m == 1) pickImage("lock")
            else if (m == 0 && !File(filesDir, "backdrop.jpg").exists()) {
                Toast.makeText(this, "Primero elige una foto en Pantalla de inicio → Fondo", Toast.LENGTH_LONG).show()
            } else { prefs.lockBg = m; changed() }
        }
        if (prefs.lockBg == 1) link("Elegir otra foto") { pickImage("lock") }
        slider({ "Oscurecer el fondo: $it%" }, 0, 80, prefs.lockDim) { prefs.lockDim = it; changed() }

        section("Hora")
        subtitle("Tamaño")
        choice(listOf("Pequeña", "Normal", "Grande"), prefs.lockClockSize.coerceIn(0, 2)) { prefs.lockClockSize = it; changed() }
        subtitle("Posición")
        choice(listOf("Abajo a la izquierda (como el original)", "Centrada arriba"), prefs.lockClockPos.coerceIn(0, 1)) {
            prefs.lockClockPos = it; changed()
        }

        section("Qué mostrar")
        switchRow("Fecha", null, prefs.lockShowDate) { prefs.lockShowDate = it; prefs.bump() }
        switchRow("Próximo evento del calendario", "Necesita el permiso de calendario", prefs.lockShowEvent) { prefs.lockShowEvent = it; prefs.bump() }
        switchRow("Notificaciones", "Íconos de las apps con su número de notificaciones", prefs.lockNotifs) { prefs.lockNotifs = it; prefs.bump() }
    }

    private fun pageNav() {
        switchRow("Usarla en Inicio", "Oculta la barra de abajo de Android mientras estás en Mosaico (← atrás, Inicio, buscar).",
            prefs.navInLauncher) { prefs.navInLauncher = it; prefs.bump() }
        if (NavService.isEnabled(this)) switchRow("Usarla en todo el teléfono",
            "Tapa la barra de Android en todas las apps. Mantén presionado ← para ver las apps recientes.",
            prefs.navSystem) { prefs.navSystem = it; prefs.bump() }
        else serviceHint()
        if (NavService.navigationMode(this) == 2) {
            add(label("Tu teléfono usa navegación por gestos. Para que la barra de Mosaico funcione en todo el teléfono, " +
                "cambia a \"Navegación con 3 botones\" en Ajustes → Sistema → Gestos (o Pantalla → Barra de navegación).",
                14f, pal.accent, Fonts.regular), 8)
        }
        section("Color")
        choice(listOf("Negra (como el original)", "Igual al fondo", "Color de énfasis"), prefs.navColor) { prefs.navColor = it; changed() }
        caption("La barra de estado usa este color cuando su fondo está en \"Automático\".")
    }

    private fun pageSystem() {
        val serviceOn = NavService.isEnabled(this)
        caption(if (serviceOn) "El servicio de Mosaico está activo. Elige qué partes quieres usar en todas las apps."
            else "Con un permiso de accesibilidad, Mosaico puede mostrar su barra de estado, su Centro de actividades, su barra de navegación y su pantalla de bloqueo en todo el teléfono, en lugar de los de Android. Solo lo usa para dibujar esas piezas y ejecutar Atrás, Inicio, Recientes, Bloquear y Captura; no lee el contenido de tu pantalla.")
        if (!serviceOn) {
            if (Build.VERSION.SDK_INT >= 33) {
                caption("Paso 1 (Android 13 o más reciente): abre la información de Mosaico, toca ⋮ arriba a la derecha y elige \"Permitir ajustes restringidos\".")
                addButton("1. Abrir información de Mosaico") {
                    go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            caption("${if (Build.VERSION.SDK_INT >= 33) "Paso 2" else "Paso 1"}: en Accesibilidad busca \"Tema de Mosaico\" (a veces está en \"Apps instaladas\" o \"Servicios descargados\") y actívala.")
            addButton("${if (Build.VERSION.SDK_INT >= 33) "2" else "1"}. Abrir Accesibilidad") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            return
        }
        switchRow("Barra de estado", "Tapa la barra de arriba de Android. Tócala o deslízala para abrir el Centro de actividades.",
            prefs.statusSystem) { prefs.statusSystem = it; prefs.bump() }
        switchRow("Barra de navegación", "Tapa la barra de abajo de Android.",
            prefs.navSystem) { prefs.navSystem = it; prefs.bump() }
        switchRow("Pantalla de bloqueo", "Aparece al encender la pantalla.", prefs.lockEnabled) { prefs.lockEnabled = it; changed() }
        caption("Personaliza cada parte en su sección: Barra de estado, Centro de actividades, Pantalla de bloqueo y Barra de navegación.")
        addButton("Desactivar en Accesibilidad") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun pageWeather() {
        section("Unidades")
        choice(listOf("Celsius (°C)", "Fahrenheit (°F)"), if (prefs.celsius) 0 else 1) {
            prefs.celsius = it == 0; prefs.weatherTime = 0; prefs.weatherJson = null; changed()
        }
        section("Ciudad")
        caption("Déjalo vacío para usar tu ubicación.")
        val city = EditText(this).apply {
            setText(prefs.city)
            hint = "Ej.: Heredia"
            setHintTextColor(pal.sub)
            setTextColor(pal.fg)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            backgroundTintList = ColorStateList.valueOf(pal.sub)
        }
        add(city, 2)
        addButton("Guardar ciudad") {
            prefs.city = city.text.toString().trim(); prefs.weatherTime = 0; prefs.weatherJson = null; prefs.bump()
            Toast.makeText(this, "Guardado. El clima se actualizará al volver a Inicio.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pagePermissions() {
        caption("Mosaico solo pide lo que necesita cada función.")
        statusRow("Notificaciones (puntos, música y Centro de actividades)", NotifService.hasAccess(this)) {
            go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        statusRow("Tema para todo el teléfono (accesibilidad)", NavService.isEnabled(this)) { openPage("sistema") }
        statusRow("Calendario", has(Manifest.permission.READ_CALENDAR)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_PERM)
        }
        statusRow("Ubicación (clima)", has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_PERM)
        }
        val photoPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        statusRow("Fotos", has(photoPerm)) { requestPermissions(arrayOf(photoPerm), REQ_PERM) }
        statusRow("Modificar ajustes (rotación y brillo)", Settings.System.canWrite(this)) {
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        statusRow("No molestar (horas de silencio)", getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun pageBackup() {
        section("Tus ajustes")
        caption("Guarda en un archivo todos tus ajustes y el diseño de Inicio, para pasarlos a otro teléfono o recuperarlos después de reinstalar. Las fotos de fondo no se incluyen.")
        addButton("Exportar ajustes") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "mosaico-ajustes.json")
            @Suppress("DEPRECATION")
            try { startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) { go(i) }
        }
        addButton("Importar ajustes") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            @Suppress("DEPRECATION")
            try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { go(i) }
        }

        section("Launcher")
        addButton("Elegir launcher predeterminado") { DefaultHome.request(this) }
        addButton("Restablecer pantalla de Inicio") {
            prefs.tilesJson = null; prefs.bump()
            Toast.makeText(this, "Inicio restablecido", Toast.LENGTH_SHORT).show()
        }
        addButton("Restablecer todos los ajustes") {
            val theme = if (pal.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            android.app.AlertDialog.Builder(this, theme)
                .setTitle("¿Restablecer todo?")
                .setMessage("Se borran tus colores, gestos, barras y el diseño de Inicio. Te conviene exportar antes.")
                .setPositiveButton("Restablecer") { _, _ ->
                    val keep = prefs.askedDefault
                    prefs.sp.edit().clear().apply()
                    prefs.askedDefault = keep
                    prefs.bump()
                    recreate()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ---------- copia de seguridad ----------

    private fun exportPrefs(uri: Uri) {
        val ok = try {
            val all = org.json.JSONObject()
            for ((k, v) in prefs.sp.all) {
                val o = org.json.JSONObject()
                when (v) {
                    is Boolean -> o.put("t", "b").put("v", v)
                    is Int -> o.put("t", "i").put("v", v)
                    is Long -> o.put("t", "l").put("v", v)
                    is Float -> o.put("t", "f").put("v", v.toDouble())
                    is String -> o.put("t", "s").put("v", v)
                    is Set<*> -> o.put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                all.put(k, o)
            }
            val root = org.json.JSONObject().put("mosaico", 1).put("prefs", all)
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) } != null
        } catch (e: Exception) { false }
        Toast.makeText(this, if (ok) "Ajustes exportados" else "No se pudo guardar el archivo", Toast.LENGTH_SHORT).show()
    }

    private fun importPrefs(uri: Uri) {
        val ok = try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw Exception()
            val root = org.json.JSONObject(text)
            if (root.optInt("mosaico", 0) < 1) throw Exception("no es un archivo de Mosaico")
            val all = root.getJSONObject("prefs")
            val e = prefs.sp.edit().clear()
            for (k in all.keys()) {
                val o = all.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> e.putString(k, o.getString("v"))
                    "set" -> {
                        val a = o.getJSONArray("v")
                        e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            e.commit()
        } catch (e: Exception) { false }
        if (ok) {
            prefs.bump()
            Toast.makeText(this, "Ajustes importados", Toast.LENGTH_SHORT).show()
            recreate()
        } else Toast.makeText(this, "Ese archivo no tiene ajustes de Mosaico", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
    }

    // ---------- foto propia ----------

    /** "backdrop" = foto de Inicio, "lock" = foto de la pantalla de bloqueo. */
    private var imageTarget = "backdrop"

    private fun pickImage(target: String = "backdrop") {
        imageTarget = target
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        @Suppress("DEPRECATION")
        try { startActivityForResult(i, REQ_IMAGE) } catch (e: Exception) { go(Intent(Intent.ACTION_GET_CONTENT).setType("image/*")) }
    }

    @Deprecated("API clásica de Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DefaultHome.REQ_ROLE) { DefaultHome.onResult(this, requestCode); return }
        if (requestCode == REQ_EXPORT || requestCode == REQ_IMPORT) {
            val u = data?.data
            if (resultCode == RESULT_OK && u != null) { if (requestCode == REQ_EXPORT) exportPrefs(u) else importPrefs(u) }
            return
        }
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val target = imageTarget
        val (sw, sh) = screenSize()
        Toast.makeText(this, "Preparando la imagen…", Toast.LENGTH_SHORT).show()
        io.execute {
            val ok = try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= sw && opts.outHeight / (sample * 2) >= sh) sample *= 2
                val src = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw Exception("imagen inválida")
                // Recorte centrado con la proporción de la pantalla.
                val scale = max(sw.toFloat() / src.width, sh.toFloat() / src.height)
                val cw = (sw / scale).toInt().coerceAtMost(src.width)
                val ch = (sh / scale).toInt().coerceAtMost(src.height)
                val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
                val out = Bitmap.createScaledBitmap(crop, sw, sh, true)
                FileOutputStream(File(filesDir, "$target.jpg")).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                true
            } catch (e: Throwable) { false }
            runOnUiThread {
                if (ok) { if (target == "lock") prefs.lockBg = 1 else prefs.homeBg = 1; changed() }
                else Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }
}
