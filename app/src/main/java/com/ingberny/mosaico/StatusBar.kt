package com.ingberny.mosaico

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import java.util.Date
import kotlin.math.abs

class StatusInfo(
    val signal: Int,      // 0..4, -1 = desconocido / sin SIM
    val wifi: Boolean,
    val airplane: Boolean,
    val quiet: Boolean,
    val battery: Int,
    val charging: Boolean,
    val time: String,
    val notifications: Int,
) {
    companion object {
        fun read(ctx: Context): StatusInfo {
            val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            val wifi = try {
                val cm = ctx.getSystemService(ConnectivityManager::class.java)
                cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (e: Exception) { false }
            val signal = try {
                val tm = ctx.getSystemService(TelephonyManager::class.java)
                if (tm.simState != TelephonyManager.SIM_STATE_READY) -1 else tm.signalStrength?.level ?: -1
            } catch (e: Exception) { -1 }
            val quiet = try {
                ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            } catch (e: Exception) { false }
            var pct = -1; var charging = false
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) pct = level * 100 / scale
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            val time = android.text.format.DateFormat.getTimeFormat(ctx).format(Date())
            return StatusInfo(signal, wifi, airplane, quiet, pct, charging, time, NotifService.active().size)
        }
    }
}

/**
 * Barra de estado al estilo Windows 10 Mobile: señal, Wi-Fi y modos a la izquierda;
 * batería y hora a la derecha. Tocarla o deslizarla hacia abajo abre el Centro de actividades.
 */
class StatusBarView(ctx: Context, private val opaque: Boolean, private val onOpen: () -> Unit) : View(ctx) {

    private var info: StatusInfo = StatusInfo.read(ctx)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textAlign = Paint.Align.RIGHT }
    private val r = RectF()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downY = 0f
    private var fired = false
    var fg = Color.WHITE
    var bg = Color.BLACK

    fun applyTheme(prefs: Prefs) {
        val pal = prefs.palette()
        bg = when (prefs.navColor) { 1 -> pal.bg; 2 -> pal.accent; else -> Color.BLACK }
        fg = if (prefs.navColor == 1 && !pal.dark) Color.BLACK else Color.WHITE
        invalidate()
    }

    fun refresh() { info = StatusInfo.read(context); invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; fired = false }
            MotionEvent.ACTION_MOVE -> if (!fired && e.y - downY > slop) { fired = true; onOpen() }
            MotionEvent.ACTION_UP -> if (!fired && abs(e.y - downY) < slop) { fired = true; onOpen() }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (opaque) c.drawColor(bg)
        // Los íconos van en la franja inferior de la barra (debajo de la cámara en pantallas con muesca).
        val band = minOf(h, dp(24f))
        val cy = h - band / 2
        val s = band * 0.62f
        var x = dp(10f)
        p.style = Paint.Style.FILL

        // Señal: 5 barras crecientes, como en Windows Phone.
        if (!info.airplane && info.signal >= 0) {
            val bw = s * 0.13f; val gap = s * 0.08f
            for (i in 0 until 5) {
                val bh = s * (0.3f + 0.175f * i)
                p.color = if (i <= info.signal) fg else withAlpha(fg, 70)
                r.set(x, cy + s / 2 - bh, x + bw, cy + s / 2)
                c.drawRect(r, p)
                x += bw + gap
            }
            x += dp(6f)
        }
        if (info.airplane) { Glyphs.draw(c, G.AIRPLANE, x + s / 2, cy, s, fg); x += s + dp(6f) }
        if (info.wifi) { Glyphs.draw(c, G.WIFI, x + s / 2, cy + s * 0.08f, s, fg); x += s + dp(6f) }
        if (info.quiet) { Glyphs.draw(c, G.QUIET, x + s / 2, cy, s * 0.9f, fg); x += s + dp(6f) }
        if (info.notifications > 0) {
            p.color = fg; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.3f)
            r.set(x, cy - s * 0.35f, x + s * 0.95f, cy + s * 0.3f); c.drawRect(r, p)
            p.style = Paint.Style.FILL
            tp.textAlign = Paint.Align.CENTER; tp.textSize = s * 0.55f; tp.color = fg
            c.drawText(if (info.notifications > 9) "9+" else info.notifications.toString(), x + s * 0.475f, cy + s * 0.17f, tp)
            x += s + dp(6f)
        }

        // Derecha: batería y hora.
        tp.textAlign = Paint.Align.RIGHT; tp.textSize = sp(13f); tp.color = fg
        val right = w - dp(10f)
        c.drawText(info.time, right, cy + tp.textSize * 0.36f, tp)
        val tw = tp.measureText(info.time)
        val bx2 = right - tw - dp(8f)
        val bw = s * 1.15f; val bh = s * 0.55f
        val bx1 = bx2 - bw
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.2f); p.color = fg
        r.set(bx1, cy - bh / 2, bx2, cy + bh / 2); c.drawRect(r, p)
        p.style = Paint.Style.FILL
        r.set(bx2, cy - bh / 5, bx2 + dp(2f), cy + bh / 5); c.drawRect(r, p)
        if (info.battery >= 0) {
            val inner = (bw - dp(4f)) * info.battery / 100f
            p.color = if (info.battery <= 15 && !info.charging) 0xFFE51400.toInt() else fg
            r.set(bx1 + dp(2f), cy - bh / 2 + dp(2f), bx1 + dp(2f) + inner, cy + bh / 2 - dp(2f)); c.drawRect(r, p)
        }
        if (info.charging) Glyphs.draw(c, G.BOLT, bx1 - s * 0.4f, cy, s * 0.7f, fg)
    }
}
