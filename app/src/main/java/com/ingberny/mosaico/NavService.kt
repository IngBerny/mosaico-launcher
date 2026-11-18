package com.ingberny.mosaico

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * "Tema" de Mosaico para todo el teléfono, con un servicio de accesibilidad:
 *  - barra de estado estilo W10M encima de la de Android (deslizar abre el Centro de actividades),
 *  - barra de navegación de Mosaico encima de la de Android,
 *  - pantalla de bloqueo de Mosaico al encender la pantalla.
 * Android no permite quitar sus barras ni su bloqueo; este servicio solo los tapa.
 */
class NavService : AccessibilityService() {

    companion object {
        @Volatile var instance: NavService? = null

        fun isEnabled(ctx: Context): Boolean {
            val am = ctx.getSystemService(AccessibilityManager::class.java) ?: return false
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo.serviceInfo.packageName == ctx.packageName &&
                    it.resolveInfo.serviceInfo.name == NavService::class.java.name
            }
        }

        /** 0 = 3 botones, 1 = 2 botones, 2 = gestos. */
        fun navigationMode(ctx: Context): Int =
            try { Settings.Secure.getInt(ctx.contentResolver, "navigation_mode", 0) } catch (e: Exception) { 0 }

        private val OWN_FULLSCREEN = setOf(LockActivity::class.java.name, ActionCenterActivity::class.java.name)
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    private var nav: NavBarView? = null
    private var navLp: WindowManager.LayoutParams? = null
    private var status: StatusBarView? = null
    private var statusLp: WindowManager.LayoutParams? = null

    private var navHiddenByApp = false
    private var statusHiddenByApp = false
    private var screenOn = true
    private var foregroundPkg = ""
    private var foregroundCls = ""

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        nav?.applyTheme(prefs); status?.applyTheme(prefs); updateVisibility()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; maybeShowLock() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> screenOn = true
            }
            status?.refresh()
            updateVisibility()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (screenOn) status?.refresh()
            main.postDelayed(this, 15_000)
        }
    }
    private val notifListener: () -> Unit = { status?.refresh() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        NotifService.addListener(notifListener)
        addBars()
        main.postDelayed(ticker, 15_000)
    }

    // ---------- medidas ----------

    private fun landscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun navHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val h = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
                if (h > 0 || landscape()) return h
            } catch (e: Exception) { }
        }
        if (landscape()) return 0
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun statusHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val h = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                if (h > 0) return h
            } catch (e: Exception) { }
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun overlayParams(height: Int, gravity: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, height.coerceAtLeast(1),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        this.gravity = gravity
        if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    // ---------- barras ----------

    private fun addBars() {
        removeBars()
        val n = NavBarView(this,
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onSearch = { try { startActivity(NavBarView.searchIntent(this)) } catch (e: Exception) { } },
            onLongBack = { performGlobalAction(GLOBAL_ACTION_RECENTS) },
            onLongHome = { try { startActivity(NavBarView.assistIntent()) } catch (e: Exception) { } },
        )
        val nlp = overlayParams(navHeight(), Gravity.BOTTOM)
        // Si una app pasa a pantalla completa (videos, juegos), Android esconde sus barras y nosotros las nuestras.
        n.setOnApplyWindowInsetsListener { _, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val navHidden = !ins.isVisible(WindowInsets.Type.navigationBars())
                val statusHidden = !ins.isVisible(WindowInsets.Type.statusBars())
                if (navHidden != navHiddenByApp || statusHidden != statusHiddenByApp) {
                    navHiddenByApp = navHidden; statusHiddenByApp = statusHidden; updateVisibility()
                }
            }
            ins
        }
        val s = StatusBarView(this, opaque = true) { openActionCenter() }
        s.applyTheme(prefs)
        val slp = overlayParams(statusHeight(), Gravity.TOP)
        try { wm.addView(n, nlp); nav = n; navLp = nlp } catch (e: Exception) { nav = null }
        try { wm.addView(s, slp); status = s; statusLp = slp } catch (e: Exception) { status = null }
        updateVisibility()
    }

    private fun removeBars() {
        nav?.let { try { wm.removeViewImmediate(it) } catch (e: Exception) { } }
        status?.let { try { wm.removeViewImmediate(it) } catch (e: Exception) { } }
        nav = null; status = null
    }

    private fun setShown(v: View?, lp: WindowManager.LayoutParams?, show: Boolean, height: Int) {
        if (v == null || lp == null) return
        // Se deja la vista (transparente) para seguir recibiendo los cambios de pantalla completa.
        v.alpha = if (show) 1f else 0f
        val flags = if (show) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (flags != lp.flags || (height > 0 && lp.height != height)) {
            lp.flags = flags
            if (height > 0) lp.height = height
            try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }
        }
    }

    private fun updateVisibility() {
        if (!::prefs.isInitialized) return
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val ownFull = foregroundPkg == packageName && foregroundCls in OWN_FULLSCREEN
        val base = screenOn && !locked && !ownFull

        val nh = navHeight()
        val gestures = navigationMode(this) == 2
        setShown(nav, navLp, base && prefs.navSystem && !navHiddenByApp && !gestures && nh > 0, nh)

        val sh = statusHeight()
        setShown(status, statusLp, base && prefs.statusSystem && !statusHiddenByApp && sh > 0, sh)
    }

    private fun openActionCenter() {
        try {
            startActivity(Intent(this, ActionCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (e: Exception) { }
    }

    // ---------- pantalla de bloqueo ----------

    private fun maybeShowLock() {
        if (!prefs.lockEnabled) return
        // No la mostramos durante una llamada (la pantalla se apaga al acercar el teléfono a la cara).
        val audio = getSystemService(AudioManager::class.java)
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == AudioManager.MODE_RINGTONE) return
        try {
            startActivity(Intent(this, LockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        } catch (e: Exception) { }
    }

    // ---------- eventos ----------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPkg = event.packageName?.toString() ?: ""
            foregroundCls = event.className?.toString() ?: ""
        }
        updateVisibility()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        main.removeCallbacks(ticker)
        removeBars()
        NotifService.removeListener(notifListener)
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        if (::prefs.isInitialized) prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }
}
