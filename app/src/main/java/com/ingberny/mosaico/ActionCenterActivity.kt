package com.ingberny.mosaico

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

/** Centro de actividades a pantalla completa, para abrirlo desde la barra de estado en cualquier app. */
class ActionCenterActivity : Activity(), TileHost {

    override val prefs: Prefs by lazy { Prefs(this) }
    override val repo: AppRepo by lazy { AppRepo(this, prefs) }
    override lateinit var palette: Palette
    override val live = LiveState()
    override fun backdrop(): Bitmap? = null

    private lateinit var center: ActionCenter
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = prefs.palette()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            live.batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            live.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }

        val root = FrameLayout(this)
        center = ActionCenter(this, this)
        center.onClosed = { finishQuietly() }
        root.addView(center, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                center.setInsets(b.top, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                center.setInsets(ins.systemWindowInsetTop, ins.systemWindowInsetBottom)
            }
            ins
        }
        setContentView(root)
        root.post { center.open() }
    }

    private fun finishQuietly() {
        if (closing) return
        closing = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Usado por compatibilidad")
    override fun onBackPressed() { if (center.isOpen) center.close() else finishQuietly() }

    /** Si se abrió otra app (por ejemplo, al tocar una notificación), el panel se cierra. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finishQuietly()
    }
}
