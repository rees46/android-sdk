package com.personalization.ui.widgets

/**
 * Логика полноэкранного просмотра сторис без вьюх (E1): где мы (сторис и слайд), с какого
 * слайда открыть сторис, почему слайд на паузе, сколько он уже идёт, открыта ли шторка.
 *
 * Вьюер только рисует и передаёт сюда события — касания, тики часов, готовность медиа,
 * уход в фон — и выполняет возвращённый [Move]. Сторис, на которую переходим, становится
 * текущей, когда пейджер на неё встал ([selectStory]), а начинает идти — когда он
 * остановился ([enter]): подсмотренная наполовину страница не считается показанной (A1).
 */
internal class StoriesViewerState(
    private val stories: List<StoryShape>,
    private val seen: SeenSlides,
    startStory: Int = 0,
    /** Слайд стартовой сторис в обход A4 — `start_position` кампании по ссылке «Поделиться». */
    startSlide: Int? = null,
    /**
     * API v3, как веб-SDK (R8): сторис открывается со своего `start_position`, который идёт за показом
     * слайдов в этой сессии ([positionOf]); пройденная до конца — с начала. Просмотренной сторис
     * становится, когда с неё уходят вперёд ([leftForward]). Своих отметок слайдов нет.
     */
    private val feedPositions: Boolean = false
) {

    /** Сторис глазами просмотра. */
    data class StoryShape(
        val id: Int,
        val slides: List<SlideShape>,
        val serverViewed: Boolean = false,
        val startPosition: Int = 0
    )

    /** Слайд: id и время из админки (уже с умолчанием A7). */
    data class SlideShape(val id: String, val durationMs: Long)

    /**
     * Почему слайд стоит. Часы идут, только когда причин нет; плеер играет и при
     * [LOADING] (иначе видео не догрузится), и не играет при остальных.
     */
    enum class Pause {
        /** Палец держит слайд (B2). */
        HOLD,

        /** Слайд тянут — вниз, чтобы закрыть, или вбок, к соседней сторис (B4). */
        DRAG,

        /** Открыта шторка товаров (B5). */
        SHEET,

        /** Приложение в фоне, окно без фокуса, экран погас (B6). */
        BACKGROUND,

        /** Пауза хоста — `pause()` виджета (B8). */
        HOST,

        /** Открыто системное окно «Поделиться». */
        SHARE,

        /** Опрос: карточку держат, пишут в поле или ждут ответа. Снимается со сменой слайда. */
        POLL,

        /** Медиа ещё не готово или видео подгружается (C4, C5). */
        LOADING,

        /** Медиа не загрузилось — на слайде кнопка «Повторить» (C5). */
        ERROR
    }

    /** Зона тапа (B1). */
    enum class Zone { PREVIOUS, CENTER, NEXT }

    /** Откуда идёт время слайда. */
    enum class Clock {
        /** Таймер: время из админки. */
        TIMER,

        /** Позиция видео: длина ролика известна (C4). */
        PLAYER
    }

    /** Что сделать вьюеру после события. */
    sealed class Move {
        /** Ничего не меняется. */
        object None : Move()

        /** Текущий слайд сменился внутри сторис — перерисовать и запустить его. */
        object Slide : Move()

        /** Перейти к сторис [index]: пейджер листает, потом зовёт [selectStory] и [enter]. */
        data class Story(val index: Int) : Move()

        /** Начать текущий слайд сначала — прогресс и видео с нуля (B3); только старая лента. */
        object Restart : Move()

        /** Закрыть шторку товаров, никуда не листая (B5). */
        object CloseSheet : Move()

        /** Закрыть просмотр. */
        object Close : Move()
    }

    var storyIndex: Int = startStory.coerceIn(0, (stories.size - 1).coerceAtLeast(0))
        private set

    var slideIndex: Int = 0
        private set

    /** Сторис идёт: пейджер на ней остановился. */
    var entered: Boolean = false
        private set

    var sheetOpen: Boolean = false
        private set

    private val pauses = HashSet<Pause>()

    /** API v3: `start_position` каждой сторис в этой сессии. */
    private val positions = IntArray(stories.size) { stories[it].startPosition }

    /** API v3: сторис, с которых ушли вперёд, — просмотренные. */
    val leftForward: Set<Int> get() = forward
    private val forward = HashSet<Int>()

    /** Сторис, к которой просмотр листает по просьбе хоста ([jumpTo]): уход к ней — не «вперёд». */
    private var jumpTarget: Int? = null

    var clock: Clock = Clock.TIMER
        private set

    /** Сколько слайд уже идёт, мс. */
    var elapsedMs: Long = 0
        private set

    /** Длина текущего слайда, мс. */
    var durationMs: Long = 0
        private set

    init {
        // Слайда с таким номером нет — с первого, как в веб-SDK.
        val forced = startSlide?.let { index -> if (story?.slides?.indices?.contains(index) == true) index else 0 }
        slideIndex = forced ?: startSlideOf(storyIndex)
        resetSlide()
    }

    val storyCount: Int get() = stories.size

    val story: StoryShape? get() = stories.getOrNull(storyIndex)

    val slide: SlideShape? get() = story?.slides?.getOrNull(slideIndex)

    val pauseReasons: Set<Pause> get() = pauses

    /** Часы слайда идут. */
    val isRunning: Boolean get() = entered && slide != null && pauses.isEmpty()

    /** Видео должно играть: всё, кроме подгрузки и ошибки, его останавливает. */
    val playerShouldPlay: Boolean
        get() = entered && pauses.none { it != Pause.LOADING && it != Pause.ERROR }

    /** Заполнение текущей полоски прогресса, 0…1. */
    val progress: Float
        get() = if (durationMs <= 0) 0f else (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)

    fun isPaused(reason: Pause): Boolean = reason in pauses

    /** API v3: `start_position` сторис [index] в этой сессии. */
    fun positionOf(index: Int): Int = positions.getOrElse(index) { 0 }

    /** Слайд, с которого откроется сторис [index] (A4; на API v3 — её `start_position`, R8). */
    fun startSlideOf(index: Int): Int {
        val shape = stories.getOrNull(index) ?: return 0
        if (feedPositions) return if (shape.slides.isEmpty()) 0 else positions[index].coerceIn(0, shape.slides.lastIndex)
        return StoriesMapping.startSlide(
            shape.slides.map { it.id },
            seen.seen(shape.id),
            shape.serverViewed,
            shape.startPosition
        )
    }

    /** Пейджер выбрал сторис [index]: она становится текущей и откроется по правилу A4. */
    fun selectStory(index: Int) {
        if (index == storyIndex || index !in stories.indices) return
        // Свайп к следующей — уход вперёд, как событие `next` веб-SDK; к той, что открыл хост, — нет.
        if (feedPositions && index > storyIndex && jumpTarget != index) forward += storyIndex
        jumpTarget = null
        entered = false
        storyIndex = index
        slideIndex = startSlideOf(index)
        resetSlide()
    }

    /**
     * Пейджер остановился на текущей сторис — она пошла: слайд показан, он просмотрен (A1).
     * Возвращает `true`, если слайд только что начался: вьюер трекает `view` и грузит медиа.
     */
    fun enter(): Boolean {
        if (entered || slide == null) return false
        entered = true
        startSlide()
        return true
    }

    /** Сторис ушла с экрана (просмотр закрыт или свёрнут насовсем). */
    fun leave() {
        entered = false
    }

    fun next(): Move {
        val shape = story ?: return away(Move.Close)
        if (slideIndex + 1 < shape.slides.size) {
            slideIndex++
            startSlide()
            return Move.Slide
        }
        // Сторис пройдена: на API v3 она просмотрена и в следующий раз откроется с начала.
        if (feedPositions) {
            positions[storyIndex] = 0
            forward += storyIndex
        }
        return if (storyIndex + 1 < stories.size) away(Move.Story(storyIndex + 1)) else away(Move.Close)
    }

    fun previous(): Move = when {
        slideIndex > 0 -> {
            slideIndex--
            startSlide()
            Move.Slide
        }
        storyIndex > 0 -> away(Move.Story(storyIndex - 1))
        // API v3, как веб-SDK (событие `prev` на первой сторис): назад с первого слайда — просмотр закрывается.
        feedPositions -> away(Move.Close)
        else -> {
            // Первый слайд первой сторис начинается заново (B3).
            elapsedMs = 0
            Move.Restart
        }
    }

    /**
     * Хост открывает сторис [index], пока просмотр открыт (F11): листаем к ней в нём же.
     * Шторка закрывается; сторис откроется по правилу A4, когда пейджер на неё встанет.
     */
    fun jumpTo(index: Int): Move {
        if (index !in stories.indices || index == storyIndex) return Move.None
        if (sheetOpen) setSheet(false)
        jumpTarget = index
        return away(Move.Story(index))
    }

    /**
     * Уходим со сторис: часы стоят, пока пейджер не встанет на соседнюю, — иначе каждый
     * следующий тик снова просил бы перейти.
     */
    private fun away(move: Move): Move {
        entered = false
        return move
    }

    /** Тап по слайду (B1, B5): при открытой шторке он только закрывает её. */
    fun tap(zone: Zone): Move {
        if (sheetOpen) {
            setSheet(false)
            return Move.CloseSheet
        }
        return when (zone) {
            Zone.PREVIOUS -> previous()
            Zone.NEXT -> next()
            Zone.CENTER -> Move.None
        }
    }

    /** Системная «Назад»: сначала шторка, потом просмотр (B5). */
    fun back(): Move {
        if (sheetOpen) {
            setSheet(false)
            return Move.CloseSheet
        }
        return Move.Close
    }

    fun setSheet(open: Boolean) {
        sheetOpen = open
        setPaused(Pause.SHEET, open)
    }

    /** Включить или снять причину паузы. Возвращает `true`, если изменилось, идут ли часы. */
    fun setPaused(reason: Pause, paused: Boolean): Boolean {
        val before = isRunning to playerShouldPlay
        if (paused) pauses.add(reason) else pauses.remove(reason)
        return before != (isRunning to playerShouldPlay)
    }

    /** Тик часов: прошло [deltaMs]. Время вышло — следующий слайд. */
    fun tick(deltaMs: Long): Move {
        if (!isRunning || clock != Clock.TIMER || deltaMs <= 0) return Move.None
        elapsedMs += deltaMs
        if (elapsedMs < durationMs) return Move.None
        elapsedMs = durationMs
        return next()
    }

    /** Позиция видео (C4): прогресс идёт за воспроизведением. */
    fun videoPosition(positionMs: Long) {
        if (clock != Clock.PLAYER) return
        elapsedMs = positionMs.coerceIn(0, durationMs)
    }

    /**
     * Медиа готово (C5): картинка декодирована, видео играет. [videoDurationMs] — длина
     * ролика, если известна: слайд идёт по ней, а не по времени из админки (C4).
     * [animationLoopMs] — проход GIF: слайд идёт по большему из него и админки (G2).
     */
    fun mediaReady(videoDurationMs: Long? = null, animationLoopMs: Long? = null) {
        if (videoDurationMs != null && videoDurationMs > 0) {
            clock = Clock.PLAYER
            durationMs = videoDurationMs
        } else if (animationLoopMs != null) {
            val serverMs = slide?.durationMs ?: StoriesMapping.DEFAULT_DURATION_MS
            durationMs = StoriesMapping.animatedDurationMs(serverMs, animationLoopMs)
        }
        setPaused(Pause.ERROR, false)
        setPaused(Pause.LOADING, false)
    }

    /** Видео встало на подгрузку и снова пошло (C4). */
    fun buffering(active: Boolean) {
        setPaused(Pause.LOADING, active)
    }

    /** Медиа не загрузилось или не успело за 10 с (C5). */
    fun mediaFailed() {
        setPaused(Pause.LOADING, false)
        setPaused(Pause.ERROR, true)
    }

    /** «Повторить»: слайд грузится заново с нуля. */
    fun retry() {
        resetClock()
        setPaused(Pause.ERROR, false)
        setPaused(Pause.LOADING, true)
    }

    /** Видео доиграло — слайд закончился (C4). */
    fun mediaEnded(): Move = if (entered) next() else Move.None

    val hasError: Boolean get() = Pause.ERROR in pauses

    /** Новый текущий слайд: шторка закрыта, время с нуля, медиа ещё грузится; он просмотрен. */
    private fun startSlide() {
        resetSlide()
        val shape = story ?: return
        val current = slide ?: return
        if (feedPositions) positions[storyIndex] = slideIndex else seen.markSeen(shape.id, current.id)
    }

    private fun resetSlide() {
        sheetOpen = false
        pauses.remove(Pause.SHEET)
        pauses.remove(Pause.ERROR)
        // Карточка опроса ушла вместе со слайдом — её пауза не должна залипнуть.
        pauses.remove(Pause.POLL)
        pauses.add(Pause.LOADING)
        resetClock()
    }

    private fun resetClock() {
        clock = Clock.TIMER
        elapsedMs = 0
        durationMs = slide?.durationMs ?: StoriesMapping.DEFAULT_DURATION_MS
    }

    companion object {
        /**
         * Зона тапа по трети ширины (B1): левая — назад, правая — вперёд, середина — ничего.
         * В RTL края меняются местами.
         */
        fun zone(x: Float, width: Int, rtl: Boolean): Zone {
            if (width <= 0) return Zone.CENTER
            val third = width / 3f
            return when {
                x < third -> if (rtl) Zone.NEXT else Zone.PREVIOUS
                x > width - third -> if (rtl) Zone.PREVIOUS else Zone.NEXT
                else -> Zone.CENTER
            }
        }
    }
}
