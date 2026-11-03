package com.personalization.popups

/** Ответ списка попапов для движка. */
internal sealed class PopupsV3ListResult {
    class Success(val list: PopupsV3List) : PopupsV3ListResult()

    /** 4xx: кешируется пустым списком на [PopupsV3List.DEFAULT_TTL]. */
    class ClientError(val code: Int, val message: String?) : PopupsV3ListResult()

    /** Сеть, 5xx, неразборчивый ответ: не кешируется, повтор при следующем поводе. */
    class Failure(val message: String?) : PopupsV3ListResult()
}

/** Сеть попапов v3. Ответы — на главном потоке. */
internal interface PopupsV3Transport {
    fun list(testMode: Boolean, debug: Boolean, callback: (PopupsV3ListResult) -> Unit)

    /** Ручной показ: попап по id; нет — `null`. */
    fun one(id: Int, testMode: Boolean, callback: (PopupV3?) -> Unit)

    /** Событие попапа: выстрелил и забыл. */
    fun event(id: Int, event: String, channel: String? = null)

    /** Персональный промокод из списка; нет — `null`. */
    fun promoCode(listId: String, callback: (String?) -> Unit)
}

/** Показ на экране. Колбэки — на главном потоке. */
internal interface PopupsV3Presenter {

    interface Listener {
        /** Попап на экране: теперь пишется частота и уходит `show`. */
        fun onShown()

        /** Попап закрыт (любым способом). */
        fun onClosed()

        /** Показать не вышло; попап не считается показанным. */
        fun onFailed(reason: String)
    }

    /** Свободен ли слот попапа: `null` — да, иначе причина (тогда попап ждёт в очереди). */
    fun canOpen(popup: PopupV3): String?

    fun present(popup: PopupV3, variables: Map<String, String>, listener: Listener)
}

/** Таймеры движка на главном потоке; возвращает отмену. Подменяется в тестах. */
internal fun interface PopupsV3Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): () -> Unit
}

