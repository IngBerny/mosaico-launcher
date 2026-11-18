package com.ingberny.mosaico

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Cuadrícula de mosaicos: maquetación, modo edición (desanclar, redimensionar, arrastrar),
 * efecto de inclinación al tocar y la animación "turnstile" al abrir apps.
 */
class TileGrid(ctx: Context, private val host: TileHost) : ViewGroup(ctx) {

    interface Listener {
        fun onTileClick(t: TileSpec, v: TileView)
        fun onTilesChanged()
        fun onEditModeChanged(editing: Boolean)
        fun scroller(): ScrollView?
    }

    var listener: Listener? = null
    var tiles: MutableList<TileSpec> = mutableListOf(); private set
    var columns = 4
        set(v) { field = v; requestLayout() }

    private var placements: List<Placement> = emptyList()
    private val views = LinkedHashMap<String, TileView>()
    private var cell = 0f
    private val gap = dp(5f)
    private val padH = dp(10f)
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    var editing = false; private set
    private var selected: TileSpec? = null

    private var dragView: TileView? = null
    private var dragging = false
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastReorder = 0L
    private var autoDy = 0
    private var autoScrolling = false
    private var animateLayout = false

    private var pressed: TileView? = null
    private var downX = 0f
    private var downY = 0f

    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, dpi(12))
        setWillNotDraw(false)
    }

    fun views(): Collection<TileView> = views.values
    fun viewFor(t: TileSpec): TileView? = views[t.id]

    fun setTiles(list: MutableList<TileSpec>) {
        tiles = list
        sync()
    }

    private fun sync() {
        val ids = tiles.map { it.id }.toSet()
        val it = views.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in ids) { removeView(e.value); it.remove() }
        }
        for (t in tiles) if (!views.containsKey(t.id)) {
            val v = TileView(context, t, host)
            views[t.id] = v
            addView(v)
        }
        requestLayout()
        invalidateTiles()
    }

    fun invalidateTiles() { views.values.forEach { it.invalidate() } }

    fun addTile(t: TileSpec) { tiles.add(t); sync(); listener?.onTilesChanged() }

    fun removeTile(t: TileSpec) {
        animateLayout = true
        tiles.remove(t)
        if (selected == t) selected = null
        sync()
        listener?.onTilesChanged()
        if (tiles.isEmpty()) exitEdit()
    }

    fun removeWhere(pred: (TileSpec) -> Boolean) {
        if (tiles.removeAll(pred)) { sync(); listener?.onTilesChanged() }
    }

    // ---------- maquetación ----------

    private fun span(units: Int) = units * cell + (units - 1) * gap
    private fun cellLeft(col: Int) = padH + col * (cell + gap)
    private fun cellTop(row: Int) = paddingTop + row * (cell + gap)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cell = (w - 2 * padH - gap * (columns - 1)) / columns
        placements = packTiles(tiles, columns)
        val rows = placements.maxOfOrNull { it.row + it.h } ?: 0
        val h = paddingTop + paddingBottom + (if (rows > 0) span(rows) else 0f)
        tiles.forEachIndexed { i, t ->
            val p = placements[i]
            views[t.id]?.measure(
                MeasureSpec.makeMeasureSpec(span(p.w).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(span(p.h).toInt(), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h.toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (placements.size != tiles.size) placements = packTiles(tiles, columns)
        tiles.forEachIndexed { i, spec ->
            val v = views[spec.id] ?: return@forEachIndexed
            val p = placements[i]
            val nl = cellLeft(p.col).toInt(); val nt = cellTop(p.row).toInt()
            val wasLaid = v.width > 0
            val ol = v.left; val ot = v.top
            v.layout(nl, nt, nl + v.measuredWidth, nt + v.measuredHeight)
            if (v === dragView && dragging) {
                v.translationX = lastX - dragOffX - nl
                v.translationY = lastY - dragOffY - nt
            } else if (animateLayout && wasLaid && (ol != nl || ot != nt)) {
                v.translationX = v.translationX + (ol - nl)
                v.translationY = v.translationY + (ot - nt)
                v.anim().translationX(0f).translationY(0f).setDuration(220).start()
            }
        }
        animateLayout = false
    }

    // ---------- modo edición ----------

    fun enterEdit(t: TileSpec) {
        if (!editing) { editing = true; listener?.onEditModeChanged(true) }
        selected = t
        applyEditVisuals()
    }

    fun exitEdit() {
        if (!editing) return
        editing = false; selected = null
        applyEditVisuals()
        listener?.onEditModeChanged(false)
        listener?.onTilesChanged()
    }

    private fun applyEditVisuals() {
        for (v in views.values) {
            v.pivotX = v.width / 2f; v.pivotY = v.height / 2f
            val (s, a) = when {
                !editing -> 1f to 1f
                v.spec === selected -> 1f to 1f
                else -> 0.9f to 0.75f
            }
            v.anim().scaleX(s).scaleY(s).alpha(a).setDuration(180).start()
        }
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val sel = selected ?: return
        if (!editing || dragging) return
        val v = views[sel.id] ?: return
        val r = dp(15f)
        drawCornerButton(canvas, v.right - dp(4f), v.top + dp(4f), r, G.UNPIN)
        drawCornerButton(canvas, v.right - dp(4f), v.bottom - dp(4f), r, G.RESIZE)
    }

    private fun drawCornerButton(c: Canvas, x: Float, y: Float, r: Float, g: G) {
        btnPaint.style = Paint.Style.FILL; btnPaint.color = host.palette.fg
        c.drawCircle(x, y, r, btnPaint)
        Glyphs.draw(c, g, x, y, r * 1.2f, host.palette.bg, 1.4f)
    }

    // ---------- toques ----------

    private fun tileAt(x: Float, y: Float): TileView? =
        views.values.firstOrNull { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { handleTap(e.x, e.y); return true }
        override fun onLongPress(e: MotionEvent) { handleLongPress(e.x, e.y) }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                if (!editing) tilt(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) dragTo(e.x, e.y)
                else if (pressed != null && hypot(e.x - downX, e.y - downY) > slop) releaseTilt()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                releaseTilt()
                if (dragging) endDrag()
            }
        }
        gestures.onTouchEvent(e)
        return true
    }

    /** Efecto "tilt": el mosaico se inclina hacia el punto tocado. */
    private fun tilt(x: Float, y: Float) {
        val v = tileAt(x, y) ?: return
        pressed = v
        v.pivotX = v.width / 2f; v.pivotY = v.height / 2f
        val nx = ((x - v.left) / v.width - 0.5f) * 2f
        val ny = ((y - v.top) / v.height - 0.5f) * 2f
        val k = if (v.width > v.height * 1.5f) 4f else 9f
        v.anim().rotationY(nx * k).rotationX(-ny * k).scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
    }

    private fun releaseTilt() {
        val v = pressed ?: return
        pressed = null
        val s = if (editing && v.spec !== selected) 0.9f else 1f
        v.anim().rotationX(0f).rotationY(0f).scaleX(s).scaleY(s).setDuration(140).start()
    }

    private fun handleTap(x: Float, y: Float) {
        if (editing) {
            val sel = selected
            val sv = sel?.let { views[it.id] }
            if (sel != null && sv != null) {
                val r = dp(26f)
                if (hypot(x - (sv.right - dp(4f)), y - (sv.top + dp(4f))) < r) { removeTile(sel); return }
                if (hypot(x - (sv.right - dp(4f)), y - (sv.bottom - dp(4f))) < r) { resize(sel); return }
            }
            val v = tileAt(x, y)
            when {
                v == null -> exitEdit()
                v.spec === selected -> exitEdit()
                else -> enterEdit(v.spec)
            }
            return
        }
        val v = tileAt(x, y) ?: return
        listener?.onTileClick(v.spec, v)
    }

    private fun resize(t: TileSpec) {
        t.size = t.nextSize()
        animateLayout = true
        views[t.id]?.let { it.resetFace(); it.invalidate() }
        requestLayout()
        listener?.onTilesChanged()
    }

    private fun handleLongPress(x: Float, y: Float) {
        releaseTilt()
        val v = tileAt(x, y) ?: return
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        enterEdit(v.spec)
        startDrag(v, x, y)
    }

    // ---------- arrastrar ----------

    private fun startDrag(v: TileView, x: Float, y: Float) {
        parent?.requestDisallowInterceptTouchEvent(true)
        dragView = v; dragging = true
        dragOffX = x - v.left; dragOffY = y - v.top
        lastX = x; lastY = y
        v.translationZ = dp(10f)
        v.anim().scaleX(1.06f).scaleY(1.06f).alpha(0.95f).setDuration(120).start()
        invalidate()
    }

    private fun dragTo(x: Float, y: Float) {
        val v = dragView ?: return
        lastX = x; lastY = y
        v.translationX = x - dragOffX - v.left
        v.translationY = y - dragOffY - v.top
        maybeReorder(v)
        updateAutoScroll(y)
    }

    private fun maybeReorder(v: TileView) {
        val now = System.currentTimeMillis()
        if (now - lastReorder < 160) return
        val cx = v.left + v.translationX + v.width / 2f
        val cy = v.top + v.translationY + v.height / 2f
        val col = ((cx - padH) / (cell + gap)).toInt().coerceIn(0, columns - 1)
        val row = max(0, ((cy - paddingTop) / (cell + gap)).toInt())
        val from = tiles.indexOf(v.spec)
        if (from < 0) return
        var to = -1
        for (i in placements.indices) {
            if (i != from && placements[i].contains(row, col)) { to = i; break }
        }
        if (to < 0) {
            val lastRow = placements.maxOfOrNull { it.row + it.h } ?: 0
            if (row >= lastRow) to = tiles.size - 1 else return
        }
        if (to == from) return
        tiles.removeAt(from)
        tiles.add(to.coerceIn(0, tiles.size), v.spec)
        lastReorder = now
        animateLayout = true
        requestLayout()
    }

    private fun updateAutoScroll(y: Float) {
        val sv = listener?.scroller() ?: return
        val gl = IntArray(2); getLocationOnScreen(gl)
        val sl = IntArray(2); sv.getLocationOnScreen(sl)
        val screenY = gl[1] + y
        val zone = dp(90f)
        autoDy = when {
            screenY < sl[1] + zone -> -dpi(10)
            screenY > sl[1] + sv.height - zone -> dpi(10)
            else -> 0
        }
        if (autoDy != 0 && !autoScrolling) { autoScrolling = true; postOnAnimation(autoScroll) }
    }

    private val autoScroll: Runnable = object : Runnable {
        override fun run() {
            val sv = listener?.scroller()
            if (!dragging || autoDy == 0 || sv == null) { autoScrolling = false; return }
            val before = sv.scrollY
            sv.scrollBy(0, autoDy)
            val moved = sv.scrollY - before
            if (moved != 0) dragTo(lastX, lastY + moved)
            postOnAnimation(this)
        }
    }

    private fun endDrag() {
        val v = dragView
        dragging = false; dragView = null; autoDy = 0
        if (v != null) {
            v.translationZ = 0f
            v.anim().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start()
        }
        listener?.onTilesChanged()
        invalidate()
    }

    // ---------- turnstile ----------

    private fun visibleSorted(): List<TileView> = views.values.sortedWith(compareBy({ it.top }, { it.left }))

    fun turnstileOut(origin: View?, then: () -> Unit) {
        val list = visibleSorted().filter { it !== origin }
        var delay = 0L
        for (v in list) {
            v.pivotX = -v.left.toFloat(); v.pivotY = v.height / 2f
            v.animate().rotationY(-80f).alpha(0f).setStartDelay(delay).setDuration(170)
                .setInterpolator(AccelerateInterpolator()).start()
            delay = minOf(delay + 12, 140)
        }
        origin?.let {
            it.pivotX = -it.left.toFloat(); it.pivotY = it.height / 2f
            it.animate().rotationY(-80f).alpha(0f).setStartDelay(delay + 40).setDuration(170)
                .setInterpolator(AccelerateInterpolator()).start()
        }
        postDelayed({ then() }, delay + 200)
    }

    fun turnstileIn(animated: Boolean) {
        var delay = 0L
        for (v in visibleSorted()) {
            v.pivotX = -v.left.toFloat(); v.pivotY = v.height / 2f
            if (!animated) {
                v.animate().cancel(); v.rotationY = 0f; v.rotationX = 0f; v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f
                continue
            }
            v.rotationY = 70f; v.alpha = 0f; v.scaleX = 1f; v.scaleY = 1f; v.rotationX = 0f
            v.animate().rotationY(0f).alpha(1f).setStartDelay(delay).setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            delay = minOf(delay + 12, 140)
        }
    }

    fun resetPivots() { for (v in views.values) { v.pivotX = v.width / 2f; v.pivotY = v.height / 2f } }

    fun flipRandom() {
        if (editing || dragging) return
        val candidates = views.values.filter { it.canFlip() && it.isShown }
        if (candidates.isNotEmpty()) candidates.random().flip()
    }

    fun cyclePhotos() { views.values.filter { it.spec.kind == TileKind.PHOTOS }.forEach { it.nextPhoto() } }
}
