package com.ingberny.mosaico

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Centro de actividades: acciones rápidas y notificaciones. */
class ActionCenter(ctx: Context, private val host: TileHost) : FrameLayout(ctx) {

    private class Toggle(val label: String, val glyph: G, val state: () -> Boolean, val action: () -> Unit)

    private val pal = host.palette
    private val prefs = host.prefs
    private val panelColor = when (prefs.acBg) { 1 -> Color.BLACK; 2 -> prefs.acBgCustom; else -> pal.bg }
    private val onColor = if (prefs.acOnMode == 1) prefs.acOnCustom else pal.accent
    private val textScale = prefs.acTextScale / 100f
    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx)
    private val clock = ctx.label("", 40f, pal.fg, Fonts.light)
    private val date = ctx.label("", 14f, pal.sub, Fonts.regular)
    private val battery = ctx.label("", 14f, pal.sub, Fonts.regular)
    private val togglesBox = LinearLayout(ctx)
    private val expandLabel = ctx.label("", 15f, pal.accent, Fonts.regular)
    private val notifBox = LinearLayout(ctx)
    private val handle = View(ctx)
    private var expanded = prefs.acStartExpanded
    private var torchOn = false
    private var torchId: String? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    /** Botones elegidos en Personalizar, en su orden. */
    private val toggles: List<Toggle> by lazy {
        val all = buildToggles()
        prefs.acToggles.mapNotNull { all[it] }
    }

    init {
        setBackgroundColor(withAlpha(panelColor, prefs.acOpacity.coerceIn(30, 100) * 255 / 100))
        isClickable = true
        visibility = View.GONE

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dpi(12), 0, dpi(12), dpi(24))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dpi(6), dpi(8), dpi(6), dpi(14))
            addView(clock)
            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(battery); addView(date)
            })
        }
        if (prefs.acHeader) content.addView(header)

        togglesBox.orientation = LinearLayout.VERTICAL
        content.addView(togglesBox)
        if (prefs.acBrightness) content.addView(brightnessRow())

        val links = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(6), dpi(10), dpi(6), dpi(10))
            addView(expandLabel.apply { setOnClickListener { expanded = !expanded; renderToggles() } },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (prefs.acAndroidLink) addView(ctx.label("Panel de Android", 15f, pal.sub, Fonts.regular).apply {
                setOnClickListener {
                    close()
                    val svc = NavService.instance
                    if (svc != null) svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    else try {
                        @Suppress("WrongConstant")
                        val sb = context.getSystemService("statusbar")
                        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
                    } catch (e: Throwable) { }
                }
            })
        }
        content.addView(links)

        notifBox.orientation = LinearLayout.VERTICAL
        content.addView(notifBox)

        scroll.addView(content)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { bottomMargin = dpi(36) })

        handle.setBackgroundColor(pal.accent)
        handle.setOnClickListener { close() }
        addView(handle, LayoutParams(LayoutParams.MATCH_PARENT, dpi(36), Gravity.BOTTOM))
        addView(GlyphView(ctx, G.CHEVRON_UP, Color.WHITE, 0.6f).apply { isClickable = false },
            LayoutParams(dpi(36), dpi(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            torchId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) { }
    }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; if (isOpen) renderToggles() }
        }
    }
    private val notifCb: () -> Unit = { if (isOpen) renderNotifications() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotifService.addListener(notifCb)
        try { context.getSystemService(CameraManager::class.java).registerTorchCallback(torchCb, Handler(Looper.getMainLooper())) } catch (e: Exception) { }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotifService.removeListener(notifCb)
        try { context.getSystemService(CameraManager::class.java).unregisterTorchCallback(torchCb) } catch (e: Exception) { }
    }

    val isOpen get() = visibility == View.VISIBLE

    private var bottomInset = 0
    fun setInsets(top: Int, bottom: Int) {
        content.setPadding(dpi(12), top + dpi(4), dpi(12), dpi(24))
        bottomInset = bottom
        (scroll.layoutParams as LayoutParams).bottomMargin = dpi(36) + bottom
        (handle.layoutParams as LayoutParams).height = dpi(36) + bottom
        requestLayout()
    }

    fun open() {
        if (isOpen) return
        refresh()
        visibility = View.VISIBLE
        translationY = -(if (height > 0) height else resources.displayMetrics.heightPixels).toFloat()
        anim().translationY(0f).setDuration(260).start()
    }

    /** Se llama cuando termina de cerrarse (la versión de pantalla completa cierra su actividad). */
    var onClosed: (() -> Unit)? = null

    fun close() {
        if (!isOpen) return
        anim().translationY(-height.toFloat()).setDuration(220).withEndAction {
            visibility = View.GONE
            onClosed?.invoke()
        }.start()
    }

    fun refresh() {
        updateClock()
        renderToggles()
        renderNotifications()
    }

    fun updateClock() {
        val now = Date()
        clock.text = android.text.format.DateFormat.getTimeFormat(context).format(now)
        date.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now).capFirst()
        battery.text = if (host.live.batteryPct >= 0) "${host.live.batteryPct}%" + if (host.live.charging) " ⚡" else "" else ""
    }

    // ---------- cerrar deslizando hacia arriba ----------

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - downY
                val atBottom = !scroll.canScrollVertically(1)
                if (dy < -slop * 4 && abs(dy) > abs(e.x - downX) * 1.5f && atBottom) { close(); return true }
            }
        }
        return false
    }

    // ---------- acciones rápidas ----------

    private fun start(i: Intent) {
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
            Toast.makeText(context, "No disponible en este teléfono", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildToggles(): Map<String, Toggle> {
        val ctx = context
        val base = listOf(
            Toggle("Wi-Fi", G.WIFI, {
                try { ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled } catch (e: Exception) { false }
            }) { start(Intent(Settings.Panel.ACTION_WIFI)) },
            Toggle("Bluetooth", G.BLUETOOTH, {
                try { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (e: Exception) { false }
            }) { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            Toggle("Linterna", G.FLASHLIGHT, { torchOn }) {
                val id = torchId
                if (id == null) Toast.makeText(ctx, "Este teléfono no tiene linterna", Toast.LENGTH_SHORT).show()
                else try { ctx.getSystemService(CameraManager::class.java).setTorchMode(id, !torchOn) } catch (e: Exception) { }
            },
            Toggle("Rotación", G.ROTATION, {
                Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            }) {
                if (Settings.System.canWrite(ctx)) {
                    val on = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                    Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
                    renderToggles()
                } else {
                    Toast.makeText(ctx, "Permite a Mosaico modificar los ajustes del sistema", Toast.LENGTH_LONG).show()
                    start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
                }
            },
            Toggle("Ubicación", G.LOCATION, {
                try { ctx.getSystemService(LocationManager::class.java).isLocationEnabled } catch (e: Exception) { false }
            }) { start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            Toggle("Modo avión", G.AIRPLANE, {
                Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            }) { start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
            Toggle("Horas de silencio", G.QUIET, {
                ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            }) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.isNotificationPolicyAccessGranted) {
                    val on = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
                    nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    renderToggles()
                } else {
                    Toast.makeText(ctx, "Permite a Mosaico controlar No molestar", Toast.LENGTH_LONG).show()
                    start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            },
            Toggle("Ahorro de batería", G.BATTERY, {
                ctx.getSystemService(PowerManager::class.java).isPowerSaveMode
            }) { start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
            Toggle("Datos móviles", G.DATA, { false }) { start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            Toggle("Zona Wi-Fi", G.HOTSPOT, { false }) {
                val i = Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings"))
                try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
                    start(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            },
            Toggle("Cámara", G.CAMERA, { false }) { start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
            Toggle("Toda la configuración", G.SETTINGS, { false }) { start(Intent(Settings.ACTION_SETTINGS)) },
        )
        val map = LinkedHashMap<String, Toggle>()
        Prefs.DEFAULT_TOGGLES.forEachIndexed { i, id -> map[id] = base[i] }
        map["lock"] = Toggle("Bloquear", G.LOCK, { false }) {
            close()
            Handler(Looper.getMainLooper()).postDelayed({ SysActions.runSystem(ctx, SysActions.LOCK) }, 350)
        }
        map["screenshot"] = Toggle("Captura", G.SCREENSHOT, { false }) {
            close()
            // Se espera a que el panel se cierre para que no salga en la captura.
            Handler(Looper.getMainLooper()).postDelayed({ SysActions.screenshot(ctx) }, 700)
        }
        map["volume"] = Toggle("Volumen", G.VOLUME, { false }) {
            try {
                ctx.getSystemService(android.media.AudioManager::class.java).adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_SAME,
                    android.media.AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) { }
        }
        map["autobright"] = Toggle("Brillo automático", G.BRIGHTNESS, {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }) {
            if (canWriteSettings()) {
                val auto = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                renderToggles()
            }
        }
        return map
    }

    /** Pide el permiso de modificar ajustes si falta. */
    private fun canWriteSettings(): Boolean {
        if (Settings.System.canWrite(context)) return true
        Toast.makeText(context, "Permite a Mosaico modificar los ajustes del sistema", Toast.LENGTH_LONG).show()
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
        return false
    }

    /** Barra de brillo, como en el Centro de actividades de Windows 10 Mobile. */
    private fun brightnessRow(): View {
        val ctx = context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(4), dpi(10), dpi(4), dpi(2))
        }
        row.addView(GlyphView(ctx, G.BRIGHTNESS, pal.fg, 0.7f), LinearLayout.LayoutParams(dpi(30), dpi(30)))
        val bar = android.widget.SeekBar(ctx).apply {
            max = 255
            progress = try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 128 }
            progressTintList = android.content.res.ColorStateList.valueOf(onColor)
            thumbTintList = android.content.res.ColorStateList.valueOf(pal.fg)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(pal.toggleOff)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, fromUser: Boolean) {
                    if (!fromUser || !Settings.System.canWrite(ctx)) return
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(1))
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) { canWriteSettings() }
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { renderToggles() }
            })
        }
        row.addView(bar, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun renderToggles() {
        togglesBox.removeAllViews()
        val cols = prefs.acColumns.coerceIn(3, 6)
        val collapsed = cols * prefs.acRows.coerceIn(1, 4)
        val shown = if (expanded) toggles else toggles.take(collapsed)
        val tileH = if (prefs.acLabels) dpi(if (cols >= 5) 70 else 78) else dpi(if (cols >= 5) 52 else 60)
        shown.chunked(cols).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val t = chunk.getOrNull(i)
                val v: View = if (t == null) View(context) else ToggleView(context, t.label, t.glyph,
                    try { t.state() } catch (e: Exception) { false }).apply {
                    setOnClickListener { t.action(); postDelayed({ if (isOpen) renderToggles() }, 400) }
                }
                row.addView(v, LinearLayout.LayoutParams(0, tileH, 1f).apply { setMargins(dpi(2), dpi(2), dpi(2), dpi(2)) })
            }
            togglesBox.addView(row)
        }
        expandLabel.text = if (expanded) "Contraer ⌃" else "Expandir ⌄"
        expandLabel.visibility = if (toggles.size > collapsed) View.VISIBLE else View.INVISIBLE
    }

    private inner class ToggleView(ctx: Context, val text: String, val glyph: G, val on: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.color = if (on) onColor else pal.toggleOff
            val rad = dp(prefs.acRadius.toFloat())
            c.drawRoundRect(0f, 0f, w, h, rad, rad, p)
            val fg = if (on) (if (Color.luminance(onColor) > 0.6f) Color.BLACK else Color.WHITE) else pal.fg
            if (!prefs.acLabels) { Glyphs.draw(c, glyph, w / 2f, h / 2f, dp(24f), fg); return }
            Glyphs.draw(c, glyph, dp(20f), dp(20f), dp(22f), fg)
            tp.color = fg; tp.textSize = sp(12f); tp.typeface = Fonts.regular
            val s = TextUtils.ellipsize(text, tp, w - dp(12f), TextUtils.TruncateAt.END).toString()
            c.drawText(s, dp(7f), h - dp(9f), tp)
        }
    }

    // ---------- notificaciones ----------

    private fun renderNotifications() {
        notifBox.removeAllViews()
        if (!NotifService.hasAccess(context)) {
            notifBox.addView(context.label("Para ver tus notificaciones aquí y los contadores en los mosaicos, " +
                "activa el acceso a notificaciones de Mosaico.", 15f, pal.sub, Fonts.light).apply {
                setPadding(dpi(6), dpi(16), dpi(6), dpi(8))
            })
            notifBox.addView(context.label("Activar acceso", 16f, pal.accent, Fonts.regular).apply {
                setPadding(dpi(6), dpi(8), dpi(6), dpi(8))
                setOnClickListener { start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            })
            return
        }
        val items = NotifService.active().sortedByDescending { it.postTime }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(6), dpi(14), dpi(6), dpi(6))
            addView(context.label(if (items.isEmpty()) "Sin notificaciones nuevas" else "Notificaciones",
                16f, pal.sub, Fonts.regular), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (items.isNotEmpty()) addView(context.label("Borrar todo  ✕", 15f, pal.accent, Fonts.regular).apply {
                setOnClickListener { NotifService.dismissAll() }
            })
        }
        notifBox.addView(bar)

        val pm = context.packageManager
        fun appLabel(pkg: String) = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        if (!prefs.acGroupNotifs) {
            items.forEach { notifBox.addView(NotifRow(context, it, appLabel(it.packageName))) }
            return
        }
        items.groupBy { it.packageName }.forEach { (pkg, list) ->
            val appName = appLabel(pkg)
            notifBox.addView(context.label(appName, 19f * textScale, pal.fg, Fonts.medium).apply {
                setPadding(dpi(6), dpi(18), dpi(6), dpi(6))
            })
            notifBox.addView(View(context).apply { setBackgroundColor(pal.line) },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpi(1)).apply { setMargins(dpi(6), 0, dpi(6), dpi(4)) })
            list.forEach { notifBox.addView(NotifRow(context, it)) }
        }
    }

    private inner class NotifRow(ctx: Context, val sbn: StatusBarNotification, appName: String? = null) : LinearLayout(ctx) {
        private var sx = 0f
        private var swiping = false

        init {
            orientation = VERTICAL
            setPadding(dpi(6), dpi(8), dpi(6), dpi(8))
            val ex = sbn.notification.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (appName != null) addView(ctx.label(appName.uppercase(), 11f * textScale, onColor, Fonts.medium).apply {
                setPadding(0, 0, 0, dpi(3))
            })
            val top = LinearLayout(ctx).apply {
                orientation = HORIZONTAL
                addView(ctx.label(title, 16f * textScale, pal.fg, Fonts.regular).apply {
                    isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(ctx.label(android.text.format.DateFormat.getTimeFormat(ctx).format(Date(sbn.postTime)), 13f * textScale, pal.sub))
            }
            addView(top)
            if (body.isNotEmpty()) addView(ctx.label(body, 14f * textScale, pal.sub, Fonts.regular).apply {
                maxLines = 3; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dpi(3), 0, 0)
            })
            setOnClickListener { openNotification(sbn) }
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; swiping = false }
                MotionEvent.ACTION_MOVE -> if (abs(e.rawX - sx) > slop * 2) {
                    swiping = true; parent.requestDisallowInterceptTouchEvent(true); return true
                }
            }
            return false
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; super.onTouchEvent(e); return true }
                MotionEvent.ACTION_MOVE -> {
                    if (!swiping && abs(e.rawX - sx) > slop * 2) { swiping = true; parent.requestDisallowInterceptTouchEvent(true) }
                    if (swiping) { translationX = e.rawX - sx; alpha = 1f - abs(translationX) / width; return true }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (swiping) {
                    swiping = false
                    if (abs(translationX) > width * 0.35f) {
                        anim().translationX(if (translationX > 0) width.toFloat() else -width.toFloat()).alpha(0f).setDuration(150)
                            .withEndAction { NotifService.dismiss(sbn.key) }.start()
                    } else anim().translationX(0f).alpha(1f).setDuration(150).start()
                    return true
                }
            }
            return super.onTouchEvent(e)
        }
    }

    private fun openNotification(sbn: StatusBarNotification) {
        val pi: PendingIntent = sbn.notification.contentIntent ?: return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(context, 0, null, null, null, null, opts.toBundle())
            } else pi.send()
            if ((sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) NotifService.dismiss(sbn.key)
            close()
        } catch (e: Exception) { }
    }
}
