package com.personalization.ui.popups

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.popups.PopupMarkup
import com.personalization.popups.PopupV3Specs
import com.personalization.popups.PopupV3Variables
import com.personalization.ui.PersonalizationTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Логические px дизайна → px устройства: 1 px CSS = 1 dp. */
internal fun Context.popupDp(value: Double): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.popupDpF(value: Double): Float = (value * resources.displayMetrics.density).toFloat()

/** Разметка и витринные переменные → текст со спанами. Значения переменных разметкой не считаются. */
internal object PopupV3Text {

    fun spans(text: String?, variables: Map<String, String>): CharSequence {
        val builder = SpannableStringBuilder()
        PopupMarkup.parse(text).forEach { span ->
            val start = builder.length
            builder.append(PopupV3Variables.applyRuntime(span.text, variables))
            val end = builder.length
            if (start == end) return@forEach
            val style = when {
                span.bold && span.italic -> Typeface.BOLD_ITALIC
                span.bold -> Typeface.BOLD
                span.italic -> Typeface.ITALIC
                else -> null
            }
            style?.let { builder.setSpan(StyleSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (span.underline) builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.strike) builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.overline) builder.setSpan(OverlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }

    /**
     * Шрифт: системный; `monospace` и `serif` — по ключевому слову (шрифтов приложения по имени
     * не ищем). Начертание — числом на API 28+, раньше — обычный или жирный от 600.
     */
    fun typeface(family: String?, weight: Double): Typeface {
        val name = family?.lowercase().orEmpty()
        val base = when {
            "mono" in name -> Typeface.MONOSPACE
            "serif" in name && "sans" !in name -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }
        val value = weight.roundToInt().coerceIn(1, 1000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, value, false)
        } else {
            Typeface.create(base, if (value >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** Размер, начертание, цвет, межстрочный (множитель) и межбуквенный (px) — как в CSS. */
    fun style(
        view: TextView,
        family: String?,
        size: Double,
        weight: Double,
        color: Int,
        lineHeight: Double? = null,
        letterSpacing: Double = 0.0
    ) {
        view.includeFontPadding = false
        view.typeface = typeface(family, weight)
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat().coerceAtLeast(1f))
        view.setTextColor(color)
        lineHeight?.let { multiplier ->
            val px = view.context.popupDp(size * multiplier)
            if (px > 0) TextViewCompat.setLineHeight(view, px)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && letterSpacing != 0.0 && size > 0) {
            view.letterSpacing = (letterSpacing / size).toFloat()
        }
    }

    fun gravity(align: String): Int = when (align) {
        "center" -> Gravity.CENTER_HORIZONTAL
        "right" -> Gravity.END
        else -> Gravity.START
    }
}

/** Надчёркивание `^^…^^`: маркер, линию рисует [PopupV3TextView]. */
internal class OverlineSpan : CharacterStyle() {
    override fun updateDrawState(paint: TextPaint) = Unit
}

/** Текст попапа: умеет надчёркивание поверх обычных спанов. */
@SuppressLint("ViewConstructor")
internal open class PopupV3TextView(context: Context) : AppCompatTextView(context) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spanned = text as? Spanned ?: return
        val layout = layout ?: return
        val spans = spanned.getSpans(0, spanned.length, OverlineSpan::class.java)
        if (spans.isEmpty()) return
        linePaint.color = currentTextColor
        val thickness = max(1f, textSize / 16f)
        val ascent = paint.fontMetrics.ascent
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        spans.forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            for (line in layout.getLineForOffset(start)..layout.getLineForOffset(end)) {
                val lineStart = max(start, layout.getLineStart(line))
                val lineEnd = minOf(end, layout.getLineEnd(line))
                if (lineEnd <= lineStart) continue
                val x1 = layout.getPrimaryHorizontal(lineStart)
                val x2 = if (lineEnd >= layout.getLineEnd(line)) layout.getLineRight(line) else layout.getPrimaryHorizontal(lineEnd)
                val top = layout.getLineBaseline(line) + ascent
                canvas.drawRect(minOf(x1, x2), top, maxOf(x1, x2), top + thickness, linePaint)
            }
        }
        canvas.restore()
    }
}

/** Фон с рамкой и радиусом — коробка элемента, рамка попапа, поля и кнопки. */
internal fun popupBackground(context: Context, color: Int, radius: Double, borderWidth: Double = 0.0, borderColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.popupDpF(radius)
        if (borderWidth > 0) setStroke(context.popupDp(borderWidth).coerceAtLeast(1), borderColor)
    }

/** Нажатие — лёгкое затемнение, как у кнопок web. */
@SuppressLint("ClickableViewAccessibility")
internal fun View.popupPressFeedback() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.alpha = PRESSED_ALPHA
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
        }
        false
    }
}

