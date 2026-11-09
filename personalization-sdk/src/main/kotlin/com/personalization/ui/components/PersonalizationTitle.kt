package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Заголовок блока.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Title (167:3802).
 * Все четыре заголовка на странице — один и тот же ряд «слева иконка, заголовок,
 * справа управление», отличается только содержимое по краям:
 * Recommender block (157:5176) — кнопка «Show all», Category (167:3812) — группа кнопок,
 * Filters (204:8340) — кнопка-крестик, Search results (167:3807) — кнопка «назад» и группа.
 * Поэтому края здесь — произвольные вью, а не фиксированные варианты.
 */
class PersonalizationTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val label = AppCompatTextView(context)
    private var leadingView: View? = null
    private var trailingView: View? = null

    var text: CharSequence?
        get() = label.text
        set(value) {
            label.text = value
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        label.includeFontPadding = false
        // Inter в SDK не поставляется, ближайшее системное к Emphasized 600.
        label.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        label.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_xl2)
        )
        TextViewCompat.setLineHeight(
            label,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_xl2)
        )
        label.setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))

        addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Вью слева от заголовка. `null` — убрать. */
    fun setLeading(view: View?) {
        leadingView?.let(::removeView)
        leadingView = view
        view?.let { addView(it, 0, gapParams(start = false)) }
    }

    /** Вью справа от заголовка. `null` — убрать. */
    fun setTrailing(view: View?) {
        trailingView?.let(::removeView)
        trailingView = view
        view?.let { addView(it, gapParams(start = true)) }
    }

    /**
     * Отступ ставится только со стороны заголовка: у ряда в макете gap 4
     * и никакого внешнего отступа, иначе края разъезжаются с соседними блоками.
     */
    private fun gapParams(start: Boolean): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
            if (start) marginStart = gap else marginEnd = gap
        }
}
