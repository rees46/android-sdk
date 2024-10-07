package com.personalization.inAppNotification.view.component.text

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat

class AlertText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        setupView()
    }

    private fun setupView() {
        textSize = 16f
        gravity = Gravity.START
        setTextColor(ResourcesCompat.getColor(resources, android.R.color.black, null))

        setPadding(0, 0, 0, 0)
    }
}
