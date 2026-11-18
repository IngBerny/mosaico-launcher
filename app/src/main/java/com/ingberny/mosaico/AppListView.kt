package com.ingberny.mosaico

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Ícono de app sobre un cuadrado de color, como en la lista de apps original. */
class IconTile(ctx: Context, private val host: TileHost) : View(ctx) {
    var entry: AppEntry? = null
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val e = entry ?: return
        val w = width.toFloat(); val h = height.toFloat()
        p.color = (if (host.prefs.appColors) host.repo.color(e) else null) ?: host.palette.accent
        val r = dp(host.prefs.tileRadius.toFloat()) * 0.6f
        canvas.drawRoundRect(0f, 0f, w, h, r, r, p)
        val fgPref = host.prefs.tileFg
        val fg = if (fgPref == TileSpec.COLOR_ACCENT) host.palette.accent else fgPref or 0xFF000000.toInt()
        host.repo.drawIcon(canvas, e, w / 2, h / 2, w * if (host.prefs.iconStyle == 0) 0.72f else 0.62f, fg)
    }
}

class AppListView(ctx: Context, private val host: TileHost, private val cb: Callbacks) : FrameLayout(ctx) {

    interface Callbacks {
        fun launch(e: AppEntry, from: View)
        fun showMenu(e: AppEntry, anchor: View)
    }

    private sealed class Row {
        class Header(val letter: String) : Row()
        object Recent : Row()
        class App(val e: AppEntry, val isNew: Boolean) : Row()
        object Empty : Row()
    }

    private val pal = host.palette
    private val column = LinearLayout(ctx)
    private val search = EditText(ctx)
    private val list = ListView(ctx)
    private val jump = FrameLayout(ctx)
    private var rows: List<Row> = emptyList()
    private var query = ""

