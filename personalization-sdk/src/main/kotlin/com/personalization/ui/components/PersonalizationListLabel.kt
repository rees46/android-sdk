package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Подпись-разделитель списка.
 *
 * Источник: Figma Mobile SDK UI Kit, секция List (241:10565), символ Label (243:10967).
 * Единственное место в макете, где шрифт берётся из Font Family/Body, а не Heading.
 * Inter в SDK не поставляется, так что на отрисовку это пока не влияет.
 *
 * Текст переводится в верхний регистр самим компонентом — так задано в макете.
 */
@InternalPersonalizationUiApi
class PersonalizationListLabel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        includeFontPadding = false
        isAllCaps = true
        setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_sm)
        )
        TextViewCompat.setLineHeight(
            this,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_sm)
        )
        // В макете трекинг 0.05px при кегле 14, у Android он в em.
        setLetterSpacingCompat(0.05f / 14f)
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_hint))
    }
}
