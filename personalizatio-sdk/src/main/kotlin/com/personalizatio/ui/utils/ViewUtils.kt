package com.personalizatio.ui.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import com.personalizatio.R

object ViewUtils {

    private const val MAX_COLOR_CHANNEL_VALUE = 255

    @FontRes
    fun getFontRes(
        fontType: String,
        bold: Boolean,
        italic: Boolean
    ): Int {
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

    fun setBackgroundColor(
        view: TextView,
        colorString: String,
        colorOpacity: Int,
        context: Context
    ) {
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

    fun setBackgroundColor(
        context: Context,
        button: Button,
        colorString: String?,
        @ColorRes defaultColor: Int
    ) {
        val backgroundColor = getColor(context, colorString, defaultColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        } else {
            button.setBackgroundColor(backgroundColor)
        }
    }

    fun getColor(
        context: Context,
        colorString: String?,
        @ColorRes defaultColorRes: Int
    ): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getColor(defaultColorRes)
            } else {
                context.resources.getColor(defaultColorRes)
            }
        }
    }

    fun setTextColor(
        context: Context,
        textView: TextView,
        colorString: String?,
        @ColorRes defaultColor: Int
    ) {
        val color = getColor(context, colorString, defaultColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textView.setTextColor(ColorStateList.valueOf(color))
        } else {
            textView.setTextColor(color)
        }
    }

    fun setTextColor(
        context: Context,
        button: Button,
        colorString: String?,
        @ColorRes defaultColor: Int
    ) {
        val color = getColor(context, colorString, defaultColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setTextColor(ColorStateList.valueOf(color))
        } else {
            button.setTextColor(color)
        }
    }

    fun dpToPx(dp: Float, context: Context): Float {
        return dp * context.resources.displayMetrics.density
    }
}
