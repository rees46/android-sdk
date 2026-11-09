package com.personalization.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import com.personalization.R
import com.personalization.ui.PersonalizationTheme
import kotlin.math.roundToInt

/**
 * Значения карточки опроса — по макету заказчика. Отступы, радиусы и кегли — токены кита
 * (берутся по месту); здесь — цвета и размеры, которых в ките нет. Карточка светлая и на тёмной
 * теме. Матовая — белый 88 % без размытия: размыть то, что под карточкой, на Android можно
 * только копией слайда на каждый кадр.
 */
internal object PollStyle {
    /** Подложка карточки — белый 88 %, граница — белый 50 %. */
    const val CARD = 0xE0FFFFFF.toInt()
    const val CARD_BORDER = 0x80FFFFFF.toInt()

    /** Цвет выбора и успеха опроса: отмеченный вариант, точка итогов, кнопка, шкала без полос. */
    const val ACCENT = 0xFF22C55E.toInt()
    const val ACCENT_FILL_ALPHA = 0.2f
    const val ACCENT_BAR_ALPHA = 0.25f

    /** Строка варианта, дорожка шкалы, неактивная кнопка — чёрный 6 %. */
    const val SURFACE = 0x0F000000

    /** Полоса доли варианта — чёрный 8 %. */
    const val BAR = 0x14000000

    /** Квадрат буквы и поле — белый 85 %. */
    const val LIGHT = 0xD9FFFFFF.toInt()
    const val ERROR = 0xFFDC2626.toInt()

    const val MAX_WIDTH_DP = 420
    const val BORDER_DP = 1f
    const val SELECTED_BORDER_DP = 1.5f
    const val OPTION_MIN_HEIGHT_DP = 56
    const val OPTION_MAX_LINES = 2
    const val BADGE_DP = 32
    const val CHECK_STROKE_DP = 2f
    const val DOT_DP = 6
    const val TRACK_HEIGHT_DP = 52
    const val FIELD_MIN_HEIGHT_DP = 52
    const val FIELD_MAX_LINES = 4
    const val BUTTON_HEIGHT_DP = 52

    /** Разрядка «Результатов», em. */
    const val CAPS_SPACING = 0.08f

    const val SENDING_ALPHA = 0.85f
    const val MUTED_ALPHA = 0.55f
    const val BAR_MS = 600L
}

/** Начертание кита: обычное и полужирное (600 — ближайшее начертание кита, 500). */
internal enum class PollWeight { REGULAR, SEMIBOLD }

/**
 * Карточка опроса на слайде. Рисует состояние [StoryPollController]: форму или отвеченное,
 * выключенность при отправке, ошибку. Весь текст — по левому краю. Касание, начатое на
 * карточке, слайду не достаётся: ни тапы-листания, ни удержание просмотра, ни свайпы пейджера
 * и закрытия; пока палец на карточке, слайд стоит. Высота — сколько влезает, остальное
 * прокручивается внутри.
 */
@SuppressLint("ViewConstructor")
internal class StoryPollView(base: Context, val controller: StoryPollController) : FrameLayout(PollUi.lightContext(base)) {

    private val scroll = ScrollView(context)
    private val content = LinearLayout(context)
    private val error = TextView(context)
    private val options = ArrayList<Pair<Int, PollOptionRow>>()
    private var scale: PollScaleRow? = null
    private var submit: TextView? = null
    private var counter: TextView? = null
    private var reanswer: TextView? = null
    private var builtFor = -1
    private var errorShown = false
    private val primary = PollUi.color(context, R.color.personalization_text_primary)
    private val secondary = PollUi.color(context, R.color.personalization_text_secondary)

    /** Поле текстового опроса; `null` — его нет. */
    var input: PollTextField? = null
        private set

