package com.personalization.ui.components

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Тост — короткое уведомление плашкой поверх экрана.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Toast (367:16179), компонент Toast (367:16178);
 * на странице Stories — уведомление о скопированном промокоде снизу (367:16063)
 * и сверху (367:16106).
 *
 * Плашка-пилюля: радиус Toast (Rounded), фон Background/Float, тень Elevation 2, поля 16
 * по вертикали и 20 по горизонтали, текст 18/18 начертанием Medium. В макете текст залит
 * сырым чёрным без переменной — здесь Text/Primary, иначе в тёмной теме он пропал бы
 * на фоне Float.
 *
 * Сама по себе это обычная view, её можно положить в разметку. Показ поверх экрана — [show]:
 * по центру, на 16 от края безопасной зоны, с затуханием, и через [durationMs] прячется
 * сам. Новый тост сменяет тот, что ещё на экране, — очереди нет. Касания проходят сквозь.
 */
@InternalPersonalizationUiApi
class PersonalizationToast @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    /** У какого края экрана показывать. */
    enum class Position { TOP, BOTTOM }

    private val hideRunnable = Runnable { hide() }

    /** Показан через [show], а не положен хостом в разметку, — такой сменяется следующим. */
    private var presented = false

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        // Medium 500 — в ките это начертание Emphasized.
        typeface = PersonalizationTheme.typeface(context, emphasized = true)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        TextViewCompat.setLineHeight(this, spToPx(18f))
        setTextColor(PersonalizationTheme.color(context, R.color.personalization_text_primary))

        val padH = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl2)
        val padV = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        setPadding(padH, padV, padH, padV)

        val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_toast)
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(PersonalizationTheme.color(context, R.color.personalization_background_float))
        }
        // Тень рисуется по контуру; радиус больше половины высоты контур не примет,
        // поэтому он ограничен здесь, а не в ресурсе.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = minOf(radius, minOf(view.width, view.height) / 2f)
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            elevation = resources.getDimension(R.dimen.personalization_elevation_e2)
        }
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
    }

    /** Спрятать раньше срока: затухание и снятие с экрана. */
    fun hide() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).withEndAction { removeFromParent() }
    }

    private fun removeFromParent() {
        removeCallbacks(hideRunnable)
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun spToPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    companion object {

        private const val FADE_MS = 200L

        /**
         * Показать тост поверх окна, в котором лежит [anchor], — экрана или диалога
         * (сторис показываются диалогом, и тост должен быть над ним).
         *
         * Отступ от края — 16 плюс та часть системных панелей и клавиатуры, что заходит
         * на контент: окно edge-to-edge получит отступ от статус-бара, обычное — нет.
         *
         * Возвращает показанный тост, чтобы его можно было спрятать раньше; null — если
         * у окна нет контейнера, куда его положить.
         */
        @JvmStatic
        @JvmOverloads
        fun show(
            anchor: View,
            text: CharSequence,
            position: Position = Position.BOTTOM,
            durationMs: Long = 2000L
        ): PersonalizationToast? {
            val root = anchor.rootView
            val container = root.findViewById<View>(android.R.id.content) as? FrameLayout
                ?: root as? FrameLayout
                ?: return null

            // Новый сменяет прежний сразу, без затухания — два тоста на экране не нужны.
            for (i in container.childCount - 1 downTo 0) {
                val child = container.getChildAt(i)
                if (child is PersonalizationToast && child.presented) {
                    child.removeFromParent()
                }
            }

            val toast = PersonalizationToast(anchor.context)
            toast.text = text
            toast.presented = true
            val margin = anchor.resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
            val (insetTop, insetBottom) = overlap(container)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or
                    if (position == Position.TOP) Gravity.TOP else Gravity.BOTTOM
            ).apply {
                leftMargin = margin
                rightMargin = margin
                topMargin = margin + if (position == Position.TOP) insetTop else 0
                bottomMargin = margin + if (position == Position.BOTTOM) insetBottom else 0
            }
            toast.alpha = 0f
            container.addView(toast, params)
            toast.animate().alpha(1f).setDuration(FADE_MS)
            toast.postDelayed(toast.hideRunnable, durationMs)
            toast.announceForAccessibility(text)
            return toast
        }

        /**
         * Насколько системные панели, вырез и клавиатура заходят на контейнер сверху и снизу.
         * Считается от положения контейнера в окне, поэтому годится и для edge-to-edge,
         * и для окна, где контент уже начинается под статус-баром.
         */
        private fun overlap(container: View): Pair<Int, Int> {
            val insets = ViewCompat.getRootWindowInsets(container) ?: return 0 to 0
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            val location = IntArray(2)
            container.getLocationInWindow(location)
            val windowHeight = container.rootView.height
            val top = maxOf(0, bars.top - location[1])
            val bottom = maxOf(0, bars.bottom - (windowHeight - location[1] - container.height))
            return top to bottom
        }
    }
}
