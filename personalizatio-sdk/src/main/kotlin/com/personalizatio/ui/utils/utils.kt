package com.personalizatio.utils

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.personalizatio.R

object ViewUtils {

    const val MAX_COLOR_CHANNEL_VALUE = 255

    @FontRes
    fun getFontRes(fontType: String, bold: Boolean, italic: Boolean): Int {
        return when (fontType) {
            "serif" -> {
                when {
                    bold && italic -> R.font.droid_serif_bold_italic
                    bold -> R.font.droid_serif_bold
                    italic -> R.font.droid_serif_italic
                    else -> R.font.droid_serif_regular
                }
            }
            "sans-serif" -> {
                if (bold) R.font.droid_sans_bold else R.font.droid_sans_regular
            }
            "monospaced" -> R.font.droid_sans_mono
            else -> R.font.droid_sans_mono
        }
    }

    fun getTypefaceStyle(bold: Boolean, italic: Boolean): Int {
        return when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
    }

    fun getColorOpacity(percentsString: String): Int {
        var percents = 0

        try {
            percents = percentsString.toInt()
        } catch (e: NumberFormatException) {
            try {
                if (percentsString.isNotEmpty()) {
                    percents = percentsString.substring(0, percentsString.length - 1).toInt()
                }
            } catch (ignored: NumberFormatException) {}
        }

        return MAX_COLOR_CHANNEL_VALUE * percents / 100
    }

    fun setBackgroundColor(view: AppCompatTextView, colorString: String, colorOpacity: Int, context: Context) {
        var correctColorString = colorString
        if (!colorString.startsWith("#")) {
            correctColorString = "#FFFFFF"
        }

        var colorOpacityValueString = colorOpacity.toString(16)
        if (colorOpacityValueString.length == 1) colorOpacityValueString = "0$colorOpacityValueString"

        val fullColorString = correctColorString.replace("#", "#$colorOpacityValueString")

        val color = getColor(context, fullColorString, R.color.white)
        view.setBackgroundColor(color)
    }

    fun getColor(context: Context, colorString: String, defaultColorRes: Int): Int {
        return try {
            android.graphics.Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            context.getColor(defaultColorRes)
        }
    }

    fun dpToPx(dp: Float, context: Context): Float {
        return dp * context.resources.displayMetrics.density
    }
}