private const val PRESSED_ALPHA = 0.8f

/** Скругление содержимого (картинки) по контуру; ниже API 21 — без скругления. */
internal fun View.popupClipRound(radiusPx: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && radiusPx > 0f) {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(radiusPx, minOf(view.width, view.height) / 2f))
            }
        }
        clipToOutline = true
    }
}

/**
 * Тема компонентов кита внутри попапа: попап рисуется цветами дизайна, и кит в нём берёт
 * светлую или тёмную тему по фону попапа под элементом, а не по теме системы. Из двух тем
 * берётся та, чей `text_primary` контрастнее с фоном (WCAG): на белом — светлая в любой теме
 * системы. Окна поверх приложения (тост) остаются по теме приложения.
 */
internal object PopupV3KitTheme {

    /**
     * Фон под элементом: свой фон элемента, если он не прозрачный, иначе фон рамки, иначе
     * белый — как рамка по умолчанию. Полупрозрачный ложится на то, что под ним.
     */
    fun background(element: Int, frame: Int): Int =
        ColorUtils.compositeColors(element, ColorUtils.compositeColors(frame, Color.WHITE))

    /** Тёмная тема кита читается на [background] лучше светлой. */
    fun night(base: Context, background: Int): Boolean {
        val opaque = ColorUtils.setAlphaComponent(background, 255)
        val light = PersonalizationTheme.color(context(base, night = false), R.color.personalization_text_primary)
        val dark = PersonalizationTheme.color(context(base, night = true), R.color.personalization_text_primary)
        return ColorUtils.calculateContrast(dark, opaque) > ColorUtils.calculateContrast(light, opaque)
    }

    /** Контекст для кита на [background]. */
    fun context(base: Context, background: Int): Context = context(base, night(base, background))

    /**
     * [base] со светлыми или тёмными ресурсами (`uiMode`), тема хоста та же — его токены
     * остаются; уже такой — он сам.
     */
    fun context(base: Context, night: Boolean): Context {
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val current = base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (current == mode) return base
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        }
        return ContextThemeWrapper(base, base.theme).apply { applyOverrideConfiguration(configuration) }
    }
}

/**
 * Обёртка элемента — «коробка» стилей компилятора: фон, рамка, радиус, отступы,
 * прозрачность и тень (тень — высотой: цвет тени до API 28 системный).
 */
@SuppressLint("ViewConstructor")
internal class PopupV3ElementBox(context: Context, box: PopupV3Specs.Box) : FrameLayout(context) {
    init {
        val border = box.borderWidth
        background = if (box.background != 0 || border > 0) {
            popupBackground(context, box.background, box.radius, border, box.borderColor)
        } else {
            null
        }
        val inset = if (border > 0) context.popupDp(border).coerceAtLeast(1) else 0
        setPadding(
            context.popupDp(box.paddingX) + inset,
            context.popupDp(box.paddingY) + inset,
            context.popupDp(box.paddingX) + inset,
            context.popupDp(box.paddingY) + inset
        )
        alpha = box.opacity.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && (box.shadowBlur > 0 || box.shadowY != 0.0) && background != null) {
            elevation = context.popupDpF(max(box.shadowBlur / 2, abs(box.shadowY)))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = box.shadowColor
                outlineSpotShadowColor = box.shadowColor
            }
        }
        clipToPadding = true
    }
}

/**
 * Ячейка сетки: столбик элементов с промежутком `gap`. По горизонтали элемент тянется
 * ([LayoutParams.stretch]) или встаёт по своему выравниванию (`cellAlign`, иначе ячейки);
 * по вертикали — `verticalAlign` ячейки. Элемент со своей вертикалью (`cellVerticalAlign`)
 * встаёт сам: свободное место уходит в его «auto»-отступы, как во flex-колонке.
 */
@SuppressLint("ViewConstructor")
internal class PopupV3CellLayout(context: Context, private val align: String, private val verticalAlign: String, private val gapPx: Int) : ViewGroup(context) {

