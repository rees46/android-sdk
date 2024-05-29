package com.personalizatio.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.ColorInt;

public class ViewUtils {

    public static final int MAX_COLOR_CHANNEL_VALUE = 255;

    public static int getColor(Context context, String colorString, @ColorInt int defaultColor) {
        if (colorString == null) {
            return context.getResources().getColor(defaultColor);
        }

        int color;
        try {
            color = Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            color = context.getResources().getColor(defaultColor);
        }

        return color;
    }

    public static void setTextColor(Context context, TextView textView, String colorString, @ColorInt int defaultColor) {
        var color = getColor(context, colorString, defaultColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textView.setTextColor(ColorStateList.valueOf(color));
        } else {
            textView.setTextColor(color);
        }
    }

    public static void setTextColor(Context context, Button button, String colorString, @ColorInt int defaultColor) {
        var color = getColor(context, colorString, defaultColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setTextColor(ColorStateList.valueOf(color));
        } else {
            button.setTextColor(color);
        }
    }

    public static void setBackgroundColor(Context context, Button button, String colorString, @ColorInt int defaultColor) {
        var backgroundColor = getColor(context, colorString, defaultColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        } else {
            button.setBackgroundColor(backgroundColor);
        }
    }

    public static int getTypefaceStyle(boolean bold, boolean italic) {
        if (bold && italic) return Typeface.BOLD_ITALIC;
        if (bold) return Typeface.BOLD;
        if (italic) return Typeface.ITALIC;
        return Typeface.NORMAL;
    }

}
