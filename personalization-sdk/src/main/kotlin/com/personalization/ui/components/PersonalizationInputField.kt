package com.personalization.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.ui.PersonalizationTheme
import com.personalization.R
import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Поле ввода дизайн-системы.
 *
 * Источник: Figma Mobile SDK UI Kit, секция Input (167:3746), фрейм Input Field (205:10194).
 * Матрица: 3 размера x 4 состояния x 3 типа.
 *
 * Состояния из макета не задаются снаружи, а выводятся из самого поля:
 * Default — пусто, Filled — есть текст, Focus — поле в фокусе, Disabled — [setEnabled].
 */
@InternalPersonalizationUiApi
class PersonalizationInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Size(
        val radiusRes: Int,
        val paddingVerticalRes: Int,
        /** Горизонтальный отступ типа Input. У Search он равен вертикальному. */
        val paddingInputRes: Int,
        /** Отступ слева у типа Select. Справа там тоже вертикальный. */
        val paddingSelectStartRes: Int,
        val fontSizeRes: Int,
        val lineHeightRes: Int,
        val letterSpacingPx: Float,
        val iconSizeDp: Int
    ) {
        // LG берёт кегль со ступени lg, а интерлиньяж со ступени xl: в макете 18/32,
        // тогда как ступень lg — это 18/28. SM так же смешан: 14/24 против 14/20.
        LG(
            R.dimen.personalization_radius_button_lg,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_spacing_xl,
            R.dimen.personalization_spacing_xl,
            R.dimen.personalization_font_size_lg,
            R.dimen.personalization_line_height_xl,
            0f,
            32
        ),
        MD(
            R.dimen.personalization_radius_button_md,
            R.dimen.personalization_spacing_md,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_font_size_base,
            R.dimen.personalization_line_height_base,
            0f,
            24
        ),
        SM(
            R.dimen.personalization_radius_button_sm,
            R.dimen.personalization_spacing_sm,
            R.dimen.personalization_spacing_lg,
            R.dimen.personalization_spacing_md,
            R.dimen.personalization_font_size_sm,
            R.dimen.personalization_line_height_base,
            0.05f,
            24
        )
    }

    enum class Type { SEARCH, INPUT, SELECT }

    private val startIcon = AppCompatImageView(context)
    private val endIcon = AppCompatImageView(context)

    /** Само поле ввода: наружу отдано, чтобы можно было задать inputType и слушателей. */
    val editText = AppCompatEditText(context)

    // Слот OnFocusChangeListener у editText один, а поле публичное — заняв его,
    // мы бы молча ломали подсветку фокуса любому, кто поставит свой слушатель.
    private val focusWatcher = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        applyStateStyle()
    }

    // Ключ последнего применённого фона: пересобирать его на каждый символ незачем,
    // он меняется только со сменой фокуса, доступности или размера.
    private var appliedBackgroundKey: Int? = null

    var text: CharSequence?
        get() = editText.text
        set(value) {
            editText.setText(value)
        }

    var placeholder: CharSequence? = null
        set(value) {
            field = value
            editText.hint = value
        }

    var size: Size = Size.LG
        set(value) {
            field = value
            applyAll()
        }

    var type: Type = Type.SEARCH
        set(value) {
            field = value
            applyAll()
        }

    /** Нажатие на крестик у типа Search. Не задан — поле просто очищается. */
    var onClear: (() -> Unit)? = null

    /** Нажатие на поле у типа Select: список открывает потребитель. */
    var onSelectClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        editText.background = null
        editText.setPadding(0, 0, 0, 0)
        editText.includeFontPadding = false
        editText.isSingleLine = true

        addView(startIcon)
        addView(editText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(endIcon)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyStateStyle()
        })
        endIcon.setOnClickListener {
            when (type) {
                Type.SEARCH -> {
                    editText.setText("")
                    onClear?.invoke()
                }
                Type.SELECT -> onSelectClick?.invoke()
                Type.INPUT -> Unit
            }
        }
        setOnClickListener { if (type == Type.SELECT) onSelectClick?.invoke() }

        applyAll()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusWatcher)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusWatcher)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        editText.isEnabled = enabled
        applyStateStyle()
    }

    private fun applyAll() {
        applyTypography()
        applyContent()
        applyStateStyle()
    }

    private fun applyTypography() {
        editText.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(size.fontSizeRes)
        )
        TextViewCompat.setLineHeight(editText, resources.getDimensionPixelSize(size.lineHeightRes))
        // В макете трекинг задан в px, у Android он в em.
        editText.setLetterSpacingCompat(
            if (size.letterSpacingPx == 0f) {
                0f
            } else {
                size.letterSpacingPx / pxToSp(resources.getDimension(size.fontSizeRes))
            }
        )
    }

    private fun applyContent() {
        val iconSize = dpToPx(size.iconSizeDp)
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)

        startIcon.isVisible = type == Type.SEARCH
        if (startIcon.isVisible) startIcon.setImageResource(R.drawable.personalization_ic_magnifier)
        startIcon.layoutParams = LayoutParams(iconSize, iconSize).apply { marginEnd = gap }

        endIcon.layoutParams = LayoutParams(iconSize, iconSize).apply { marginStart = gap }
        when (type) {
            Type.SELECT -> endIcon.setImageResource(R.drawable.personalization_ic_angle_down)
            Type.SEARCH -> endIcon.setImageResource(R.drawable.personalization_ic_cross)
            Type.INPUT -> Unit
        }

        // Select — не поле ввода: текст не редактируется, нажатие ловит контейнер.
        val editable = type != Type.SELECT
        editText.isFocusable = editable
        editText.isFocusableInTouchMode = editable
        editText.isCursorVisible = editable
        isClickable = !editable

        val vertical = resources.getDimensionPixelSize(size.paddingVerticalRes)
        val start = when (type) {
            Type.SEARCH -> vertical
            Type.INPUT -> resources.getDimensionPixelSize(size.paddingInputRes)
            Type.SELECT -> resources.getDimensionPixelSize(size.paddingSelectStartRes)
        }
        val end = when (type) {
            Type.INPUT -> resources.getDimensionPixelSize(size.paddingInputRes)
            Type.SEARCH, Type.SELECT -> vertical
        }
        setPadding(start, vertical, end, vertical)
    }

    private fun applyStateStyle() {
        val filled = !editText.text.isNullOrEmpty()
        // Крестик — это очистка, поэтому он появляется только когда есть что чистить.
        endIcon.isVisible = when (type) {
            Type.SELECT -> true
            Type.SEARCH -> filled && isEnabled
            Type.INPUT -> false
        }

        val foreground = PersonalizationTheme.color(context,
            if (filled && isEnabled) R.color.personalization_text_primary
            else R.color.personalization_text_hint
        )
        editText.setTextColor(foreground)
        editText.setHintTextColor(
            PersonalizationTheme.color(context, R.color.personalization_text_hint)
        )
        // В макете иконка идёт в цвет текста: серая в Default и Disabled, тёмная в Filled.
        val tint = ColorStateList.valueOf(foreground)
        ImageViewCompat.setImageTintList(startIcon, tint)
        ImageViewCompat.setImageTintList(endIcon, tint)

        val focused = editText.hasFocus() && isEnabled
        val key = (if (focused) 1 else 0) or (if (isEnabled) 2 else 0) or (size.ordinal shl 2)
        if (key == appliedBackgroundKey) return
        appliedBackgroundKey = key

        val borderColor = if (focused) {
            R.color.personalization_line_input_focus
        } else {
            R.color.personalization_line_input
        }
        val fillColor = if (isEnabled) {
            R.color.personalization_background_input
        } else {
            R.color.personalization_background_input_disabled
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PersonalizationTheme.radius(context, size.radiusRes)
            setColor(PersonalizationTheme.color(context, fillColor))
            setStroke(dpToPx(1), PersonalizationTheme.color(context, borderColor))
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun pxToSp(px: Float): Float =
        px / resources.displayMetrics.scaledDensity
}
