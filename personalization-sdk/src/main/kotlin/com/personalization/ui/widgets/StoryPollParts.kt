package com.personalization.ui.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
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
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.BaseInputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.core.os.ConfigurationCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.PersonalizationTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
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

    /** Кегль и интерлиньяж — токены кита ([size], [lineHeight]), начертание кита и цвет. */
    fun style(view: TextView, @DimenRes size: Int, @DimenRes lineHeight: Int, weight: PollWeight, color: Int) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.resources.getDimension(size))
        view.typeface = PersonalizationTheme.typeface(view.context, weight == PollWeight.SEMIBOLD)
        view.setTextColor(color)
        view.includeFontPadding = false
        TextViewCompat.setLineHeight(view, view.resources.getDimensionPixelSize(lineHeight))
    }

    /** Цвет токена кита — с подменой темой хоста. */
    fun color(context: Context, @ColorRes token: Int): Int = PersonalizationTheme.color(context, token)

    /** [color] с прозрачностью [alpha] (0…1). */
    fun alpha(color: Int, alpha: Float): Int = ColorUtils.setAlphaComponent(color, (alpha * 255).roundToInt())

    /**
     * Текст и значок на заливке [fill] — кнопка, галочка, выбранная цифра шкалы: белый или
     * тёмный (`text_dark_primary`), что контрастнее.
     */
    fun onFill(context: Context, fill: Int): Int {
        val dark = color(context, R.color.personalization_text_dark_primary)
        val opaque = ColorUtils.setAlphaComponent(fill, 255)
        return if (ColorUtils.calculateContrast(Color.WHITE, opaque) > ColorUtils.calculateContrast(dark, opaque)) Color.WHITE else dark
    }

    /** Буква варианта по его номеру: A…Z, дальше AA, AB… */
    fun letter(index: Int): String {
        val letters = StringBuilder()
        var number = index + 1
        while (number > 0) {
            letters.append('A' + (number - 1) % 26)
            number = (number - 1) / 26
        }
        return letters.reverse().toString()
    }

    /** Число голосов со склонением и разрядами по языку ресурсов: «1 248 голосов»; нет голосов — `null`. */
    fun votes(resources: Resources, total: Int): String? {
        if (total <= 0) return null
        val locale = ConfigurationCompat.getLocales(resources.configuration).get(0) ?: Locale.getDefault()
        val number = NumberFormat.getIntegerInstance(locale).format(total.toLong())
        return resources.getQuantityString(R.plurals.personalization_stories_poll_votes, total, number)
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

/** Начертание кита для части текста: `TypefaceSpan(Typeface)` есть только с API 28. */
internal class PollTypefaceSpan(private val typeface: Typeface?) : MetricAffectingSpan() {

    override fun updateDrawState(paint: TextPaint) = apply(paint)

    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        if (typeface != null) paint.typeface = typeface
    }
}

/**
 * Квадрат буквы варианта: светлая подложка и буква; отмеченный — заливка акцента и галочка
 * вместо буквы. Рисуется сам: для чтеца он — часть строки, не текст.
 */
@SuppressLint("ViewConstructor")
internal class PollBadge(context: Context, val letter: String) : View(context) {

    private val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_lg)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val check = Path()
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PollUi.dp(this@PollBadge, PollStyle.CHECK_STROKE_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = PollUi.onFill(context, PollStyle.ACCENT)
    }
    private val letterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.personalization_font_size_sm)
        typeface = PersonalizationTheme.typeface(context, true)
        color = PollUi.color(context, R.color.personalization_text_secondary)
        textAlign = Paint.Align.CENTER
    }

    var checked = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        fill.color = if (checked) PollStyle.ACCENT else PollStyle.LIGHT
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (checked) {
            check.reset()
            check.moveTo(w * 0.3f, h * 0.52f)
            check.lineTo(w * 0.44f, h * 0.66f)
            check.lineTo(w * 0.71f, h * 0.37f)
            canvas.drawPath(check, checkPaint)
        } else {
            val baseline = h / 2 - (letterPaint.descent() + letterPaint.ascent()) / 2
            canvas.drawText(letter, w / 2, baseline, letterPaint)
        }
    }
}

/**
 * Вариант ответа: квадрат буквы, подпись (до двух строк), в итогах ([result]) — процент в конце
 * и полоса доли от начала строки (в RTL справа), которая растёт от нуля и обрезана скруглением
 * строки. Отмеченный — рамка акцента и галочка в квадрате; в форме строка ещё и залита
 * акцентом, в итогах подложка обычная, а полоса — акцентом.
 */
@SuppressLint("ViewConstructor")
internal class PollOptionRow(context: Context, letter: String, private val result: Boolean) : LinearLayout(context) {

    val badge = PollBadge(context, letter)
    val label = TextView(context)
    private val percent = TextView(context)
    private val radius = PersonalizationTheme.radius(context, R.dimen.personalization_radius_xl5)
    private val border = PollUi.dp(this, PollStyle.SELECTED_BORDER_DP)
    private val selectedFill = PollUi.alpha(PollStyle.ACCENT, PollStyle.ACCENT_FILL_ALPHA)
    private val selectedBar = PollUi.alpha(PollStyle.ACCENT, PollStyle.ACCENT_BAR_ALPHA)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    /** Заполнение полосы доли, 0…1. */
    var fraction = 0f
        set(value) {
            field = value
            invalidate()
        }

