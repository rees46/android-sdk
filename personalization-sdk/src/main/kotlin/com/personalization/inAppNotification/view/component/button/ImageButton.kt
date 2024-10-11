package com.personalization.inAppNotification.view.component.button

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.personalization.R

class ImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        setupView(attrs)
    }

    private fun setupView(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.InAppNotificationImageButton
        )

        val srcDrawable = typedArray.getResourceId(
            R.styleable.InAppNotificationImageButton_src,
            R.drawable.ic_close
        )
        setImageResource(srcDrawable)

        val alphaValue = typedArray.getFloat(
            R.styleable.InAppNotificationImageButton_alpha,
            0.5f
        )
        alpha = alphaValue

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val backgroundColor = typedArray.getColor(
                R.styleable.InAppNotificationImageButton_backgroundColor,
                ContextCompat.getColor(context, R.color.gray_scale)
            )
            setColor(backgroundColor)
        }
        background = backgroundDrawable

        isClickable = true
        isFocusable = true
        typedArray.recycle()
    }
}
