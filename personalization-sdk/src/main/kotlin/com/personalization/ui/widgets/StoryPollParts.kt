package com.personalization.ui.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.text.Spannable
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.BaseInputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.PersonalizationTheme
import kotlin.math.roundToInt

/** Общее для частей карточки опроса. */
internal object PollUi {

    fun dp(view: View, value: Float): Float = value * view.resources.displayMetrics.density

    fun rounded(color: Int, radius: Float, stroke: Int? = null, strokeWidth: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (stroke != null) setStroke(strokeWidth.roundToInt().coerceAtLeast(1), stroke)
        }

    /** Кегль, начертание кита, цвет и интерлиньяж в долях кегля, как `line-height` в вебе. */
    fun style(view: TextView, unit: Int, size: Float, weight: PollWeight, color: Int, lineHeight: Float = PollStyle.LINE_HEIGHT) {
        view.setTextSize(unit, size)
        view.typeface = when (weight) {
            PollWeight.REGULAR -> PersonalizationTheme.typeface(view.context, false)
            PollWeight.SEMIBOLD -> PersonalizationTheme.typeface(view.context, true)
            PollWeight.BOLD -> Typeface.create(PersonalizationTheme.typeface(view.context, false), Typeface.BOLD)
        }
        view.setTextColor(color)
        view.includeFontPadding = false
        TextViewCompat.setLineHeight(view, (view.textSize * lineHeight).roundToInt())
    }

    /**
     * Карточка светлая и на медиа-слайде, где просмотр строит вьюхи в тёмном контексте:
     * поле, курсор и выделение берут дневные значения темы хоста.
     */
    fun lightContext(base: Context): Context {
        val night = base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != Configuration.UI_MODE_NIGHT_YES) return base
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base, base.theme).apply { applyOverrideConfiguration(configuration) }
    }

    /** Для экранного чтеца — кнопка; [checkable] — переключатель с состоянием «отмечено». */
    fun buttonRole(view: View, checkable: Boolean = false) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = (if (checkable) ToggleButton::class.java else Button::class.java).name
                if (checkable) {
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            }
        })
    }
}

/**
 * Вариант ответа: подпись в начале, процент в конце, под ними полоска итогов — от начала
 * строки (в RTL справа), растёт от нуля. Выбранный — с зелёной рамкой и полоской.
 */
internal class PollOptionRow(context: Context) : LinearLayout(context) {

    val label = TextView(context)
    private val percent = TextView(context)
    private val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_xl2)
    private val border = PollUi.dp(this, PollStyle.BORDER_DP)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    /** Заполнение полоски, 0…1. */
    var fraction = 0f
        set(value) {
            field = value
            invalidate()
        }

    var selectedState = false
        set(value) {
            field = value
            isSelected = value
            invalidate()
        }

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        val horizontal = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val vertical = PollUi.dp(this, PollStyle.OPTION_PADDING_V_DP.toFloat()).roundToInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        PollUi.style(label, TypedValue.COMPLEX_UNIT_SP, PollStyle.TEXT_SP, PollWeight.SEMIBOLD, PollStyle.TEXT)
        PollUi.style(percent, TypedValue.COMPLEX_UNIT_SP, PollStyle.TEXT_SP, PollWeight.SEMIBOLD, PollStyle.TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) percent.fontFeatureSettings = "tnum"
        percent.visibility = View.GONE
        addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(percent, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        })
    }

    /** Итог варианта: процент и полоска, которая растёт от нуля. Процент — число, не перевод. */
    @SuppressLint("SetTextI18n")
    fun showPercent(value: Int) {
        percent.text = "$value%"
        percent.visibility = View.VISIBLE
        contentDescription = "${label.text}, $value%"
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, value / 100f).apply {
            duration = PollStyle.BAR_MS
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { fraction = it.animatedValue as Float }
            start()
        }
    }

    val percentText: CharSequence? get() = percent.text.takeIf { percent.visibility == View.VISIBLE }

    override fun onDetachedFromWindow() {
        animator?.end()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val half = border / 2
        rect.set(half, half, width - half, height - half)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.color = PollStyle.SURFACE
        canvas.drawPath(path, paint)
        if (fraction > 0f) {
            canvas.save()
            canvas.clipPath(path)
            paint.color = if (selectedState) PollStyle.FILL_SELECTED else PollStyle.FILL
            val fill = width * fraction
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                canvas.drawRect(width - fill, 0f, width.toFloat(), height.toFloat(), paint)
            } else {
                canvas.drawRect(0f, 0f, fill, height.toFloat(), paint)
            }
            canvas.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = border
        paint.color = if (selectedState) PollStyle.ACCENT else PollStyle.LINE
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }
}

