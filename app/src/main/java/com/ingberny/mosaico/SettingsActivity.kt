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

    // ---------- contenido ----------

    private fun build() {
        body.removeAllViews()
        body.setPadding(dpi(20), topInset + dpi(12), dpi(20), bottomInset + dpi(32))

        add(label("MOSAICO", 14f, pal.fg, Fonts.medium))
        add(label("personalizar", 46f, pal.fg, Fonts.light).apply { setPadding(0, dpi(2), 0, 0) })

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

        // Tema
        section("Fondo")
        choice(listOf("Oscuro", "Claro"), if (prefs.dark) 0 else 1) { prefs.dark = it == 0; changed(true) }

        section("Color de énfasis")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Prefs.ACCENTS.chunked(5).forEach { chunk ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (name, col) ->
                val sw = object : View(this) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(c: Canvas) {
                        p.style = Paint.Style.FILL; p.color = col
                        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                        if (col == prefs.accent) {
                            p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f); p.color = pal.fg
                            c.drawRect(dp(1.5f), dp(1.5f), width - dp(1.5f), height - dp(1.5f), p)
                            Glyphs.draw(c, G.CHECK, width / 2f, height / 2f, width * 0.35f, Color.WHITE, 1.5f)
                        }
                    }
                }
                sw.contentDescription = name
                sw.setOnClickListener { prefs.accent = col; changed(true) }
                r.addView(sw, LinearLayout.LayoutParams(0, dpi(58), 1f).apply { setMargins(dpi(3), dpi(3), dpi(3), dpi(3)) })
            }
            grid.addView(r)
        }
        add(grid, 8)
        caption("Actual: " + (Prefs.ACCENTS.firstOrNull { it.second == prefs.accent }?.first ?: "personalizado"))
        link("Color personalizado…") {
            Pickers.askColor(this, pal, "Color de énfasis", prefs.accent) { prefs.accent = it; changed(true) }
        }

        section("Color de los mosaicos")
        choice(listOf("Color de énfasis", "Color de cada app"), if (prefs.appColors) 1 else 0) { prefs.appColors = it == 1; changed() }

        // Fondo de Inicio
        section("Imagen de fondo de Inicio")
        choice(listOf("Fondo de pantalla de Android (mosaicos translúcidos)", "Foto propia dentro de los mosaicos",
            "Color sólido (estilo clásico)"), prefs.bgMode) { m ->
            if (m == 1 && !File(filesDir, "backdrop.jpg").exists()) pickImage()
            else { prefs.bgMode = m; changed() }
        }
        if (prefs.bgMode == 1) link("Elegir otra foto") { pickImage() }
        if (prefs.bgMode == 0) {
            link("Cambiar el fondo de pantalla de Android") {
                go(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
            }
            add(label("Opacidad de los mosaicos: ${prefs.tileOpacity}%", 15f, pal.fg, Fonts.regular), 10)
            add(SeekBar(this).apply {
                max = 100
                progress = prefs.tileOpacity
                progressTintList = ColorStateList.valueOf(pal.accent)
                thumbTintList = ColorStateList.valueOf(pal.fg)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {
                        prefs.tileOpacity = max(20, s?.progress ?: 80); changed()
                    }
                })
            }, 4)
        }

        section("Mosaicos")
        caption("Para cambiar el color o el nombre de un solo mosaico: mantenlo presionado y toca el botón de paleta (esquina superior izquierda).")
        switchRow("Mostrar más mosaicos", "Seis columnas en lugar de cuatro", prefs.moreTiles) { prefs.moreTiles = it; prefs.bump() }
        switchRow("Ocultar nombres", "Muestra solo los íconos en todos los mosaicos", prefs.hideLabels) { prefs.hideLabels = it; prefs.bump() }
        add(label("Espacio entre mosaicos", 17f, pal.fg, Fonts.regular), 12)
        choice(listOf("Juntos", "Normal", "Separados"), prefs.tileGap) { prefs.tileGap = it; changed() }
        add(label("Esquinas de los mosaicos: ${if (prefs.tileRadius == 0) "cuadradas" else "${prefs.tileRadius} dp"}", 15f, pal.fg, Fonts.regular), 12)
        add(SeekBar(this).apply {
            max = 24
            progress = prefs.tileRadius
            progressTintList = ColorStateList.valueOf(pal.accent)
            thumbTintList = ColorStateList.valueOf(pal.fg)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.tileRadius = s?.progress ?: 0; changed() }
            })
        }, 4)
        add(label("Tamaño del texto", 17f, pal.fg, Fonts.regular), 12)
        val scales = listOf(85, 100, 120)
        choice(listOf("Pequeño", "Normal", "Grande"), scales.indexOf(prefs.textScale).let { if (it < 0) 1 else it }) {
            prefs.textScale = scales[it]; changed()
        }
        switchRow("Bloquear diseño de Inicio", "Evita mover, redimensionar o desanclar mosaicos por accidente",
            prefs.lockLayout) { prefs.lockLayout = it; prefs.bump() }
        switchRow("Animación de giro al abrir apps", "El efecto \"turnstile\" de los mosaicos", prefs.turnstile) { prefs.turnstile = it; prefs.bump() }
        switchRow("Mostrar \"Todas las apps\" al final", "Los enlaces debajo de los mosaicos", prefs.showFooter) { prefs.showFooter = it; prefs.bump() }

        section("Íconos")
        val packs = IconPacks.installed(this)
        if (packs.isEmpty()) {
            caption("No tienes paquetes de íconos instalados. Busca \"icon pack\" en Google Play (por ejemplo, alguno estilo Metro o Windows) y vuelve aquí para elegirlo.")
        } else {
            add(label("Paquete de íconos", 17f, pal.fg, Fonts.regular), 8)
            val names = listOf("Íconos del sistema") + packs.map { it.label }
            val sel = packs.indexOfFirst { it.pkg == prefs.iconPack } + 1
            choice(names, sel) { i -> prefs.iconPack = if (i == 0) "" else packs[i - 1].pkg; changed() }
        }
        add(label("Estilo de los íconos", 17f, pal.fg, Fonts.regular), 12)
        choice(listOf(
            "Colores originales de cada app",
            "Monocromáticos cuando la app lo permite (Android 13+)",
            "Silueta de un solo color para todos (estilo Metro puro)",
        ), prefs.iconStyle) { prefs.iconStyle = it; changed() }
        caption("El color de los íconos se aplica a los estilos monocromático y silueta.")

        add(label("Color de íconos y texto", 17f, pal.fg, Fonts.regular), 14)
        val fgOptions = listOf("Blanco" to Color.WHITE, "Negro" to Color.BLACK, "Color de énfasis" to TileSpec.COLOR_ACCENT)
        val fgSel = fgOptions.indexOfFirst { it.second == prefs.tileFg }.let { if (it < 0) 3 else it }
        choice(fgOptions.map { it.first } + "Personalizado", fgSel) { i ->
            if (i < 3) { prefs.tileFg = fgOptions[i].second; changed() }
            else Pickers.askColor(this, pal, "Color de íconos y texto",
                if (prefs.tileFg == TileSpec.COLOR_ACCENT) prefs.accent else prefs.tileFg) { prefs.tileFg = it; changed() }
        }
        caption("Cada mosaico puede tener el suyo: mantenlo presionado → paleta → Color del ícono y texto.")
        add(label("Tamaño de los íconos: ${prefs.iconScale}%", 15f, pal.fg, Fonts.regular), 10)
        add(SeekBar(this).apply {
            max = 80
            progress = prefs.iconScale - 60
            progressTintList = ColorStateList.valueOf(pal.accent)
            thumbTintList = ColorStateList.valueOf(pal.fg)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.iconScale = 60 + (s?.progress ?: 40); changed() }
            })
        }, 4)

        val hidden = prefs.hiddenApps
        if (hidden.isNotEmpty()) {
            section("Apps ocultas")
            caption("Estas apps no aparecen en la lista. Para ocultar otra, mantenla presionada en la lista de apps.")
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

        section("Deslizar hacia abajo en Inicio")
        choice(listOf("Abre el Centro de actividades de Mosaico", "Abre el panel de notificaciones de Android"), prefs.swipeDown) {
            prefs.swipeDown = it; changed()
        }

        section("Mosaicos dinámicos")
        caption("Ánclalos a Inicio y luego mantenlos presionados para moverlos o cambiarles el tamaño.")
        val liveRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("Calendario" to TileKind.CALENDAR, "Clima" to TileKind.WEATHER, "Fotos" to TileKind.PHOTOS, "Batería" to TileKind.BATTERY)
            .forEach { (name, kind) ->
                liveRow.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dpi(6), 0, dpi(6))
                    addView(label(name, 17f, pal.fg, Fonts.regular), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button("Anclar") { pinLive(kind, name) })
                })
            }
        add(liveRow, 4)

        section("Clima")
        choice(listOf("Celsius (°C)", "Fahrenheit (°F)"), if (prefs.celsius) 0 else 1) {
            prefs.celsius = it == 0; prefs.weatherTime = 0; prefs.weatherJson = null; changed()
        }
        add(label("Ciudad (déjalo vacío para usar tu ubicación)", 15f, pal.fg, Fonts.regular), 10)
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

        section("Permisos")
        caption("Mosaico solo pide lo que necesita cada función.")
        statusRow("Notificaciones (contadores y Centro de actividades)", NotifService.hasAccess(this)) {
            go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        statusRow("Calendario", has(Manifest.permission.READ_CALENDAR)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_PERM)
        }
        statusRow("Ubicación (clima)", has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_PERM)
        }
        val photoPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        statusRow("Fotos", has(photoPerm)) { requestPermissions(arrayOf(photoPerm), REQ_PERM) }
        statusRow("Modificar ajustes (rotación)", Settings.System.canWrite(this)) {
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        statusRow("No molestar (horas de silencio)", getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        section("Launcher")
        addButton("Elegir launcher predeterminado") { DefaultHome.request(this) }
        addButton("Restablecer pantalla de Inicio") {
            prefs.tilesJson = null; prefs.bump()
            Toast.makeText(this, "Inicio restablecido", Toast.LENGTH_SHORT).show()
        }

        section("Acerca de")
        caption("Mosaico 1.0 · Launcher inspirado en el diseño de Windows 10 Mobile.\n" +
            "Proyecto independiente, sin relación con Microsoft. Clima: Open-Meteo.")
    }

    private fun pinLive(kind: TileKind, name: String) {
        val tiles = TileStore.load(prefs) ?: mutableListOf()
        val size = when (kind) { TileKind.WEATHER -> TileSize.WIDE; TileKind.BATTERY -> TileSize.SMALL; else -> TileSize.MEDIUM }
        tiles.add(TileSpec.live(kind, size))
        TileStore.save(prefs, tiles)
        prefs.bump()
        Toast.makeText(this, "$name anclado a Inicio", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
    }

    // ---------- foto propia ----------

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        @Suppress("DEPRECATION")
        try { startActivityForResult(i, REQ_IMAGE) } catch (e: Exception) { go(Intent(Intent.ACTION_GET_CONTENT).setType("image/*")) }
    }

    @Deprecated("API clásica de Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DefaultHome.REQ_ROLE) { DefaultHome.onResult(this, requestCode); return }
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
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
                FileOutputStream(File(filesDir, "backdrop.jpg")).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                true
            } catch (e: Throwable) { false }
            runOnUiThread {
                if (ok) { prefs.bgMode = 1; changed() }
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
