package com.personalization.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Обратный отсчёт акции в сторис.
 *
 * Источник: Figma, Card/Story/Timer (345:14226), два вида:
 * - [Style.MERGED] — одна плашка со скруглением 12, отступами 8/12 и шагом 12;
 * - [Style.SEPARATE] — четыре плашки со скруглением 8 и шагом 8, делят ширину поровну.
 * Заливка Neutral/Neutral 100. Число — SemiBold 36 Text/Primary, единица — Regular 20
 * Text/Secondary через 2, по базовой линии.
 *
 * В ответе сторис таймера нет — компонент для хоста: он задаёт [endTimeMillis].
 */
@InternalPersonalizationUiApi
class PersonalizationStoryTimer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Style { MERGED, SEPARATE }

    var style: Style = Style.MERGED
        set(value) {
            field = value
            build()
        }

    /** Момент окончания, `System.currentTimeMillis()`-время. */
    var endTimeMillis: Long = 0L
        set(value) {
            field = value
            tick()
        }

    /** Подписи дней, часов, минут и секунд. */
    var labels: List<CharSequence> = listOf("d", "h", "m", "s")
        set(value) {
            require(value.size == UNITS) { "Timer needs $UNITS labels: days, hours, minutes, seconds" }
            field = value
            render()
        }

    /** Отсчёт дошёл до нуля. */
    var onFinished: (() -> Unit)? = null

    /** Источник времени — подменяется в тестах. */
    internal var clock: () -> Long = System::currentTimeMillis

    private val units = List(UNITS) { AppCompatTextView(context) }
    private var parts = PersonalizationStoryTimerParts(0, 0, 0, 0)
    private var finished = false
    private val ticker = Runnable { tick() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        units.forEach { unit ->
            unit.includeFontPadding = false
            unit.gravity = Gravity.CENTER
            unit.maxLines = 1
        }
        build()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun build() {
        removeAllViews()
        val merged = style == Style.MERGED
        background = if (merged) box(dp(12).toFloat()) else null
        if (merged) setPadding(dp(12), dp(8), dp(12), dp(8)) else setPadding(0, 0, 0, 0)
        units.forEachIndexed { index, unit ->
            unit.background = if (merged) null else box(dp(8).toFloat())
            if (merged) unit.setPadding(0, 0, 0, 0) else unit.setPadding(dp(12), dp(8), dp(12), dp(8))
            val params = if (merged) {
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            } else {
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            if (index > 0) params.marginStart = dp(if (merged) 12 else 8)
            addView(unit, params)
        }
        render()
    }

    private fun tick() {
        removeCallbacks(ticker)
        val remaining = endTimeMillis - clock()
        parts = PersonalizationStoryTimerParts.of(remaining)
        render()
        if (remaining > 0) {
            finished = false
            // До следующей целой секунды, чтобы цифры не отставали.
            val delay = (remaining % SECOND).takeIf { it > 0 } ?: SECOND
            if (isAttachedToWindow) postDelayed(ticker, delay)
        } else if (!finished && endTimeMillis > 0) {
            finished = true
            onFinished?.invoke()
        }
    }

    private fun render() {
        val values = listOf(parts.days, parts.hours, parts.minutes, parts.seconds)
        val numberColor = PersonalizationTheme.color(context, R.color.personalization_text_primary)
        val labelColor = PersonalizationTheme.color(context, R.color.personalization_text_secondary)
        val numberSize = spPx(36f)
        val labelSize = spPx(20f)
        units.forEachIndexed { index, unit ->
            // Дни как есть, часы, минуты и секунды — всегда две цифры: ширина не прыгает.
            val number = if (index == 0) values[index].toString() else values[index].toString().padStart(2, '0')
            val text = SpannableStringBuilder(number)
            text.setSpan(AbsoluteSizeSpan(numberSize), 0, number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(numberColor), 0, number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            PersonalizationTheme.typeface(context, emphasized = true)?.let {
                text.setSpan(StoryTypefaceSpan(it), 0, number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val start = text.length
            // Зазор 2 между числом и единицей — узкий пробел мелким кеглем.
            text.append(' ').append(labels[index])
            text.setSpan(AbsoluteSizeSpan(labelSize), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(labelColor), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            unit.typeface = PersonalizationTheme.typeface(context, emphasized = false)
            unit.text = text
        }
    }

    private fun box(radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(PersonalizationTheme.color(context, R.color.personalization_neutral_100))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun spPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    private companion object {
        const val UNITS = 4
        const val SECOND = 1000L
    }
}

/** Остаток времени по единицам таймера. Отрицательный остаток — нули. */
@InternalPersonalizationUiApi
data class PersonalizationStoryTimerParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
) {
    companion object {
        fun of(remainingMillis: Long): PersonalizationStoryTimerParts {
            // Округление вверх: пока идёт последняя секунда, на экране 1, а не 0.
            val total = if (remainingMillis <= 0) 0L else (remainingMillis + 999) / 1000
            return PersonalizationStoryTimerParts(
                days = total / 86_400,
                hours = total % 86_400 / 3_600,
                minutes = total % 3_600 / 60,
                seconds = total % 60
            )
        }
    }
}
