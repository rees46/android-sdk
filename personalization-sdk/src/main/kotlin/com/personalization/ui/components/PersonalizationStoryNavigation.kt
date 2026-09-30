package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Прогресс слайдов сторис: по полоске на слайд.
 *
 * Источник: Figma, Navigation/Story (10:863), рамка Progress: полоски высотой 2
 * с шагом 2 и скруглением 1 делят ширину поровну. Пройденное — Text/Secondary,
 * дорожка — Line/Generic. Текущая полоска заполнена на [progress].
 */
@InternalPersonalizationUiApi
class PersonalizationStoryProgress @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Число слайдов. */
    var count: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    /** Индекс текущего слайда: полоски до него заполнены целиком. */
    var current: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** Заполнение текущей полоски, 0…1. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(dp(HEIGHT_DP), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val gap = dp(GAP_DP).toFloat()
        val radius = dp(RADIUS_DP).toFloat()
        val segment = (width - gap * (count - 1)) / count
        val filled = PersonalizationTheme.color(context, R.color.personalization_text_secondary)
        val track = PersonalizationTheme.color(context, R.color.personalization_line_generic)

        for (index in 0 until count) {
            val left = index * (segment + gap)
            rect.set(left, 0f, left + segment, height.toFloat())
            paint.color = track
            canvas.drawRoundRect(rect, radius, radius, paint)

            val fraction = when {
                index < current -> 1f
                index == current -> progress
                else -> 0f
            }
            if (fraction > 0f) {
                rect.set(left, 0f, left + segment * fraction, height.toFloat())
                paint.color = filled
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HEIGHT_DP = 2
        const val GAP_DP = 2
        const val RADIUS_DP = 1
    }
}

/**
 * Шапка сторис: прогресс слайдов и крестик.
 *
 * Источник: Figma, Navigation/Story (10:863): колонка с шагом 12 — прогресс и строка
 * высотой 32 с крестиком справа (cross 32, глиф Text/Secondary, прозрачность 65%).
 * Аватар и заголовок в символе есть, но во всех экранах сторис скрыты.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryNavigation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val progress = PersonalizationStoryProgress(context)

    private val close = AppCompatImageView(context)

    var onClose: (() -> Unit)? = null

    var closeContentDescription: CharSequence? = "Close"
        set(value) {
            field = value
            close.contentDescription = value
        }

    init {
        orientation = VERTICAL
        addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val row = FrameLayout(context)
        close.setImageResource(R.drawable.personalization_ic_cross)
        ImageViewCompat.setImageTintList(
            close,
            PersonalizationTheme.colorStateList(context, R.color.personalization_text_secondary)
        )
        close.alpha = CLOSE_ALPHA
        close.contentDescription = closeContentDescription
        close.setOnClickListener { onClose?.invoke() }
        row.addView(close, FrameLayout.LayoutParams(dp(CLOSE_DP), dp(CLOSE_DP), Gravity.END or Gravity.CENTER_VERTICAL))
        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(CLOSE_DP)).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
            }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val CLOSE_DP = 32
        const val CLOSE_ALPHA = 0.65f
    }
}
