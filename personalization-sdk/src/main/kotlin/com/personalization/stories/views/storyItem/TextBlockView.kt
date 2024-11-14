package com.personalization.stories.views.storyItem

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import com.personalization.R
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.utils.ColorUtils
import com.personalization.ui.utils.TextUtils
import com.personalization.ui.utils.ViewUtils

class TextBlockView(context: Context) : AppCompatTextView(context) {

    @SuppressLint("ResourceAsColor")
    fun updateView(element: TextBlockElement, parentHeight: Int, parentTopOffset: Int) {
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        setLayoutParams(layoutParams)

        text = element.textInput
        setPadding(0, element.fontSize, 0, element.fontSize)
        y = parentHeight * element.yOffset / 100f + parentTopOffset
        textSize = element.fontSize.toFloat()

        TextUtils.setTypeface(context, this, element.fontType, element.isBold, element.isItalic)

        textAlignment = getTextAlignment(element.textAlign)
        setLineSpacing(lineSpacingExtra, element.textLineSpacing.toFloat())

        val colorOpacity = ColorUtils.getColorOpacity(element.textBackgroundColorOpacity)
        if (colorOpacity > 0) {
            val paddingPx = ViewUtils.dpToPx(PADDING_DP.toFloat(), context).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        ColorUtils.setBackgroundTextColor(context, this, element.textBackgroundColor, colorOpacity)
        TextUtils.setTextColor(context, this, element.textColor, R.color.white)
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
