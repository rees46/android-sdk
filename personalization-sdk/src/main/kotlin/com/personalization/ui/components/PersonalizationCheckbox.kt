package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.personalization.ui.PersonalizationTheme
import com.personalization.R

/**
 * Чекбокс дизайн-системы, 20x20.
 *
 * Источник: Figma Mobile SDK UI Kit, фрейм Checkbox (205:10128).
 * В макете только размер MD, поэтому размера в API нет.
 *
 * Галка и черта рисуются штрихом по геометрии из макета:
 * `M5 10 L8.75 13.75 L15 7.5` и `M5 10 H15`, толщина 2, круглые концы.
 *
 * Цвета — токены кита; заданный цвет заменяет свой токен, кроме заливки
 * неактивного — она всегда из disabled-токенов кита. Геометрия от цветов не зависит.
 */
class PersonalizationCheckbox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class CheckState { UNCHECKED, CHECKED, INDETERMINATE }

    /** Сторона из макета; вся геометрия задана в этих координатах. */
    private val referenceSide = 20f

    var checkState: CheckState = CheckState.UNCHECKED
        set(value) {
            field = value
            invalidate()
        }

    /** Зовётся с состоянием, в которое чекбокс перешёл. */
    var onCheckedChange: ((CheckState) -> Unit)? = null

    /** Заливка отмеченного и частично отмеченного; null — Button/Primary. */
    @ColorInt
    var checkedColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Галка и черта; null — Text/Light/Primary. */
    @ColorInt
    var checkmarkColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Заливка пустого; null — Background/Input. */
    @ColorInt
    var uncheckedColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Рамка пустого; null — Line/Input. */
    @ColorInt
    var uncheckedBorderColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val boxRect = RectF()
    private val glyphPath = Path()

    init {
        isClickable = true
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            checkState = if (checkState == CheckState.CHECKED) {
                CheckState.UNCHECKED
            } else {
                CheckState.CHECKED
            }
            onCheckedChange?.invoke(checkState)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (referenceSide * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(side, widthMeasureSpec),
            resolveSize(side, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val scale = side / referenceSide
        val radius = 4f * scale
        val filled = checkState != CheckState.UNCHECKED

        fillPaint.color = if (filled) {
            if (isEnabled) checkedColor ?: PersonalizationTheme.color(context, R.color.personalization_button_primary)
            else PersonalizationTheme.color(context, R.color.personalization_button_primary_disabled)
        } else {
            if (isEnabled) uncheckedColor ?: PersonalizationTheme.color(context, R.color.personalization_background_input)
            else PersonalizationTheme.color(context, R.color.personalization_background_input_disabled)
        }

        boxRect.set(0f, 0f, side, side)
        canvas.drawRoundRect(boxRect, radius, radius, fillPaint)

        if (!filled) {
            // Рамка внутрь, чтобы внешний размер остался ровно 20dp.
            val inset = 0.5f * scale
            borderPaint.strokeWidth = 1f * scale
            borderPaint.color = uncheckedBorderColor
                ?: PersonalizationTheme.color(context, R.color.personalization_line_input)
            boxRect.set(inset, inset, side - inset, side - inset)
            canvas.drawRoundRect(boxRect, radius, radius, borderPaint)
            return
        }

        glyphPath.reset()
        when (checkState) {
            CheckState.CHECKED -> {
                glyphPath.moveTo(5f * scale, 10f * scale)
                glyphPath.lineTo(8.75f * scale, 13.75f * scale)
                glyphPath.lineTo(15f * scale, 7.5f * scale)
            }
            CheckState.INDETERMINATE -> {
                glyphPath.moveTo(5f * scale, 10f * scale)
                glyphPath.lineTo(15f * scale, 10f * scale)
            }
            CheckState.UNCHECKED -> return
        }
        glyphPaint.strokeWidth = 2f * scale
        glyphPaint.color = checkmarkColor
            ?: PersonalizationTheme.color(context, R.color.personalization_text_light_primary)
        canvas.drawPath(glyphPath, glyphPaint)
    }
}
