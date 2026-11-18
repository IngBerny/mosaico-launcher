package com.ingberny.mosaico

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/** Barra de navegación estilo Windows 10 Mobile: atrás, Inicio y buscar. */
class NavBarView(
    ctx: Context,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLongBack: (() -> Unit)? = null,
    onLongHome: (() -> Unit)? = null,
) : LinearLayout(ctx) {

    private val buttons = listOf(
        NavButton(ctx, G.ARROW_LEFT, onBack, onLongBack),
        NavButton(ctx, G.START, onHome, onLongHome),
        NavButton(ctx, G.SEARCH, onSearch, null),
    )

    init {
        orientation = HORIZONTAL
        buttons.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
        applyTheme(Prefs(ctx))
    }

    fun applyTheme(prefs: Prefs) {
        val pal = prefs.palette()
        val bg = when (prefs.navColor) {
            1 -> pal.bg
            2 -> pal.accent
            else -> Color.BLACK
        }
        setBackgroundColor(bg)
        val fg = if (prefs.navColor == 1 && !pal.dark) Color.BLACK else Color.WHITE
        buttons.forEach { it.color = fg; it.pressColor = withAlpha(fg, 50); it.invalidate() }
    }

    private class NavButton(ctx: Context, val glyph: G, val click: () -> Unit, val longClick: (() -> Unit)?) : View(ctx) {
        var color = Color.WHITE
        var pressColor = 0x33FFFFFF
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var down = false

        init {
            isClickable = true
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
            if (longClick != null) setOnLongClickListener { longClick.invoke(); true }
            contentDescription = when (glyph) { G.ARROW_LEFT -> "Atrás"; G.START -> "Inicio"; else -> "Buscar" }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { down = true; invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { down = false; invalidate() }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(c: Canvas) {
            if (down) { p.color = pressColor; c.drawCircle(width / 2f, height / 2f, height * 0.42f, p) }
            val s = minOf(height * 0.5f, dp(26f))
            Glyphs.draw(c, glyph, width / 2f, height / 2f, s, color, 1.1f)
        }
    }

    companion object {
        const val EXTRA_SEARCH = "mosaico.search"

        /** Abre la búsqueda de apps de Mosaico. */
        fun searchIntent(ctx: Context): Intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_SEARCH, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun assistIntent(): Intent = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
