package com.personalization.ui.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.personalization.R
import kotlin.math.abs

/** Тексты блока сторис — одни имена на всех платформах (раздел D правил). */
internal data class StoriesTexts(
    val promoCaption: CharSequence,
    val promoCopiedText: CharSequence,
    val productButtonText: CharSequence,
    val showProductsText: CharSequence,
    val hideProductsText: CharSequence,
    val retryText: CharSequence,
    val closeLabel: CharSequence,
    val soundOnLabel: CharSequence,
    val soundOffLabel: CharSequence
) {
    companion object {
        /** Тексты по умолчанию — из ресурсов SDK (английские, с переводом в `values-ru`). */
        fun defaults(context: Context) = StoriesTexts(
            promoCaption = context.getString(R.string.personalization_stories_promo_caption),
            promoCopiedText = context.getString(R.string.personalization_stories_promo_copied),
            productButtonText = context.getString(R.string.personalization_stories_product_button),
            showProductsText = context.getString(R.string.personalization_stories_show_products),
            hideProductsText = context.getString(R.string.personalization_stories_hide_products),
            retryText = context.getString(R.string.personalization_stories_retry),
            closeLabel = context.getString(R.string.personalization_stories_close),
            soundOnLabel = context.getString(R.string.personalization_stories_sound_on),
            soundOffLabel = context.getString(R.string.personalization_stories_sound_off)
        )
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

internal fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

/**
 * Часы слайда (B7): кадры [Choreographer], время — по меткам кадров. От масштаба анимаций
 * системы они не зависят, в отличие от `ValueAnimator`: при «Отключить анимацию» слайды не
 * пролетают. Шаг между кадрами ограничен — подвисший поток не перескакивает слайд.
 */
internal class StoryFrameClock(private val onTick: (deltaMs: Long) -> Unit) : Choreographer.FrameCallback {

    private var running = false
    private var lastFrameNanos = 0L

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val delta = if (lastFrameNanos == 0L) 0L else (frameTimeNanos - lastFrameNanos) / 1_000_000L
        lastFrameNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
        onTick(delta.coerceIn(0L, MAX_FRAME_MS))
    }

    private companion object {
        const val MAX_FRAME_MS = 250L
    }
}

/**
 * Слой касаний слайда: тап по трети ширины (B1) и удержание (B2). Нажатие дольше 200 мс —
 * пауза; отпускание её снимает и никуда не листает. Сдвинутый палец — не тап: его заберёт
 * пейджер или свайп закрытия.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class StoryTouchLayer(
    context: Context,
    private val onTap: (x: Float, width: Int, rtl: Boolean) -> Unit,
    private val onHold: (Boolean) -> Unit
) : View(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var holding = false
    private val holdRunnable = Runnable {
        holding = true
        onHold(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                postDelayed(holdRunnable, HOLD_MS)
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) moved = true
            MotionEvent.ACTION_UP -> {
                removeCallbacks(holdRunnable)
                if (holding) release() else if (!moved) onTap(event.x, width, layoutDirection == LAYOUT_DIRECTION_RTL)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdRunnable)
                release()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(holdRunnable)
        release()
        super.onDetachedFromWindow()
    }

    private fun release() {
        if (!holding) return
        holding = false
        onHold(false)
    }

    private companion object {
        const val HOLD_MS = 200L
    }
}

/**
 * Корень просмотра: свайп вниз утаскивает сторис (B4). Закрывает, если протянуть дальше
 * 120 dp или бросить быстрее 800 dp/с, иначе пружинит обратно. Пока тянут — пауза
 * ([onDragChange]). При открытой шторке ([canDrag] = false) не тянется вовсе (B5).
 * Горизонтальные жесты остаются пейджеру и карусели.
 */
@SuppressLint("ViewConstructor")
internal class StoryDismissLayout(context: Context) : FrameLayout(context) {

    var canDrag: () -> Boolean = { true }
    var onDragChange: ((Boolean) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
                track(event)
            }
            MotionEvent.ACTION_MOVE -> {
                track(event)
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (canDrag() && dy > touchSlop && dy > abs(dx) * 1.5f) {
                    dragging = true
                    onDragChange?.invoke(true)
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> translationY = (event.rawY - downY).coerceAtLeast(0f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val speedDp = (tracker?.yVelocity ?: 0f) / density
                velocity?.recycle()
                velocity = null
                val far = translationY / density > DISMISS_DISTANCE_DP
                val fast = event.actionMasked == MotionEvent.ACTION_UP && speedDp > DISMISS_VELOCITY_DP
                if (far || fast) {
                    animate().translationY(height.toFloat()).setDuration(ANIMATION_MS)
                        .withEndAction { onDismiss?.invoke() }.start()
                } else {
                    animate().translationY(0f).setDuration(ANIMATION_MS)
                        .withEndAction { onDragChange?.invoke(false) }.start()
                }
            }
        }
        return true
    }

    /** Скорость — по координатам экрана: сама вьюха едет за пальцем. */
    private fun track(event: MotionEvent) {
        val copy = MotionEvent.obtain(event)
        copy.setLocation(event.rawX, event.rawY)
        velocity?.addMovement(copy)
        copy.recycle()
    }

    private companion object {
        const val DISMISS_DISTANCE_DP = 120f
        const val DISMISS_VELOCITY_DP = 800f
        const val ANIMATION_MS = 200L
    }
}

/**
 * Обёртка карусели внутри пейджера: пока карусель может листаться в сторону жеста,
 * родители (пейджер, свайп закрытия) жест не перехватывают. Схема NestedScrollableHost
 * из примеров ViewPager2.
 */
@SuppressLint("ViewConstructor")
internal class StoryNestedScrollHost(context: Context, private val target: View) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (target.canScrollHorizontally(1) || target.canScrollHorizontally(-1)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - initialX
                    val dy = event.y - initialY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        val horizontal = abs(dx) > abs(dy)
                        val canScroll = target.canScrollHorizontally(if (dx > 0) -1 else 1)
                        parent?.requestDisallowInterceptTouchEvent(horizontal && canScroll)
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }
}
