package com.ingberny.mosaico

import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.MotionEvent
import android.view.View

/** Lo que está sonando (Spotify, YouTube Music, etc.). Necesita el acceso a notificaciones. */
class MediaInfo(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val controller: MediaController,
)

object Media {
    fun current(ctx: Context): MediaInfo? = try {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val list = msm.getActiveSessions(ComponentName(ctx, NotifService::class.java))
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || md == null || title.isNullOrBlank()) null
        else MediaInfo(
            title,
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            c.playbackState?.state == PlaybackState.STATE_PLAYING,
            c,
        )
    } catch (e: Exception) { null }
}

/** Controles finos: anterior, reproducir/pausar y siguiente. */
class MediaControlsView(ctx: Context, private val color: Int, private val onChanged: () -> Unit) : View(ctx) {
    var info: MediaInfo? = null
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = h * 0.32f
        val cy = h / 2f
        p.color = color
        p.style = Paint.Style.FILL
        // Anterior
        val x1 = w / 6f
        tri(c, x1 + s * 0.3f, cy, -s); c.drawRect(x1 - s * 0.55f, cy - s * 0.6f, x1 - s * 0.4f, cy + s * 0.6f, p)
        // Reproducir / pausa
        val x2 = w / 2f
        if (info?.playing == true) {
            c.drawRect(x2 - s * 0.45f, cy - s * 0.65f, x2 - s * 0.15f, cy + s * 0.65f, p)
            c.drawRect(x2 + s * 0.15f, cy - s * 0.65f, x2 + s * 0.45f, cy + s * 0.65f, p)
        } else tri(c, x2 - s * 0.35f, cy, s * 1.1f)
        // Siguiente
        val x3 = w * 5f / 6f
        tri(c, x3 - s * 0.3f, cy, s); c.drawRect(x3 + s * 0.4f, cy - s * 0.6f, x3 + s * 0.55f, cy + s * 0.6f, p)
    }

    /** Triángulo con la base en x y la punta hacia la derecha (s > 0) o la izquierda (s < 0). */
    private fun tri(c: Canvas, x: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(x, cy - kotlin.math.abs(s) * 0.6f)
        path.lineTo(x + s * 0.9f, cy)
        path.lineTo(x, cy + kotlin.math.abs(s) * 0.6f)
        path.close()
        c.drawPath(path, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val t = info?.controller?.transportControls ?: return true
            when ((e.x / width * 3).toInt().coerceIn(0, 2)) {
                0 -> t.skipToPrevious()
                1 -> if (info?.playing == true) t.pause() else t.play()
                else -> t.skipToNext()
            }
            postDelayed({ onChanged() }, 450)
        }
        return true
    }
}
