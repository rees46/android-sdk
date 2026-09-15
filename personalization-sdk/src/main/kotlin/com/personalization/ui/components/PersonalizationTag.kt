package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
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
 * Тег дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Tag (243:10993).
 * В макете только размер MD, поэтому размера в API нет.
 */
@InternalPersonalizationUiApi
class PersonalizationTag @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class TagView { PRIMARY, SECONDARY }

    private val label = AppCompatTextView(context)
    private val removeIcon = AppCompatImageView(context)

    var text: CharSequence?
        get() = label.text
        set(value) {
            label.text = value
        }

    var tagView: TagView = TagView.PRIMARY
        set(value) {
            field = value
            applyStyle()
        }

    /** Задан — тег показывает крестик и зовёт колбэк по нажатию на него. */
    var onRemove: (() -> Unit)? = null
        set(value) {
            field = value
            applyStyle()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        label.includeFontPadding = false
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        TextViewCompat.setLineHeight(label, spToPx(16f))
        label.letterSpacing = 0.05f / 12f  // 0.05px при кегле 12 -> в em
        addView(label)

        removeIcon.setImageResource(R.drawable.personalization_ic_cross)
        addView(
            removeIcon,
            LayoutParams(dpToPx(16), dpToPx(16)).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
            }
        )
        removeIcon.setOnClickListener { onRemove?.invoke() }

        applyStyle()
    }

    private fun applyStyle() {
        val removable = onRemove != null
        removeIcon.isVisible = removable

        val isPrimary = tagView == TagView.PRIMARY
        val textColor = PersonalizationTheme.color(context,
            if (isPrimary) R.color.personalization_text_light_primary
            else R.color.personalization_text_primary
        )
        label.setTextColor(textColor)
        ImageViewCompat.setImageTintList(
            removeIcon,
            android.content.res.ColorStateList.valueOf(textColor)
        )

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_button_sm)
            setColor(
                PersonalizationTheme.color(context,
                    if (isPrimary) R.color.personalization_button_primary
                    else R.color.personalization_button_secondary
                )
            )
        }

        // Справа отступ меньше, когда есть крестик: 8/4 против 8/8.
        setPadding(dpToPx(8), dpToPx(4), dpToPx(if (removable) 4 else 8), dpToPx(4))
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
        ).toInt()
}
