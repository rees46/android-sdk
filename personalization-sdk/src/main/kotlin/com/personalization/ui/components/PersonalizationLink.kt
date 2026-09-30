package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Текстовая ссылка: «Cancel» у поля поиска, «Clear» у недавних запросов.
 *
 * В секции Components такого символа нет — стиль снят с экранов Instant Search
 * (страница InstantSearchField, 151:3976 и 310:9829): кегль base 16/24,
 * начертание 600, цвет Text/Link, без подложки и отступов. Нажатого состояния
 * в макете нет, поэтому ссылка только слегка гаснет под пальцем.
 */
@InternalPersonalizationUiApi
class PersonalizationLink @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        typeface = PersonalizationTheme.typeface(context, emphasized = true)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_base))
        TextViewCompat.setLineHeight(this, resources.getDimensionPixelSize(R.dimen.personalization_line_height_base))
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_link))
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (pressed) PRESSED_ALPHA else 1f
    }

    private companion object {
        const val PRESSED_ALPHA = 0.6f
    }
}