    init {
        background = PollUi.rounded(PollStyle.CARD, radius(R.dimen.personalization_radius_xl6), PollStyle.CARD_BORDER, dp(PollStyle.BORDER_DP))
        // Фокус уходит на карточку, когда поле его теряет, — а не обратно в поле.
        isFocusableInTouchMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        scroll.isVerticalScrollBarEnabled = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        val padding = space(R.dimen.personalization_spacing_xl2)
        content.setPadding(padding, padding, padding, padding)
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        PollUi.style(error, R.dimen.personalization_font_size_xs, R.dimen.personalization_line_height_xs, PollWeight.REGULAR, PollStyle.ERROR)
        ViewCompat.setAccessibilityLiveRegion(error, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)

        controller.onChange = ::update
        update()
    }

    /** Касание карточки — её, не слайда: родители его не перехватывают. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                controller.setPressed(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> controller.setPressed(false)
        }
        super.dispatchTouchEvent(event)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.attach()
    }

    override fun onDetachedFromWindow() {
        controller.detach()
        super.onDetachedFromWindow()
    }

    /** Поле в фокусе — клавиатура открыта или открывается. */
    val inputFocused: Boolean get() = input?.hasFocus() == true

    /** Убрать клавиатуру и фокус поля: тап мимо, сворачивание, закрытие. */
    fun hideKeyboard() {
        val field = input ?: return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(field.windowToken, 0)
        if (field.hasFocus()) field.clearFocus()
        controller.setFocused(false)
    }

    private fun update() {
        if (builtFor != controller.renderCount) rebuild()
        refresh()
    }

    /** Форма или отвеченное — заново; ошибка и её место — в конце. */
    private fun rebuild() {
        builtFor = controller.renderCount
        // Текст ушёл — поле исчезает, клавиатура не нужна.
        if (input != null) hideKeyboard()
        content.removeAllViews()
        options.clear()
        scale = null
        submit = null
        counter = null
        reanswer = null
        input = null
        val poll = controller.poll
        val choice = poll.type == StoryPoll.Type.SINGLE_CHOICE || poll.type == StoryPoll.Type.MULTIPLE_CHOICE
        if (controller.answered && choice) add(resultsHeader())
        poll.question?.let { add(question(it), top = space(R.dimen.personalization_spacing_lg)) }
        val gap = space(if (poll.question != null) R.dimen.personalization_spacing_xl else R.dimen.personalization_spacing_lg)
        if (controller.answered) buildAnswered(poll, gap) else buildForm(poll, gap)
        if (controller.answered && poll.allowReanswer) {
            add(reanswerLink(), width = ViewGroup.LayoutParams.WRAP_CONTENT, top = space(R.dimen.personalization_spacing_md))
        }
        error.text = ""
        error.visibility = View.GONE
        errorShown = false
        add(error, top = space(R.dimen.personalization_spacing_lg))
        controller.setFocused(inputFocused)
    }

    /** Форма; [gap] — от вопроса до содержимого. */
    private fun buildForm(poll: StoryPoll, gap: Int) {
        when (poll.type) {
            StoryPoll.Type.SINGLE_CHOICE -> add(optionList(poll, interactive = true), top = gap)
            StoryPoll.Type.MULTIPLE_CHOICE -> {
                add(optionList(poll, interactive = true), top = gap)
                add(submitButton(poll), height = dp(PollStyle.BUTTON_HEIGHT_DP), top = space(R.dimen.personalization_spacing_xl))
            }
            StoryPoll.Type.TEXT -> {
                add(inputField(poll), top = gap)
                if (poll.maxLength != null) {
                    val view = text("", R.dimen.personalization_font_size_xs, R.dimen.personalization_line_height_xs, PollWeight.REGULAR, secondary)
                    view.gravity = Gravity.END
                    tabular(view)
                    counter = view
                    add(view, top = space(R.dimen.personalization_spacing_md))
                }
                add(submitButton(poll), height = dp(PollStyle.BUTTON_HEIGHT_DP), top = space(R.dimen.personalization_spacing_lg))
            }
            else -> add(scaleRow(poll, interactive = true), height = dp(PollStyle.TRACK_HEIGHT_DP), top = gap)
        }
    }

