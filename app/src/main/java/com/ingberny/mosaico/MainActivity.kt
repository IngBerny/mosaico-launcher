package com.ingberny.mosaico

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity(), TileHost, TileGrid.Listener, AppListView.Callbacks {

    override lateinit var repo: AppRepo
    override val prefs: Prefs by lazy { Prefs(this) }
    override lateinit var palette: Palette
    override val live = LiveState()
    private var backdropBmp: Bitmap? = null
    override fun backdrop(): Bitmap? = backdropBmp

    private lateinit var root: FrameLayout
    private lateinit var pager: Pager
    private lateinit var start: StartScreen
    private lateinit var appList: AppListView
    private lateinit var action: ActionCenter
    private lateinit var menu: MetroMenu
    private lateinit var colorSheet: ColorSheet
    private var navBar: NavBarView? = null
    /** true si usamos la barra propia dentro de Mosaico (y ocultamos la de Android). */
    private var ownBar = false
    private var ownStatus = false
    private var statusBar: StatusBarView? = null
    private var serviceOn = false

    private var appliedVersion = -1
    private var launchedOut = false
    private var resumed = false
    private var weatherLoading = false
    private var lastCalendar = 0L
    private var lastPhotos = 0L
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var appsCallback: LauncherApps.Callback? = null
    private val notifListener: () -> Unit = { updateBadges() }

    companion object {
        private const val REQ_CAL = 11
        private const val REQ_LOC = 12
        private const val REQ_PHOTOS = 13
    }

    // ---------- ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = prefs.palette()
        appliedVersion = prefs.version
        serviceOn = NavService.isEnabled(this)
        ownBar = prefs.navInLauncher && !(serviceOn && prefs.navSystem)
        ownStatus = prefs.statusInLauncher && !(serviceOn && prefs.statusSystem)

        repo = AppRepo(this, prefs)
        repo.load()
        val tiles = TileStore.load(prefs) ?: DefaultTiles.build(this, repo).also { TileStore.save(prefs, it) }
        live.weather = WeatherData.fromJson(prefs.weatherJson)
        if (prefs.bgMode == 1) loadBackdrop()

        buildUi(tiles)
        setupWindow()
        registerReceivers()
        NotifService.addListener(notifListener)
        appsCallback = repo.watch { removed -> onAppsChanged(removed) }
        updateBadges()
        loadIconPack()
        if (intent?.getBooleanExtra(NavBarView.EXTRA_SEARCH, false) == true) root.post { openSearch() }
    }

    private fun loadIconPack() {
        val pkg = prefs.iconPack
        if (pkg.isEmpty()) return
        io.execute {
            val loader = try { IconPackLoader(this, pkg) } catch (e: Exception) { null }
            main.post {
                if (loader == null) { prefs.iconPack = ""; return@post }
                repo.pack = loader
                start.grid.invalidateTiles()
                appList.refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (prefs.version != appliedVersion || NavService.isEnabled(this) != serviceOn) { recreate(); return }
        applyImmersive()
        statusBar?.refresh()
        start.grid.turnstileIn(launchedOut)
        launchedOut = false
        refreshAll(false)
        updateBadges()
        val isDefault = DefaultHome.isDefault(this)
        start.showBanner(!isDefault)
        if (!isDefault && !prefs.askedDefault) {
            prefs.askedDefault = true
            main.postDelayed({ if (resumed) DefaultHome.request(this) }, 600)
        }
        action.updateClock()
        main.removeCallbacks(flipLoop); main.postDelayed(flipLoop, 3500)
        main.removeCallbacks(photoLoop); main.postDelayed(photoLoop, 6000)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        main.removeCallbacks(flipLoop)
        main.removeCallbacks(photoLoop)
        start.grid.exitEdit()
        menu.dismiss()
        appList.hideKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        NotifService.removeListener(notifListener)
        appsCallback?.let { repo.la.unregisterCallback(it) }
        io.shutdownNow()
    }

    /** Botón Inicio con el launcher ya abierto. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(NavBarView.EXTRA_SEARCH, false)) { openSearch(); return }
        goHomeState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private fun openSearch() {
        if (colorSheet.isOpen) colorSheet.dismiss()
        if (menu.isOpen) menu.dismiss()
        if (action.isOpen) action.close()
        start.grid.exitEdit()
        pager.goTo(1)
        appList.focusSearch()
    }

    /** Botón Inicio: cierra lo que esté abierto o vuelve arriba en Inicio. */
    private fun goHomeState() {
        when {
            colorSheet.isOpen -> colorSheet.dismiss()
            menu.isOpen -> menu.dismiss()
            action.isOpen -> action.close()
            start.grid.editing -> start.grid.exitEdit()
            pager.page == 1 -> { appList.reset(); pager.goTo(0) }
            else -> start.scrollToTop()
        }
    }

    @Deprecated("Usado por compatibilidad con todas las versiones")
    override fun onBackPressed() {
        when {
            colorSheet.isOpen -> colorSheet.dismiss()
            menu.isOpen -> menu.dismiss()
            action.isOpen -> action.close()
            appList.jumpOpen -> appList.hideJump()
            start.grid.editing -> start.grid.exitEdit()
            pager.page == 1 -> { appList.reset(); pager.goTo(0) }
        }
    }

    // ---------- interfaz ----------

    private fun buildUi(tiles: MutableList<TileSpec>) {
        root = FrameLayout(this)
        if (prefs.bgMode != 0) root.setBackgroundColor(palette.bg)

        start = StartScreen(this, this, { pager.goTo(1) }, { openSettings() })
        start.grid.columns = if (prefs.moreTiles) 6 else 4
        start.grid.listener = this
        start.grid.setTiles(tiles)
        start.scroll.onPullDown = { onPullDown() }
        start.onBannerClick = { DefaultHome.request(this) }

        appList = AppListView(this, this, this)
        appList.refresh()

        pager = Pager(this)
        pager.addView(start, FrameLayout.LayoutParams(-1, -1))
        pager.addView(appList, FrameLayout.LayoutParams(-1, -1))
        pager.onPageChanged = { p ->
            if (p == 0) appList.hideKeyboard() else start.grid.exitEdit()
        }
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))

        action = ActionCenter(this, this)
        root.addView(action, FrameLayout.LayoutParams(-1, -1))
        menu = MetroMenu(this, palette)
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))
        colorSheet = ColorSheet(this, palette)
        root.addView(colorSheet, FrameLayout.LayoutParams(-1, -1))

        val statusH = resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) resources.getDimensionPixelSize(it) else dpi(24) }
        if (ownStatus) {
            statusBar = StatusBarView(this, opaque = false) { action.open() }.also {
                it.fg = if (!palette.dark && prefs.bgMode != 0) Color.BLACK else Color.WHITE
                root.addView(it, root.indexOfChild(action), FrameLayout.LayoutParams(-1, statusH, android.view.Gravity.TOP))
            }
        }

        val barH = dpi(48)
        if (ownBar) {
            navBar = NavBarView(this,
                onBack = {
                    @Suppress("DEPRECATION")
                    onBackPressed()
                },
                onHome = { goHomeState() },
                onSearch = { if (pager.page == 1 && !colorSheet.isOpen) appList.focusSearch() else openSearch() },
                onLongBack = { NavService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) },
                onLongHome = { startSafe(NavBarView.assistIntent()) },
            )
            root.addView(navBar, FrameLayout.LayoutParams(-1, barH, android.view.Gravity.BOTTOM))
        }

        root.setOnApplyWindowInsetsListener { _, ins ->
            val top: Int; val bottom: Int; val ime: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                top = b.top; bottom = b.bottom
                ime = ins.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION")
                top = ins.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = ins.stableInsetBottom
                @Suppress("DEPRECATION")
                ime = ins.systemWindowInsetBottom
            }
            val tt = if (ownStatus) maxOf(top, statusH) else top
            val bar = navBar
            val bb = if (bar != null) {
                (bar.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottom
                bar.requestLayout()
                bottom + barH
            } else bottom
            start.setInsets(tt, bb)
            appList.setInsets(tt, maxOf(bb, ime))
            action.setInsets(tt, bb)
            colorSheet.setInsets(bb)
            ins
        }
        setContentView(root)
    }

    private fun setupWindow() {
        val w = window
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        w.isNavigationBarContrastEnforced = false
        w.isStatusBarContrastEnforced = false
        val lightBars = !palette.dark && prefs.bgMode != 0
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (lightBars) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            @Suppress("DEPRECATION")
            if (lightBars) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = flags
        }
        applyImmersive()
    }

    /** Oculta la barra de Android mientras Mosaico está en pantalla (se puede mostrar deslizando desde abajo). */
    private fun applyImmersive() {
        if (!ownBar && !ownStatus) return
        if (Build.VERSION.SDK_INT >= 30) {
            var types = 0
            if (ownBar) types = types or WindowInsets.Type.navigationBars()
            if (ownStatus) types = types or WindowInsets.Type.statusBars()
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(types)
            }
        } else {
            @Suppress("DEPRECATION")
            var f = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            @Suppress("DEPRECATION")
            if (ownBar) f = f or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            @Suppress("DEPRECATION")
            if (ownStatus) f = f or View.SYSTEM_UI_FLAG_FULLSCREEN
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = f
        }
    }

    private fun loadBackdrop() {
        val f = File(filesDir, "backdrop.jpg")
        backdropBmp = if (f.exists()) try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null } else null
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun onPullDown() {
        if (prefs.swipeDown == 1) {
            try {
                @Suppress("WrongConstant")
                val sb = getSystemService("statusbar")
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
                return
            } catch (e: Throwable) { }
        }
        action.open()
    }

    // ---------- mosaicos ----------

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun photoPerm() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun startSafe(i: Intent): Boolean = try { startActivity(i); true } catch (e: Exception) {
        toast("No se encontró una app para abrir esto"); false
    }

    private fun launchWithTurnstile(v: View, go: () -> Unit) {
        if (!prefs.turnstile) { go(); return }
        launchedOut = true
        start.grid.turnstileOut(v) {
            go()
            // Si no se abrió nada (error), devolvemos los mosaicos.
            main.postDelayed({ if (resumed && launchedOut) { start.grid.turnstileIn(true); launchedOut = false } }, 1200)
        }
    }

    override fun onTileClick(t: TileSpec, v: TileView) {
        when (t.kind) {
            TileKind.APP -> {
                val e = repo.find(t) ?: run { toast("Esta app ya no está instalada"); return }
                launchWithTurnstile(v) { repo.launch(e, v) }
            }
            TileKind.CALENDAR ->
                if (!has(Manifest.permission.READ_CALENDAR)) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CAL)
                else launchWithTurnstile(v) {
                    startSafe(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                }
            TileKind.WEATHER ->
                if (prefs.city.isBlank() && !has(Manifest.permission.ACCESS_COARSE_LOCATION))
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC)
                else { toast("Actualizando el clima…"); refreshWeather(true) }
            TileKind.PHOTOS ->
                if (!has(photoPerm())) requestPermissions(arrayOf(photoPerm()), REQ_PHOTOS)
                else launchWithTurnstile(v) {
                    startSafe(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
                }
            TileKind.BATTERY -> startSafe(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
    }

    override fun onTilesChanged() = TileStore.save(prefs, start.grid.tiles)

    override fun onLayoutLocked() = toast("El diseño de Inicio está bloqueado. Puedes desbloquearlo en Personalizar.")

    /** Botón de paleta en modo edición: color, nombre y visibilidad del nombre del mosaico. */
    override fun onTileCustomize(t: TileSpec, v: TileView) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Color del mosaico" to {
            val appColor = repo.find(t)?.let { repo.color(it) }
            colorSheet.show("Color del mosaico", t.color, palette.accent, appColor, { c -> setTileColor(t, v, c) }) {
                val start = if (t.color > TileSpec.COLOR_ACCENT || t.color < 0) t.color else v.baseColor()
                Pickers.askColor(this, palette, "Color personalizado", start or 0xFF000000.toInt()) { c -> setTileColor(t, v, c) }
            }
        }
        items += "Color del ícono y texto" to {
            colorSheet.show("Color del ícono y texto", t.iconColor, iconColorOptions(true), { c ->
                t.iconColor = c; v.invalidate(); onTilesChanged()
            }) {
                val cur = if (t.iconColor == TileSpec.COLOR_DEFAULT || t.iconColor == TileSpec.COLOR_ACCENT) Color.WHITE else t.iconColor
                Pickers.askColor(this, palette, "Color del ícono", cur) { c -> t.iconColor = c; v.invalidate(); onTilesChanged() }
            }
        }
        val currentName = t.label ?: repo.find(t)?.label ?: when (t.kind) {
            TileKind.CALENDAR -> "Calendario"; TileKind.WEATHER -> "Clima"; TileKind.PHOTOS -> "Fotos"
            TileKind.BATTERY -> "Batería"; TileKind.APP -> ""
        }
        items += "Cambiar nombre" to {
            Pickers.askText(this, palette, "Nombre del mosaico", currentName, "Nombre") { s ->
                t.label = s.ifBlank { null }; v.invalidate(); onTilesChanged()
            }
        }
        if (t.kind == TileKind.APP && prefs.tilePreviews) {
            items += (if (t.noPreview) "Mostrar vista previa de mensajes" else "Ocultar vista previa de mensajes") to {
                t.noPreview = !t.noPreview; v.resetFace(); v.invalidate(); onTilesChanged()
            }
        }
        items += (if (t.hideLabel) "Mostrar nombre" else "Ocultar nombre") to {
            t.hideLabel = !t.hideLabel; v.invalidate(); onTilesChanged()
        }
        if (t.color != TileSpec.COLOR_DEFAULT || t.iconColor != TileSpec.COLOR_DEFAULT || t.label != null || t.hideLabel)
            items += "Restablecer estilo" to {
                t.color = TileSpec.COLOR_DEFAULT; t.iconColor = TileSpec.COLOR_DEFAULT
                t.label = null; t.hideLabel = false; v.invalidate(); onTilesChanged()
            }
        menu.show(v, items)
    }

    private fun iconColorOptions(withAuto: Boolean): List<Triple<String, Int, Int>> {
        val l = ArrayList<Triple<String, Int, Int>>()
        if (withAuto) l += Triple("Automático", TileSpec.COLOR_DEFAULT, Color.WHITE)
        l += Triple("Blanco", Color.WHITE, Color.WHITE)
        l += Triple("Negro", Color.BLACK, Color.BLACK)
        l += Triple("Énfasis", TileSpec.COLOR_ACCENT, palette.accent)
        return l
    }

    private fun setTileColor(t: TileSpec, v: TileView, c: Int) {
        t.color = c; v.invalidate(); onTilesChanged()
    }

    @Deprecated("API clásica de Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        DefaultHome.onResult(this, requestCode)
    }
    override fun onEditModeChanged(editing: Boolean) {}
    override fun scroller(): ScrollView = start.scroll

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CAL -> { lastCalendar = 0; refreshCalendar() }
            REQ_LOC -> refreshWeather(true)
            REQ_PHOTOS -> { lastPhotos = 0; refreshPhotos() }
        }
    }

    // ---------- lista de apps ----------

    override fun launch(e: AppEntry, from: View) = repo.launch(e, from)

    override fun showMenu(e: AppEntry, anchor: View) {
        val pinned = start.grid.tiles.any { it.kind == TileKind.APP && it.pkg == e.pkg && it.cls == e.cls }
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (pinned) items += "Desanclar de Inicio" to {
            start.grid.removeWhere { it.kind == TileKind.APP && it.pkg == e.pkg && it.cls == e.cls }
        } else items += "Anclar a Inicio" to {
            start.grid.addTile(TileSpec.app(e, TileSize.MEDIUM))
            appList.reset()
            pager.goTo(0)
            start.scrollToBottom()
        }
        items += "Ocultar de la lista" to {
            prefs.hiddenApps = prefs.hiddenApps + e.key
            repo.applyHidden(); appList.refresh()
            toast("${e.label} oculta. Puedes volver a mostrarla en Personalizar.")
        }
        items += "Información de la app" to { repo.appInfo(e) }
        items += "Desinstalar" to { repo.uninstall(e) }
        menu.show(anchor, items)
    }

    private fun onAppsChanged(removedPkg: String?) {
        repo.load()
        if (removedPkg != null && repo.apps.none { it.pkg == removedPkg }) {
            start.grid.removeWhere { it.kind == TileKind.APP && it.pkg == removedPkg }
        }
        appList.refresh()
        start.grid.invalidateTiles()
    }

    // ---------- datos vivos ----------

    private val flipLoop = object : Runnable {
        override fun run() { start.grid.flipRandom(); main.postDelayed(this, 4000L + (0..2500).random()) }
    }
    private val photoLoop = object : Runnable {
        override fun run() { start.grid.cyclePhotos(); main.postDelayed(this, 6000) }
    }

    private fun hasTile(k: TileKind) = start.grid.tiles.any { it.kind == k }

    private fun refreshAll(force: Boolean) {
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { readBattery(it) }
        if (hasTile(TileKind.WEATHER)) refreshWeather(force)
        if (hasTile(TileKind.CALENDAR)) refreshCalendar()
        if (hasTile(TileKind.PHOTOS)) refreshPhotos()
        start.grid.invalidateTiles()
    }

    private fun updateBadges() {
        live.badges = NotifService.counts()
        live.previews = if (prefs.tilePreviews) NotifService.previews(this) else emptyMap()
        if (::start.isInitialized) start.grid.views().forEach { it.resetFace() }
        statusBar?.refresh()
        if (::start.isInitialized) start.grid.invalidateTiles()
    }

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        live.batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        live.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun refreshWeather(force: Boolean) {
        if (!force && live.weather != null && System.currentTimeMillis() - prefs.weatherTime < 30 * 60_000L) return
        if (weatherLoading) return
        weatherLoading = true
        io.execute {
            val res = try { Result.success(Weather.fetch(this, prefs)) } catch (e: Throwable) { Result.failure(e) }
            main.post {
                weatherLoading = false
                res.onSuccess {
                    live.weather = it; live.weatherError = null
                    prefs.weatherJson = it.toJson(); prefs.weatherTime = System.currentTimeMillis()
                }.onFailure {
                    val msg = when (it) {
                        is Weather.NeedsLocation -> "Toca para usar tu ubicación"
                        is Weather.NoFix -> "Ubicación no disponible"
                        else -> "Sin conexión"
                    }
                    if (live.weather == null) live.weatherError = msg
                    if (force) toast(when (it) {
                        is Weather.NeedsLocation, is Weather.NoFix -> "Activa la ubicación o escribe tu ciudad en Personalizar"
                        else -> "No se pudo actualizar el clima"
                    })
                }
                start.grid.views().forEach { v -> v.resetFace() }
                start.grid.invalidateTiles()
            }
        }
    }

    private fun refreshCalendar() {
        if (!has(Manifest.permission.READ_CALENDAR)) return
        val now = System.currentTimeMillis()
        if (now - lastCalendar < 10 * 60_000L) return
        lastCalendar = now
        io.execute {
            val ev: CalEvent? = try {
                val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(b, now - 3_600_000L)
                ContentUris.appendId(b, now + 7 * 86_400_000L)
                contentResolver.query(b.build(), arrayOf("title", "begin", "end", "allDay"), null, null, "begin ASC")?.use { c ->
                    var found: CalEvent? = null
                    while (c.moveToNext()) {
                        if (c.getLong(2) > now) {
                            found = CalEvent(c.getString(0)?.ifBlank { null } ?: "(Sin título)", c.getLong(1), c.getInt(3) == 1)
                            break
                        }
                    }
                    found
                }
            } catch (e: Exception) { null }
            main.post {
                live.nextEvent = ev
                start.grid.views().forEach { it.resetFace() }
                start.grid.invalidateTiles()
            }
        }
    }

    private fun refreshPhotos() {
        if (!has(photoPerm())) return
        val now = System.currentTimeMillis()
        if (now - lastPhotos < 10 * 60_000L && live.photos.isNotEmpty()) return
        lastPhotos = now
        io.execute {
            val list = ArrayList<Bitmap>()
            try {
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                contentResolver.query(uri, arrayOf("_id"), null, null, "date_added DESC")?.use { c ->
                    while (c.moveToNext() && list.size < 10) {
                        try { list += contentResolver.loadThumbnail(ContentUris.withAppendedId(uri, c.getLong(0)), Size(420, 420), null) }
                        catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) { }
            main.post { live.photos = list; start.grid.invalidateTiles() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                    lastCalendar = 0; refreshCalendar()
                }
                else -> if (resumed) refreshCalendar()
            }
            action.updateClock()
            statusBar?.refresh()
            start.grid.invalidateTiles()
        }
    }

    private fun registerReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
    }
}
