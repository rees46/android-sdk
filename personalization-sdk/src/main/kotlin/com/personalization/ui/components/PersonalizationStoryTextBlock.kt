package com.personalization.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme

/**
 * Текстовый блок слайда.
 *
 * Источник: Figma, Text block (370:16189) и задача «Текстовый блок» на странице Stories:
 * блок с фоном ([blockColor] не `null`) — скругление 12 и отступы по горизонтали 16,
 * по вертикали 12 у крупного текста и 8 у остальных; блок без фона — без отступов.
 * Размеры из макета — [Size]: 48/48, 30/36, 20/24, Regular. Текст по умолчанию —
 * Text/Light Primary на фоне и Text/Primary без него.
 *
 * Кегль, цвет, выравнивание и начертание сторис задаёт админка — всё это свойства,
 * пресеты [Size] только стартовые значения. Без своего интерлиньяжа ([lineHeightSp] = `null`)
 * берётся высота строки ближайшего по кеглю пресета.
 */
@InternalPersonalizationUiApi
class PersonalizationStoryTextBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class Size(val textSp: Float, val lineHeightSp: Float) {
        LARGE(48f, 48f),
        MEDIUM(30f, 36f),
        SMALL(20f, 24f);

        internal companion object {
            /** Пресет, ближайший к кеглю [textSp]. */
            fun nearest(textSp: Float): Size = values().minByOrNull { kotlin.math.abs(it.textSp - textSp) } ?: SMALL

            /** Высота строки пресета, пересчитанная на кегль: 48/48 → 1.0, 30/36 и 20/24 → 1.2. */
            fun lineHeightFor(textSp: Float): Float = nearest(textSp).let { textSp * it.lineHeightSp / it.textSp }
        }
    }

    enum class Align { START, CENTER, END }

    /** Пресет кегля и интерлиньяжа. */
    var size: Size = Size.SMALL
        set(value) {
            field = value
            textSizeSp = value.textSp
            lineHeightSp = value.lineHeightSp
        }

    var textSizeSp: Float = Size.SMALL.textSp
        set(value) {
            field = value
            applyStyle()
        }

    /** Интерлиньяж; `null` — высота строки пресета для этого кегля. */
    var lineHeightSp: Float? = Size.SMALL.lineHeightSp
        set(value) {
            field = value
            applyStyle()
        }

    /** Цвет подложки; `null` — блок без фона. */
    @ColorInt
    var blockColor: Int? = null
        set(value) {
            field = value
            applyStyle()
        }

    /** Цвет текста; `null` — токен по виду блока. */
    @ColorInt
    var textColorOverride: Int? = null
        set(value) {
            field = value
            applyStyle()
        }

    var align: Align = Align.START
        set(value) {
            field = value
            applyStyle()
        }

    var bold: Boolean = false
        set(value) {
            field = value
            applyStyle()
        }

    var italic: Boolean = false
        set(value) {
            field = value
            applyStyle()
        }

    /** Семейство от админки (`serif`, `monospaced`); `null` — шрифт кита. */
    var fontFamily: String? = null
        set(value) {
            field = value
            applyStyle()
        }

    init {
        includeFontPadding = false
        applyStyle()
    }

    /** Отступы блока с фоном: по вертикали 12 у текста крупнее Medium, 8 у остального. */
    internal fun solidPadding(): Pair<Int, Int> {
        val vertical = if (textSizeSp > MEDIUM_TEXT_SP) 12 else 8
        return dp(16) to dp(vertical)
    }

    private fun applyStyle() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        // Без своего интерлиньяжа — пресет для кегля: иначе осталась бы высота прошлого кегля.
        TextViewCompat.setLineHeight(this, spPx(lineHeightSp ?: Size.lineHeightFor(textSizeSp)))
        val base = when (fontFamily) {
            "serif" -> Typeface.SERIF
            "monospaced", "monospace" -> Typeface.MONOSPACE
            else -> PersonalizationTheme.typeface(context, emphasized = false)
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        setTypeface(base, style)
        gravity = Gravity.CENTER_VERTICAL or when (align) {
            Align.START -> Gravity.START
            Align.CENTER -> Gravity.CENTER_HORIZONTAL
            Align.END -> Gravity.END
        }
        textAlignment = when (align) {
            Align.START -> TEXT_ALIGNMENT_VIEW_START
            Align.CENTER -> TEXT_ALIGNMENT_CENTER
            Align.END -> TEXT_ALIGNMENT_VIEW_END
        }

        val solid = blockColor
        if (solid != null) {
            val (horizontal, vertical) = solidPadding()
            setPadding(horizontal, vertical, horizontal, vertical)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(solid)
            }
        } else {
            setPadding(0, 0, 0, 0)
            background = null
        }
        setTextColor(
            textColorOverride ?: PersonalizationTheme.color(
                context,
                if (solid != null) R.color.personalization_text_light_primary
                else R.color.personalization_text_primary
            )
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun spPx(sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()

    private companion object {
        /** Кегль Medium (30): всё крупнее получает отступы Large. */
        const val MEDIUM_TEXT_SP = 30f
    }
}
