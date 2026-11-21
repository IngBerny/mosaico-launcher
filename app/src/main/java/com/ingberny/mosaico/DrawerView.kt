package com.ingberny.mosaico

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Cajón de apps estilo Android: sube desde abajo, búsqueda arriba, apps en lista o en cuadrícula.
 * Se cierra deslizando hacia abajo cuando la lista está al principio.
 */
class DrawerView(ctx: Context, private val host: HomeHost) : FrameLayout(ctx) {

    private val prefs = host.prefs
    private val pal = host.palette
    private val look = host.look

    // Colores del cajón (Personalizar → Cajón de apps → Colores).
    private val bgBase = when (prefs.drawerBgMode) {
        1 -> android.graphics.Color.BLACK; 2 -> android.graphics.Color.WHITE; 3 -> prefs.drawerBgColor; else -> pal.bg
    }
    private val textColor = when (prefs.drawerTextMode) {
        1 -> android.graphics.Color.WHITE; 2 -> android.graphics.Color.BLACK; 3 -> prefs.drawerTextColor
        else -> if (prefs.drawerBgMode == 0) pal.fg else readableOn(bgBase)
    }
    private val subColor = withAlpha(textColor, 150)
    private val accent = if (prefs.drawerAccentMode == 1) prefs.drawerAccentColor else pal.accent
    private val pillRadius = dp(prefs.drawerSearchRadius.coerceIn(0, 30).toFloat())

    /** Se llama al abrir (true) y al empezar a cerrar (false). */
    var onShow: ((Boolean) -> Unit)? = null