    class LayoutParams(val stretch: Boolean, val align: String?, val verticalSelf: String?) :
        ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    private val visibleChildren: List<View> get() = (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        var total = 0
        val children = visibleChildren
        children.forEachIndexed { index, child ->
            val params = child.layoutParams as? LayoutParams
            val childWidth = if (params?.stretch == true) {
                MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY)
            } else {
                MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST)
            }
            child.measure(childWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            total += child.measuredHeight + if (index > 0) gapPx else 0
        }
        total += paddingTop + paddingBottom
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> total
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val children = visibleChildren
        val inner = r - l - paddingLeft - paddingRight
        val content = children.sumOf { it.measuredHeight } + gapPx * (children.size - 1).coerceAtLeast(0)
        val free = (b - t - paddingTop - paddingBottom - content).coerceAtLeast(0)
        val autos = children.sumOf { child ->
            when ((child.layoutParams as? LayoutParams)?.verticalSelf) {
                "center" -> 2
                "bottom" -> 1
                else -> 0
            }.toInt()
        }
        val autoMargin = if (autos > 0) free / autos else 0
        var y = paddingTop + if (autos > 0) 0 else when (verticalAlign) {
            "center" -> free / 2
            "bottom" -> free
            else -> 0
        }
        children.forEach { child ->
            val params = child.layoutParams as? LayoutParams
            when (params?.verticalSelf) {
                "center", "bottom" -> y += autoMargin
            }
            val x = paddingLeft + when (params?.align ?: align) {
                "center" -> (inner - child.measuredWidth) / 2
                "right" -> inner - child.measuredWidth
                else -> 0
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            y += child.measuredHeight + gapPx
            if (params?.verticalSelf == "center") y += autoMargin
        }
    }
}

/**
 * Строка сетки: ячейки рядом по долям колонок с промежутком `gap`, высота — не меньше
 * `minHeight`, содержимое обрезается; фон цветом и картинкой (первый ребёнок, если есть).
 */
@SuppressLint("ViewConstructor")
internal class PopupV3RowLayout(
    context: Context,
    private val fractions: List<Double>,
    private val gapPx: Int,
    private val minHeightPx: Int,
    private val backgroundView: View?
) : ViewGroup(context) {

    init {
        backgroundView?.let { addView(it) }
        clipChildren = true
    }

    private val cells: List<View> get() = (0 until childCount).map(::getChildAt).filter { it !== backgroundView }

    private fun cellWidths(width: Int): List<Int> {
        val count = fractions.size
        val available = (width - gapPx * (count - 1)).coerceAtLeast(0)
        return fractions.map { (available * it).roundToInt() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val widths = cellWidths(width)
        var height = minHeightPx
        cells.forEachIndexed { index, cell ->
            cell.measure(
                MeasureSpec.makeMeasureSpec(widths.getOrElse(index) { 0 }, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            height = max(height, cell.measuredHeight)
        }
        cells.forEachIndexed { index, cell ->
            cell.measure(
                MeasureSpec.makeMeasureSpec(widths.getOrElse(index) { 0 }, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
        backgroundView?.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        backgroundView?.layout(0, 0, r - l, b - t)
        val widths = cellWidths(r - l)
        var x = 0
        cells.forEachIndexed { index, cell ->
            val cellWidth = widths.getOrElse(index) { 0 }
            cell.layout(x, 0, x + cellWidth, b - t)
            x += cellWidth + gapPx
        }
    }
}

/** Столбик строк с промежутком [gapPx]. */
@SuppressLint("ViewConstructor")
internal class PopupV3StackLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var height = 0
        var visible = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            height += child.measuredHeight + if (visible > 0) gapPx else 0
            visible++
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            child.layout(0, y, r - l, y + child.measuredHeight)
            y += child.measuredHeight + gapPx
        }
    }
}

/** Значок крестика для `closeIconMode: svg`: стандартный крест цвета кнопки. */
@SuppressLint("ViewConstructor")
internal class PopupV3CrossView(context: Context, private val sizePx: Int, color: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.color = color
        strokeWidth = max(1f, sizePx / 12f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(canvas: Canvas) {
        val inset = width * 0.25f
        canvas.drawLine(inset, inset, width - inset, height - inset, paint)
        canvas.drawLine(width - inset, inset, inset, height - inset, paint)
    }
}
