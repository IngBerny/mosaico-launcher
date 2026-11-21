package com.ingberny.mosaico

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Lo que la pantalla de inicio necesita de MainActivity. */
interface HomeHost {
    val repo: AppRepo
    val prefs: Prefs
    val palette: Palette
    val live: LiveState
    val look: AppLook
    fun launch(e: AppEntry, from: View)
    fun appMenu(e: AppEntry, anchor: View, place: String)
    fun gesture(id: Int, anchor: View?)
    fun openClock()
    fun openCalendar()
    fun weatherTapped()
}

/**
 * Inicio minimalista: saludo, hora, fecha, una línea con clima, batería y próximo evento,
 * lo que está sonando, tus apps favoritas y un dock. Gestos en las cuatro direcciones,
 * doble toque y mantener presionado.
 */
class HomeView(ctx: Context, private val host: HomeHost) : FrameLayout(ctx) {

    private val prefs = host.prefs
    private val pal = host.palette
    private val look = host.look
    private val align = when (prefs.homeAlign) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
    private val favGravity = when (prefs.favAlign) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }

    /** Contenido de Inicio (reloj, apps, dock), sin el fondo. */
    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val widgets = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = align }
    private val favBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = favGravity }
    private val dockBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

    private val greeting = ctx.label("", 16f, pal.sub, look.face)
    private val clock = ctx.label("", 80f, pal.fg, look.bigFace)
    private val date = ctx.label("", 17f, pal.fg, look.face)
    private val info = ctx.label("", 14f, pal.sub, look.face)
    private val mediaBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = align }
    private val mediaTitle = ctx.label("", 15f, pal.fg, look.face).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val controls = MediaControlsView(ctx, pal.fg) { refreshMedia() }
    private var media: MediaInfo? = null

    private var insetTop = 0
    private var insetBottom = 0

    init {
        clipChildren = false
        addBackground()
        widgets.setPadding(dpi(28), dpi(44), dpi(28), 0)

        if (prefs.showGreeting) widgets.addView(greeting.apply { gravity = align })
        if (prefs.clockStyle != 3) {
            clock.textSize = when (prefs.clockStyle) { 1 -> 52f; 2 -> 96f; else -> 80f }
            clock.gravity = align
            if (prefs.clockStyle == 2) clock.setLineSpacing(0f, 0.82f)
            clock.setOnClickListener { host.openClock() }
            widgets.addView(clock)
        }
        if (prefs.showDate) widgets.addView(date.apply {
            gravity = align
            setPadding(0, dpi(2), 0, 0)
            setOnClickListener { host.openCalendar() }
        })
        widgets.addView(info.apply {
            gravity = align
            setPadding(0, dpi(6), 0, 0)
            setOnClickListener { host.weatherTapped() }
        })
        if (prefs.showMedia) {
            mediaBox.setPadding(0, dpi(18), 0, 0)
            mediaBox.addView(mediaTitle.apply { gravity = align })
            mediaBox.addView(controls, LinearLayout.LayoutParams(dpi(150), dpi(40)).apply { gravity = align; topMargin = dpi(2) })
            mediaBox.visibility = View.GONE
            widgets.addView(mediaBox, LinearLayout.LayoutParams(-1, -2))
        }

        column.addView(widgets, LinearLayout.LayoutParams(-1, -2))
        val favLp = LinearLayout.LayoutParams(-1, -2)
        fun spacer() = View(ctx).also { column.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        when (prefs.favPos) {
            0 -> { column.addView(favBox, favLp.apply { topMargin = dpi(36) }); spacer() }
            1 -> { spacer(); column.addView(favBox, favLp); spacer() }
            else -> { spacer(); column.addView(favBox, favLp.apply { bottomMargin = dpi(28) }) }
        }
        if (prefs.dockOn) {
            if (prefs.dockBg == 1) dockBox.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(withAlpha(pal.fg, 26)); cornerRadius = dp(28f)
            }
            column.addView(dockBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(12), 0, dpi(12), dpi(10)) })
        }
        favBox.setPadding(if (prefs.favStyle == 3) dpi(8) else dpi(28), 0, if (prefs.favStyle == 3) dpi(8) else dpi(28), 0)
        dockBox.setPadding(dpi(8), dpi(10), dpi(8), dpi(10))
        addView(column, LayoutParams(-1, -1))

        renderApps()
        tick()
    }

    private fun addBackground() {
        when (prefs.homeBg) {
            1 -> {
                val f = File(context.filesDir, "backdrop.jpg")
                val bmp = if (f.exists()) try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null } else null
                if (bmp != null) addView(ImageView(context).apply {
                    setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP
                }, LayoutParams(-1, -1))
                else setBackgroundColor(pal.bg)
            }
            2 -> setBackgroundColor(pal.bg)
            3 -> addView(MountainsView(context, pal), LayoutParams(-1, -1))
        }
        if (prefs.homeDim > 0) addView(View(context).apply {
            setBackgroundColor(withAlpha(android.graphics.Color.BLACK, prefs.homeDim.coerceIn(0, 80) * 255 / 100))
        }, LayoutParams(-1, -1))
    }

    fun setInsets(top: Int, bottom: Int) {
        insetTop = top; insetBottom = bottom
        column.setPadding(0, top, 0, bottom)
    }

    // ---------- contenido ----------

    /** Hora, fecha, saludo y línea de información. Se llama cada minuto. */
    fun tick() {
        val now = Date()
        val h24 = DateFormat.is24HourFormat(context)
        val hh = SimpleDateFormat(if (h24) "HH" else "h", Locale.getDefault()).format(now)
        val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        clock.text = if (prefs.clockStyle == 2) "$hh\n$mm" else "$hh:$mm"
        date.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now).capFirst()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
        val name = prefs.greetName.trim()
        greeting.text = if (name.isEmpty()) hello else "$hello, $name"
        refreshInfo()
    }

    fun refreshInfo() {
        val parts = ArrayList<String>()
        val live = host.live
        if (prefs.showWeather) live.weather?.let { parts += "${it.temp}° ${it.city}".trim() }
        if (prefs.showBattery && live.batteryPct >= 0) parts += (if (live.charging) "⚡" else "") + "${live.batteryPct}%"
        if (prefs.showEvent) live.nextEvent?.let { ev ->
            val t = if (ev.allDay) "hoy" else SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
                Locale.getDefault()).format(Date(ev.begin))
            parts += "${ev.title} · $t"
        }
        info.text = parts.joinToString("   ·   ")
        info.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    fun refreshMedia() {
        if (!prefs.showMedia) return
        media = Media.current(context)
        val m = media
        if (m == null) { mediaBox.visibility = View.GONE; return }
        mediaTitle.text = if (m.artist.isBlank()) m.title else "${m.title}  —  ${m.artist}"
        controls.info = m
        mediaBox.visibility = View.VISIBLE
    }

    /** Favoritos y dock. */
    fun renderApps() {
        favBox.removeAllViews()
        dockBox.removeAllViews()
        val repo = host.repo
        val favs = prefs.favorites.mapNotNull { k -> repo.apps.firstOrNull { it.key == k } }
        if (favs.isEmpty()) {
            favBox.addView(context.label("Mantén presionada una app del cajón → Agregar a Inicio", 14f, pal.sub, look.face).apply {
                gravity = favGravity
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val shadow = prefs.labelShadow && prefs.homeBg != 2
        when (prefs.favStyle) {
            3 -> {
                // Cuadrícula con nombres, como en POCO Launcher.
                val cols = prefs.homeCols.coerceIn(3, 7)
                favs.chunked(cols).forEach { chunk ->
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    for (i in 0 until cols) {
                        val e = chunk.getOrNull(i)
                        if (e == null) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                        row.addView(appCell(context, host, e, prefs.homeIconSize, prefs.homeLabelSize, prefs.homeLabels,
                            pal.fg, shadow, "fav"), LinearLayout.LayoutParams(0, -2, 1f).apply {
                            setMargins(0, dpi(prefs.favSpacing / 2), 0, dpi(prefs.favSpacing / 2))
                        })
                    }
                    favBox.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            }
            2 -> {
                // Solo íconos, en filas de hasta 5.
                favs.chunked(5).forEach { chunk ->
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = favGravity }
                    chunk.forEach { e -> row.addView(iconView(e, "fav"), LinearLayout.LayoutParams(dpi(64), dpi(64)).apply {
                        setMargins(dpi(6), dpi(prefs.favSpacing / 2), dpi(6), dpi(prefs.favSpacing / 2))
                    }) }
                    favBox.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            }
            else -> favs.forEach { e -> favBox.addView(favRow(e), LinearLayout.LayoutParams(-1, -2)) }
        }
        if (prefs.dockOn) {
            prefs.dock.mapNotNull { k -> repo.apps.firstOrNull { it.key == k } }.take(6).forEach { e ->
                dockBox.addView(appCell(context, host, e, prefs.dockIconSize, prefs.homeLabelSize, prefs.dockLabels,
                    pal.fg, shadow && prefs.dockBg == 0, "dock"), LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
    }

    private fun favRow(e: AppEntry): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = favGravity or Gravity.CENTER_VERTICAL
            setPadding(0, dpi(prefs.favSpacing / 2), 0, dpi(prefs.favSpacing / 2))
        }
        if (prefs.favStyle == 1) {
            val s = (prefs.favSize * 1.5f).toInt()
            row.addView(AppIconView(context, look, host.repo, e) { host.live.badges[e.pkg] ?: 0 },
                LinearLayout.LayoutParams(dpi(s), dpi(s)).apply { rightMargin = dpi(14) })
        }
        val count = host.live.badges[e.pkg] ?: 0
        val t: TextView = context.label(look.name(e), prefs.favSize.toFloat(), pal.fg, look.face)
        row.addView(t)
        if (count > 0 && prefs.favStyle == 0) row.addView(context.label("  •", prefs.favSize * 0.8f, pal.accent, look.face))
        row.setOnClickListener { host.launch(e, row) }
        row.setOnLongClickListener { host.appMenu(e, row, "fav"); true }
        return row
    }

    private fun iconView(e: AppEntry, place: String): View =
        AppIconView(context, look, host.repo, e) { host.live.badges[e.pkg] ?: 0 }.apply {
            contentDescription = look.name(e)
            setOnClickListener { host.launch(e, this) }
            setOnLongClickListener { host.appMenu(e, this, place); true }
        }

    fun refreshBadges() {
        renderApps()
    }

    // ---------- gestos ----------

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { host.gesture(prefs.gestureDoubleTap, null); return true }
        override fun onLongPress(e: MotionEvent) { if (!swiping) host.gesture(prefs.gestureLong, this@HomeView) }
    })

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiping = false }
            MotionEvent.ACTION_MOVE -> if (abs(e.y - downY) > slop || abs(e.x - downX) > slop) { swiping = true; return true }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        taps.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiping = false }
            MotionEvent.ACTION_MOVE -> if (abs(e.y - downY) > slop || abs(e.x - downX) > slop) swiping = true
            MotionEvent.ACTION_UP -> if (swiping) {
                swiping = false
                val dx = e.x - downX; val dy = e.y - downY
                val id = if (abs(dy) >= abs(dx)) { if (dy < 0) prefs.gestureUp else prefs.swipeDown }
                    else { if (dx < 0) prefs.gestureLeft else prefs.gestureRight }
                host.gesture(id, null)
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }
}
