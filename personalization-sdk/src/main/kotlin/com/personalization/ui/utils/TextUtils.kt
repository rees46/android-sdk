package com.personalization.ui.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.personalization.R

object TextUtils {

    fun setTypeface(
        context: Context,
        textView: TextView,
        fontType: String,
        bold: Boolean,
        italic: Boolean
    ) {
        val typeface = ResourcesCompat.getFont(context, getFontRes(fontType, bold, italic))
        textView.setTypeface(typeface, getTypefaceStyle(bold, italic))
    }

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

    fun setTextColor(
        context: Context,
        textView: TextView,
        colorString: String?,
        @ColorRes defaultColor: Int
    ) {
        val color = ColorUtils.getColor(context, colorString, defaultColor)

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
        val color = ColorUtils.getColor(context, colorString, defaultColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setTextColor(ColorStateList.valueOf(color))
        } else {
            button.setTextColor(color)
        }
    }
}
