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

        repo = AppRepo(this)
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
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (prefs.version != appliedVersion) { recreate(); return }
        start.grid.turnstileIn(launchedOut)
        launchedOut = false
        refreshAll(false)
        updateBadges()
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
        when {
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
            start.setInsets(top, bottom)
            appList.setInsets(top, maxOf(bottom, ime))
            action.setInsets(top, bottom)
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
