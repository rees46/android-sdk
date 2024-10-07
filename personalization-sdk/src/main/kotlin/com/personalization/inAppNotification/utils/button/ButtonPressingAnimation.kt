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
    var isAnimating = false
    var isClicked = false

    this.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isAnimating) {
                    isAnimating = true
                    this.animate().scaleX(scaleDown).scaleY(scaleDown)
                        .setDuration(duration)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            isAnimating = false
                        }
                        .start()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isAnimating) {
                    isAnimating = true
                    this.animate().scaleX(1f).scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            isAnimating = false
                        }
                        .start()

                    if (!isClicked) {
                        this.performClick()
                        isClicked = true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isAnimating = false
            }
        }

        this.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            isClicked = false
        }

        true
    }
}
