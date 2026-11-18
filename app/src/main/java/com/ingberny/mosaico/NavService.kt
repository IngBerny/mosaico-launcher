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
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Dibuja la barra de Mosaico encima de la barra de navegación de Android, en todas las apps.
 * Android no permite quitar su barra, pero un servicio de accesibilidad sí puede taparla
 * y ejecutar las acciones Atrás / Inicio / Recientes.
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
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var bar: NavBarView? = null
    private var params: WindowManager.LayoutParams? = null
    private var systemHidesBars = false
    private var screenOn = true

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> bar?.applyTheme(prefs) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            updateVisibility()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, f)
        addBar()
    }

    private fun navHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val h = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
                if (h > 0) return h
                // Barra lateral (horizontal) o sin barra: no mostramos nada.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return 0
            } catch (e: Exception) { }
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return 0
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun addBar() {
        removeBar()
        val v = NavBarView(this,
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onSearch = { try { startActivity(NavBarView.searchIntent(this)) } catch (e: Exception) { } },
            onLongBack = { performGlobalAction(GLOBAL_ACTION_RECENTS) },
            onLongHome = { try { startActivity(NavBarView.assistIntent()) } catch (e: Exception) { } },
        )
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, navHeight().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Si una app pasa a pantalla completa (videos, juegos), Android esconde su barra y nosotros la nuestra.
        v.setOnApplyWindowInsetsListener { _, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val hidden = !ins.isVisible(WindowInsets.Type.navigationBars())
                if (hidden != systemHidesBars) { systemHidesBars = hidden; updateVisibility() }
            }
            ins
        }
        try {
            wm.addView(v, lp)
            bar = v; params = lp
            updateVisibility()
        } catch (e: Exception) { bar = null }
    }

    private fun removeBar() {
        bar?.let { try { wm.removeViewImmediate(it) } catch (e: Exception) { } }
        bar = null
    }

    private fun updateVisibility() {
        val v = bar ?: return
        val lp = params ?: return
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val gestures = navigationMode(this) == 2
        val h = navHeight()
        val show = screenOn && !locked && !systemHidesBars && !gestures && h > 0
        // Se deja visible (transparente) para seguir recibiendo los cambios de pantalla completa.
        v.alpha = if (show) 1f else 0f
        val wantFlags = if (show) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (wantFlags != lp.flags || (h > 0 && lp.height != h)) {
            lp.flags = wantFlags
            if (h > 0) lp.height = h
            try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { updateVisibility() }
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        removeBar()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
        if (::prefs.isInitialized) prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }
}
