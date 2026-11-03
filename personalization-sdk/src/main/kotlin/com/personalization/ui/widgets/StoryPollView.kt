package com.personalization.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.personalization.R
import com.personalization.ui.PersonalizationTheme
import kotlin.math.roundToInt

/**
 * Значения карточки опроса — из стилей веб-SDK (`styles.ts`) один в один: макета нет, карточка
 * светлая и на тёмной теме, как в вебе. Где у кита есть токен с тем же значением, берётся токен
 * (радиусы, отступы 8/12/16, кегль 12); остальное — здесь, чтобы поменять разом.
 */
internal object PollStyle {
    const val CARD = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFF0B1410.toInt()
    const val LINE = 0xFFE1E8E4.toInt()
    const val SURFACE = 0xFFF4F7F5.toInt()
    const val FILL = 0xFFD9F99D.toInt()
    const val FILL_SELECTED = 0xFF86EFAC.toInt()
    const val ACCENT = 0xFF22C55E.toInt()
    const val DARK = 0xFF0F3D2A.toInt()
    const val BUTTON_TEXT = 0xFF06210F.toInt()
    const val MUTED = 0xFF8A9A90.toInt()
    const val DONE_TEXT = 0xFF5B6B62.toInt()
    const val ERROR = 0xFFDC2626.toInt()

    const val MAX_WIDTH_DP = 420
    const val PADDING_DP = 14
    const val GAP_DP = 10
    const val ELEVATION_DP = 12f
    const val BORDER_DP = 1.5f
    const val OPTION_PADDING_V_DP = 10
    const val CHIP_GAP_DP = 6
    const val CHIP_PADDING_V_DP = 7
    const val CHIP_PADDING_H_DP = 11
    const val BUTTON_PADDING_DP = 11
    const val INPUT_HEIGHT_DP = 72
    const val INPUT_PADDING_DP = 10
    const val COUNTER_SHIFT_DP = -6
    const val SCALE_GAP_DP = 3
    const val SCALE_PADDING_V_DP = 8
    const val SCALE_RING_DP = 2f
    const val SCALE_RING_OFFSET_DP = 1f
    const val DONE_GAP_DP = 6
    const val DONE_ICON_DP = 38

    const val QUESTION_SP = 15f
    const val TEXT_SP = 13f
    const val SCALE_SP = 11.5f
    const val COUNTER_SP = 10.5f
    const val DONE_ICON_SP = 18f

    const val LINE_HEIGHT = 1.3f
    const val QUESTION_LINE_HEIGHT = 1.25f
    const val SENDING_ALPHA = 0.85f
    const val MUTED_ALPHA = 0.55f
    const val BAR_MS = 600L
}

/** Шрифт кита: обычный, полужирный (600 и 500 — ближайшее начертание кита) и жирный (700+). */
internal enum class PollWeight { REGULAR, SEMIBOLD, BOLD }

/**
 * Карточка опроса на слайде. Рисует состояние [StoryPollController]: форму или отвеченное,
 * выключенность при отправке, ошибку. Касание, начатое на карточке, слайду не достаётся: ни
 * тапы-листания, ни удержание просмотра, ни свайпы пейджера и закрытия; пока палец на
 * карточке, слайд стоит. Высота — сколько влезает, остальное прокручивается внутри.
 */
@SuppressLint("ViewConstructor")
internal class StoryPollView(base: Context, val controller: StoryPollController) : FrameLayout(PollUi.lightContext(base)) {

    private val scroll = ScrollView(context)
    private val content = LinearLayout(context)
    private val error = TextView(context)
    private val options = ArrayList<Pair<Int, PollOptionRow>>()
    private val chips = ArrayList<Pair<Int, TextView>>()
    private var scale: PollScaleRow? = null
    private var submit: TextView? = null
    private var counter: TextView? = null
    private var reanswer: TextView? = null
    private var builtFor = -1
    private var errorShown = false

    /** Поле текстового опроса; `null` — его нет. */
    var input: PollTextField? = null
        private set