    var selectedState = false
        set(value) {
            field = value
            isSelected = value
            badge.checked = value
            invalidate()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        minimumHeight = PollUi.dp(this, PollStyle.OPTION_MIN_HEIGHT_DP.toFloat()).roundToInt()
        val horizontal = resources.getDimensionPixelSize(R.dimen.personalization_spacing_lg)
        val vertical = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        setPadding(horizontal, vertical, horizontal, vertical)
        val primary = PollUi.color(context, R.color.personalization_text_primary)
        PollUi.style(label, R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.REGULAR, primary)
        label.maxLines = PollStyle.OPTION_MAX_LINES
        label.ellipsize = TextUtils.TruncateAt.END
        PollUi.style(percent, R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.SEMIBOLD, primary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) percent.fontFeatureSettings = "tnum"
        percent.visibility = View.GONE
        val size = PollUi.dp(this, PollStyle.BADGE_DP.toFloat()).roundToInt()
        addView(badge, LayoutParams(size, size))
        addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = horizontal })
        addView(percent, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = horizontal
        })
    }

    /** Итог варианта: процент и полоса, которая растёт от нуля. Процент — число, не перевод. */
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

    /** Подложка строки: акцентом — только у отмеченного в форме. */
    val surface: Int get() = if (selectedState && !result) selectedFill else PollStyle.SURFACE

    override fun onDetachedFromWindow() {
        animator?.end()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Рамка отмеченного — внутри строки: путь сдвинут на полтолщины.
        val inset = if (selectedState) border / 2 else 0f
        rect.set(inset, inset, width - inset, height - inset)
        path.reset()
        path.addRoundRect(rect, radius - inset, radius - inset, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawPath(path, paint)
        if (fraction > 0f) {
            canvas.save()
            canvas.clipPath(path)
            paint.color = if (selectedState) selectedBar else PollStyle.BAR
            val fill = width * fraction
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                canvas.drawRect(width - fill, 0f, width.toFloat(), height.toFloat(), paint)
            } else {
                canvas.drawRect(0f, 0f, fill, height.toFloat(), paint)
            }
            canvas.restore()
        }
        if (selectedState) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = PollStyle.ACCENT
            canvas.drawPath(path, paint)
        }
        super.onDraw(canvas)
    }
}

/**
 * Шкала — дорожка-капсула, цифры поровну (в RTL справа налево). Выбранная — капсула на всю
 * высоту дорожки цветом своей полосы ([fillOf]), цифра на ней белая или тёмная, что контрастнее;
 * в ответе ([muted]) остальные цифры приглушены.
 */
@SuppressLint("ViewConstructor")
internal class PollScaleRow(
    context: Context,
    private val muted: Boolean,
    private val fillOf: (Int) -> Int
) : LinearLayout(context) {

    private val cells = LinkedHashMap<Int, TextView>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val textColor = PollUi.color(context, R.color.personalization_text_primary)

    /** Выбранная оценка; дробная не выделяет ни одной цифры, как в веб-SDK. */
    var selectedValue: Double? = null
        set(value) {
            field = value
            cells.forEach { (rate, cell) ->
                cell.isSelected = rate.toDouble() == value
                cell.setTextColor(if (cell.isSelected) PollUi.onFill(context, fillOf(rate)) else textColor)
                if (muted) cell.alpha = if (cell.isSelected) 1f else PollStyle.MUTED_ALPHA
            }
            invalidate()
        }

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        val inset = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        setPadding(inset, 0, inset, 0)
        background = PollUi.rounded(PollStyle.SURFACE, PersonalizationTheme.radius(context, R.dimen.personalization_radius_rounded))
    }

    val values: List<Int> get() = cells.keys.toList()

    fun cell(value: Int): TextView? = cells[value]

    fun addCell(value: Int, cell: TextView) {
        cells[value] = cell
        addView(cell, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    fun setCellsEnabled(enabled: Boolean) = cells.values.forEach { it.isEnabled = enabled }

    override fun dispatchDraw(canvas: Canvas) {
        // Капсула выбранной — под цифрой.
        cells.entries.firstOrNull { it.key.toDouble() == selectedValue }?.let { (rate, cell) ->
            rect.set(cell.left.toFloat(), 0f, cell.right.toFloat(), height.toFloat())
            val radius = min(rect.width(), rect.height()) / 2
            paint.color = fillOf(rate)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        super.dispatchDraw(canvas)
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

/**
 * Слой опросов слайда — над серединой между шапкой и кнопками (или «товарами»). Карточка не шире
 * 420 по центру; верх — по [StorySlidePlacement], как у текстовых блоков: `y_offset` процентов всей
 * высоты слайда, как `top` в вебе, затем сдвиг, чтобы карточка целиком влезла в слой; не влезает и
 * так — прокручивается внутри себя. Пока в поле фокус и открыта клавиатура,
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
        val (layerTop, slideHeight) = StorySlidePlacement.frame(this)
        cards.forEach { card ->
            val y = StorySlidePlacement.top(card.controller.poll.yOffset, slideHeight, layerTop, height, card.measuredHeight)
            val x = (width - card.measuredWidth) / 2
            card.layout(x, y, x + card.measuredWidth, y + card.measuredHeight)
        }
        updateLift()
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
