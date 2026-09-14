package com.personalization.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Кнопка дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Button (90:862), фрейм 90:869.
 * Матрица: 3 размера x 3 вида x 3 состояния x 4 конфигурации контента.
 *
 * Состояние Focus из макета — это нажатие: рисуется через state_pressed,
 * отдельным свойством не управляется. Disabled — обычный [setEnabled].
 */
class PersonalizationButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Size(
        val fontSizeRes: Int,
        val lineHeightRes: Int,
        val letterSpacingPx: Float,
        val radiusRes: Int,
        val paddingVerticalRes: Int,
        /** Горизонтальный отступ со стороны без иконки. */
        val paddingWideRes: Int,
        /** Горизонтальный отступ со стороны с иконкой. */
        val paddingNarrowRes: Int,
        /** Горизонтальный отступ кнопки-иконки: у LG он 12 при вертикальном 8, у остальных равен вертикальному. */
        val paddingIconOnlyRes: Int,
        val iconSizeDp: Int
    ) {
        // SM берёт кегль со ступени sm, а интерлиньяж со ступени base:
        // в макете 14/24, тогда как ступень sm — это 14/20.
        LG(
            R.dimen.personalization_font_size_xl,
            R.dimen.personalization_line_height_xl,
            0f,
            R.dimen.personalization_radius_button_lg,
            R.dimen.personalization_spacing_md,
            R.dimen.personalization_spacing_xl2,
            R.dimen.personalization_spacing_xl,
            R.dimen.personalization_spacing_lg,
            32
        ),
        MD(
            R.dimen.personalization_font_size_base,
            R.dimen.personalization_line_height_base,
            0f,
            R.dimen.personalization_radius_button_md,
            R.dimen.personalization_spacing_md,
            R.dimen.personalization_spacing_xl,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_spacing_md,
            24
        ),
        SM(
            R.dimen.personalization_font_size_sm,
            R.dimen.personalization_line_height_base,
            0.05f,
            R.dimen.personalization_radius_button_sm,
            R.dimen.personalization_spacing_sm,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_spacing_md,
            R.dimen.personalization_spacing_sm,
            20
        )
    }

    enum class ButtonView { PRIMARY, SECONDARY, GHOST }

    private val label = AppCompatTextView(context)
    private val iconStartView = AppCompatImageView(context)
    private val iconEndView = AppCompatImageView(context)

    var text: CharSequence? = null
        set(value) {
            field = value
            label.text = value
            label.isVisible = !value.isNullOrEmpty()
            applyStyle()
        }

    var size: Size = Size.LG
        set(value) {
            field = value
            applyStyle()
        }

    var buttonView: ButtonView = ButtonView.PRIMARY
        set(value) {
            field = value
            applyStyle()
        }

    /** Иконка перед текстом. Без текста кнопка становится кнопкой-иконкой. */
    @DrawableRes
    var iconStart: Int? = null
        set(value) {
            field = value
            value?.let(iconStartView::setImageResource)
            iconStartView.isVisible = value != null
            applyStyle()
        }

    /** Иконка после текста. */
    @DrawableRes
    var iconEnd: Int? = null
        set(value) {
            field = value
            value?.let(iconEndView::setImageResource)
            iconEndView.isVisible = value != null
            applyStyle()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true

        label.includeFontPadding = false
        label.gravity = Gravity.CENTER
        // Inter в SDK не поставляется, ближайшее системное к Emphasized 600.
        label.typeface = PersonalizationTheme.typeface(context, emphasized = true)
        // Цвет текста и тинт иконок приходят из ColorStateList родителя.
        label.isDuplicateParentStateEnabled = true
        iconStartView.isDuplicateParentStateEnabled = true
        iconEndView.isDuplicateParentStateEnabled = true

        addView(iconStartView)
        addView(label)
        addView(iconEndView)

        iconStartView.isVisible = false
        iconEndView.isVisible = false
        label.isVisible = false

        applyStyle()
    }

    private fun applyStyle() {
        applyTypography()
        applyColors()
        applyMetrics()
    }

    private fun applyTypography() {
        label.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(size.fontSizeRes)
        )
        TextViewCompat.setLineHeight(label, resources.getDimensionPixelSize(size.lineHeightRes))
        // В макете трекинг задан в px, у Android он в em.
        label.letterSpacing = if (size.letterSpacingPx == 0f) {
            0f
        } else {
            size.letterSpacingPx / pxToSp(resources.getDimension(size.fontSizeRes))
        }
    }

    private fun applyColors() {
        val backgroundDefault = when (buttonView) {
            // Кнопка привязана к Brand/Primary, а не к Button/Primary — так в макете.
            ButtonView.PRIMARY -> R.color.personalization_brand_primary
            ButtonView.SECONDARY -> R.color.personalization_button_secondary
            ButtonView.GHOST -> R.color.personalization_background_transparent
        }
        val backgroundPressed = when (buttonView) {
            ButtonView.PRIMARY -> R.color.personalization_button_primary_focus
            // Ghost в нажатии красится тем же, что и Secondary.
            ButtonView.SECONDARY, ButtonView.GHOST -> R.color.personalization_button_secondary_focus
        }
        val backgroundDisabled = when (buttonView) {
            ButtonView.PRIMARY -> R.color.personalization_button_primary_disabled
            ButtonView.SECONDARY -> R.color.personalization_button_secondary_disabled
            ButtonView.GHOST -> R.color.personalization_background_transparent
        }

        val radius = PersonalizationTheme.radius(context, size.radiusRes)
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), fill(backgroundDisabled, radius))
            addState(intArrayOf(android.R.attr.state_pressed), fill(backgroundPressed, radius))
            addState(intArrayOf(), fill(backgroundDefault, radius))
        }

        val foregroundDefault = if (buttonView == ButtonView.PRIMARY) {
            R.color.personalization_text_light_primary
        } else {
            R.color.personalization_text_primary
        }
        val foregroundDisabled = if (buttonView == ButtonView.PRIMARY) {
            R.color.personalization_text_light_hint
        } else {
            R.color.personalization_text_hint
        }
        val foreground = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(
                PersonalizationTheme.color(context, foregroundDisabled),
                PersonalizationTheme.color(context, foregroundDefault)
            )
        )
        label.setTextColor(foreground)
        ImageViewCompat.setImageTintList(iconStartView, foreground)
        ImageViewCompat.setImageTintList(iconEndView, foreground)
    }

    private fun applyMetrics() {
        val iconSize = dpToPx(size.iconSizeDp)
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        iconStartView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = if (label.isVisible) gap else 0
        }
        iconEndView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginStart = if (label.isVisible) gap else 0
        }

        val vertical = resources.getDimensionPixelSize(size.paddingVerticalRes)
        val wide = resources.getDimensionPixelSize(size.paddingWideRes)
        val narrow = resources.getDimensionPixelSize(size.paddingNarrowRes)

        if (!label.isVisible) {
            // Кнопка-иконка: по вертикали как у текстовой, по горизонтали своё — 12/8/4.
            val horizontal = resources.getDimensionPixelSize(size.paddingIconOnlyRes)
            setPadding(horizontal, vertical, horizontal, vertical)
            return
        }
        setPadding(
            if (iconStartView.isVisible) narrow else wide,
            vertical,
            if (iconEndView.isVisible) narrow else wide,
            vertical
        )
    }

    private fun fill(colorRes: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(PersonalizationTheme.color(context, colorRes))
        }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun pxToSp(px: Float): Float =
        px / resources.displayMetrics.scaledDensity
}
