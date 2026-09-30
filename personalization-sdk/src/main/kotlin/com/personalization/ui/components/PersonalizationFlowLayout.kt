package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Ряд с переносом: дети идут слева направо и переходят на новую строку,
 * когда не влезают. Нужен тегам подсказок поиска — в макете они лежат
 * во flex-wrap с шагом 8 по обеим осям, а не в горизонтальном скролле.
 */
internal class PersonalizationFlowLayout(
    context: Context,
    private val horizontalGap: Int,
    private val verticalGap: Int
) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = 0
        var widest = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val width = child.measuredWidth
            val height = child.measuredHeight
            if (lineWidth > 0 && lineWidth + horizontalGap + width > maxWidth) {
                totalHeight += lineHeight + verticalGap
                widest = maxOf(widest, lineWidth)
                lineWidth = width
                lineHeight = height
            } else {
                lineWidth += (if (lineWidth > 0) horizontalGap else 0) + width
                lineHeight = maxOf(lineHeight, height)
            }
        }
        totalHeight += lineHeight
        widest = maxOf(widest, lineWidth)
        setMeasuredDimension(
            resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val width = child.measuredWidth
            val height = child.measuredHeight
            if (x > paddingLeft && x - paddingLeft + horizontalGap + width > maxWidth) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            } else if (x > paddingLeft) {
                x += horizontalGap
            }
            child.layout(x, y, x + width, y + height)
            x += width
            lineHeight = maxOf(lineHeight, height)
        }
    }
}
