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
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast
import java.util.concurrent.Executors

/** Launcher minimalista: inicio con widgets y favoritos, cajón de apps y todo lo demás personalizable. */
class MainActivity : Activity(), TileHost, HomeHost {

    override lateinit var repo: AppRepo
    override val prefs: Prefs by lazy { Prefs(this) }
    override lateinit var palette: Palette
    override lateinit var look: AppLook
    override val live = LiveState()
    override fun backdrop(): Bitmap? = null

    private lateinit var root: FrameLayout
    private lateinit var home: HomeView
    private lateinit var drawer: DrawerView
    private lateinit var action: ActionCenter
    private lateinit var menu: MetroMenu
    private var navBar: NavBarView? = null
    private var ownBar = false
    private var ownStatus = false
    private var statusBar: StatusBarView? = null
    private var serviceOn = false

    private var appliedVersion = -1
    private var resumed = false
    private var weatherLoading = false
    private var lastCalendar = 0L
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var appsCallback: LauncherApps.Callback? = null
    private val notifListener: () -> Unit = { main.post { updateBadges() } }

    companion object {
        private const val REQ_CAL = 11
        private const val REQ_LOC = 12
    }

    // ---------- ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferMaxRefreshRate()
        prefs.migrateToMinimal()
        palette = prefs.palette()
        look = AppLook(prefs, palette)
        appliedVersion = prefs.version
        serviceOn = NavService.isEnabled(this)
        ownBar = prefs.navInLauncher && !(serviceOn && prefs.navSystem)
        ownStatus = prefs.statusInLauncher && !(serviceOn && prefs.statusSystem)

        repo = AppRepo(this, prefs)
        repo.load()
        if (!prefs.sp.contains("dock")) prefs.dock = DefaultApps.dock(this, repo)
        if (!prefs.sp.contains("favorites")) prefs.favorites = DefaultApps.favorites(this, repo, prefs.dock)
        appliedVersion = prefs.version
        live.weather = WeatherData.fromJson(prefs.weatherJson)