    /** Отвеченное; [gap] — от вопроса до содержимого. */
    private fun buildAnswered(poll: StoryPoll, gap: Int) {
        when (poll.type) {
            StoryPoll.Type.SINGLE_CHOICE, StoryPoll.Type.MULTIPLE_CHOICE -> {
                add(optionList(poll, interactive = false), top = gap)
                add(footer(poll), top = space(R.dimen.personalization_spacing_xl))
            }
            StoryPoll.Type.TEXT -> add(doneBlock(poll), top = gap)
            else -> {
                add(scaleRow(poll, interactive = false), height = dp(PollStyle.TRACK_HEIGHT_DP), top = gap)
                poll.bandText(controller.answer?.rate)?.let { band ->
                    add(text(band, R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.REGULAR, primary), top = space(R.dimen.personalization_spacing_xl))
                }
            }
        }
    }

    /** Состояние без пересборки: выделение, выключенность при отправке, кнопка, счётчик, ошибка. */
    private fun refresh() {
        val sending = controller.sending
        val answered = controller.answered
        alpha = if (sending) PollStyle.SENDING_ALPHA else 1f
        if (!answered) {
            options.forEach { (id, row) ->
                row.selectedState = id in controller.selected
                row.isEnabled = !sending
            }
        }
        scale?.let { row ->
            row.selectedValue = if (answered) controller.answer?.rate else controller.pressedRate
            if (!answered) row.setCellsEnabled(!sending)
        }
        submit?.let { button ->
            button.isEnabled = controller.canSubmit
            buttonStyle(button)
        }
        input?.let { field ->
            field.isEnabled = !sending
            // Слово в композиции длиннее лимита — его не трогаем, поле обрежется само.
            if (field.text.toString() != controller.text && !field.composing) {
                field.setText(controller.text)
                field.setSelection(field.length())
            }
        }
        counter?.text = controller.counter
        reanswer?.isEnabled = !sending
        refreshError()
        if (controller.focusRequested) {
            controller.focusRequested = false
            input?.let { field -> field.post { showKeyboard(field) } }
        }
    }

    /** Ошибка объявляется: текст меняется в живой области, когда она появляется. */
    private fun refreshError() {
        val show = controller.error
        if (show == errorShown) return
        errorShown = show
        error.visibility = if (show) View.VISIBLE else View.GONE
        error.text = if (show) controller.texts.error else ""
    }

    private fun showKeyboard(field: EditText) {
        if (!field.isAttachedToWindow || !field.requestFocus()) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(field, 0)
    }

    // --- Части карточки ---------------------------------------------------------------------

