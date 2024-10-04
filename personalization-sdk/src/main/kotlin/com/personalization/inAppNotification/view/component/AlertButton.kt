package com.personalization.inAppNotification.view.component

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.personalization.R

class AlertButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    init {
        setupView(attrs)
    }

    private fun setupView(attrs: AttributeSet?) {
        val typedArray: TypedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.AlertButton
        )

        val buttonColor = typedArray.getColor(
            R.styleable.AlertButton_buttonColor,
            ContextCompat.getColor(context, R.color.colorPrimary)
        )
        setBackgroundColor(buttonColor)

        val textColor = typedArray.getColor(
            R.styleable.AlertButton_textColor,
            ContextCompat.getColor(context, android.R.color.white)
        )
        setTextColor(textColor)

        val textSize = typedArray.getDimension(
            R.styleable.AlertButton_textSize,
            16f
        )
        setTextSize(pxToSp(context, textSize))

        text = typedArray.getString(R.styleable.AlertButton_buttonText)

        gravity = Gravity.CENTER
        textAlignment = TEXT_ALIGNMENT_CENTER

        typedArray.recycle()
    }

    private fun pxToSp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.scaledDensity
    }
}