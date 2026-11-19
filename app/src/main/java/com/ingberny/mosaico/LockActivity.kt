package com.ingberny.mosaico

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Pantalla de bloqueo al estilo Windows 10 Mobile. Se muestra encima del bloqueo de Android;
 * al deslizarla hacia arriba aparece el bloqueo real (PIN, patrón o huella), así que la seguridad no cambia.
 */
class LockActivity : Activity(), TileHost {

    override val prefs: Prefs by lazy { Prefs(this) }
    override val repo: AppRepo by lazy { AppRepo(this, prefs).also { it.load() } }
    override lateinit var palette: Palette
    override val live = LiveState()
    override fun backdrop(): Bitmap? = null

    private lateinit var root: FrameLayout
    private lateinit var sheet: FrameLayout
    private val time = lazy { label("", listOf(70f, 92f, 120f)[prefs.lockClockSize.coerceIn(0, 2)], Color.WHITE, Fonts.thin) }
    private val date = lazy { label("", 24f, Color.WHITE, Fonts.light) }
    private val event = lazy { label("", 16f, Color.WHITE, Fonts.regular) }
    private lateinit var notifRow: NotifIcons
    private var downY = 0f
    private var dragging = false
    private var finishing = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Desbloqueado con huella o cara: entramos directo.
                Intent.ACTION_USER_PRESENT -> if (getSystemService(KeyguardManager::class.java).isKeyguardSecure) finishLock()
                else -> updateClock()
            }
        }
    }
    private val notifListener: () -> Unit = { if (::notifRow.isInitialized) notifRow.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = prefs.palette()
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        sheet = FrameLayout(this)
        root.addView(sheet, FrameLayout.LayoutParams(-1, -1))

        // Fondo
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        loadBackground()?.let { img.setImageBitmap(it) } ?: img.setBackgroundColor(
            if (prefs.lockBg == 3) Color.BLACK else palette.accent)
        sheet.addView(img, FrameLayout.LayoutParams(-1, -1))
        sheet.addView(Scrim(this, prefs.lockDim), FrameLayout.LayoutParams(-1, -1))

        // Hora, fecha, próximo evento y notificaciones: abajo a la izquierda como en el original, o centrados arriba.
        val centered = prefs.lockClockPos == 1
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (centered) gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(24), if (centered) dpi(96) else 0, dpi(24), dpi(56))
        }
        col.addView(event.value.apply { maxLines = 2; setPadding(dpi(4), 0, 0, dpi(6)) })
        col.addView(time.value.apply { setPadding(0, 0, 0, 0) })
        col.addView(date.value.apply { setPadding(dpi(4), dpi(2), 0, dpi(18)) })
        if (!prefs.lockShowDate) date.value.visibility = View.GONE
        if (centered) listOf(event.value, time.value, date.value).forEach { it.gravity = Gravity.CENTER_HORIZONTAL }
        notifRow = NotifIcons(this)
        col.addView(notifRow, LinearLayout.LayoutParams(-1, dpi(40)))
        sheet.addView(col, FrameLayout.LayoutParams(-1, -2, if (centered) Gravity.TOP else Gravity.BOTTOM))
        sheet.addView(GlyphView(this, G.CHEVRON_UP, withAlpha(Color.WHITE, 180), 0.6f),
            FrameLayout.LayoutParams(dpi(36), dpi(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(14) })

        setContentView(root)
        hideBars()
        updateClock()
        loadEvent()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, f)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onResume() {
        super.onResume()
        NotifService.addListener(notifListener)
        updateClock()
        notifRow.refresh()
        hideBars()
        // Si el teléfono ya está desbloqueado (sin bloqueo o con huella), no estorbamos.
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardSecure && !km.isKeyguardLocked) finishLock()
    }

    override fun onPause() {
        super.onPause()
        NotifService.removeListener(notifListener)
    }

    private fun loadBackground(): Bitmap? {
        val name = when (prefs.lockBg) { 0 -> "backdrop.jpg"; 1 -> "lock.jpg"; else -> return null }
        val f = File(filesDir, name)
        return if (f.exists()) try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null } else null
    }

    private fun updateClock() {
        val now = Date()
        val tf = android.text.format.DateFormat.getTimeFormat(this).format(now)
        time.value.text = tf.replace(" a. m.", "").replace(" p. m.", "").replace(" AM", "").replace(" PM", "")
        date.value.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now).capFirst()
    }

    private fun loadEvent() {
        if (!prefs.lockShowEvent || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            event.value.visibility = View.GONE; return
        }
        Thread {
            val now = System.currentTimeMillis()
            val txt = try {
                val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(b, now); ContentUris.appendId(b, now + 86_400_000L)
                contentResolver.query(b.build(), arrayOf("title", "begin", "allDay"), null, null, "begin ASC")?.use { c ->
                    if (c.moveToFirst()) {
                        val t = c.getString(0) ?: ""
                        if (c.getInt(2) == 1) "$t\nTodo el día"
                        else "$t\n" + android.text.format.DateFormat.getTimeFormat(this).format(Date(c.getLong(1)))
                    } else null
                }
            } catch (e: Exception) { null }
            runOnUiThread {
                if (txt == null) event.value.visibility = View.GONE
                else { event.value.text = txt; event.value.visibility = View.VISIBLE }
            }
        }.start()
    }

    // ---------- deslizar hacia arriba para desbloquear ----------

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.rawY; dragging = true; sheet.animate().cancel() }
            MotionEvent.ACTION_MOVE -> if (dragging) sheet.translationY = min(0f, e.rawY - downY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                val dy = e.rawY - downY
                if (-dy > root.height * 0.22f) unlock()
                else sheet.anim().translationY(0f).setDuration(220).start()
            }
        }
        return true
    }

    private fun unlock() {
        sheet.animate().translationY(-root.height.toFloat()).setDuration(220)
            .setInterpolator(AccelerateInterpolator()).withEndAction {
                val km = getSystemService(KeyguardManager::class.java)
                if (km.isKeyguardLocked) {
                    km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() { finishLock() }
                        override fun onDismissCancelled() { sheet.anim().translationY(0f).setDuration(250).start() }
                        override fun onDismissError() { finishLock() }
                    })
                } else finishLock()
            }.start()
    }

    private fun finishLock() {
        if (finishing) return
        finishing = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Deprecated("Atrás no hace nada en la pantalla de bloqueo")
    override fun onBackPressed() { }

    // ---------- piezas ----------

    /** Oscurece la parte de abajo para que el texto se lea sobre cualquier foto. */
    private class Scrim(ctx: Context, private val dim: Int) : View(ctx) {
        private val p = Paint()
        private val flat = Paint().apply { color = withAlpha(Color.BLACK, dim.coerceIn(0, 80) * 255 / 100) }
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            p.shader = LinearGradient(0f, h * 0.4f, 0f, h.toFloat(), Color.TRANSPARENT, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        }
        override fun onDraw(c: Canvas) {
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flat)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        }
    }

    /** Íconos de las apps con notificaciones y su contador, como en el bloqueo de Windows Phone. */
    private inner class NotifIcons(ctx: Context) : View(ctx) {
        private var items: List<Pair<AppEntry, Int>> = emptyList()
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Fonts.light }

        fun refresh() {
            items = if (!prefs.lockNotifs) emptyList() else NotifService.counts().entries
                .mapNotNull { (pkg, n) -> repo.allApps.firstOrNull { it.pkg == pkg }?.let { it to n } }
                .sortedByDescending { it.second }.take(6)
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            var x = dp(4f)
            val s = height * 0.62f
            val cy = height / 2f
            tp.textSize = sp(20f)
            for ((e, n) in items) {
                repo.drawIcon(c, e, x + s / 2, cy, s, Color.WHITE)
                x += s + dp(6f)
                val t = if (n > 99) "99+" else n.toString()
                c.drawText(t, x, cy + tp.textSize * 0.35f, tp)
                x += tp.measureText(t) + dp(18f)
                if (x > width - s) break
            }
        }
    }
}
