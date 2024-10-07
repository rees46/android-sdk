package com.personalization.inAppNotification.utils.button

import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

private const val buttonPressScale = 0.9f
private const val buttonPressDuration = 5L

fun View.addPressEffect(
    scaleDown: Float = buttonPressScale,
    duration: Long = buttonPressDuration
) {
    this.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                this.animate().scaleX(scaleDown).scaleY(scaleDown)
                    .setDuration(duration)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                this.animate().scaleX(1f).scaleY(1f)
                    .setDuration(duration)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                this.performClick()
            }
        }
        true
    }
}
