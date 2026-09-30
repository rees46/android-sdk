package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Пустое состояние дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Empty State (319:7736).
 * Горизонтальные отступы 16, вертикальные 92, текст по центру ступенью
 * XL/Default цветом Text/Secondary.
 *
 * Текст не зашит: в макете стоит «No results for your request.», но строку
 * подставляет потребитель — локализация остаётся на его стороне.
 */
@InternalPersonalizationUiApi
class PersonalizationEmptyState @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false

        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        TextViewCompat.setLineHeight(this, spToPx(32f))
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_secondary))

        val padH = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        setPadding(padH, dpToPx(92), padH, dpToPx(92))
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
        ).toInt()
}
