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

    private val search = EditText(ctx).apply {
        hint = "Buscar apps"
        setHintTextColor(pal.sub)
        setTextColor(pal.fg)
        typeface = look.face
        textSize = 18f
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO
        background = null
        setPadding(dpi(20), dpi(14), dpi(20), dpi(14))
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

    init {
        setBackgroundColor(withAlpha(pal.bg, prefs.drawerOpacity.coerceIn(40, 100) * 255 / 100))
        isClickable = true
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // Asa para indicar que se puede bajar.
        col.addView(View(ctx).apply { setBackgroundColor(withAlpha(pal.fg, 70)) },
            LinearLayout.LayoutParams(dpi(36), dpi(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpi(10) })
        val bar = FrameLayout(ctx).apply { setBackgroundColor(withAlpha(pal.fg, 18)) }
        bar.addView(search, LayoutParams(-1, -2))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(16), dpi(12), dpi(16), dpi(6)) })
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(-1, -2))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(col, LayoutParams(-1, -1))
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

    fun setInsets(top: Int, bottom: Int) {
        setPadding(0, top, 0, 0)
        list.setPadding(dpi(8), dpi(4), dpi(8), bottom + dpi(24))
    }

    fun open(withKeyboard: Boolean = prefs.drawerKeyboard) {
        render()
        visibility = View.VISIBLE
        translationY = height.toFloat().takeIf { it > 0 } ?: dp(600f)
        alpha = 0.6f
        anim().translationY(0f).alpha(1f).setDuration(220).start()
        if (withKeyboard) focusSearch()
    }

    fun close() {
        if (!isOpen) return
        hideKeyboard()
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
        shown = host.repo.apps.filter { q.isEmpty() || AppEntry.normalize(look.name(it)).contains(q) || it.sortKey.contains(q) }
            .sortedBy { AppEntry.normalize(look.name(it)) }
        if (q.isNotEmpty() && prefs.drawerAutoLaunch && shown.size == 1) { host.launch(shown[0], search); return }

        if (shown.isEmpty()) {
            val raw = search.text.toString().trim()
            list.addView(context.label("Buscar \"$raw\" en la web", 17f, pal.accent, look.face).apply {
                setPadding(dpi(16), dpi(18), dpi(16), dpi(18))
                setOnClickListener { webSearch(raw) }
            })
            return
        }
        if (prefs.drawerStyle == 1) renderGrid() else renderList(q.isEmpty())
    }

    private fun badge(e: AppEntry) = host.live.badges[e.pkg] ?: 0

    private fun renderList(withLetters: Boolean) {
        var last = ""
        for (e in shown) {
            val name = look.name(e)
            val letter = AppEntry.normalize(name).take(1).uppercase().let { if (it.isEmpty() || !it[0].isLetter()) "#" else it }
            if (withLetters && prefs.drawerLetters && letter != last) {
                last = letter
                list.addView(context.label(letter, 13f, pal.accent, Fonts.medium).apply {
                    setPadding(dpi(16), dpi(16), dpi(16), dpi(4))
                })
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(12), dpi(7), dpi(12), dpi(7))
            }
            if (prefs.drawerIcons) row.addView(AppIconView(context, look, host.repo, e) { badge(e) },
                LinearLayout.LayoutParams(dpi(42), dpi(42)).apply { rightMargin = dpi(16) })
            row.addView(context.label(name, 18f, pal.fg, look.face), LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener { host.launch(e, row) }
            row.setOnLongClickListener { host.appMenu(e, row, "drawer"); true }
            list.addView(row)
        }
    }

    private fun renderGrid() {
        val cols = prefs.drawerColumns.coerceIn(3, 6)
        shown.chunked(cols).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val e = chunk.getOrNull(i)
                if (e == null) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dpi(4), dpi(10), dpi(4), dpi(10))
                }
                cell.addView(AppIconView(context, look, host.repo, e) { badge(e) }, LinearLayout.LayoutParams(dpi(54), dpi(54)))
                cell.addView(context.label(look.name(e), 12f, pal.fg, look.face).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    setPadding(0, dpi(6), 0, 0)
                }, LinearLayout.LayoutParams(-1, -2))
                cell.setOnClickListener { host.launch(e, cell) }
                cell.setOnLongClickListener { host.appMenu(e, cell, "drawer"); true }
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            list.addView(row)
        }
    }
}
