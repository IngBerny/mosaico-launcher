package com.ingberny.mosaico

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar

object Pickers {

    private fun theme(pal: Palette) =
        if (pal.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    /** Diálogo con un campo de texto. */
    fun askText(act: Activity, pal: Palette, title: String, initial: String, hint: String, onOk: (String) -> Unit) {
        val input = EditText(act).apply {
            setText(initial); setSelection(initial.length)
            this.hint = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            backgroundTintList = ColorStateList.valueOf(pal.accent)
        }
        val box = FrameLayout(act).apply { setPadding(act.dpi(20), act.dpi(8), act.dpi(20), 0); addView(input) }
        AlertDialog.Builder(act, theme(pal))
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Listo") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
        input.requestFocus()
    }

    /** Selector de color libre: tono, saturación, brillo y código hexadecimal. */
    fun askColor(act: Activity, pal: Palette, title: String, initial: Int, onOk: (Int) -> Unit) {
        val hsv = FloatArray(3); Color.colorToHSV(initial, hsv)
        val preview = View(act)
        val hex = EditText(act).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            backgroundTintList = ColorStateList.valueOf(pal.accent)
        }
        var updating = false
        fun current() = Color.HSVToColor(hsv)
        val bars = ArrayList<SeekBar>()
        fun refresh(fromHex: Boolean) {
            updating = true
            preview.setBackgroundColor(current())
            if (!fromHex) hex.setText(String.format("%06X", current() and 0xFFFFFF))
            bars.getOrNull(0)?.progress = hsv[0].toInt()
            bars.getOrNull(1)?.progress = (hsv[1] * 100).toInt()
            bars.getOrNull(2)?.progress = (hsv[2] * 100).toInt()
            updating = false
        }
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dpi(20), act.dpi(8), act.dpi(20), 0)
            addView(preview, LinearLayout.LayoutParams(-1, act.dpi(56)))
        }
        listOf("Tono" to 360, "Saturación" to 100, "Brillo" to 100).forEachIndexed { i, (name, max) ->
            col.addView(act.label(name, 13f, pal.sub).apply { setPadding(0, act.dpi(12), 0, 0) })
            val sb = SeekBar(act).apply {
                this.max = max
                progressTintList = ColorStateList.valueOf(pal.accent)
                thumbTintList = ColorStateList.valueOf(pal.accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                        if (updating || !fromUser) return
                        if (i == 0) hsv[0] = v.toFloat() else hsv[i] = v / 100f
                        refresh(false)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            bars += sb
            col.addView(sb)
        }
        col.addView(act.label("Código (hex)", 13f, pal.sub).apply { setPadding(0, act.dpi(12), 0, 0) })
        col.addView(hex)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val t = s?.toString()?.trim()?.removePrefix("#") ?: return
                if (t.length == 6) try {
                    Color.colorToHSV(Color.parseColor("#$t"), hsv); refresh(true)
                } catch (e: Exception) { }
            }
        })
        refresh(false)
        AlertDialog.Builder(act, theme(pal))
            .setTitle(title)
            .setView(ScrollView(act).apply { addView(col) })
            .setPositiveButton("Usar este color") { _, _ -> onOk(current() or 0xFF000000.toInt()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/** Hoja para elegir el color de un mosaico: opciones automáticas, los 20 colores y uno libre. */
class ColorSheet(ctx: Context, private val pal: Palette) : FrameLayout(ctx) {

    private val card = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pal.chrome)
        setPadding(dpi(16), dpi(16), dpi(16), dpi(16))
        isClickable = true
    }

    init {
        setBackgroundColor(withAlpha(Color.BLACK, 150))
        isClickable = true
        setOnClickListener { dismiss() }
        addView(ScrollView(ctx).apply { addView(card) }, LayoutParams(-1, -2, Gravity.BOTTOM))
        visibility = View.GONE
    }

    val isOpen get() = visibility == View.VISIBLE
    private var bottomPad = 0
    fun setInsets(bottom: Int) { bottomPad = bottom }

    fun show(title: String, current: Int, accent: Int, appColor: Int?, onPick: (Int) -> Unit, onCustom: () -> Unit) =
        show(title, current, listOf(
            Triple("Automático", TileSpec.COLOR_DEFAULT, accent),
            Triple("Énfasis", TileSpec.COLOR_ACCENT, accent),
            Triple("De la app", TileSpec.COLOR_APP, appColor ?: accent),
            Triple("Transparente", TileSpec.COLOR_TRANSPARENT, Color.TRANSPARENT),
        ), onPick, onCustom)

    /** [specials]: (nombre, valor guardado, color que se muestra). */
    fun show(title: String, current: Int, specials: List<Triple<String, Int, Int>>, onPick: (Int) -> Unit, onCustom: () -> Unit) {
        card.removeAllViews()
        card.setPadding(dpi(16), dpi(16), dpi(16), dpi(16) + bottomPad)
        card.addView(context.label(title, 20f, pal.fg, Fonts.light))

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, value, shown) in specials) {
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Swatch(context, shown, value == current, value == TileSpec.COLOR_TRANSPARENT),
                    LinearLayout.LayoutParams(-1, dpi(48)))
                addView(context.label(name, 12f, pal.sub).apply { gravity = Gravity.CENTER; setPadding(0, dpi(4), 0, 0) })
                setOnClickListener { dismiss(); onPick(value) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dpi(3), dpi(12), dpi(3), dpi(6)) })
        }
        card.addView(row)

        Prefs.ACCENTS.chunked(5).forEach { chunk ->
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (_, c) ->
                r.addView(Swatch(context, c, c == current, false).apply {
                    setOnClickListener { dismiss(); onPick(c) }
                }, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { setMargins(dpi(3), dpi(3), dpi(3), dpi(3)) })
            }
            card.addView(r)
        }
        card.addView(context.label("Color personalizado…", 16f, pal.accent).apply {
            setPadding(dpi(3), dpi(14), 0, dpi(4))
            setOnClickListener { dismiss(); onCustom() }
        })

        visibility = View.VISIBLE
        alpha = 0f
        anim().alpha(1f).setDuration(150).start()
        card.translationY = dp(200f)
        card.anim().translationY(0f).setDuration(220).start()
    }

    fun dismiss() {
        if (!isOpen) return
        anim().alpha(0f).setDuration(120).withEndAction { visibility = View.GONE }.start()
    }

    private inner class Swatch(ctx: Context, val color: Int, val selected: Boolean, val transparent: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (transparent) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = pal.sub
                c.drawRect(dp(1f), dp(1f), w - dp(1f), h - dp(1f), p)
                c.drawLine(dp(4f), h - dp(4f), w - dp(4f), dp(4f), p)
            } else {
                p.style = Paint.Style.FILL; p.color = color; c.drawRect(0f, 0f, w, h, p)
            }
            if (selected) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f); p.color = pal.fg
                c.drawRect(dp(1.5f), dp(1.5f), w - dp(1.5f), h - dp(1.5f), p)
                Glyphs.draw(c, G.CHECK, w / 2, h / 2, h * 0.4f, if (transparent) pal.fg else Color.WHITE, 1.5f)
            }
        }
    }
}
