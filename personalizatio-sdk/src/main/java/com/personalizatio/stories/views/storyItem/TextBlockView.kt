package com.personalizatio.stories.views.storyItem

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.annotation.FontRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.personalizatio.R
import com.personalizatio.stories.models.elements.TextBlockElement
import com.personalizatio.utils.ViewUtils

class TextBlockView(context: Context) : AppCompatTextView(context) {
    @SuppressLint("ResourceAsColor")
    fun updateView(element: TextBlockElement, parentHeight: Int, parentTopOffset: Int) {
        val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        setLayoutParams(layoutParams)

        text = element.textInput

        val fontSize = element.fontSize

        setPadding(0, fontSize, 0, fontSize)

        y = parentHeight * element.yOffset / 100f + parentTopOffset

        textSize = fontSize.toFloat()

        val typeface = ResourcesCompat.getFont(
            context,
            getFontRes(element.fontType, element.isBold, element.isItalic)
        )
        setTypeface(typeface, ViewUtils.getTypefaceStyle(element.isBold, element.isItalic))

        textAlignment = getTextAlignment(element.textAlign)

        setLineSpacing(lineHeight.toFloat(), element.textLineSpacing.toFloat())

        setBackgroundColor(element.textBackgroundColor, element.textBackgroundColorOpacity)
        ViewUtils.setTextColor(context, this, element.textColor, R.color.white)
    }

    @FontRes
    private fun getFontRes(fontType: String, bold: Boolean, italic: Boolean): Int {
        when (fontType) {
            "serif" -> {
                if (bold && italic) return R.font.droid_serif_bold_italic
                if (bold) return R.font.droid_serif_bold
                if (italic) return R.font.droid_serif_italic
                return R.font.droid_serif_regular
            }

            "sans-serif" -> {
                if (bold) return R.font.droid_sans_bold
                return R.font.droid_sans_regular
            }

            "monospaced" -> {
                return R.font.droid_sans_mono
            }

            else -> {
                return R.font.droid_sans_mono
            }
        }
    }

    @SuppressLint("ResourceAsColor")
    private fun setBackgroundColor(colorString: String, colorOpacityString: String) {
        var correctColorString = colorString
        if (!colorString.startsWith("#")) {
            correctColorString = "#FFFFFF"
        }

        val colorOpacity = getColorOpacity(colorOpacityString)
        var colorOpacityValueString = colorOpacity.toString(16)
        if (colorOpacityValueString.length == 1) colorOpacityValueString = "0$colorOpacityValueString"

        val fullColorString = correctColorString.replace("#", "#$colorOpacityValueString")

        val color = ViewUtils.getColor(context, fullColorString, R.color.white)
        setBackgroundColor(color)
    }

    private fun getColorOpacity(percentsString: String): Int {
        var percents = 0

        try {
            percents = percentsString.toInt()
        } catch (e: NumberFormatException) {
            try {
                if (percentsString.isNotEmpty()) {
                    percents = percentsString.substring(0, percentsString.length - 1).toInt()
                }
            } catch (ignored: NumberFormatException) {
            }
        }

        return ViewUtils.MAX_COLOR_CHANNEL_VALUE * percents / 100
    }

    companion object {
        private fun getTextAlignment(textAlign: String): Int {
            return when (textAlign) {
                "center" -> TEXT_ALIGNMENT_CENTER
                "right" -> TEXT_ALIGNMENT_TEXT_END
                else -> TEXT_ALIGNMENT_TEXT_START
            }
        }
    }
}
