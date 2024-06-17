package com.personalizatio.stories.views.storyItem

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import com.personalizatio.R
import com.personalizatio.stories.models.elements.TextBlockElement
import com.personalizatio.utils.ViewUtils

class TextBlockView(context: Context) : AppCompatTextView(context) {

    @SuppressLint("ResourceAsColor")
    fun updateView(element: TextBlockElement, parentHeight: Int, parentTopOffset: Int) {
        val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        setLayoutParams(layoutParams)

        text = element.textInput
        setPadding(0, element.fontSize, 0, element.fontSize)
        y = parentHeight * element.yOffset / 100f + parentTopOffset
        textSize = element.fontSize.toFloat()

        val typeface = ResourcesCompat.getFont(
            context,
            ViewUtils.getFontRes(element.fontType, element.isBold, element.isItalic)
        )
        setTypeface(typeface, ViewUtils.getTypefaceStyle(element.isBold, element.isItalic))

        textAlignment = getTextAlignment(element.textAlign)
        setLineSpacing(lineSpacingExtra, element.textLineSpacing.toFloat())

        val colorOpacity = ViewUtils.getColorOpacity(element.textBackgroundColorOpacity)
        if (colorOpacity > 0) {
            val paddingPx = ViewUtils.dpToPx(PADDING_DP.toFloat(), context).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        ViewUtils.setBackgroundColor(this, element.textBackgroundColor, colorOpacity, context)
        ViewUtils.setTextColor(context, this, element.textColor, R.color.white)
    }

    companion object {
        private const val PADDING_DP = 16

        private fun getTextAlignment(textAlign: String): Int {
            return when (textAlign) {
                "center" -> TEXT_ALIGNMENT_CENTER
                "right" -> TEXT_ALIGNMENT_TEXT_END
                else -> TEXT_ALIGNMENT_TEXT_START
            }
        }
    }
}
