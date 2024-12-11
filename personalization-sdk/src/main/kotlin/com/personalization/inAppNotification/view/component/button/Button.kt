package com.personalization.inAppNotification.view.component.button

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.personalization.R

class Button @JvmOverloads constructor(
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
            R.styleable.InAppNotificationButton
        )

        val buttonColor: Int = typedArray.getColor(
            R.styleable.InAppNotificationButton_buttonColor,
            ContextCompat.getColor(context, R.color.colorPrimary)
        )
        setBackgroundColor(buttonColor)

        val textColor: Int = typedArray.getColor(
            R.styleable.InAppNotificationButton_textColor,
            ContextCompat.getColor(context, android.R.color.white)
        )
        setTextColor(textColor)

        val textSize = typedArray.getDimension(
            R.styleable.InAppNotificationButton_textSize,
            16f
        )
        setTextSize(pxToSp(context, textSize))

        text = typedArray.getString(R.styleable.InAppNotificationButton_buttonText)

        gravity = Gravity.CENTER
        textAlignment = TEXT_ALIGNMENT_CENTER

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val rippleColor = ContextCompat.getColor(context, R.color.colorGray)
            val colorStateList = ColorStateList.valueOf(rippleColor)
            val rippleDrawable = RippleDrawable(colorStateList, ColorDrawable(buttonColor), null)
            background = rippleDrawable
        } else {
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.LTGRAY))
            states.addState(intArrayOf(), ColorDrawable(buttonColor))
            background = states
        }

        typedArray.recycle()
    }

    private val Int.dpToPx: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private fun pxToSp(context: Context, px: Float): Float {
        return px / TypedValue.applyDimension(
            /* unit = */ TypedValue.COMPLEX_UNIT_SP,
            /* value = */ 1f,
            /* metrics = */ context.resources.displayMetrics
        )
    }
}
