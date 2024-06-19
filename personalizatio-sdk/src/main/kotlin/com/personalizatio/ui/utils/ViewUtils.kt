package com.personalizatio.ui.utils

import android.content.Context

object ViewUtils {

    fun dpToPx(dp: Float, context: Context): Float {
        return dp * context.resources.displayMetrics.density
    }
}
