package com.personalization.ui.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes
import com.personalization.R

object ColorUtils {

    private const val MAX_COLOR_CHANNEL_VALUE = 255

    fun getColorOpacity(percentsString: String): Int {
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

        return MAX_COLOR_CHANNEL_VALUE * percents / 100
    }

    fun setBackgroundTextColor(
        context: Context,
        textView: TextView,
        colorString: String,
        colorOpacity: Int
    ) {
        val color = getColor(context, colorString, R.color.white)

        val alphaColor = androidx.core.graphics.ColorUtils.setAlphaComponent(color, colorOpacity)

        textView.setBackgroundColor(alphaColor)
    }

    fun setBackgroundButtonColor(
        context: Context,
        button: Button,
        colorString: String,
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
}
