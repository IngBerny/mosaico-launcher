package com.ingberny.mosaico

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Cajón de apps estilo Android: sube desde abajo (siguiendo el dedo), búsqueda arriba, apps en lista o en cuadrícula.
 * Usa una lista que recicla filas, así que desplazarse es fluido aunque tengas cientos de apps.
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

    /** Curva "emphasized" de Material: arranca rápido y frena suave. */
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

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
        setPadding(dpi(2), dpi(12), dpi(20), dpi(12))
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop * 2
    private val list = object : ListView(ctx) {
        private var y0 = 0f
        private var pulling = false
        private var vt: VelocityTracker? = null
        private fun atTop() = !canScrollVertically(-1)
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.rawY; pulling = false }
                MotionEvent.ACTION_MOVE -> if (atTop() && e.rawY - y0 > slop) { pulling = true; return true }
            }
            return super.onInterceptTouchEvent(e)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!pulling) {
                if (e.actionMasked == MotionEvent.ACTION_MOVE && atTop() && e.rawY - y0 > slop) pulling = true
                else return super.onTouchEvent(e)
            }
            if (vt == null) vt = VelocityTracker.obtain()
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> this@DrawerView.translationY = maxOf(0f, e.rawY - y0)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    vt?.recycle(); vt = null
                    pulling = false
                    if (e.rawY - y0 > this@DrawerView.height * 0.2f || vy > 1500f) close() else settleOpen()
                }
            }
            return true
        }
    }

    // ---------- filas del cajón ----------

    private sealed class Row {
        class Header(val letter: String) : Row()
        class Apps(val apps: List<AppEntry>) : Row()
        class Web(val q: String) : Row()
    }

    private var rows: List<Row> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private val grid get() = prefs.drawerStyle == 1
    private val cols get() = if (grid) prefs.drawerColumns.coerceIn(3, 7) else 1

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(p: Int) = when (rows[p]) { is Row.Header -> 0; is Row.Apps -> 1; is Row.Web -> 2 }
        override fun areAllItemsEnabled() = false
        override fun isEnabled(p: Int) = false

        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View = when (val r = rows[p]) {
            is Row.Header -> ((convert as? TextView) ?: context.label("", 13f, accent, Fonts.medium).apply {
                setPadding(dpi(16), dpi(16), dpi(16), dpi(4))
            }).also { it.text = r.letter }
            is Row.Web -> ((convert as? TextView) ?: context.label("", 17f, accent, look.face).apply {
                setPadding(dpi(16), dpi(18), dpi(16), dpi(18))
            }).also { tv -> tv.text = "Buscar \"${r.q}\" en la web"; tv.setOnClickListener { webSearch(r.q) } }
            is Row.Apps -> {
                val row = (convert as? AppRow) ?: AppRow(context)
                row.bind(r.apps)
                row
            }
        }
    }

    /** Una fila con [cols] celdas (cuadrícula) o una sola app (lista). Se reutiliza al desplazarse. */
    private inner class AppRow(ctx: Context) : LinearLayout(ctx) {
        private val cells = ArrayList<Cell>()
        init {
            orientation = HORIZONTAL
            repeat(cols) {
                val c = Cell(ctx)
                cells += c
                addView(c, LayoutParams(0, -2, 1f))
            }
        }
        fun bind(apps: List<AppEntry>) {
            cells.forEachIndexed { i, c -> c.bind(apps.getOrNull(i)) }
        }
    }

    private inner class Cell(ctx: Context) : LinearLayout(ctx) {
        private var entry: AppEntry? = null
        private var icon: AppIconView? = null
        private val name: TextView
        init {
            if (grid) {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dpi(prefs.drawerRowGap / 2), 0, dpi(prefs.drawerRowGap / 2))
                name = ctx.label("", prefs.drawerLabelSize.toFloat(), textColor, look.labelFace).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    setPadding(dpi(2), dpi(5), dpi(2), 0)
                }
            } else {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(12), dpi(prefs.drawerRowGap / 2 + 2), dpi(12), dpi(prefs.drawerRowGap / 2 + 2))
                name = ctx.label("", prefs.drawerLabelSize + 5f, textColor, look.labelFace).apply { maxLines = 1 }
            }
            setOnClickListener { entry?.let { host.launch(it, this) } }
            setOnLongClickListener { entry?.let { host.appMenu(it, this, "drawer") }; entry != null }
        }

        fun bind(e: AppEntry?) {
            entry = e
            visibility = if (e == null) View.INVISIBLE else View.VISIBLE
            if (e == null) return
            val showIcon = grid || prefs.drawerIcons
            if (showIcon) {
                val iv = icon
                if (iv == null) {
                    val size = if (grid) dpi(prefs.drawerIconSize) else dpi(prefs.drawerIconSize - 8)
                    val v = AppIconView(context, look, host.repo, e) { badge(e) }
                    icon = v
                    addView(v, 0, LayoutParams(size, size).apply { if (!grid) rightMargin = dpi(16) })
                    if (grid && prefs.drawerLabels) addView(name, LayoutParams(-1, -2))
                    else if (!grid) addView(name, LayoutParams(0, -2, 1f))
                } else iv.bind(e) { badge(e) }
            } else if (name.parent == null) addView(name, LayoutParams(0, -2, 1f))
            name.text = look.name(e)
            contentDescription = look.name(e)
        }
    }

    // ---------- armado ----------

    private val partial = prefs.drawerHeight < 100
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private var category = -100   // -100 = todas
    private var dirty = true

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
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(searchRow, LayoutParams(-1, -2))
        if (prefs.drawerSearchStyle != 3)
            col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(16), dpi(14), dpi(16), dpi(8)) })
        if (prefs.drawerCategories) {
            val hs = android.widget.HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
            hs.addView(chips)
            col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(12), dpi(2), dpi(12), dpi(4)) })
        }
        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        list.selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.clipToPadding = false
        list.cacheColorHint = android.graphics.Color.TRANSPARENT
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView?, state: Int) {
                if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
            }
            override fun onScroll(v: AbsListView?, a: Int, b: Int, c: Int) {}
        })
        col.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
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

    /** Marca que las apps cambiaron: la lista se rehace al abrir. */
    fun markDirty() { dirty = true; if (isOpen) render() }

    private fun prepare() {
        if (dirty || search.text.isNotEmpty() || category != -100) { render(); dirty = false }
        visibility = View.VISIBLE
        onShow?.invoke(true)
    }

    private fun travel(): Float = (if (height > 0) height else resources.displayMetrics.heightPixels).toFloat()

    fun open(withKeyboard: Boolean = prefs.drawerKeyboard) {
        if (!isOpen) {
            prepare()
            translationY = travel()
        }
        alpha = 1f
        animate().translationY(0f).setInterpolator(ease).setDuration(300).withLayer().start()
        if (withKeyboard) focusSearch()
    }

    /** El cajón sigue el dedo mientras deslizas hacia arriba desde Inicio. [dy] = cuánto subió el dedo. */
    fun dragFromHome(dy: Float) {
        if (!isOpen) { prepare(); setLayerType(View.LAYER_TYPE_HARDWARE, null) }
        animate().cancel()
        translationY = (travel() - dy).coerceIn(0f, travel())
    }

    /** Al soltar: se abre si subió lo suficiente o con impulso; si no, vuelve a bajar. */
    fun releaseFromHome(dy: Float, vy: Float) {
        setLayerType(View.LAYER_TYPE_NONE, null)
        if (dy > travel() * 0.22f || vy < -1200f) {
            animate().translationY(0f).setInterpolator(ease).setDuration(260).withLayer().start()
            if (prefs.drawerKeyboard) focusSearch()
        } else close()
    }

    private fun settleOpen() {
        animate().translationY(0f).setInterpolator(ease).setDuration(200).withLayer().start()
    }

    fun close() {
        if (!isOpen) return
        hideKeyboard()
        onShow?.invoke(false)
        animate().translationY(travel()).setInterpolator(ease).setDuration(240).withLayer().withEndAction {
            visibility = View.GONE; translationY = 0f
            if (search.text.isNotEmpty() || category != -100) { category = -100; search.setText("") }
            list.setSelection(0)
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

    /** Arma las filas (al abrir, al buscar o cuando cambian las apps). Solo datos: las vistas se reciclan. */
    fun render() {
        val raw = search.text.toString().trim()
        val q = AppEntry.normalize(raw)
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

        val out = ArrayList<Row>()
        when {
            shown.isEmpty() -> out += Row.Web(raw)
            grid -> shown.chunked(cols).forEach { out += Row.Apps(it) }
            else -> {
                val letters = q.isEmpty() && prefs.drawerSort == 0 && prefs.drawerLetters
                var last = ""
                for (e in shown) {
                    if (letters) {
                        val letter = AppEntry.normalize(look.name(e)).take(1).uppercase()
                            .let { if (it.isEmpty() || !it[0].isLetter()) "#" else it }
                        if (letter != last) { last = letter; out += Row.Header(letter) }
                    }
                    out += Row.Apps(listOf(e))
                }
            }
        }
        rows = out
        adapter.notifyDataSetChanged()
    }

    private fun badge(e: AppEntry) = host.live.badges[e.pkg] ?: 0

    /** Redibuja solo los puntos de notificación visibles, sin rehacer la lista. */
    fun refreshBadges() {
        if (isOpen) list.invalidateViews()
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

    private var chipsKey = ""

    private fun renderChips(all: List<AppEntry>) {
        if (!prefs.drawerCategories) return
        val present = all.map { cat(it) }.filter { catName(it) != null }.distinct().sortedBy { catName(it) }
        if (category != -100 && category !in present) category = -100
        val key = present.joinToString(",") + "|" + category
        if (key == chipsKey) return
        chipsKey = key
        chips.removeAllViews()
        fun chip(text: String, value: Int) {
            val on = category == value
            chips.addView(context.label(text, 14f, if (on) readableOn(accent) else textColor, look.labelFace).apply {
                setPadding(dpi(14), dpi(7), dpi(14), dpi(7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (on) accent else withAlpha(textColor, 22)); cornerRadius = minOf(pillRadius, dp(18f))
                }
                setOnClickListener { category = value; render(); list.setSelection(0) }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8) })
        }
        chip("Todas", -100)
        present.forEach { c -> chip(catName(c) ?: "", c) }
    }

    /** Agrupa por color: tono del color principal del ícono (los grises al final). */
    private val hueCache = HashMap<String, Float>()
    private fun hueKey(e: AppEntry): Float = hueCache.getOrPut(e.key) {
        val c = host.repo.color(e)
        if (c == null) 999f else {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.2f) 400f + hsv[2] else hsv[0]
        }
    }
}