    private val search = EditText(ctx).apply {
        hint = "Buscar apps"
        setHintTextColor(subColor)
        setTextColor(textColor)
        typeface = look.face
        textSize = 18f
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO
        background = null
        setPadding(dpi(22), dpi(12), dpi(22), dpi(12))
    }
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = object : ScrollView(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop * 2
        private var y0 = 0f
        private var pulling = false
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.y; pulling = false }
                MotionEvent.ACTION_MOVE -> if (scrollY == 0 && e.y - y0 > slop) { pulling = true; return true }
            }
            return super.onInterceptTouchEvent(e)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (pulling) {
                if (e.actionMasked == MotionEvent.ACTION_MOVE) this@DrawerView.translationY = maxOf(0f, e.y - y0)
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pulling = false
                    if (e.y - y0 > height * 0.18f) close() else this@DrawerView.anim().translationY(0f).setDuration(160).start()
                }
                return true
            }
            return super.onTouchEvent(e)
        }
    }

    private var shown: List<AppEntry> = emptyList()
    private val partial = prefs.drawerHeight < 100
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private var category = -100   // -100 = todas

    init {
        val panelColor = withAlpha(bgBase, prefs.drawerOpacity.coerceIn(0, 100) * 255 / 100)
        if (partial) {
            // Tarjeta más pequeña abajo; tocar fuera la cierra.
            setBackgroundColor(withAlpha(android.graphics.Color.BLACK, 90))
            setOnClickListener { close() }
            col.isClickable = true
            col.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panelColor)
                val r = dp(prefs.drawerCorner.coerceIn(0, 48).toFloat())
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        } else {
            setBackgroundColor(panelColor)
            isClickable = true
        }
        // Asa para indicar que se puede bajar.
        if (prefs.drawerHandle) col.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(withAlpha(textColor, 80)); cornerRadius = dp(2f) }
        }, LinearLayout.LayoutParams(dpi(36), dpi(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpi(10) })
        // Barra de búsqueda redondeada (píldora por defecto).
        val bar = FrameLayout(ctx).apply {
            background = when (prefs.drawerSearchStyle) {
                1 -> android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = pillRadius; setStroke(dpi(1).coerceAtLeast(2), withAlpha(textColor, 90))
                }
                2 -> null
                else -> android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = pillRadius
                    setColor(if (prefs.drawerSearchMode == 1) prefs.drawerSearchColor else withAlpha(textColor, 26))
                }
            }
        }
        val searchRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(GlyphView(ctx, G.SEARCH, subColor, 0.5f), LinearLayout.LayoutParams(dpi(44), dpi(44)).apply { leftMargin = dpi(8) })
        search.setPadding(dpi(2), dpi(12), dpi(20), dpi(12))
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(searchRow, LayoutParams(-1, -2))
        if (prefs.drawerSearchStyle != 3)
            col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(16), dpi(14), dpi(16), dpi(8)) })
        if (prefs.drawerCategories) {
            val hs = android.widget.HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
            hs.addView(chips)
            col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(12), dpi(2), dpi(12), dpi(4)) })
        }
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(-1, -2))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(col, LayoutParams(-1, -1, Gravity.BOTTOM))
        visibility = View.GONE

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render() }
        })
        search.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) { launchFirst(); true } else false
        }
    }

    val isOpen get() = visibility == View.VISIBLE

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (partial) post {
            col.layoutParams = (col.layoutParams as LayoutParams).apply { height = h * prefs.drawerHeight.coerceIn(40, 100) / 100 }
        }
    }

    fun setInsets(top: Int, bottom: Int) {
        setPadding(0, if (partial) 0 else top, 0, 0)
        list.setPadding(dpi(8), dpi(4), dpi(8), bottom + dpi(24))
    }

    fun open(withKeyboard: Boolean = prefs.drawerKeyboard) {
        render()
        visibility = View.VISIBLE
        onShow?.invoke(true)
        translationY = height.toFloat().takeIf { it > 0 } ?: dp(600f)
        alpha = 0.6f
        anim().translationY(0f).alpha(1f).setDuration(220).start()
        if (withKeyboard) focusSearch()
    }

    fun close() {
        if (!isOpen) return
        hideKeyboard()
        onShow?.invoke(false)
        anim().translationY(height.toFloat()).alpha(0.6f).setDuration(200).withEndAction {
            visibility = View.GONE; translationY = 0f; search.setText(""); scroll.scrollTo(0, 0)
        }.start()
    }

    fun focusSearch() {
        search.requestFocus()
        post { context.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    private fun launchFirst() {
        val q = search.text.toString().trim()
        val first = shown.firstOrNull()
        if (first != null) { host.launch(first, search); return }
        if (q.isNotEmpty()) webSearch(q)
    }

    private fun webSearch(q: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) { }
        }
    }

    /** Vuelve a dibujar la lista (al abrir, al buscar o cuando cambian las apps). */
    fun render() {
        list.removeAllViews()
        val q = AppEntry.normalize(search.text.toString().trim())
        val all = host.repo.apps
        renderChips(all)
        val filtered = all.filter { q.isEmpty() || AppEntry.normalize(look.name(it)).contains(q) || it.sortKey.contains(q) }
            .filter { category == -100 || cat(it) == category }
        shown = when (prefs.drawerSort) {
            1 -> filtered.sortedByDescending { it.installTime }
            2 -> filtered.sortedWith(compareBy({ hueKey(it) }, { AppEntry.normalize(look.name(it)) }))
            else -> filtered.sortedBy { AppEntry.normalize(look.name(it)) }
        }
        if (q.isNotEmpty() && prefs.drawerAutoLaunch && shown.size == 1) { host.launch(shown[0], search); return }

        if (shown.isEmpty()) {
            val raw = search.text.toString().trim()
            list.addView(context.label("Buscar \"$raw\" en la web", 17f, accent, look.face).apply {
                setPadding(dpi(16), dpi(18), dpi(16), dpi(18))
                setOnClickListener { webSearch(raw) }
            })
            return
        }
        if (prefs.drawerStyle == 1) renderGrid() else renderList(q.isEmpty() && prefs.drawerSort == 0)
    }

    private fun badge(e: AppEntry) = host.live.badges[e.pkg] ?: 0

    private fun renderList(withLetters: Boolean) {
        var last = ""
        for (e in shown) {
            val name = look.name(e)
            val letter = AppEntry.normalize(name).take(1).uppercase().let { if (it.isEmpty() || !it[0].isLetter()) "#" else it }
            if (withLetters && prefs.drawerLetters && letter != last) {
                last = letter
                list.addView(context.label(letter, 13f, accent, Fonts.medium).apply {
                    setPadding(dpi(16), dpi(16), dpi(16), dpi(4))
                })
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(12), dpi(7), dpi(12), dpi(7))
            }
            row.setPadding(dpi(12), dpi(prefs.drawerRowGap / 2 + 2), dpi(12), dpi(prefs.drawerRowGap / 2 + 2))
            if (prefs.drawerIcons) row.addView(AppIconView(context, look, host.repo, e) { badge(e) },
                LinearLayout.LayoutParams(dpi(prefs.drawerIconSize - 8), dpi(prefs.drawerIconSize - 8)).apply { rightMargin = dpi(16) })
            row.addView(context.label(name, prefs.drawerLabelSize + 5f, textColor, look.labelFace), LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener { host.launch(e, row) }
            row.setOnLongClickListener { host.appMenu(e, row, "drawer"); true }
            list.addView(row)
        }
    }

    private fun renderGrid() {
        val cols = prefs.drawerColumns.coerceIn(3, 7)
        shown.chunked(cols).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val e = chunk.getOrNull(i)
                if (e == null) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                row.addView(appCell(context, host, e, prefs.drawerIconSize, prefs.drawerLabelSize, prefs.drawerLabels,
                    textColor, false, "drawer"), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(0, dpi(prefs.drawerRowGap / 2), 0, dpi(prefs.drawerRowGap / 2))
                })
            }
            list.addView(row)
        }
    }

    // ---------- categorías, como en POCO Launcher ----------

    private fun cat(e: AppEntry): Int = e.info.applicationInfo.category

    private fun catName(c: Int): String? = when (c) {
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Juegos"
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Música"
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Fotos"
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "Noticias"
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Mapas"
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productividad"
        else -> null
    }

    private fun renderChips(all: List<AppEntry>) {
        if (!prefs.drawerCategories) return
        chips.removeAllViews()
        val present = all.map { cat(it) }.filter { catName(it) != null }.distinct().sortedBy { catName(it) }
        if (category != -100 && category !in present) category = -100
        fun chip(text: String, value: Int) {
            val on = category == value
            chips.addView(context.label(text, 14f, if (on) readableOn(accent) else textColor, look.labelFace).apply {
                setPadding(dpi(14), dpi(7), dpi(14), dpi(7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (on) accent else withAlpha(textColor, 22)); cornerRadius = minOf(pillRadius, dp(18f))
                }
                setOnClickListener { category = value; render() }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8) })
        }
        chip("Todas", -100)
        present.forEach { c -> chip(catName(c) ?: "", c) }
    }

    /** Agrupa por color: tono del color principal del ícono (los grises al final). */
    private fun hueKey(e: AppEntry): Float {
        val c = host.repo.color(e) ?: return 999f
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        return if (hsv[1] < 0.2f) 400f + hsv[2] else hsv[0]
    }
}
