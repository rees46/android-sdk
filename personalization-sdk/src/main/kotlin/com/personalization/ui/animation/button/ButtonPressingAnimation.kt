@file:SuppressLint("ClickableViewAccessibility")

package com.personalization.ui.animation.button

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

fun View.addPressEffectDeclarative(
    scaleDown: Float = 0.9f,
    duration: Long = 150L
) {
    setOnClickListener {
        this.animate().scaleX(1f).scaleY(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

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
            }
        }
        false
    }
}