    init {
        background = PollUi.rounded(PollStyle.CARD, radius(R.dimen.personalization_radius_xl5))
        ViewCompat.setElevation(this, dp(PollStyle.ELEVATION_DP))
        // Фокус уходит на карточку, когда поле его теряет, — а не обратно в поле.
        isFocusableInTouchMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        scroll.isVerticalScrollBarEnabled = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        val padding = dp(PollStyle.PADDING_DP)
        content.setPadding(padding, padding, padding, padding)
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        PollUi.style(error, TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.personalization_font_size_xs), PollWeight.REGULAR, PollStyle.ERROR)
        error.gravity = Gravity.CENTER
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
        chips.clear()
        scale = null
        submit = null
        counter = null
        reanswer = null
        input = null
        val poll = controller.poll
        poll.question?.let { add(text(it, PollStyle.QUESTION_SP, PollWeight.BOLD, center = true, lineHeight = PollStyle.QUESTION_LINE_HEIGHT)) }
        if (controller.answered) buildAnswered(poll) else buildForm(poll)
        if (controller.answered && poll.allowReanswer) {
            add(reanswerLink(), width = ViewGroup.LayoutParams.WRAP_CONTENT, gravity = Gravity.CENTER_HORIZONTAL)
        }
        error.text = ""
        error.visibility = View.GONE
        errorShown = false
        add(error)
        controller.setFocused(inputFocused)
    }

    private fun buildForm(poll: StoryPoll) {
        when (poll.type) {
            StoryPoll.Type.SINGLE_CHOICE -> add(optionList(poll, interactive = true))
            StoryPoll.Type.MULTIPLE_CHOICE -> {
                add(chipFlow(poll))
                add(submitButton(poll))
            }
            StoryPoll.Type.TEXT -> {
                add(inputField(poll), height = dp(PollStyle.INPUT_HEIGHT_DP))
                if (poll.maxLength != null) {
                    val view = text("", PollStyle.COUNTER_SP, PollWeight.REGULAR, color = PollStyle.MUTED)
                    view.gravity = Gravity.END
                    tabular(view)
                    counter = view
                    add(view, top = dp(PollStyle.GAP_DP + PollStyle.COUNTER_SHIFT_DP))
                }
                add(submitButton(poll))
            }
            else -> add(scaleRow(poll, interactive = true))
        }
    }

    private fun buildAnswered(poll: StoryPoll) {
        when (poll.type) {
            StoryPoll.Type.SINGLE_CHOICE, StoryPoll.Type.MULTIPLE_CHOICE -> {
                add(optionList(poll, interactive = false))
                thanks(poll.thanksText)
            }
            StoryPoll.Type.TEXT -> add(doneBlock(poll))
            else -> {
                add(scaleRow(poll, interactive = false))
                thanks(poll.bandText(controller.answer?.rate))
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
        chips.forEach { (id, chip) ->
            chipStyle(chip, id in controller.selected)
            chip.isEnabled = !sending
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

    /** Варианты: в форме — кнопки, в ответе — с выделением ответа и полосками итогов. */
    private fun optionList(poll: StoryPoll, interactive: Boolean): View {
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val chosen = controller.answer?.optionIds.orEmpty()
        val results = controller.results
        val gap = resources.getDimensionPixelSize(R.dimen.personalization_spacing_md)
        poll.options.forEachIndexed { index, option ->
            val row = PollOptionRow(context)
            row.label.text = option.label
            if (interactive) {
                row.setOnClickListener { controller.choose(option.id) }
                PollUi.buttonRole(row)
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

    private fun chipFlow(poll: StoryPoll): View {
        val flow = PollFlowRow(context, dp(PollStyle.CHIP_GAP_DP))
        poll.options.forEach { option ->
            val chip = text(option.label, 0f, PollWeight.SEMIBOLD, size = resources.getDimension(R.dimen.personalization_font_size_xs))
            chip.setPadding(dp(PollStyle.CHIP_PADDING_H_DP), dp(PollStyle.CHIP_PADDING_V_DP), dp(PollStyle.CHIP_PADDING_H_DP), dp(PollStyle.CHIP_PADDING_V_DP))
            chip.setOnClickListener { controller.toggle(option.id) }
            PollUi.buttonRole(chip, checkable = true)
            chips += option.id to chip
            flow.addView(chip)
        }
        return flow
    }

    private fun submitButton(poll: StoryPoll): View {
        val button = text(poll.submitLabel ?: controller.texts.submit, PollStyle.TEXT_SP, PollWeight.BOLD, center = true)
        val padding = dp(PollStyle.BUTTON_PADDING_DP)
        button.setPadding(padding, padding, padding, padding)
        button.setOnClickListener { controller.submit() }
        PollUi.buttonRole(button)
        submit = button
        return button
    }

    /** Многострочное поле — своё: в ките поле ввода однострочное. */
    private fun inputField(poll: StoryPoll): View {
        val field = PollTextField(context, poll.maxLength)
        field.background = PollUi.rounded(PollStyle.SURFACE, radius(R.dimen.personalization_radius_xl2), PollStyle.LINE, dp(PollStyle.BORDER_DP))
        val padding = dp(PollStyle.INPUT_PADDING_DP)
        field.setPadding(padding, padding, padding, padding)
        PollUi.style(field, TypedValue.COMPLEX_UNIT_SP, PollStyle.TEXT_SP, PollWeight.SEMIBOLD, PollStyle.TEXT)
        field.setHintTextColor(PollStyle.MUTED)
        field.hint = poll.placeholder
        field.gravity = Gravity.TOP or Gravity.START
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        field.imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
        val row = PollScaleRow(context, dp(PollStyle.SCALE_GAP_DP), dp(PollStyle.SCALE_RING_DP), dp(PollStyle.SCALE_RING_OFFSET_DP), muted = !interactive)
        val min = values.firstOrNull() ?: 0
        val max = values.lastOrNull() ?: 0
        values.forEach { value ->
            val cell = text(value.toString(), PollStyle.SCALE_SP, PollWeight.BOLD, center = true)
            tabular(cell)
            cell.background = PollUi.rounded(StoryPoll.scaleColor(poll.type, value, min, max), radius(R.dimen.personalization_radius_md))
            cell.setPadding(0, dp(PollStyle.SCALE_PADDING_V_DP), 0, dp(PollStyle.SCALE_PADDING_V_DP))
            if (interactive) {
                cell.setOnClickListener { controller.rate(value) }
                PollUi.buttonRole(cell)
            }
            row.addCell(value, cell)
        }
        scale = row
        return row
    }

    /** Текст отправлен: галочка, спасибо и сам ответ. */
    private fun doneBlock(poll: StoryPoll): View {
        val block = LinearLayout(context)
        block.orientation = LinearLayout.VERTICAL
        block.gravity = Gravity.CENTER_HORIZONTAL
        val gap = dp(PollStyle.DONE_GAP_DP)
        val icon = text("✓", PollStyle.DONE_ICON_SP, PollWeight.BOLD, center = true, color = PollStyle.DARK)
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PollStyle.FILL)
        }
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val size = dp(PollStyle.DONE_ICON_DP)
        block.addView(icon, LinearLayout.LayoutParams(size, size))
        poll.thanksText?.let { thanks ->
            block.addView(text(thanks, PollStyle.TEXT_SP, PollWeight.SEMIBOLD, center = true), doneParams(gap))
        }
        controller.answer?.text?.takeIf { it.isNotEmpty() }?.let { answer ->
            block.addView(text(answer, PollStyle.SCALE_SP, PollWeight.REGULAR, center = true, color = PollStyle.DONE_TEXT), doneParams(gap))
        }
        return block
    }

    private fun doneParams(gap: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = gap }

    private fun thanks(value: String?) {
        if (value.isNullOrEmpty()) return
        add(text(value, PollStyle.TEXT_SP, PollWeight.SEMIBOLD, center = true))
    }

    private fun reanswerLink(): View {
        val link = text(controller.texts.reanswer, 0f, PollWeight.SEMIBOLD, color = PollStyle.DARK, size = resources.getDimension(R.dimen.personalization_font_size_xs))
        link.paintFlags = link.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        val vertical = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xs)
        val horizontal = resources.getDimensionPixelSize(R.dimen.personalization_spacing_sm)
        link.setPadding(horizontal, vertical, horizontal, vertical)
        link.setOnClickListener { controller.reanswer() }
        PollUi.buttonRole(link)
        reanswer = link
        return link
    }

    private fun chipStyle(chip: TextView, selected: Boolean) {
        val fill = if (selected) PollStyle.DARK else PollStyle.SURFACE
        val line = if (selected) PollStyle.DARK else PollStyle.LINE
        chip.background = PollUi.rounded(fill, radius(R.dimen.personalization_radius_rounded), line, dp(PollStyle.BORDER_DP))
        chip.setTextColor(if (selected) PollStyle.FILL else PollStyle.TEXT)
        chip.isSelected = selected
    }

    private fun buttonStyle(button: TextView) {
        val enabled = button.isEnabled
        button.background = PollUi.rounded(if (enabled) PollStyle.ACCENT else PollStyle.LINE, radius(R.dimen.personalization_radius_button_lg))
        button.setTextColor(if (enabled) PollStyle.BUTTON_TEXT else PollStyle.MUTED)
    }

    /** Строка карточки: отступ 10 от предыдущей. */
    private fun add(view: View, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, gravity: Int = Gravity.NO_GRAVITY, top: Int? = null) {
        val params = LinearLayout.LayoutParams(width, height)
        if (content.childCount > 0) params.topMargin = top ?: dp(PollStyle.GAP_DP)
        params.gravity = gravity
        content.addView(view, params)
    }

    /** Текст карточки; [size] в пикселях — кегль токена кита вместо [sizeSp]. */
    private fun text(
        value: CharSequence,
        sizeSp: Float,
        weight: PollWeight,
        center: Boolean = false,
        color: Int = PollStyle.TEXT,
        lineHeight: Float = PollStyle.LINE_HEIGHT,
        size: Float? = null
    ): TextView {
        val view = TextView(context)
        view.text = value
        if (size != null) {
            PollUi.style(view, TypedValue.COMPLEX_UNIT_PX, size, weight, color, lineHeight)
        } else {
            PollUi.style(view, TypedValue.COMPLEX_UNIT_SP, sizeSp, weight, color, lineHeight)
        }
        if (center) view.gravity = Gravity.CENTER
        return view
    }

    private fun tabular(view: TextView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.fontFeatureSettings = "tnum"
    }

    private fun dp(value: Int): Int = PollUi.dp(this, value.toFloat()).roundToInt()

    private fun dp(value: Float): Float = PollUi.dp(this, value)

    private fun radius(token: Int): Float = PersonalizationTheme.radius(context, token)
}
