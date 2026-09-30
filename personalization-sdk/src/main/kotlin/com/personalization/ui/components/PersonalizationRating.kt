package com.personalization.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Рейтинг товара, короткая форма.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Rating (88:209), фрейм Product Short (237:4711).
 * Вариант Reviews меняет только данные и цвет звезды: без отзывов она серая.
 *
 * Внимание: типографика 16/20 — кегль со ступени base, интерлиньяж со ступени sm.
 * Ступень base — это 16/24, поэтому размеры заданы явно.
 *
 * Цвет заполненной звезды в макете не привязан к переменной, взят ближайший
 * существующий токен Semantic/Warning — его стоит подтвердить у дизайнера.
 */
@InternalPersonalizationUiApi
class PersonalizationRating @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val star = AppCompatImageView(context)
    private val valueView = AppCompatTextView(context)
    private val reviewsView = AppCompatTextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm))

        star.setImageResource(R.drawable.personalization_ic_star_fill)
        // Вектор чёрный, а тинт ставится в set(). Без этой строки компонент
        // до первого вызова показывал бы чёрную звезду.
        ImageViewCompat.setImageTintList(
            star,
            ColorStateList.valueOf(
                PersonalizationTheme.color(context, R.color.personalization_line_generic)
            )
        )
        addView(
            star,
            LayoutParams(dpToPx(STAR_SIZE_DP), dpToPx(STAR_SIZE_DP))
        )

        style(valueView, emphasized = true, colorRes = R.color.personalization_text_secondary)
        style(reviewsView, emphasized = false, colorRes = R.color.personalization_text_hint)

        addView(
            valueView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
            }
        )
        addView(
            reviewsView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xs)
            }
        )
    }

    /** @param value уже отформатированная оценка: в макете «4,7» с запятой. */
    fun set(value: CharSequence, reviews: Int) {
        valueView.text = value
        reviewsView.text = "($reviews)"
        ImageViewCompat.setImageTintList(
            star,
            ColorStateList.valueOf(
                PersonalizationTheme.color(context,
                    if (reviews > 0) R.color.personalization_semantic_warning
                    else R.color.personalization_line_generic
                )
            )
        )
    }

    private fun style(view: AppCompatTextView, emphasized: Boolean, colorRes: Int) {
        view.includeFontPadding = false
        if (emphasized) {
            view.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        }
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_base)
        )
        TextViewCompat.setLineHeight(
            view,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_sm)
        )
        view.setTextColor(PersonalizationTheme.color(context, colorRes))
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private companion object {
        const val STAR_SIZE_DP = 20
    }
}
