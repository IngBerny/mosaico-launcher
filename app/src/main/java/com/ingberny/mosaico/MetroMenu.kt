package com.ingberny.mosaico

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Menú contextual: una tarjeta redondeada que aparece junto al elemento pulsado. */
class MetroMenu(ctx: Context, private val pal: Palette) : FrameLayout(ctx) {

    private val card = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(pal.chrome); cornerRadius = dp(20f)
        }
        setPadding(0, dpi(8), 0, dpi(8))
        elevation = dp(8f)
    }

    init {
        setBackgroundColor(withAlpha(Color.BLACK, if (pal.dark) 140 else 90))
        isClickable = true
        setOnClickListener { dismiss() }
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dpi(16), 0, dpi(16), 0) })
        card.isClickable = true
        visibility = View.GONE
    }

    val isOpen get() = visibility == View.VISIBLE

    fun show(anchor: View, items: List<Pair<String, () -> Unit>>) {
        card.removeAllViews()
        for ((title, action) in items) {
            card.addView(context.label(title, 17f, pal.fg, Fonts.regular).apply {
                setPadding(dpi(22), dpi(13), dpi(22), dpi(13))
                setOnClickListener { dismiss(); action() }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        visibility = View.VISIBLE
        alpha = 0f
        anim().alpha(1f).setDuration(120).start()
        post {
            val a = IntArray(2); anchor.getLocationOnScreen(a)
            val me = IntArray(2); getLocationOnScreen(me)
            val below = (a[1] - me[1] + anchor.height).toFloat()
            val y = if (below + card.height > height - dp(16f)) (a[1] - me[1] - card.height).toFloat() else below
            card.translationY = y.coerceIn(0f, maxOf(0f, height - card.height.toFloat()))
            card.pivotY = if (y < below) card.height.toFloat() else 0f
            card.scaleY = 0.85f
            card.anim().scaleY(1f).setDuration(160).start()
        }
    }

    fun dismiss() {
        if (!isOpen) return
        anim().alpha(0f).setDuration(100).withEndAction { visibility = View.GONE }.start()
    }
}
