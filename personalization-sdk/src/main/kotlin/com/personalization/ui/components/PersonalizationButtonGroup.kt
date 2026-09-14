package com.personalization.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Группа кнопок (сегментированный переключатель).
 *
 * Источник: Figma Mobile SDK UI Kit, секция Button Group (185:5253):
 * фрейм Base Button (185:5315) — сегмент, фрейм Button Group (185:5484) — сама группа.
 *
 * В макете нарисован только случай на два сегмента (Grid/List) в размере MD,
 * сегмент же есть и в MD, и в SM — поэтому размер вынесен в API,
 * а число сегментов не ограничено.
 */
class PersonalizationButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Size(
        val radiusRes: Int,
        /** Отступ сегмента: 6dp в MD и 2dp в SM, обоих значений нет в шкале спейсингов. */
        val paddingDp: Int,
        val iconSizeDp: Int
    ) {
        MD(R.dimen.personalization_radius_md, 6, 24),
        SM(R.dimen.personalization_radius_sm, 2, 20)
    }

    /**
     * Сегмент группы.
     *
     * @param activeIcon иконка выбранного сегмента. В макете Grid оставляет ту же
     * иконку, а List подменяет её на залитую — поэтому это отдельный параметр.
     */
    data class Item(
        @DrawableRes val icon: Int,
        @DrawableRes val activeIcon: Int = icon
    )

    var size: Size = Size.MD
        set(value) {
            field = value
            rebuild()
        }

    var items: List<Item> = emptyList()
        set(value) {
            field = value
            if (selectedIndex >= value.size) selectedIndex = 0
            rebuild()
        }

    var selectedIndex: Int = 0
        set(value) {
            field = value
            applySelection()
        }

    /** Зовётся при выборе сегмента пользователем. */
    var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        val inset = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xs)
        setPadding(inset, inset, inset, inset)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_lg)
            setColor(PersonalizationTheme.color(context, R.color.personalization_button_secondary))
        }
    }

    private fun rebuild() {
        removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xs)
        val padding = dpToPx(size.paddingDp)
        val iconSize = dpToPx(size.iconSizeDp)

        items.forEachIndexed { index, _ ->
            val segment = AppCompatImageView(context)
            segment.setPadding(padding, padding, padding, padding)
            segment.isClickable = true
            segment.setOnClickListener {
                selectedIndex = index
                onSelected?.invoke(index)
            }
            addView(
                segment,
                LayoutParams(iconSize + padding * 2, iconSize + padding * 2).apply {
                    if (index > 0) marginStart = gap
                }
            )
        }
        applySelection()
    }

    private fun applySelection() {
        val radius = PersonalizationTheme.radius(context, size.radiusRes)
        items.forEachIndexed { index, item ->
            val segment = getChildAt(index) as? AppCompatImageView ?: return@forEachIndexed
            val active = index == selectedIndex
            segment.setImageResource(if (active) item.activeIcon else item.icon)
            segment.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(
                    PersonalizationTheme.color(context,
                        if (active) R.color.personalization_button_primary
                        else R.color.personalization_background_transparent
                    )
                )
            }
            ImageViewCompat.setImageTintList(
                segment,
                ColorStateList.valueOf(
                    PersonalizationTheme.color(context,
                        if (active) R.color.personalization_text_inverted_primary
                        else R.color.personalization_text_hint
                    )
                )
            )
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