/** Хранилище частоты: по магазину, переживает перезапуск. */
internal interface PopupsV3Storage {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** Хранилище в памяти — для тестов и показа без SDK. */
internal class PopupsV3MemoryStorage : PopupsV3Storage {
    val values = HashMap<String, Any>()
    override fun getLong(key: String): Long? = values[key] as? Long
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
    override fun getString(key: String): String? = values[key] as? String
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

/**
 * Частота и пауза показов — как `frequency.ts` web: время и сеанс последнего показа по id
 * (`pv3_<id>_last`, `pv3_<id>_sid`), `once_session` сравнивает сеанс, `once_day/week/month`
 * — 24 ч, 7 и 30 дней, `every_page` — один показ на открытие экрана, пауза
 * `cooldown_minutes` проверяется первой. В тестовом режиме на диск ничего не пишется.
 */
internal class PopupsV3Frequency(
    private val storage: PopupsV3Storage,
    private val now: () -> Long,
    private val sid: () -> String?,
    private val testMode: () -> Boolean
) {
    private val shownOnScreen = HashSet<Int>()

    /** Открыт новый экран — новая «страница» для `every_page`. */
    fun newScreen() = shownOnScreen.clear()

    fun lastShow(id: Int): Long? = storage.getLong(lastKey(id))

    /** `null` — показать можно, иначе причина. */
    fun canShow(popup: PopupV3): String? {
        val id = popup.id
        val now = now()
        val last = lastShow(id)
        if (id in shownOnScreen) return "already shown on this screen"
        val cooldown = popup.cooldownMinutes
        if (cooldown > 0 && last != null && now - last < cooldown * MINUTE) return "cooldown ${format(cooldown)} min"
        return when (popup.frequency) {
            "every_page" -> null
            "once_life" -> if (last == null) null else "once_life: already shown"
            "once_day", "once_week", "once_month" -> {
                val period = PERIODS.getValue(popup.frequency)
                if (last == null || now - last >= period) null else "${popup.frequency}: shown recently"
            }
            else -> {
                val shownSid = storage.getString(sidKey(id))
                if (shownSid != null && shownSid == sid()) "once_session: already shown in this session" else null
            }
        }
    }

    fun record(popup: PopupV3) {
        shownOnScreen += popup.id
        if (testMode()) return
        storage.putLong(lastKey(popup.id), now())
        sid()?.takeIf { it.isNotEmpty() }?.let { storage.putString(sidKey(popup.id), it) }
    }

    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
        val PERIODS = mapOf("once_day" to DAY, "once_week" to 7 * DAY, "once_month" to 30 * DAY)

        fun lastKey(id: Int) = "pv3_${id}_last"
        fun sidKey(id: Int) = "pv3_${id}_sid"
    }
}

/**
 * Движок попапов v3 одного инстанса SDK, без UI: список и его кеш, экраны и триггеры,
 * частота, очередь занятых слотов, промокод до показа, события. Всё — на главном потоке.
 *
 * - `app_start` проверяется при каждой загрузке списка и на каждом [screenOpened] подходящего
 *   экрана; показ решают частота и пауза (`once_session` — раз за сеанс, `every_page` — на
 *   каждом экране), так попап со списком экранов сам дожидается своего.
 * - `screen_open` — на [screenOpened] подходящего экрана (и на загрузке списка, если такой
 *   экран уже открыт).
 * - `time_on_screen` — через `trigger_value` секунд на том же экране; смена экрана отменяет
 *   таймер, уход в фон сбрасывает, на переднем плане он стартует заново.
 * - `track_event` — на кастомном событии SDK с таким именем, если текущий экран подходит;
 *   экран не ждёт.
 * - `manual` — только [showManual]: без триггера, экранов и частоты.
 *
 * Хост выключил автопоказ попапов ([autoPresentation] — `false`): попапы триггеров
 * пропускаются, ручной показ работает — его хост попросил сам.
 */
internal class PopupsV3Engine(
    private val transport: PopupsV3Transport,
    private val presenter: PopupsV3Presenter,
    private val frequency: PopupsV3Frequency,
    private val scheduler: PopupsV3Scheduler,
    private val clock: () -> Long,
    private val sid: () -> String,
    private val testMode: () -> Boolean,
    private val debug: () -> Boolean,
    private val log: (String) -> Unit,
    /** Показывать ли попапы триггеров (`enableAutoPopupPresentation` SDK); ручной — всегда. */
    private val autoPresentation: () -> Boolean = { true }
) {
    private class Cache(val sid: String, val test: Boolean, val expires: Long, val popups: List<PopupV3>)

    private class BufferedEvent(val name: String, val at: Long)

    private var cache: Cache? = null
    private var loading = false
    private var reloadAfterLoad = false
    private var started = false
    private var listLoaded = false
    private var popups = LinkedHashMap<Int, PopupV3>()

    private var currentScreen: String? = null
    private var screenSeq = 0
    private var screenOpenedAt = 0L
    private var foreground = true

    private var session: String? = null
    private val timers = HashMap<Int, () -> Unit>()
    private val queue = ArrayList<Int>()
    private val showing = HashSet<Int>()
    private val open = HashSet<Int>()
    private val buffer = ArrayList<BufferedEvent>()

    private val signatures = HashMap<String, String>()
    private var invalidateTask: (() -> Unit)? = null
    private val promoCodes = HashMap<String, Pair<String, Long>>()

    val isStarted: Boolean get() = started

    /** Попапы текущего списка — для отладки и тестов. */
    val loaded: List<PopupV3> get() = popups.values.toList()

    /** Старт после `/init` с флагом (или с принудительным включением хоста). Повторный — только список. */
    fun start() {
        started = true
        load(force = false)
    }

    fun screenOpened(name: String) {
        currentScreen = name
        screenSeq++
        screenOpenedAt = clock()
        frequency.newScreen()
        cancelTimers()
        if (!listLoaded) return
        popups.values.toList().forEach { popup ->
            when (popup.trigger) {
                PopupV3.Trigger.APP_START, PopupV3.Trigger.SCREEN_OPEN -> if (popup.matchesScreen(name)) fire(popup)
                PopupV3.Trigger.TIME_ON_SCREEN -> if (popup.matchesScreen(name) && foreground) startTimer(popup, 0L)
                else -> Unit
            }
        }
    }

    fun onBackground() {
        foreground = false
        cancelTimers()
    }

    fun onForeground() {
        if (foreground) return
        foreground = true
        if (started) {
            val cached = cache
            if (cached == null || cached.sid != sid() || cached.test != testMode() || cached.expires <= clock()) load(force = false)
        }
        if (!listLoaded || currentScreen == null) return
        popups.values.forEach { popup ->
            if (popup.trigger == PopupV3.Trigger.TIME_ON_SCREEN && popup.matchesScreen(currentScreen)) startTimer(popup, 0L)
        }
    }

    /** Кастомное событие SDK (`push/custom`). До загрузки списка ждёт в буфере 10 с. */
    fun onTrackEvent(name: String) {
        if (!listLoaded) {
            buffer += BufferedEvent(name, clock())
            if (buffer.size > BUFFER_LIMIT) buffer.removeAt(0)
            return
        }
        popups.values.toList().forEach { popup ->
            if (popup.trigger != PopupV3.Trigger.TRACK_EVENT || popup.triggerEvent != name) return@forEach
            if (popup.matchesScreen(currentScreen)) {
                fire(popup)
            } else {
                debugLog("popup ${popup.id} skipped: screen $currentScreen is not in ${popup.targetScreens}")
            }
        }
    }

    /**
     * Смена контекста (профиль, подписка, сегмент): кеш списка сбрасывается и через 2 с
     * список запрашивается заново. Те же данные повторно в этом сеансе кеш не сбрасывают.
     */
    fun onContextChange(type: String, signature: String) {
        if (signatures[type] == signature) return
        signatures[type] = signature
        invalidate()
    }

    /** Покупка: список запрашивается заново. */
    fun onPurchase() = invalidate()

    /** Ручной показ: попап по id с сервера, без триггера, экранов и частоты. */
    fun showManual(id: Int) {
        transport.one(id, testMode()) { popup ->
            if (popup == null) {
                debugLog("popup $id not found")
            } else {
                show(popup, manual = true)
            }
        }
    }

    private fun invalidate() {
        if (!started) return
        invalidateTask?.invoke()
        invalidateTask = scheduler.schedule(INVALIDATE_DELAY_MS) {
            invalidateTask = null
            debugLog("list invalidated")
            cache = null
            load(force = true)
        }
    }

    private fun load(force: Boolean) {
        val currentSid = sid()
        val test = testMode()
        if (!force) {
            cache?.takeIf { it.sid == currentSid && it.test == test && it.expires > clock() }?.let {
                setList(it.popups)
                return
            }
        }
        if (loading) {
            if (force) reloadAfterLoad = true
            return
        }
        loading = true
        transport.list(test, debug()) { result ->
            loading = false
            when (result) {
                is PopupsV3ListResult.Success -> {
                    val list = result.list
                    list.skipped.forEach { (id, reason) -> debugLog("popup $id skipped: $reason") }
                    list.excluded.forEach { (id, reason) -> debugLog("popup $id excluded by the server: $reason") }
                    cache = Cache(currentSid, test, clock() + list.ttlSeconds * 1000, list.popups)
                    setList(list.popups)
                }
                is PopupsV3ListResult.ClientError -> {
                    debugLog("list request failed (${result.code}): ${result.message}")
                    cache = Cache(currentSid, test, clock() + PopupsV3List.DEFAULT_TTL * 1000, emptyList())
                    setList(emptyList())
                }
                is PopupsV3ListResult.Failure -> debugLog("list request failed: ${result.message}")
            }
            if (reloadAfterLoad) {
                reloadAfterLoad = false
                load(force = true)
            }
        }
    }

    /** Новый список: исчезнувшие попапы снимаются, новые встают на триггеры. Открытый остаётся. */
    private fun setList(list: List<PopupV3>) {
        val previous = popups
        val next = LinkedHashMap<Int, PopupV3>()
        list.forEach { next[it.id] = it }
        previous.keys.filter { it !in next }.forEach { id ->
            timers.remove(id)?.invoke()
            queue -= id
        }
        popups = next
        listLoaded = true
        debugLog("${next.size} popup(s) loaded")

        val current = sid()
        if (session != current) {
            session = current
            signatures.clear()
        }
        next.values.toList().forEach { popup ->
            when (popup.trigger) {
                PopupV3.Trigger.APP_START -> if (popup.matchesScreen(currentScreen)) {
                    fire(popup)
                } else {
                    debugLog("popup ${popup.id} waits for a screen of ${popup.targetScreens}")
                }
                PopupV3.Trigger.SCREEN_OPEN ->
                    if (popup.id !in previous && currentScreen != null && popup.matchesScreen(currentScreen)) fire(popup)
                PopupV3.Trigger.TIME_ON_SCREEN ->
                    if (currentScreen != null && foreground && popup.id !in timers && popup.matchesScreen(currentScreen)) {
                        startTimer(popup, clock() - screenOpenedAt)
                    }
                else -> Unit
            }
        }
        replayBuffer()
    }

    private fun replayBuffer() {
        if (buffer.isEmpty()) return
        val since = clock() - BUFFER_WINDOW_MS
        val events = buffer.filter { it.at >= since }
        buffer.clear()
        events.forEach { onTrackEvent(it.name) }
    }

    private fun startTimer(popup: PopupV3, elapsedMs: Long) {
        timers.remove(popup.id)?.invoke()
        val seq = screenSeq
        val delay = (popup.triggerSeconds * 1000).toLong() - elapsedMs
        timers[popup.id] = scheduler.schedule(delay.coerceAtLeast(0L)) {
            timers.remove(popup.id)
            if (seq == screenSeq && foreground) popups[popup.id]?.let(::fire)
        }
    }

    private fun cancelTimers() {
        timers.values.forEach { it() }
        timers.clear()
    }

    /** Триггер сработал: частота, затем показ. */
    private fun fire(popup: PopupV3) {
        frequency.canShow(popup)?.let { reason ->
            debugLog("popup ${popup.id} skipped: $reason")
            return
        }
        show(popup, manual = false)
    }

    private fun show(popup: PopupV3, manual: Boolean) {
        val id = popup.id
        if (id in showing || id in open) return
        if (!manual) {
            frequency.canShow(popup)?.let { reason ->
                debugLog("popup $id skipped: $reason")
                return
            }
            if (!autoPresentation()) {
                debugLog("popup $id skipped: automatic popup presentation is off")
                return
            }
        }
        if (popup.design.isEmpty) {
            debugLog("popup $id skipped: empty design")
            return
        }
        presenter.canOpen(popup)?.let { reason ->
            debugLog("popup $id postponed: $reason")
            if (!manual) enqueue(id)
            return
        }
        showing += id
        if (popup.needsPromoCode) {
            promoCode(popup.promoCodeListId!!) { code ->
                if (code == null) {
                    showing -= id
                    debugLog("popup $id skipped: no promo code")
                } else {
                    present(popup, manual, mapOf("promo_code" to code))
                }
            }
        } else {
            present(popup, manual, emptyMap())
        }
    }

    private fun present(popup: PopupV3, manual: Boolean, variables: Map<String, String>) {
        val id = popup.id
        // Пока ждали промокод, могла открыться другая модалка.
        presenter.canOpen(popup)?.let { reason ->
            showing -= id
            debugLog("popup $id postponed: $reason")
            if (!manual) enqueue(id)
            return
        }
        presenter.present(popup, variables, object : PopupsV3Presenter.Listener {
            override fun onShown() {
                showing -= id
                open += id
                if (!manual) frequency.record(popup)
                transport.event(id, EVENT_SHOW)
                debugLog("popup $id shown")
            }

            override fun onClosed() {
                showing -= id
                open -= id
                processQueue()
            }

            override fun onFailed(reason: String) {
                showing -= id
                debugLog("popup $id was not shown: $reason")
            }
        })
    }

    private fun enqueue(id: Int) {
        if (id !in queue) queue += id
    }

    private fun processQueue() {
        if (queue.isEmpty()) return
        val pending = queue.toList()
        queue.clear()
        pending.forEach { id -> popups[id]?.let { show(it, manual = false) } }
    }

    /** Код из списка: кеш 2 ч, ответ не дольше 10 с; нет кода — `null`. */
    private fun promoCode(listId: String, callback: (String?) -> Unit) {
        promoCodes[listId]?.takeIf { it.second > clock() }?.let {
            callback(it.first)
            return
        }
        var finished = false
        var cancelTimeout: (() -> Unit)? = null
        cancelTimeout = scheduler.schedule(PROMO_TIMEOUT_MS) {
            if (finished) return@schedule
            finished = true
            debugLog("promo code of list $listId: no answer in ${PROMO_TIMEOUT_MS / 1000} s")
            callback(null)
        }
        transport.promoCode(listId) { raw ->
            if (finished) return@promoCode
            finished = true
            cancelTimeout?.invoke()
            val code = raw?.replace(Regex("[<>\"'&]"), "")?.trim()?.takeIf { it.isNotEmpty() }
            if (code != null) promoCodes[listId] = code to clock() + PROMO_CACHE_MS
            callback(code)
        }
    }

    private fun debugLog(message: String) {
        if (debug()) log("popups v3: $message")
    }

    companion object {
        const val EVENT_SHOW = "show"
        const val INVALIDATE_DELAY_MS = 2_000L
        const val PROMO_TIMEOUT_MS = 10_000L
        const val PROMO_CACHE_MS = 2 * 60 * 60 * 1000L
        const val BUFFER_LIMIT = 20
        const val BUFFER_WINDOW_MS = 10_000L
    }
}
