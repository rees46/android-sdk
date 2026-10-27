package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Два ребёнка в строку, а когда вместе не влезают — второй переносится под первый.
 * Нужен list-карточке товара: в узкой колонке (экран 360 dp, крупный системный шрифт)
 * цена с бейджем скидки и кнопка иначе делят ширину, и бейдж ломается по буквам.
 *
 * В строке дети выровнены по центру по вертикали, второй стоит через [horizontalGap] после
 * первого, а с [pinEnd] — у правого края. После переноса второй лежит под первым через
 * [verticalGap]: у левого края, а с [pinEnd] — у правого. Ребёнок шире ряда ужимается до него.
 */
internal class PersonalizationWrapRow(
    context: Context,
    private val horizontalGap: Int,
    private val verticalGap: Int,
    private val pinEnd: Boolean
) : ViewGroup(context) {

    /** Перенесён ли второй ребёнок по последнему замеру. */
    var isWrapped: Boolean = false
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        val available = if (bounded) MeasureSpec.getSize(widthMeasureSpec) else Int.MAX_VALUE
        val childWidthSpec =
            if (bounded) MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)
            else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val shown = shownChildren()
        shown.forEach { it.measure(childWidthSpec, childHeightSpec) }

        val first = shown.getOrNull(0)
        val second = shown.getOrNull(1)
        isWrapped = first != null && second != null &&
            first.measuredWidth.toLong() + horizontalGap + second.measuredWidth > available
        val (width, height) = when {
            first == null -> 0 to 0
            second == null -> first.measuredWidth to first.measuredHeight
            isWrapped -> maxOf(first.measuredWidth, second.measuredWidth) to
                first.measuredHeight + verticalGap + second.measuredHeight
            else -> first.measuredWidth + horizontalGap + second.measuredWidth to
                maxOf(first.measuredHeight, second.measuredHeight)
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val shown = shownChildren()
        val first = shown.getOrNull(0) ?: return
        val second = shown.getOrNull(1)
        if (second == null) {
            first.layout(0, 0, first.measuredWidth, first.measuredHeight)
            return
        }
        if (isWrapped) {
            first.layout(0, 0, first.measuredWidth, first.measuredHeight)
            val top = first.measuredHeight + verticalGap
            val left = if (pinEnd) width - second.measuredWidth else 0
            second.layout(left, top, left + second.measuredWidth, top + second.measuredHeight)
            return
        }
        val lineHeight = maxOf(first.measuredHeight, second.measuredHeight)
        val firstTop = (lineHeight - first.measuredHeight) / 2
        first.layout(0, firstTop, first.measuredWidth, firstTop + first.measuredHeight)
        val left = if (pinEnd) width - second.measuredWidth else first.measuredWidth + horizontalGap
        val secondTop = (lineHeight - second.measuredHeight) / 2
        second.layout(left, secondTop, left + second.measuredWidth, secondTop + second.measuredHeight)
    }

    private fun shownChildren(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
}
