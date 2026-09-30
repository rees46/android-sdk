package com.personalization.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Счётчик «показано N из M».
 *
 * Источник: Figma Mobile SDK UI Kit, секция Navigation (90:660), символ Count (301:6810).
 * Слова — параметры, а не константы: локализация остаётся за интегратором.
 * В макете это «Showed 6 from 569».
 */
@InternalPersonalizationUiApi
class PersonalizationCount @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val prefixView = AppCompatTextView(context)
    private val shownView = AppCompatTextView(context)
    private val separatorView = AppCompatTextView(context)
    private val totalView = AppCompatTextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val vertical = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        setPadding(0, vertical, 0, vertical)

        style(prefixView, emphasized = false)
        style(shownView, emphasized = true)
        style(separatorView, emphasized = false)
        style(totalView, emphasized = true)

        addView(prefixView)
        addView(shownView, gapParams())
        addView(separatorView, gapParams())
        addView(totalView, gapParams())
    }

    /**
     * @param prefix слово перед первым числом («Показано»).
     * @param separator слово между числами («из»).
     */
    fun set(prefix: CharSequence, shown: Int, separator: CharSequence, total: Int) {
        prefixView.text = prefix
        shownView.text = shown.toString()
        separatorView.text = separator
        totalView.text = total.toString()
    }

    private fun style(view: AppCompatTextView, emphasized: Boolean) {
        view.includeFontPadding = false
        if (emphasized) {
            // Inter в SDK не поставляется, ближайшее системное к Emphasized 600.
            view.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        }
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_base)
        )
        TextViewCompat.setLineHeight(
            view,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_base)
        )
        view.setTextColor(
            PersonalizationTheme.color(context,
                if (emphasized) R.color.personalization_text_primary
                else R.color.personalization_text_secondary
            )
        )
    }

    private fun gapParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        }
}
