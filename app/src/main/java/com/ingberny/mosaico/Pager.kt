package com.ingberny.mosaico

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.abs

/** Dos páginas horizontales: Inicio y la lista de todas las apps. */
class Pager(ctx: Context) : FrameLayout(ctx) {
    var page = 0; private set
    var onPageChanged: ((Int) -> Unit)? = null

    private var offset = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val minFling = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var startOffset = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null
    private var anim: ValueAnimator? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && !dragging && anim?.isRunning != true) offset = page * width.toFloat()
        apply()
    }

    private fun apply() {
        val w = width.toFloat()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.translationX = i * w - offset
            // ligero desvanecimiento al deslizar, como en el original
            val d = abs(c.translationX) / maxOf(1f, w)
            c.alpha = 1f - d * 0.6f
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                anim?.cancel()
                startOffset = offset
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - downX; val dy = e.y - downY
                val allowed = (page == 0 && dx < 0) || (page == 1 && dx > 0)
                if (allowed && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.4f) {
                    dragging = true
                    downX = e.x
                    startOffset = offset
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        vt?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) {
                offset = (startOffset - (e.x - downX)).coerceIn(0f, width.toFloat() * (childCount - 1))
                apply()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                vt?.computeCurrentVelocity(1000)
                val v = vt?.xVelocity ?: 0f
                val target = when {
                    v < -minFling -> 1
                    v > minFling -> 0
                    else -> if (offset > width / 2f) 1 else 0
                }
                goTo(target)
            }
        }
        return true
    }

    fun goTo(p: Int, animate: Boolean = true) {
        val target = p * width.toFloat()
        val changed = p != page
        page = p
        anim?.cancel()
        if (!animate || width == 0) {
            offset = target; apply()
        } else {
            anim = ValueAnimator.ofFloat(offset, target).apply {
                duration = 280
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { offset = it.animatedValue as Float; apply() }
                start()
            }
        }
        if (changed) onPageChanged?.invoke(p)
    }
}

/** ScrollView que avisa cuando se desliza hacia abajo estando arriba del todo. */
class PullScrollView(ctx: Context) : ScrollView(ctx) {
    var onPullDown: (() -> Unit)? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startScroll = 0
    private var triggered = false

    private fun check(e: MotionEvent): Boolean {
        if (triggered || startScroll != 0 || scrollY != 0) return false
        val dy = e.y - downY; val dx = abs(e.x - downX)
        if (dy > slop * 4 && dy > dx * 1.5f) {
            triggered = true
            onPullDown?.invoke()
            return true
        }
        return false
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; triggered = false; startScroll = scrollY }
            MotionEvent.ACTION_MOVE -> if (check(e)) return true
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; triggered = false; startScroll = scrollY }
        if (triggered) return true
        if (e.actionMasked == MotionEvent.ACTION_MOVE && check(e)) return true
        return super.onTouchEvent(e)
    }
}