    // ---------- adaptador ----------

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 4
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.Recent -> 1
            is Row.App -> 2
            is Row.Empty -> 3
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return when (val r = rows[position]) {
                is Row.Header -> {
                    val t = (convertView as? TextView) ?: context.label("", 28f, pal.fg, Fonts.light).apply {
                        setPadding(dpi(18), dpi(14), dpi(18), dpi(8))
                    }
                    t.text = r.letter.lowercase()
                    t.setOnClickListener { showJump() }
                    t
                }
                is Row.Recent -> (convertView as? TextView) ?: context.label("Agregadas recientemente", 16f, pal.fg, Fonts.regular).apply {
                    setPadding(dpi(18), dpi(6), dpi(18), dpi(10))
                }
                is Row.Empty -> (convertView as? TextView) ?: context.label("No hay resultados", 16f, pal.sub, Fonts.light).apply {
                    setPadding(dpi(18), dpi(20), dpi(18), dpi(20))
                }
                is Row.App -> {
                    val row = (convertView as? AppRow) ?: AppRow(context)
                    row.bind(r.e, r.isNew)
                    row
                }
            }
        }
    }

    init {
        column.orientation = LinearLayout.VERTICAL

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(if (pal.dark) Color.BLACK else Color.WHITE)
                setStroke(dpi(2), pal.sub)
            }
            setPadding(dpi(10), 0, dpi(4), 0)
        }
        search.apply {
            hint = "Buscar"
            setHintTextColor(pal.sub)
            setTextColor(pal.fg)
            textSize = 18f
            typeface = Fonts.regular
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(0, dpi(10), 0, dpi(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; rebuild() }
            })
            setOnEditorActionListener { v, actionId, ev ->
                val go = actionId == EditorInfo.IME_ACTION_GO ||
                    (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_UP)
                if (go) {
                    (rows.firstOrNull { it is Row.App } as? Row.App)?.let { cb.launch(it.e, v) }
                    true
                } else false
            }
        }
        box.addView(search, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        box.addView(GlyphView(ctx, G.SEARCH, pal.fg, 0.55f), LinearLayout.LayoutParams(dpi(36), dpi(36)))
        column.addView(box, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dpi(14), dpi(8), dpi(14), dpi(8))
        })

        list.divider = null
        list.dividerHeight = 0
        list.selector = ColorDrawable(Color.TRANSPARENT)
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.clipToPadding = false
        list.adapter = adapter
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) {
                if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
            }
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {}
        })
        column.addView(list, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        jump.setBackgroundColor(withAlpha(pal.bg, 235))
        jump.visibility = View.GONE
        jump.isClickable = true
        jump.setOnClickListener { hideJump() }
        addView(jump, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setInsets(top: Int, bottom: Int) {
        column.setPadding(0, top, 0, 0)
        list.setPadding(0, 0, 0, bottom + dpi(12))
        jump.setPadding(0, top, 0, bottom)
    }

    fun refresh() = rebuild()

    fun reset() {
        hideJump()
        if (search.text.isNotEmpty()) search.setText("")
        hideKeyboard()
        list.setSelection(0)
    }

    fun focusSearch() {
        hideJump()
        search.requestFocus()
        search.post {
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        search.clearFocus()
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    val jumpOpen get() = jump.visibility == View.VISIBLE

    private fun letterOf(e: AppEntry): String {
        val c = e.sortKey.firstOrNull() ?: return "#"
        return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
    }

    private fun rebuild() {
        val apps = host.repo.apps
        val q = AppEntry.normalize(query)
        val out = ArrayList<Row>()
        if (q.isNotEmpty()) {
            val matches = apps.filter { it.sortKey.contains(q) }
                .sortedBy { if (it.sortKey.startsWith(q)) 0 else 1 }
            matches.forEach { out += Row.App(it, false) }
            if (matches.isEmpty()) out += Row.Empty
        } else {
            val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val recent = apps.filter { it.installTime > week }.sortedByDescending { it.installTime }.take(3)
            if (recent.isNotEmpty()) {
                out += Row.Recent
                recent.forEach { out += Row.App(it, true) }
            }
            var last = ""
            for (e in apps) {
                val l = letterOf(e)
                if (l != last) { out += Row.Header(l); last = l }
                out += Row.App(e, false)
            }
        }
        rows = out
        adapter.notifyDataSetChanged()
    }

    // ---------- lista de saltos (A–Z) ----------

    private fun showJump() {
        hideKeyboard()
        jump.removeAllViews()
        val present = rows.filterIsInstance<Row.Header>().map { it.letter }.toSet()
        val letters = listOf("#") + ('A'..'Z').map { it.toString() }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cols = 4
        letters.chunked(cols).forEach { chunk ->
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val l = chunk.getOrNull(i)
                val has = l != null && l in present
                val t = TextView(context).apply {
                    text = l ?: ""
                    textSize = 28f
                    typeface = Fonts.light
                    gravity = Gravity.START or Gravity.BOTTOM
                    setPadding(dpi(10), 0, 0, dpi(6))
                    setTextColor(if (has) Color.WHITE else pal.sub)
                    if (l != null) setBackgroundColor(if (has) pal.accent else pal.toggleOff)
                    if (has) setOnClickListener { jumpTo(l!!) }
                }
                r.addView(t, LinearLayout.LayoutParams(0, dpi(76), 1f).apply { setMargins(dpi(3), dpi(3), dpi(3), dpi(3)) })
            }
            grid.addView(r)
        }
        jump.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dpi(10), dpi(24), dpi(10), 0)
        })
        jump.visibility = View.VISIBLE
        jump.alpha = 0f; jump.scaleX = 1.1f; jump.scaleY = 1.1f
        jump.anim().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    fun hideJump() {
        if (!jumpOpen) return
        jump.anim().alpha(0f).setDuration(120).withEndAction { jump.visibility = View.GONE }.start()
    }

    private fun jumpTo(letter: String) {
        val idx = rows.indexOfFirst { it is Row.Header && it.letter == letter }
        if (idx >= 0) list.setSelection(idx)
        hideJump()
    }

    private inner class AppRow(ctx: Context) : LinearLayout(ctx) {
        val icon = IconTile(ctx, host)
        val name = ctx.label("", 18f, pal.fg, Fonts.light)
        val sub = ctx.label("Nueva", 12f, pal.accent, Fonts.regular)
        var entry: AppEntry? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(18), dpi(4), dpi(18), dpi(4))
            addView(icon, LayoutParams(dpi(46), dpi(46)))
            val texts = LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(name); addView(sub)
                setPadding(dpi(14), 0, 0, 0)
            }
            name.isSingleLine = true
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            minimumHeight = dpi(58)
            setOnClickListener { entry?.let { cb.launch(it, icon) } }
            setOnLongClickListener { entry?.let { cb.showMenu(it, this) }; true }
        }

        fun bind(e: AppEntry, isNew: Boolean) {
            entry = e
            icon.entry = e
            name.text = e.label
            sub.visibility = if (isNew) View.VISIBLE else View.GONE
        }
    }
}