    /** Шапка итогов опроса с выбором: точка акцента и «РЕЗУЛЬТАТЫ» прописными с разрядкой. */
    private fun resultsHeader(): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val dot = View(context)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PollStyle.ACCENT)
        }
        val size = dp(PollStyle.DOT_DP)
        row.addView(dot, LinearLayout.LayoutParams(size, size))
        val label = text(resources.getString(R.string.personalization_stories_poll_results), R.dimen.personalization_font_size_xs, R.dimen.personalization_line_height_xs, PollWeight.SEMIBOLD, secondary)
        label.setAllCaps(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) label.letterSpacing = PollStyle.CAPS_SPACING
        row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = space(R.dimen.personalization_spacing_md)
        })
        return row
    }

    private fun question(value: String): TextView =
        text(value, R.dimen.personalization_font_size_lg, R.dimen.personalization_line_height_base, PollWeight.SEMIBOLD, primary)

    /**
     * Варианты: строки с буквами A, B, C… В форме — кнопки (у выбора нескольких —
     * переключатели), в ответе — с отметкой ответа и полосами итогов.
     */
    private fun optionList(poll: StoryPoll, interactive: Boolean): View {
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val chosen = controller.answer?.optionIds.orEmpty()
        val results = controller.results
        val multiple = poll.type == StoryPoll.Type.MULTIPLE_CHOICE
        val gap = space(R.dimen.personalization_spacing_md)
        poll.options.forEachIndexed { index, option ->
            val row = PollOptionRow(context, PollUi.letter(index), result = !interactive)
            row.label.text = option.label
            if (interactive) {
                row.setOnClickListener { if (multiple) controller.toggle(option.id) else controller.choose(option.id) }
                PollUi.buttonRole(row, checkable = multiple)
                options += option.id to row
            } else {
                row.selectedState = option.id in chosen
                if (results != null) row.showPercent(results.percentOf(option.id))
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = gap
            })
        }
        return list
    }

    /**
     * Подвал итогов: «Спасибо! Голос учтён» или свой текст благодарности опроса, справа — число
     * голосов (`results.total`); голосов нет — справа пусто.
     */
    private fun footer(poll: StoryPoll): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        val thanks = poll.thanksText?.let { text(it, R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.SEMIBOLD, primary) }
            ?: text(thanksPair(), R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.REGULAR, secondary)
        row.addView(thanks, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        PollUi.votes(resources, controller.results?.total ?: 0)?.let { votes ->
            val view = text(votes, R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.REGULAR, secondary)
            tabular(view)
            row.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = space(R.dimen.personalization_spacing_lg)
            })
        }
        return row
    }

    /** «Спасибо!» полужирным основного цвета, « Голос учтён» — вторичным. */
    private fun thanksPair(): CharSequence {
        val thanks = resources.getString(R.string.personalization_stories_poll_thanks)
        val text = SpannableStringBuilder(thanks).append(' ').append(resources.getString(R.string.personalization_stories_poll_vote_counted))
        text.setSpan(PollTypefaceSpan(PersonalizationTheme.typeface(context, true)), 0, thanks.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(primary), 0, thanks.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun submitButton(poll: StoryPoll): View {
        val button = text(poll.submitLabel ?: controller.texts.submit, R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.SEMIBOLD, secondary)
        button.gravity = Gravity.CENTER
        button.maxLines = 1
        button.ellipsize = TextUtils.TruncateAt.END
        val padding = space(R.dimen.personalization_spacing_lg)
        button.setPadding(padding, 0, padding, 0)
        button.setOnClickListener { controller.submit() }
        PollUi.buttonRole(button)
        submit = button
        return button
    }

    /** Многострочное поле — своё: в ките поле ввода однострочное. */
    private fun inputField(poll: StoryPoll): View {
        val field = PollTextField(context, poll.maxLength)
        field.background = PollUi.rounded(PollStyle.LIGHT, radius(R.dimen.personalization_radius_xl4))
        val padding = space(R.dimen.personalization_spacing_xl)
        field.setPadding(padding, padding, padding, padding)
        PollUi.style(field, R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.REGULAR, primary)
        field.setHintTextColor(PollUi.color(context, R.color.personalization_text_hint))
        field.hint = poll.placeholder
        field.gravity = Gravity.TOP or Gravity.START
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        field.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        // Растёт до нескольких строк, дальше прокручивается внутри.
        field.minHeight = dp(PollStyle.FIELD_MIN_HEIGHT_DP)
        field.maxLines = PollStyle.FIELD_MAX_LINES
        field.isVerticalScrollBarEnabled = true
        field.setText(controller.text)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = controller.setText(s?.toString().orEmpty())
        })
        field.setOnFocusChangeListener { _, focused -> controller.setFocused(focused) }
        input = field
        return field
    }

    private fun scaleRow(poll: StoryPoll, interactive: Boolean): View {
        val values = poll.scale
        val min = values.firstOrNull() ?: 0
        val max = values.lastOrNull() ?: 0
        val row = PollScaleRow(context, muted = !interactive) { value -> StoryPoll.scaleColor(poll.type, value, min, max) }
        val size = resources.getDimensionPixelSize(R.dimen.personalization_font_size_base)
        val smallest = resources.getDimensionPixelSize(R.dimen.personalization_font_size_xs)
        values.forEach { value ->
            val cell = text(value.toString(), R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.SEMIBOLD, primary)
            cell.gravity = Gravity.CENTER
            cell.maxLines = 1
            tabular(cell)
            // До 21 цифры в ряд: на узкой дорожке двузначные мельчают, а не обрезаются.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(cell, smallest, size, 1, TypedValue.COMPLEX_UNIT_PX)
            if (interactive) {
                cell.setOnClickListener { controller.rate(value) }
                PollUi.buttonRole(cell)
            }
            row.addCell(value, cell)
        }
        scale = row
        return row
    }

    /** Текст отправлен: галочка и спасибо в строку, под ними — сам ответ. */
    private fun doneBlock(poll: StoryPoll): View {
        val block = LinearLayout(context)
        block.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val badge = PollBadge(context, "")
        badge.checked = true
        val size = dp(PollStyle.BADGE_DP)
        head.addView(badge, LinearLayout.LayoutParams(size, size))
        poll.thanksText?.let { thanks ->
            val view = text(thanks, R.dimen.personalization_font_size_base, R.dimen.personalization_line_height_base, PollWeight.SEMIBOLD, primary)
            head.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = space(R.dimen.personalization_spacing_lg)
            })
        }
        block.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        controller.answer?.text?.takeIf { it.isNotEmpty() }?.let { answer ->
            val view = text(answer, R.dimen.personalization_font_size_sm, R.dimen.personalization_line_height_sm, PollWeight.REGULAR, secondary)
            block.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = space(R.dimen.personalization_spacing_lg)
            })
        }
        return block
    }

    private fun reanswerLink(): View {
        val link = text(controller.texts.reanswer, R.dimen.personalization_font_size_xs, R.dimen.personalization_line_height_xs, PollWeight.SEMIBOLD, primary)
        link.paintFlags = link.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        val vertical = space(R.dimen.personalization_spacing_sm)
        link.setPadding(0, vertical, space(R.dimen.personalization_spacing_md), vertical)
        link.setOnClickListener { controller.reanswer() }
        PollUi.buttonRole(link)
        reanswer = link
        return link
    }

    /** Основная — заливка акцента, текст по контрасту с ней; неактивная — чёрный 6 % и вторичный текст. */
    private fun buttonStyle(button: TextView) {
        val enabled = button.isEnabled
        button.background = PollUi.rounded(if (enabled) PollStyle.ACCENT else PollStyle.SURFACE, radius(R.dimen.personalization_radius_xl4))
        button.setTextColor(if (enabled) PollUi.onFill(context, PollStyle.ACCENT) else secondary)
    }

    /** Строка карточки: [top] — отступ от предыдущей. */
    private fun add(view: View, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) {
        val params = LinearLayout.LayoutParams(width, height)
        if (content.childCount > 0) params.topMargin = top
        content.addView(view, params)
    }

    /** Текст карточки: кегль и интерлиньяж — токены кита, по левому краю. */
    private fun text(value: CharSequence, @DimenRes size: Int, @DimenRes lineHeight: Int, weight: PollWeight, color: Int): TextView {
        val view = TextView(context)
        view.text = value
        PollUi.style(view, size, lineHeight, weight, color)
        return view
    }

    private fun tabular(view: TextView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.fontFeatureSettings = "tnum"
    }

    private fun space(@DimenRes token: Int): Int = resources.getDimensionPixelSize(token)

    private fun dp(value: Int): Int = PollUi.dp(this, value.toFloat()).roundToInt()

    private fun dp(value: Float): Float = PollUi.dp(this, value)

    private fun radius(token: Int): Float = PersonalizationTheme.radius(context, token)
}
