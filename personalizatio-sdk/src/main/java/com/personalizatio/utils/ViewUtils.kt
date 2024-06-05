package com.personalizatio.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes

object ViewUtils {
    const val MAX_COLOR_CHANNEL_VALUE: Int = 255

    fun getColor(context: Context, colorString: String?, @ColorRes defaultColor: Int): Int {
        if (colorString == null) {
            return context.resources.getColor(defaultColor)
        }
        val color = try {
            Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            context.resources.getColor(defaultColor)
        }

        return color
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

    fun getTypefaceStyle(bold: Boolean, italic: Boolean): Int {
        if (bold && italic) return Typeface.BOLD_ITALIC
        if (bold) return Typeface.BOLD
        if (italic) return Typeface.ITALIC
        return Typeface.NORMAL
    }
}
