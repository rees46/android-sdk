package com.personalization.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Строка-аккордеон списка.
 *
 * Источник: Figma Mobile SDK UI Kit, секция List (241:10565), фрейм Accordion (241:10575).
 * Два варианта — Expanded=False и True, отличаются только направлением шеврона.
 *
 * Состояние компонент держит сам: нажатие переключает [expanded] и только потом
 * зовёт [onToggle]. Отдельно выставлять [expanded] из колбэка не нужно —
 * в React Native и Flutter тот же компонент, наоборот, ничего не хранит
 * и ждёт перерисовки сверху.
 */
@InternalPersonalizationUiApi
class PersonalizationAccordion @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val label = AppCompatTextView(context)
    private val counter = AppCompatTextView(context)
    private val chevron = AppCompatImageView(context)

    var text: CharSequence?
        get() = label.text
        set(value) {
            label.text = value
        }

    /** Число в скобках после подписи. `null` — не показывать. */
    var count: Int? = null
        set(value) {
            field = value
            counter.text = value?.let { "($it)" }
            counter.isVisible = value != null
        }

    var expanded: Boolean = false
        set(value) {
            field = value
            chevron.setImageResource(
                if (value) R.drawable.personalization_ic_angle_up
                else R.drawable.personalization_ic_angle_down
            )
        }

    /** Зовётся после переключения, уже с новым значением [expanded]. */
    var onToggle: ((Boolean) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true

        applyText(label, R.color.personalization_text_primary)
        applyText(counter, R.color.personalization_text_secondary)
        counter.isVisible = false

        addView(label)
        addView(counter, gapParams())
        addView(
            chevron,
            LayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
            }
        )
        ImageViewCompat.setImageTintList(
            chevron,
            ColorStateList.valueOf(
                PersonalizationTheme.color(context, R.color.personalization_text_primary)
            )
        )
        expanded = false

        setOnClickListener {
            expanded = !expanded
            onToggle?.invoke(expanded)
        }
    }

    private fun applyText(view: AppCompatTextView, colorRes: Int) {
        view.includeFontPadding = false
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.personalization_font_size_base)
        )
        TextViewCompat.setLineHeight(
            view,
            resources.getDimensionPixelSize(R.dimen.personalization_line_height_base)
        )
        view.setTextColor(PersonalizationTheme.color(context, colorRes))
    }

    private fun gapParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
