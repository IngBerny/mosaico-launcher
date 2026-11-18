package com.ingberny.mosaico

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Página de Inicio: cuadrícula de mosaicos con desplazamiento vertical. */
class StartScreen(ctx: Context, host: TileHost, onAllApps: () -> Unit, onCustomize: () -> Unit) : FrameLayout(ctx) {

    val scroll = PullScrollView(ctx)
    val grid = TileGrid(ctx, host)
    private val column = LinearLayout(ctx)
    private val footer = LinearLayout(ctx)
    private val banner = LinearLayout(ctx)
    var onBannerClick: (() -> Unit)? = null

    init {
        val pal = host.palette
        column.orientation = LinearLayout.VERTICAL

        banner.orientation = LinearLayout.HORIZONTAL
        banner.gravity = Gravity.CENTER_VERTICAL
        banner.setBackgroundColor(pal.accent)
        banner.setPadding(dpi(14), dpi(12), dpi(10), dpi(12))
        banner.addView(ctx.label("Mosaico todavía no es tu pantalla de inicio", 14f, android.graphics.Color.WHITE),
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        banner.addView(ctx.label("Establecer", 14f, android.graphics.Color.WHITE, Fonts.medium).apply { setPadding(dpi(10), 0, dpi(4), 0) })
        banner.setOnClickListener { onBannerClick?.invoke() }
        banner.visibility = View.GONE
        column.addView(banner, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dpi(10), 0, dpi(10), dpi(10))
        })
        column.addView(grid, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(dpi(12), dpi(8), dpi(12), dpi(24))

        val all = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val circle = GlyphView(ctx, G.ARROW_RIGHT, pal.fg, 0.55f).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dpi(2), pal.fg)
                }
            }
            addView(circle, LinearLayout.LayoutParams(dpi(38), dpi(38)))
            addView(ctx.label("Todas las apps", 16f, pal.fg, Fonts.light).apply { setPadding(dpi(10), 0, 0, 0) })
            setOnClickListener { onAllApps() }
            setPadding(0, dpi(6), dpi(6), dpi(6))
        }
        footer.addView(all, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(ctx.label("Personalizar", 16f, pal.sub, Fonts.light).apply {
            setPadding(dpi(10), dpi(10), dpi(4), dpi(10))
            setOnClickListener { onCustomize() }
        })
        column.addView(footer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (!host.prefs.showFooter) footer.visibility = View.GONE

        scroll.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (host.prefs.bgMode == 1) grid.invalidateTiles()
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setInsets(top: Int, bottom: Int) {
        scroll.setPadding(0, top + dpi(8), 0, bottom)
    }

    fun showBanner(show: Boolean) { banner.visibility = if (show) View.VISIBLE else View.GONE }

    fun scrollToTop() { scroll.smoothScrollTo(0, 0) }
    fun scrollToBottom() { scroll.post { scroll.smoothScrollTo(0, column.height) } }
}
