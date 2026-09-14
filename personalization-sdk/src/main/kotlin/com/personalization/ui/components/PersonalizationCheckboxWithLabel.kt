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

/**
 * Чекбокс с подписью.
 *
 * Источник: Figma Mobile SDK UI Kit, фрейм Checkbox with Label (205:10151).
 * Зазор 8, подпись 16/20 обычного начертания; в disabled подпись уходит
 * в Text/Hint.
 */
class PersonalizationCheckboxWithLabel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val checkbox = PersonalizationCheckbox(context)
    private val label = AppCompatTextView(context)

    var text: CharSequence?
        get() = label.text
        set(value) {
            label.text = value
        }

    var checkState: PersonalizationCheckbox.CheckState
        get() = checkbox.checkState
        set(value) {
            checkbox.checkState = value
        }

    var onCheckedChange: ((PersonalizationCheckbox.CheckState) -> Unit)?
        get() = checkbox.onCheckedChange
        set(value) {
            checkbox.onCheckedChange = value
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true

        addView(checkbox)
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        })

        label.includeFontPadding = false
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        TextViewCompat.setLineHeight(
            label,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics
            ).toInt()
        )

        setOnClickListener { checkbox.performClick() }
        applyEnabledState()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkbox.isEnabled = enabled
        applyEnabledState()
    }

    private fun applyEnabledState() {
        label.setTextColor(
            PersonalizationTheme.color(context,
                if (isEnabled) R.color.personalization_text_primary
                else R.color.personalization_text_hint
            )
        )
    }
}
