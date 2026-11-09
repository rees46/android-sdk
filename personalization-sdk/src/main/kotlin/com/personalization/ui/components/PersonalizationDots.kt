package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Точки-индикатор карусели.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Navigation (90:660),
 * символы Dots (90:669) и Dot (90:685).
 * В макете нарисовано пять точек, число вынесено в API.
 */
class PersonalizationDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var count: Int = 0
        set(value) {
            field = value
            if (selectedIndex >= value) selectedIndex = 0
            rebuild()
        }

    var selectedIndex: Int = 0
        set(value) {
            field = value
            applySelection()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val vertical = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        setPadding(0, vertical, 0, vertical)
    }

    private fun rebuild() {
        removeAllViews()
        val size = dpToPx(DOT_SIZE_DP)
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        repeat(count) { index ->
            addView(
                View(context),
                LayoutParams(size, size).apply {
                    if (index > 0) marginStart = gap
                }
            )
        }
        applySelection()
    }

    private fun applySelection() {
        for (index in 0 until childCount) {
            getChildAt(index).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    PersonalizationTheme.color(context,
                        if (index == selectedIndex) R.color.personalization_brand_primary
                        else R.color.personalization_line_generic
                    )
                )
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private companion object {
        const val DOT_SIZE_DP = 16
    }
}
