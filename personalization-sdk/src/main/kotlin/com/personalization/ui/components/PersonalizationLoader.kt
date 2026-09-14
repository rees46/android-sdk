package com.personalization.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Индикатор загрузки дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Loader (296:3717).
 * Кольцо 26 с внешним радиусом 13 и толщиной 3.5, свип-градиент от прозрачного
 * к чёрному 47%, непрерывное вращение.
 *
 * Градиент снят с растрового экспорта макета: в переменных Figma значение
 * `Gradient/Loader` приходит пустым. Рисуется SweepGradient, а не картинкой —
 * чтобы не зависеть от плотности экрана.
 */
class PersonalizationLoader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        /** Слот лоадера из макета: кольцо 26 лежит в квадрате 32 с полем 3. */
        const val SLOT_SIDE = 32f
        /** Диаметр кольца из макета; толщина пропорциональна ему. */
        const val RING_SIDE = 26f
        const val REFERENCE_STROKE = 3.5f
        const val PERIOD_MS = 900L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var rotation = 0f
    private var animator: ValueAnimator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (SLOT_SIDE * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(side, widthMeasureSpec),
            resolveSize(side, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = minOf(w, h).toFloat()
        if (side <= 0f) return
        val ring = side * RING_SIDE / SLOT_SIDE
        paint.strokeWidth = REFERENCE_STROKE * (ring / RING_SIDE)
        paint.shader = SweepGradient(
            side / 2f,
            side / 2f,
            intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
            floatArrayOf(0f, 1f)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val ring = side * RING_SIDE / SLOT_SIDE
        val inset = paint.strokeWidth / 2f

        canvas.save()
        canvas.rotate(rotation, side / 2f, side / 2f)
        canvas.drawCircle(side / 2f, side / 2f, ring / 2f - inset, paint)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }
}