        buildUi()
        setupWindow()
        registerReceivers()
        NotifService.addListener(notifListener)
        appsCallback = repo.watch { main.post { onAppsChanged() } }
        updateBadges()
        loadIconPack()
        if (intent?.getBooleanExtra(NavBarView.EXTRA_SEARCH, false) == true) root.post { drawer.open(true) }
        intent?.getStringExtra(SysActions.EXTRA_OPEN)?.let { what -> root.post { openFromExtra(what) } }
    }

    private fun openFromExtra(what: String) {
        if (what == "search") drawer.open(true) else drawer.open()
    }

    private fun loadIconPack() {
        val pkg = prefs.iconPack
        if (pkg.isEmpty()) return
        io.execute {
            val loader = try { IconPackLoader(this, pkg) } catch (e: Exception) { null }
            main.post {
                if (loader == null) { prefs.iconPack = ""; return@post }
                repo.pack = loader
                home.renderApps()
                IconCache.clear()
                drawer.markDirty()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (prefs.version != appliedVersion || NavService.isEnabled(this) != serviceOn) { recreate(); return }
        applyImmersive()
        statusBar?.refresh()
        refreshAll(false)
        updateBadges()
        home.refreshMedia()
        if (!DefaultHome.isDefault(this) && !prefs.askedDefault) {
            prefs.askedDefault = true
            main.postDelayed({ if (resumed) DefaultHome.request(this) }, 600)
        }
        action.updateClock()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        menu.dismiss()
        drawer.hideKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        NotifService.removeListener(notifListener)
        appsCallback?.let { repo.la.unregisterCallback(it) }
        io.shutdownNow()
    }

    /** Botón Inicio con el launcher ya abierto: cierra lo que esté abierto. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(NavBarView.EXTRA_SEARCH, false)) { drawer.open(true); return }
        val extra = intent.getStringExtra(SysActions.EXTRA_OPEN)
        if (extra != null) { openFromExtra(extra); return }
        goHomeState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private fun goHomeState() {
        when {
            menu.isOpen -> menu.dismiss()
            action.isOpen -> action.close()
            drawer.isOpen -> drawer.close()
        }
    }

    @Deprecated("Usado por compatibilidad con todas las versiones")
    override fun onBackPressed() = goHomeState()

    // ---------- interfaz ----------

    private fun buildUi() {
        root = FrameLayout(this)

        home = HomeView(this, this)
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        drawer = DrawerView(this, this)
        drawer.onShow = { open -> behindDrawer(open) }
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        action = ActionCenter(this, this)
        root.addView(action, FrameLayout.LayoutParams(-1, -1))
        menu = MetroMenu(this, palette)
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))

        val statusH = resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) resources.getDimensionPixelSize(it) else dpi(24) }
        if (ownStatus) {
            statusBar = StatusBarView(this, opaque = false,
                onOpen = { gesture(prefs.statusSwipe, null) }, onTap = { gesture(prefs.statusTap, null) }).also {
                it.applyTheme(prefs, palette.fg)
                root.addView(it, root.indexOfChild(action), FrameLayout.LayoutParams(-1, statusH, android.view.Gravity.TOP))
            }
        }

        val barH = dpi(48)
        if (ownBar) {
            navBar = NavBarView(this,
                onBack = { goHomeState() },
                onHome = { goHomeState() },
                onSearch = { drawer.open(true) },
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
            home.setInsets(tt, bb)
            drawer.setInsets(tt, maxOf(bb, ime))
            action.setInsets(tt, bb)
            ins
        }
        setContentView(root)
    }

    /** Con el cajón abierto: esconde el contenido de Inicio y desenfoca el fondo (Android 12+). */
    private fun behindDrawer(open: Boolean) {
        if (prefs.drawerHideHome) home.column.animate().alpha(if (open) 0f else 1f).setDuration(180).withLayer().start()
        if (Build.VERSION.SDK_INT >= 31 && prefs.drawerBlur > 0) {
            val r = dp(prefs.drawerBlur.toFloat())
            home.setRenderEffect(if (open) android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null)
        }
    }

    private fun setupWindow() {
        val w = window
        w.statusBarColor = android.graphics.Color.TRANSPARENT
        w.navigationBarColor = android.graphics.Color.TRANSPARENT
        w.isNavigationBarContrastEnforced = false
        w.isStatusBarContrastEnforced = false
        val lightBars = !palette.dark
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

    private fun applyImmersive() {
        val hideStatus = ownStatus || prefs.hideNativeStatus
        if (!ownBar && !hideStatus) return
        if (Build.VERSION.SDK_INT >= 30) {
            var types = 0
            if (ownBar) types = types or WindowInsets.Type.navigationBars()
            if (hideStatus) types = types or WindowInsets.Type.statusBars()
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
            if (hideStatus) f = f or View.SYSTEM_UI_FLAG_FULLSCREEN
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = f
        }
    }

    private fun openSettings(page: String? = null) {
        val i = Intent(this, SettingsActivity::class.java)
        if (page != null) i.putExtra(SettingsActivity.EXTRA_PAGE, page)
        startActivity(i)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun startSafe(i: Intent): Boolean = try { startActivity(i); true } catch (e: Exception) {
        toast("No se encontró una app para abrir esto"); false
    }

    // ---------- HomeHost ----------

    override fun launch(e: AppEntry, from: View) {
        repo.launch(e, from)
        if (drawer.isOpen) main.postDelayed({ drawer.close() }, 400)
    }

    override fun gesture(id: Int, anchor: View?) {
        when (id) {
            SysActions.ACTION_CENTER -> action.open()
            SysActions.APPS -> drawer.open()
            SysActions.SEARCH -> drawer.open(true)
            SysActions.SETTINGS -> openSettings()
            SysActions.MENU -> homeMenu(anchor ?: home)
            SysActions.NONE -> {}
            SysActions.ANDROID_PANEL, SysActions.ANDROID_QUICK ->
                if (!SysActions.runSystem(this, id)) toast("Este teléfono no deja abrir el panel desde Mosaico")
            else -> SysActions.run(this, id)
        }
    }

    override fun drawerDrag(dy: Float) = drawer.dragFromHome(dy)
    override fun drawerRelease(dy: Float, vy: Float) = drawer.releaseFromHome(dy, vy)

    override fun openClock() { startSafe(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }

    override fun openCalendar() {
        startSafe(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }

    override fun weatherTapped() {
        if (prefs.city.isBlank() && !has(Manifest.permission.ACCESS_COARSE_LOCATION))
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC)
        else { toast("Actualizando el clima…"); refreshWeather(true) }
    }

    private fun homeMenu(anchor: View) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Personalizar inicio" to { openSettings("inicio") }
        items += "Cajón de apps" to { openSettings("cajon") }
        items += "Tema, colores y fuente" to { openSettings("tema") }
        items += "Íconos" to { openSettings("iconos") }
        items += "Gestos" to { openSettings("gestos") }
        items += "Fondo de pantalla de Android" to {
            startSafe(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
        }
        items += "Todos los ajustes de Mosaico" to { openSettings() }
        menu.show(anchor, items)
    }

    override fun appMenu(e: AppEntry, anchor: View, place: String) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        val favs = prefs.favorites
        val dock = prefs.dock
        val inFav = e.key in favs
        val inDock = e.key in dock
        if (inFav) {
            val i = favs.indexOf(e.key)
            if (i > 0) items += (if (prefs.favStyle == 3) "Mover antes" else "Subir") to { prefs.favorites = favs.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }; home.renderApps() }
            if (i < favs.size - 1) items += (if (prefs.favStyle == 3) "Mover después" else "Bajar") to { prefs.favorites = favs.toMutableList().also { java.util.Collections.swap(it, i, i + 1) }; home.renderApps() }
            items += "Quitar de Inicio" to { prefs.favorites = favs - e.key; home.renderApps() }
        } else items += "Agregar a Inicio" to {
            prefs.favorites = favs + e.key; home.renderApps(); toast("${look.name(e)} está en Inicio")
        }
        if (inDock) {
            val i = dock.indexOf(e.key)
            if (place == "dock" && i > 0) items += "Mover a la izquierda" to { prefs.dock = dock.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }; home.renderApps() }
            if (place == "dock" && i < dock.size - 1) items += "Mover a la derecha" to { prefs.dock = dock.toMutableList().also { java.util.Collections.swap(it, i, i + 1) }; home.renderApps() }
            items += "Quitar del dock" to { prefs.dock = dock - e.key; home.renderApps() }
        } else if (dock.size < 6) items += "Agregar al dock" to { prefs.dock = dock + e.key; home.renderApps() }
        items += "Cambiar nombre" to {
            Pickers.askText(this, palette, "Nombre", look.name(e), e.label) { s ->
                look.rename(e, s); home.renderApps(); drawer.markDirty()
            }
        }
        items += "Ocultar del cajón" to {
            prefs.hiddenApps = prefs.hiddenApps + e.key
            repo.applyHidden(); drawer.markDirty()
            toast("${look.name(e)} oculta. Puedes volver a mostrarla en Personalizar → Íconos.")
        }
        items += "Información de la app" to { repo.appInfo(e) }
        items += "Desinstalar" to { repo.uninstall(e) }
        menu.show(anchor, items)
    }

    // ---------- datos ----------

    private fun onAppsChanged() {
        repo.load()
        IconCache.clear()
        val keys = repo.allApps.map { it.key }.toSet()
        prefs.favorites.filter { it !in keys }.let { gone -> if (gone.isNotEmpty()) prefs.favorites = prefs.favorites - gone.toSet() }
        prefs.dock.filter { it !in keys }.let { gone -> if (gone.isNotEmpty()) prefs.dock = prefs.dock - gone.toSet() }
        home.renderApps()
        drawer.markDirty()
    }

    @Deprecated("API clásica de Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        DefaultHome.onResult(this, requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CAL -> { lastCalendar = 0; refreshCalendar() }
            REQ_LOC -> refreshWeather(true)
        }
    }

    private fun refreshAll(force: Boolean) {
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { readBattery(it) }
        if (prefs.showWeather) refreshWeather(force)
        if (prefs.showEvent) refreshCalendar()
        home.refreshInfo()
    }

    private fun updateBadges() {
        live.badges = NotifService.counts()
        statusBar?.refresh()
        if (::home.isInitialized) { home.refreshBadges(); home.refreshMedia(); drawer.refreshBadges() }
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
        if (prefs.city.isBlank() && !has(Manifest.permission.ACCESS_COARSE_LOCATION) && !force) return
        weatherLoading = true
        io.execute {
            val res = try { Result.success(Weather.fetch(this, prefs)) } catch (e: Throwable) { Result.failure(e) }
            main.post {
                weatherLoading = false
                res.onSuccess {
                    live.weather = it; live.weatherError = null
                    prefs.weatherJson = it.toJson(); prefs.weatherTime = System.currentTimeMillis()
                }.onFailure {
                    if (force) toast(when (it) {
                        is Weather.NeedsLocation, is Weather.NoFix -> "Activa la ubicación o escribe tu ciudad en Personalizar → Clima"
                        else -> "No se pudo actualizar el clima"
                    })
                }
                home.refreshInfo()
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
                ContentUris.appendId(b, now + 2 * 86_400_000L)
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
            main.post { live.nextEvent = ev; home.refreshInfo() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> { readBattery(intent); home.refreshInfo() }
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                    lastCalendar = 0; refreshCalendar(); home.tick()
                }
                else -> { home.tick(); if (resumed) refreshCalendar() }
            }
            action.updateClock()
            statusBar?.refresh()
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
