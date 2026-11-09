package com.personalization.ui.widgets

import com.personalization.InternalPersonalizationV3Api

/**
 * Логика карточки опроса — как `createPoll` веб-SDK, без вьюх: их рисует [StoryPollView].
 *
 * Два состояния: форма и отвеченное. Выбор одного и шкала отправляются по нажатию,
 * несколько вариантов и текст — кнопкой. Пока ответ отправляется, всё выключено и повторное
 * нажатие — игнор; ошибка — текст под содержимым, всё снова активно, можно повторить
 * (автоповтора нет). Успех пишется в [StoryPollStore]; ответ, пришедший после ухода карточки
 * ([detach]), — только туда: ни вьюха, ни пауза слайда его уже не видят.
 *
 * Слайд стоит, пока карточку держат, пишут в поле или ждут ответа: [onHold] зовётся при
 * смене `pressed || focused || sending`.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class StoryPollController(
    val poll: StoryPoll,
    private val storyId: Int,
    private val slideId: String,
    private val shopId: String,
    private val transport: StoriesPollsTransport,
    hostTexts: PollTexts,
    private val onHold: (Boolean) -> Unit,
    /** Куда вернуть ответ транспорта: главный поток. Подменяется в тестах. */
    private val post: (() -> Unit) -> Unit = MainThread::run
) {

    val texts: PollTexts = hostTexts.with(poll.labels)

    var answer: StoriesPollAnswer?
        private set
    var results: StoriesPollResults?
        private set

    /** Отвеченное состояние, а не форма. */
    var answered = false
        private set

    /** Номер сборки: форма или отвеченное собраны заново — вьюха перестраивается. */
    var renderCount = 0
        private set

    var sending = false
        private set

    /** Последняя отправка не удалась. */
    var error = false
        private set

    /** Выделенные варианты формы: у выбора одного — нажатый, у нескольких — отмеченные. */
    val selected: Set<Int> get() = selection
    private val selection = LinkedHashSet<Int>()

    /** Выделенная оценка шкалы в форме; прежняя дробная не выделяет ни одной ячейки. */
    var pressedRate: Double? = null
        private set

    var text = ""
        private set

    /** После переответа текстом поле получает фокус, как в веб-SDK. Вьюха сбрасывает флаг. */
    var focusRequested = false

    /** Что-то изменилось — перерисовать. */
    var onChange: (() -> Unit)? = null

    private var pressed = false
    private var focused = false
    private var paused = false
    private var attached = true
    private var request = 0

    init {
        val stored = StoryPollStore.get(shopId, poll.pollType, poll.code)
        answer = if (stored != null) stored.answer else poll.answer
        results = if (stored != null) stored.results else poll.results
        // Запись хранилища — только после успеха, а после успеха карточка всегда отвеченная.
        render(stored != null || answer?.isAnswered == true)
    }

    /** Кнопка формы активна: выбран вариант или в поле есть текст. */
    val canSubmit: Boolean
        get() = !sending && !answered && when (poll.type) {
            StoryPoll.Type.MULTIPLE_CHOICE -> selection.isNotEmpty()
            StoryPoll.Type.TEXT -> text.isNotBlank()
            else -> false
        }

    /** Счётчик поля `n/limit` в единицах UTF-16; без `max_length` — `null`. */
    val counter: String? get() = poll.maxLength?.let { "${text.length}/$it" }

    /** Выбор одного: отправка сразу; не ушло — выделение возвращается к прежнему ответу. */
    fun choose(optionId: Int) {
        if (answered || sending || poll.type != StoryPoll.Type.SINGLE_CHOICE) return
        val previous = previousIds()
        select(setOf(optionId))
        val ids = listOf(optionId)
        send(StoriesPollAnswer(optionIds = ids), onFailure = { select(previous) }) { callback ->
            transport.answerMiniPoll(poll.code, StoryPollBodies.answer(storyId, slideId, optionIds = ids), callback)
        }
    }

    /** Чип выбора нескольких: только отметка, отправляет кнопка. */
    fun toggle(optionId: Int) {
        if (answered || sending || poll.type != StoryPoll.Type.MULTIPLE_CHOICE) return
        if (!selection.remove(optionId)) selection.add(optionId)
        changed()
    }

    /**
     * Ввод поля; лимит `max_length` обрезает текст, не разрезая пару суррогатов. Пока клавиатура
     * составляет слово, в поле бывает больше — считается и уходит обрезанный.
     */
    fun setText(value: String) {
        val limited = StoryPoll.limitText(value, poll.maxLength)
        if (limited == text) return
        text = limited
        changed()
    }

    /** Кнопка: варианты — в порядке опроса, текст — без пробелов по краям. */
    fun submit() {
        if (!canSubmit) return
        when (poll.type) {
            StoryPoll.Type.MULTIPLE_CHOICE -> {
                val ids = poll.options.map { it.id }.filter { it in selection }
                send(StoriesPollAnswer(optionIds = ids)) { callback ->
                    transport.answerMiniPoll(poll.code, StoryPollBodies.answer(storyId, slideId, optionIds = ids), callback)
                }
            }
            StoryPoll.Type.TEXT -> {
                val trimmed = text.trim()
                send(StoriesPollAnswer(text = trimmed)) { callback ->
                    transport.answerMiniPoll(poll.code, StoryPollBodies.answer(storyId, slideId, text = trimmed), callback)
                }
            }
            else -> Unit
        }
    }

    /** Шкала: отправка сразу; не ушло — выделена прежняя оценка. */
    fun rate(value: Int) {
        val kind = poll.type.scaleKind ?: return
        if (answered || sending) return
        val previous = answer?.rate
        pressedRate = value.toDouble()
        changed()
        send(StoriesPollAnswer(rate = value.toDouble()), onFailure = { pressedRate = previous }) { callback ->
            transport.review(kind, StoryPollBodies.review(poll, value), callback)
        }
    }

    /** «Ответить заново»: снова форма, прежний ответ выделен. */
    fun reanswer() {
        if (!answered || !poll.allowReanswer || sending) return
        render(false)
        if (poll.type == StoryPoll.Type.TEXT) {
            focusRequested = true
            changed()
        }
    }

    /** Палец на карточке. */
    fun setPressed(value: Boolean) {
        pressed = value
        sync()
    }

    /** Фокус в поле (клавиатура открыта). */
    fun setFocused(value: Boolean) {
        focused = value
        sync()
    }

    /** Карточка снова на экране. */
    fun attach() {
        attached = true
    }

    /**
     * Карточка ушла (сменили слайд, закрыли просмотр): пауза снимается без [onHold] — её
     * причину просмотр уже убрал, а поздний ответ пойдёт только в хранилище.
     */
    fun detach() {
        attached = false
        pressed = false
        focused = false
        paused = false
    }

    private fun previousIds(): Set<Int> = answer?.optionIds.orEmpty().toSet()

    private fun select(ids: Set<Int>) {
        selection.clear()
        selection.addAll(poll.options.map { it.id }.filter { it in ids })
        changed()
    }

    private fun send(
        sent: StoriesPollAnswer,
        onFailure: () -> Unit = {},
        call: ((StoriesPollsResult) -> Unit) -> Unit
    ) {
        if (sending) return
        sending = true
        error = false
        val token = ++request
        changed()
        sync()
        val callback: (StoriesPollsResult) -> Unit = { result -> post { finish(token, sent, result, onFailure) } }
        try {
            call(callback)
        } catch (throwable: Throwable) {
            callback(StoriesPollsResult.Failure(StoriesLoader.ERROR_CODE, throwable.message))
        }
    }

    private fun finish(token: Int, sent: StoriesPollAnswer, result: StoriesPollsResult, onFailure: () -> Unit) {
        // Повторный ответ транспорта — игнор.
        if (token != request || !sending) return
        sending = false
        when (result) {
            is StoriesPollsResult.Success -> {
                val scale = poll.type.isScale
                answer = if (scale) sent else result.answer ?: sent
                results = if (scale) null else result.results
                StoryPollStore.put(shopId, poll.pollType, poll.code, StoryPollStore.Entry(answer, results))
                if (!attached) return
                render(true)
            }
            is StoriesPollsResult.Failure -> {
                if (!attached) return
                error = true
                onFailure()
                changed()
            }
        }
        sync()
    }

    /** Форма или отвеченное заново; в форме выделен прежний ответ. */
    private fun render(answered: Boolean) {
        this.answered = answered
        error = false
        renderCount++
        selection.clear()
        if (!answered) {
            val previous = previousIds()
            selection.addAll(poll.options.map { it.id }.filter { it in previous })
            pressedRate = answer?.rate
            text = ""
        }
        changed()
    }

    private fun sync() {
        val hold = pressed || focused || sending
        if (hold == paused) return
        paused = hold
        if (attached) onHold(hold)
    }

    private fun changed() {
        onChange?.invoke()
    }
}

/** Что опросам слайда нужно от просмотра: транспорт, магазин (ключ хранилища) и тексты хоста. */
@OptIn(InternalPersonalizationV3Api::class)
internal class StoryPollsContext(val transport: StoriesPollsTransport, val shopId: String, val texts: PollTexts)
