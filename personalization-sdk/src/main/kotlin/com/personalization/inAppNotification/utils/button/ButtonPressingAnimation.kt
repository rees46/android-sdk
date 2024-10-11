import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

fun View.addPressEffectDeclarative(
    scaleDown: Float = 0.9f,
    duration: Long = 150L
) {
    setOnClickListener {
        // Плавное возвращение к оригинальному размеру после клика
        this.animate().scaleX(1f).scaleY(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    // Анимация нажатия
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