/**
 * Шкала: ячейки поровну в ряд (в RTL — справа налево). Выбранная — с обводкой поверх соседей;
 * в ответе ([muted]) остальные приглушены.
 */
@SuppressLint("ViewConstructor")
internal class PollScaleRow(
    context: Context,
    private val gap: Int,
    private val ring: Float,
    private val offset: Float,
    private val muted: Boolean
) : LinearLayout(context) {

    private val cells = LinkedHashMap<Int, View>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_md)

    /** Выбранная оценка; дробная не выделяет ни одной ячейки, как в веб-SDK. */
    var selectedValue: Double? = null
        set(value) {
            field = value
            cells.forEach { (rate, cell) ->
                cell.isSelected = rate.toDouble() == value
                if (muted) cell.alpha = if (cell.isSelected) 1f else PollStyle.MUTED_ALPHA
            }
            invalidate()
        }

    init {
        orientation = HORIZONTAL
        // Обводка выходит за ячейку — и за ряд.
        clipChildren = false
        setWillNotDraw(false)
    }

    val values: List<Int> get() = cells.keys.toList()

    fun cell(value: Int): View? = cells[value]

    fun addCell(value: Int, cell: View) {
        val params = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (childCount > 0) params.marginStart = gap
        cells[value] = cell
        addView(cell, params)
    }

    fun setCellsEnabled(enabled: Boolean) = cells.values.forEach { it.isEnabled = enabled }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val cell = cells.entries.firstOrNull { it.key.toDouble() == selectedValue }?.value ?: return
        val inset = offset + ring / 2
        rect.set(cell.left - inset, cell.top - inset, cell.right + inset, cell.bottom + inset)
        paint.strokeWidth = ring
        paint.color = PollStyle.TEXT
        canvas.drawRoundRect(rect, radius + inset, radius + inset, paint)
    }
}

/**
 * Поле текстового опроса с лимитом [limit] в единицах UTF-16, как `value.length` в вебе: пара
 * суррогатов не разрезается. Пока клавиатура составляет слово (IME-композиция), ввод не режется,
 * чтобы она не сбилась: лишнее отрезается, когда слово вставлено или брошено.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
internal class PollTextField(context: Context, private val limit: Int?) : EditText(context) {

    init {
        if (limit != null) filters = arrayOf(Limit(limit))
    }

    /** Клавиатура составляет слово: текст пока может быть длиннее лимита. */
    val composing: Boolean get() = editableText?.let { BaseInputConnection.getComposingSpanStart(it) >= 0 } == true

    /** Композиция закончилась без вставки (`finishComposingText`) — после неё и режем. */
    override fun onEndBatchEdit() {
        super.onEndBatchEdit()
        cut()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) cut()
    }

    private fun cut() {
        val limit = limit ?: return
        val text = editableText ?: return
        if (text.length <= limit || composing) return
        text.delete(StoryPoll.cutIndex(text, limit), text.length)
    }

    /** Как [InputFilter.LengthFilter], но слово в композиции не режет. */
    private class Limit(limit: Int) : InputFilter {
        private val length = InputFilter.LengthFilter(limit)

        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? =
            if (source is Spannable && BaseInputConnection.getComposingSpanStart(source) >= 0) null
            else length.filter(source, start, end, dest, dstart, dend)
    }
}

/** Чипы с переносом строк, строки по центру; в RTL порядок справа налево. */
@SuppressLint("ViewConstructor")
internal class PollFlowRow(context: Context, private val gap: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val childSpec = MeasureSpec.makeMeasureSpec(maxWidth.coerceAtLeast(0), MeasureSpec.AT_MOST)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        shown().forEach { it.measure(childSpec, unspecified) }
        val height = lines(maxWidth).sumOf { line -> line.maxOf { it.measuredHeight } } + gap * (lines(maxWidth).size - 1).coerceAtLeast(0)
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(height + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var y = paddingTop
        lines(maxWidth).forEach { line ->
            val lineWidth = line.sumOf { it.measuredWidth } + gap * (line.size - 1)
            var x = paddingLeft + (maxWidth - lineWidth) / 2
            val ordered = if (rtl) line.asReversed() else line
            ordered.forEach { child ->
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
            }
            y += line.maxOf { it.measuredHeight } + gap
        }
    }

    private fun shown(): List<View> = (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }

    private fun lines(maxWidth: Int): List<List<View>> {
        val result = ArrayList<MutableList<View>>()
        var lineWidth = 0
        shown().forEach { child ->
            val line = result.lastOrNull()
            if (line == null || lineWidth + gap + child.measuredWidth > maxWidth) {
                result += mutableListOf(child)
                lineWidth = child.measuredWidth
            } else {
                line += child
                lineWidth += gap + child.measuredWidth
            }
        }
        return result
    }
}

