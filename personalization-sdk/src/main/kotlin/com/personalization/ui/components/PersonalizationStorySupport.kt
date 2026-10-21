package com.personalization.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.AttributeSet
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageView
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Общие куски сторис: разбор цены на сумму и валюту, скелетон, круглая картинка,
 * принудительно тёмный контекст для медиа-слайдов.
 */
internal object StorySupport {

    /**
     * Скелетон в макете — градиент чёрного 5% → 30% → 5%. Цвет берётся из Text/Primary,
     * чтобы в тёмной теме полоса была белой: чёрная на тёмном фоне не видна.
     */
    fun skeletonColors(context: Context): IntArray {
        val base = PersonalizationTheme.color(context, R.color.personalization_text_primary)
        fun alpha(value: Int) = Color.argb(value, Color.red(base), Color.green(base), Color.blue(base))
        return intArrayOf(alpha(13), alpha(77), alpha(13))
    }

    fun skeletonShader(context: Context, width: Float, height: Float): Shader =
        LinearGradient(0f, 0f, width, height, skeletonColors(context), null, Shader.TileMode.CLAMP)

    fun skeletonDrawable(context: Context, shape: Int, radius: Float = 0f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, skeletonColors(context)).apply {
            this.shape = shape
            if (shape == GradientDrawable.RECTANGLE) cornerRadius = radius
        }

    /**
     * Контекст с тёмными токенами для поддерева: медиа-слайды сторис в макете
     * всегда в режиме Dark — белые прогресс, крестик и текст, тёмная шторка товаров.
     *
     * Тема хоста сохраняется (брендовые цвета остаются), а ресурсы берутся из
     * `values-night`: [PersonalizationTheme] резолвит атрибут в id ресурса и читает
     * значение уже через ресурсы этого контекста.
     */
    fun darkContext(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES
        }
        return ContextThemeWrapper(base, base.theme).apply {
            applyOverrideConfiguration(configuration)
        }
    }
}

/**
 * Цена, как её отдаёт сервер (`"1 190 900 ₽"`), разбитая на сумму и валюту:
 * в макете валюта набрана мельче и обычным начертанием.
 */
@InternalPersonalizationUiApi
data class PersonalizationStoryAmount(val amount: String, val currency: String?) {

    companion object {
        /**
         * Валюта — последний токен через пробел, если в нём нет цифр
         * (`"19 990 ₽"` → `19 990` + `₽`). Иначе строка остаётся целиком.
         */
        fun parse(formatted: CharSequence): PersonalizationStoryAmount {
            val text = formatted.toString().trim()
            val space = text.lastIndexOf(' ')
            if (space <= 0) return PersonalizationStoryAmount(text, null)
            val tail = text.substring(space + 1)
            if (tail.isEmpty() || tail.any { it.isDigit() }) return PersonalizationStoryAmount(text, null)
            return PersonalizationStoryAmount(text.substring(0, space).trimEnd(), tail)
        }
    }

    /**
     * Сумма крупным кеглем, валюта — [currencySizePx] после пробела. Пробел тоже
     * мелкий: в макете между ними 4.
     */
    fun toSpannable(
        currencySizePx: Int,
        currencyColor: Int? = null,
        strikethrough: Boolean = false
    ): CharSequence {
        val builder = SpannableStringBuilder(amount)
        if (strikethrough) {
            builder.setSpan(StrikethroughSpan(), 0, amount.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (currency != null) {
            val start = builder.length
            builder.append(' ').append(currency)
            builder.setSpan(
                AbsoluteSizeSpan(currencySizePx),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (currencyColor != null) {
                builder.setSpan(
                    ForegroundColorSpan(currencyColor),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }
}

/**
 * Картинка, обрезанная кругом. `clipToOutline` есть только с API 21, а SDK держит
 * minSdk 19 — поэтому клип по пути, он работает везде.
 */
internal class StoryCircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val clip = Path()

    init {
        scaleType = ScaleType.CENTER_CROP
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        clip.addCircle(w / 2f, h / 2f, minOf(w, h) / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}

/**
 * Начертание для части строки. `TypefaceSpan(Typeface)` есть только с API 28,
 * а число и единица таймера набраны разными начертаниями в одной строке.
 */
internal class StoryTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = typeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}
