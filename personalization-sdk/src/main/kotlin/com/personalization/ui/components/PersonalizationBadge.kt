package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Бейдж дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Badge (241:7434).
 *
 * Внимание: типографика бейджа не совпадает со ступенями TextAppearance.Personalization.* —
 * кегль берётся с одной ступени, интерлиньяж с другой (20/24, 16/20, 14/16).
 * Поэтому размеры заданы здесь явно, а не через стиль.
 */
class PersonalizationBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class Size(
        val paddingHorizontalDp: Int,
        val paddingVerticalDp: Int,
        val radiusRes: Int,
        val textSizeSp: Float,
        val lineHeightSp: Float
    ) {
        LG(12, 8, R.dimen.personalization_radius_button_lg, 20f, 24f),
        MD(8, 4, R.dimen.personalization_radius_button_md, 16f, 20f),
        SM(4, 2, R.dimen.personalization_radius_button_sm, 14f, 16f)
    }

    var size: Size = Size.LG
        set(value) {
            field = value
            applyStyle()
        }

    init {
        applyStyle()
    }

    private fun applyStyle() {
        gravity = Gravity.CENTER
        includeFontPadding = false
        // Inter в SDK не поставляется, ближайшее системное к Emphasized 600.
        typeface = PersonalizationTheme.typeface(context, emphasized = true)

        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.textSizeSp)
        TextViewCompat.setLineHeight(this, spToPx(size.lineHeightSp))
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_light_primary))

        val padH = dpToPx(size.paddingHorizontalDp)
        val padV = dpToPx(size.paddingVerticalDp)
        setPadding(padH, padV, padH, padV)

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PersonalizationTheme.radius(context, size.radiusRes)
            setColor(PersonalizationTheme.color(context, R.color.personalization_semantic_warning))
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
        ).toInt()
}