/**
 * Слой опросов слайда — над серединой между шапкой и кнопками (или «товарами»). Карточка не шире
 * 420 по центру; верх — на `y_offset` процентов всей высоты слайда, как `top` в вебе (не середины,
 * как у текстовых блоков), затем сдвиг, чтобы карточка целиком влезла в слой; не влезает и так —
 * прокручивается внутри себя. Пока в поле фокус и открыта клавиатура,
 * карточка поднимается над ней (+16), но не выше слоя: места мало — она ниже и прокручивается
 * внутри, поле в фокусе остаётся видно. Слайд под ней не сжимается.
 */
internal class StoryPollLayer(context: Context) : android.widget.FrameLayout(context) {

    val cards = ArrayList<StoryPollView>()
    private val maxCardWidth = PollUi.dp(this, PollStyle.MAX_WIDTH_DP.toFloat()).roundToInt()
    private val keyboardGap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
    private var imeBottom = 0

    fun add(card: StoryPollView) {
        cards += card
        addView(card, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Карточка с полем в фокусе — клавиатура открыта. */
    val focusedCard: StoryPollView? get() = cards.firstOrNull { it.inputFocused }

    fun hideKeyboard() = cards.forEach { it.hideKeyboard() }

    /** Клавиатура: [bottom] — сколько она закрывает снизу окна. Спряталась — фокус из поля уходит. */
    fun setIme(bottom: Int) {
        if (imeBottom == bottom) return
        val hidden = imeBottom > 0 && bottom == 0
        imeBottom = bottom
        if (hidden) cards.filter { it.inputFocused }.forEach { it.hideKeyboard() }
        requestLayout()
        updateLift()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val cardWidth = MeasureSpec.makeMeasureSpec(minOf(width, maxCardWidth), MeasureSpec.EXACTLY)
        cards.forEach { card ->
            val room = keyboardRoom(card)?.let { minOf(it, height) } ?: height
            card.measure(cardWidth, MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST))
        }
        setMeasuredDimension(width, height)
    }

    /** Место карточки с полем в фокусе между верхом слоя и клавиатурой; клавиатуры нет — `null`. */
    private fun keyboardRoom(card: StoryPollView): Int? {
        val windowHeight = rootView?.height ?: 0
        if (imeBottom <= 0 || windowHeight <= 0 || !card.inputFocused) return null
        val location = IntArray(2)
        getLocationInWindow(location)
        return (windowHeight - imeBottom - keyboardGap - location[1]).coerceAtLeast(keyboardGap * 3)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val (layerTop, slideHeight) = slideFrame()
        cards.forEach { card ->
            val wanted = slideHeight * (card.controller.poll.yOffset ?: 0f) / 100f - layerTop
            val y = wanted.coerceAtMost((height - card.measuredHeight).toFloat()).coerceAtLeast(0f).roundToInt()
            val x = (width - card.measuredWidth) / 2
            card.layout(x, y, x + card.measuredWidth, y + card.measuredHeight)
        }
        updateLift()
    }

    /** Верх слоя в слайде и высота слайда; слой не на слайде — он сам. */
    private fun slideFrame(): Pair<Int, Int> {
        var offset = 0
        var view: View = this
        while (true) {
            val parent = view.parent as? View ?: return 0 to height
            offset += view.top
            if (parent is StorySlideView) return offset to parent.height
            view = parent
        }
    }

    /** Подъём карточки с полем в фокусе: низ — над клавиатурой, верх — не выше окна. */
    fun updateLift() {
        val windowHeight = rootView?.height ?: 0
        cards.forEach { card ->
            var lift = 0f
            if (imeBottom > 0 && windowHeight > 0 && card.inputFocused) {
                val location = IntArray(2)
                card.getLocationInWindow(location)
                val cardTop = location[1] - card.translationY
                val keyboardTop = windowHeight - imeBottom
                // Не выше слоя: над ним шапка, и родитель обрезал бы карточку.
                lift = (cardTop + card.height + keyboardGap - keyboardTop).coerceAtLeast(0f).coerceAtMost(card.top.toFloat())
            }
            card.translationY = if (lift > 0f) -lift else 0f
        }
    }
}
