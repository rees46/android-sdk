package com.personalization.ui.widgets

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.personalization.OnClickListener
import com.personalization.SDK
import com.personalization.stories.models.Story
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.widgets.StoriesViewerState.Pause

/**
 * Полноэкранный просмотр сторис (V2) — на компонентах кита.
 *
 * Паттерн Stories из Figma (страница 1:32): слайд со скримом, сверху
 * [com.personalization.ui.components.PersonalizationStoryNavigation], дальше по содержимому —
 * карточка товара или текстовые блоки, кнопки и «Используемые товары» (см. [StorySlideView]).
 *
 * Логика — в [StoriesViewerState]: тап по трети ширины, удержание — пауза, свайп вбок —
 * соседняя сторис, вниз — закрыть; сторис открывается с первого невиденного слайда, и
 * увиденное хранится по магазину. Пауза и при уходе в фон, потере фокуса, погасшем экране.
 *
 * Ссылки и товары — через [onClickListener] с контрактом старых сторис: `true` — SDK
 * открывает сам, `false` — хост обработал. Перед переходом просмотр закрывается.
 *
 * Модели — те же, что у старого `StoriesView`: V2 его не трогает.
 */
@InternalPersonalizationUiApi
class PersonalizationStoriesViewer(
    context: Context,
    stories: List<Story>,
    startStory: Int = 0
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    /** Контекст хоста: вьюхи строятся в его теме, чтобы работал брендинг кита. */
    private val hostContext = context

    /** Сторис без слайдов не показываются (C16). */
    private val stories: List<Story> = stories.filter { it.slidesCount > 0 }
    private var startIndex: Int = stories.getOrNull(startStory)?.let { this.stories.indexOf(it) }?.takeIf { it >= 0 } ?: 0

    /** Код блока — для трекинга. */
    var code: String? = null

    /** Инстанс SDK: трекинг и хранилище увиденного магазина. Без него сторис просто показываются. */
    var sdk: SDK? = null

    /** Магазин для хранилища увиденного, если [sdk] не зарегистрирован в реестре. */
    var shopId: String? = null

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    /** Клики по ссылкам и товарам — контракт старых сторис ([OnClickListener]). */
    var onClickListener: OnClickListener? = null

    var promoCaption: CharSequence
    var promoCopiedText: CharSequence
    var productButtonText: CharSequence
    var showProductsText: CharSequence
    var hideProductsText: CharSequence
    var retryText: CharSequence
    var closeLabel: CharSequence
    var soundOnLabel: CharSequence
    var soundOffLabel: CharSequence

    init {
        val defaults = StoriesTexts.defaults(context)
        promoCaption = defaults.promoCaption
        promoCopiedText = defaults.promoCopiedText
        productButtonText = defaults.productButtonText
        showProductsText = defaults.showProductsText
        hideProductsText = defaults.hideProductsText
        retryText = defaults.retryText
        closeLabel = defaults.closeLabel
        soundOnLabel = defaults.soundOnLabel
        soundOffLabel = defaults.soundOffLabel
    }

    private lateinit var state: StoriesViewerState
    private lateinit var root: StoryDismissLayout
    private lateinit var pager: ViewPager2
    private lateinit var texts: StoriesTexts
    private val pages = HashMap<Int, StoryPage>()
    private val handler = Handler(Looper.getMainLooper())
    private val frameClock = StoryFrameClock(::onFrame)
    private val readyTimer = StoryTimer(handler) { onReadyTimeout() }
    private val videoRetryTimer = StoryTimer(handler) { currentSlideView()?.let(::retry) }
    private var player: ExoPlayer? = null
    private var playerSlide: StorySlideView? = null
    private var audioFocus: StoriesAudioFocus? = null
    private val sound = StoriesSound()
    private var insets: Insets = Insets.NONE
    private var hostLifecycle: Lifecycle? = null
    private var created = false
    private var dismissed = false
    private var hostStopped = false
    private var windowFocused = true
    private var hostPaused = false
    private var dismissDragging = false
    private var pagerDragging = false

    /** Поставить просмотр на паузу из кода хоста (B8). */
    fun pause() = setHostPaused(true)

    /** Снять паузу хоста. */
    fun resume() = setHostPaused(false)

    /**
     * Открыть сторис [storyId] в уже открытом просмотре (F11): пейджер листает к ней, просмотр
     * не пересоздаётся. `false` — такой сторис в просмотре нет или он уже закрыт.
     */
    internal fun showStory(storyId: Int): Boolean {
        if (dismissed) return false
        val index = stories.indexOfFirst { it.id == storyId }
        if (index < 0) return false
        if (!created) {
            startIndex = index
            return true
        }
        if (state.sheetOpen) currentSlideView()?.closeSheet()
        apply(state.jumpTo(index))
        return true
    }

    private fun setHostPaused(paused: Boolean) {
        hostPaused = paused
        if (!created) return
        state.setPaused(Pause.HOST, paused)
        sync()
    }

    internal fun applyTexts(texts: StoriesTexts) {
        promoCaption = texts.promoCaption
        promoCopiedText = texts.promoCopiedText
        productButtonText = texts.productButtonText
        showProductsText = texts.showProductsText
        hideProductsText = texts.hideProductsText
        retryText = texts.retryText
        closeLabel = texts.closeLabel
        soundOnLabel = texts.soundOnLabel
        soundOffLabel = texts.soundOffLabel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texts = StoriesTexts(
            promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
            retryText, closeLabel, soundOnLabel, soundOffLabel
        )
        val seen = StoriesSeenStore.forSdk(hostContext, sdk, shopId)
        state = StoriesViewerState(stories.map(::shape), seen, startIndex)
        state.setPaused(Pause.HOST, hostPaused)
        created = true
        setupWindow()

        root = StoryDismissLayout(hostContext).apply {
            canDrag = { !state.sheetOpen }
            onDragChange = { dragging ->
                dismissDragging = dragging
                updateDrag()
            }
            onDismiss = { dismiss() }
        }
        pager = ViewPager2(hostContext).apply {
            adapter = StoriesAdapter()
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageCallback)
        }
        root.addView(pager, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            pages.values.forEach { it.slideView?.applyInsets(insets) }
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        volumeControlStream = AudioManager.STREAM_MUSIC
        audioFocus = StoriesAudioFocus(hostContext) { silence() }
        observeHost()

        if (stories.isEmpty()) {
            handler.post { dismiss() }
            return
        }
        pager.setCurrentItem(state.storyIndex, false)
        // Для начальной страницы пейджер onPageSelected может не прислать.
        handler.post { enterCurrent() }
    }

    /** Окно во весь экран: медиа уходит под панели и вырез, органы управления — нет. */
    private fun setupWindow() {
        val window = window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** Значки панелей светлые над медиа и тёмные на светлой теме хоста. */
    private fun applySystemBars(view: StorySlideView) {
        val window = window ?: return
        val night = (hostContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val light = !view.isMedia && !night
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Хост ушёл в фон, его закрыло другое окно, погас экран — пауза (B6). */
    private fun observeHost() {
        val lifecycle = hostContext.findLifecycleOwner()?.lifecycle ?: return
        hostLifecycle = lifecycle
        hostStopped = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycle.addObserver(hostObserver)
        setBackground()
    }

    private val hostObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> setBackground(stopped = false)
            Lifecycle.Event.ON_STOP -> setBackground(stopped = true)
            Lifecycle.Event.ON_DESTROY -> dismiss()
            else -> Unit
        }
    }

    private fun setBackground(stopped: Boolean = hostStopped) {
        hostStopped = stopped
        if (!created) return
        val background = hostStopped || !windowFocused
        state.setPaused(Pause.BACKGROUND, background)
        // Ожидание готовности и автоповтор видео в фоне стоят и потом идут с остатка (F5).
        readyTimer.freeze(background)
        videoRetryTimer.freeze(background)
        sync()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        setBackground()
    }

    /** «Назад» сначала закрывает шторку товаров (B5). */
    @Deprecated("Dialog still routes back here, also through OnBackInvokedDispatcher on API 33+")
    override fun onBackPressed() {
        if (!created) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            return
        }
        apply(state.back())
    }

    /** Кнопка громкости вверх включает звук (C7); громкость меняет система, как обычно. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && sound.muted) setMuted(false)
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        if (created && !dismissed) {
            dismissed = true
            frameClock.stop()
            handler.removeCallbacksAndMessages(null)
            state.leave()
            stopMedia()
            player?.release()
            player = null
            audioFocus?.abandon()
            hostLifecycle?.removeObserver(hostObserver)
            pages.clear()
        }
        super.dismiss()
    }

    // --- Навигация -------------------------------------------------------------------------

    private fun shape(story: Story) = StoriesViewerState.StoryShape(
        id = story.id,
        slides = List(story.slidesCount) { index ->
            val slide = story.getSlide(index)
            StoriesViewerState.SlideShape(slide.id, StoriesMapping.durationMs(slide))
        },
        serverViewed = story.isViewed,
        startPosition = story.startPosition
    )

    private fun currentPage(): StoryPage? = pages[state.storyIndex]

    private fun currentSlideView(): StorySlideView? = currentPage()?.slideView

    /** Какой слайд показывает страница: у текущей — текущий, у соседних — стартовый (A4). */
    private fun slideFor(index: Int): Int = if (index == state.storyIndex) state.slideIndex else state.startSlideOf(index)

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (!created || dismissed) return
            if (position != state.storyIndex) {
                stopMedia()
                state.selectStory(position)
                pages[position]?.showSlide(state.slideIndex)
            }
            if (pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) enterCurrent()
            sync()
        }

        override fun onPageScrollStateChanged(scrollState: Int) {
            if (!created || dismissed) return
            pagerDragging = scrollState == ViewPager2.SCROLL_STATE_DRAGGING
            updateDrag()
            // Страница, на которой пейджер остановился, — показанная (A1), подсмотренная — нет.
            if (scrollState == ViewPager2.SCROLL_STATE_IDLE) enterCurrent()
        }
    }

    private fun updateDrag() {
        if (!created) return
        state.setPaused(Pause.DRAG, dismissDragging || pagerDragging)
        sync()
    }

    private fun enterCurrent() {
        if (!created || dismissed) return
        // Ушедшие сторис откроются по правилу A4 — их страницы показывают стартовый слайд.
        pages.forEach { (index, page) -> if (index != state.storyIndex) page.showSlide(slideFor(index)) }
        if (state.enter()) onSlideStarted()
        sync()
    }

    /** Слайд стал текущим на остановившейся странице: трекинг, медиа, предзагрузка. */
    private fun onSlideStarted() {
        track("view")
        preloadNext()
        // Страницы может ещё не быть: тогда медиа запустит привязка адаптера.
        val page = currentPage() ?: return
        page.showSlide(state.slideIndex)
        page.slideView?.let(::startMedia)
    }

    private fun apply(move: StoriesViewerState.Move) {
        if (dismissed) return
        when (move) {
            StoriesViewerState.Move.None -> Unit
            StoriesViewerState.Move.Slide -> {
                stopMedia()
                onSlideStarted()
            }
            is StoriesViewerState.Move.Story -> {
                stopMedia()
                pager.setCurrentItem(move.index, true)
            }
            StoriesViewerState.Move.Restart -> {
                val view = currentSlideView()
                if (view != null && view === playerSlide) player?.seekTo(0)
                view?.restartAnimation()
                view?.setProgress(0f)
            }
            StoriesViewerState.Move.CloseSheet -> currentSlideView()?.closeSheet()
            StoriesViewerState.Move.Close -> {
                dismiss()
                return
            }
        }
        sync()
    }

    /** Привести часы, плеер, пейджер и фокус звука к состоянию. */
    private fun sync() {
        if (!created || dismissed) return
        if (state.isRunning) frameClock.start() else frameClock.stop()
        player?.playWhenReady = playerSlide != null && state.playerShouldPlay
        currentSlideView()?.setAnimationRunning(state.isRunning)
        pager.isUserInputEnabled = !state.sheetOpen
        currentSlideView()?.setProgress(state.progress)
        updateAudioFocus()
    }

    private fun onFrame(deltaMs: Long) {
        if (!created || dismissed) return
        val view = currentSlideView()
        val exo = player
        if (state.clock == StoriesViewerState.Clock.PLAYER && exo != null && view != null && view === playerSlide) {
            state.videoPosition(exo.currentPosition)
        }
        val move = state.tick(deltaMs)
        view?.setProgress(state.progress)
        if (move != StoriesViewerState.Move.None) apply(move)
    }

    // --- Слайд: касания и клики -------------------------------------------------------------

    private val slideHost = object : StorySlideHost {
        override val texts: StoriesTexts get() = this@PersonalizationStoriesViewer.texts
        override val muted: Boolean get() = sound.muted
        override val sheetOpen: Boolean get() = created && state.sheetOpen
        override val insets: Insets get() = this@PersonalizationStoriesViewer.insets

        override fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)?, onFailed: (() -> Unit)?) =
            this@PersonalizationStoriesViewer.loadImage(view, url, onReady, onFailed)

        override fun loadSlideImage(
            view: ImageView,
            url: String,
            onAnimation: (Long?, GifDrawable) -> Unit,
            onReady: () -> Unit,
            onFailed: () -> Unit
        ) = this@PersonalizationStoriesViewer.loadSlideImage(view, url, onAnimation, onReady, onFailed)

        override fun onTap(x: Float, width: Int, rtl: Boolean) = apply(state.tap(StoriesViewerState.zone(x, width, rtl)))

        override fun onHold(hold: Boolean) {
            if (!created) return
            state.setPaused(Pause.HOLD, hold)
            sync()
        }

        override fun onOutsideSheetTap() = apply(state.tap(StoriesViewerState.Zone.CENTER))

        override fun onSheet(open: Boolean) {
            if (!created || state.sheetOpen == open) return
            state.setSheet(open)
            sync()
        }

        override fun onButton(button: com.personalization.stories.models.elements.ButtonElement, product: com.personalization.Product?) {
            track("click")
            val link = button.link
            when {
                link.isNotEmpty() -> {
                    dismiss()
                    if (clicks.onClick(link)) openUrl(link)
                }
                // Кнопка без ссылки открывает товар слайда (C11).
                product != null -> openProduct(product)
            }
        }

        override fun onProduct(product: com.personalization.Product) {
            track("click")
            openProduct(product)
        }

        /** Карусель — как у старых сторис: `onCloseDialogClick` решает, закрыть или открыть. */
        override fun onCarouselProduct(product: com.personalization.Product) {
            track("click")
            val url = StoriesMapping.productLink(product)
            when {
                !clicks.onClick(product) -> dismiss()
                clicks.onCloseDialogClick(product, url) -> dismiss()
                else -> openUrl(url)
            }
        }

        override fun onPromoCopied() = track("click")

        override fun onSoundToggle() = setMuted(!sound.muted)

        override fun onRetry(view: StorySlideView) = retry(view)

        override fun onClose() = dismiss()
    }

    private val clicks: OnClickListener get() = onClickListener ?: DEFAULT_CLICKS

    private fun openProduct(product: com.personalization.Product) {
        dismiss()
        if (clicks.onClick(product)) openUrl(StoriesMapping.productLink(product))
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) return
        val activity = hostContext.findActivity()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        if (activity == null) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            (activity ?: hostContext).startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            SDK.error("PersonalizationStoriesViewer: no app can open $url")
        }
    }

    /** `view` и `click` с кодом блока; id не целые положительные — не трекаются (A6). */
    private fun track(event: String) {
        if (!created) return
        val blockCode = code ?: return
        val story = stories.getOrNull(state.storyIndex) ?: return
        if (state.slideIndex !in 0 until story.slidesCount) return
        val slide = story.getSlide(state.slideIndex)
        if (!StoriesMapping.isTrackable(story.id, slide.id)) return
        runCatching { sdk?.storiesManager?.trackStory(event, blockCode, story.id, slide.id) }
    }

    /** Картинка компонента: у GIF только первый кадр (G6). */
    private fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)?, onFailed: (() -> Unit)?) {
        val loader = imageLoader
        if (loader != null) {
            // Загрузчик хоста о себе не сообщает: готовность видна по картинке во вьюхе.
            loader(view, url)
            return
        }
        Glide.with(view).asBitmap().load(url)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    onFailed?.invoke()
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    onReady?.invoke()
                    return false
                }
            })
            .into(view)
    }

    /**
     * Фон слайда: GIF здесь играет (G1). Своя цель вместо обычной: Glide сам запускает GIF
     * при показе и на `onStart` хоста, а он должен идти только вместе с часами слайда (G5).
     * Проход и сам GIF отдаются до того, как картинка встанет во вьюху: по ней слайд готов.
     */
    private fun loadSlideImage(
        view: ImageView,
        url: String,
        onAnimation: (Long?, GifDrawable) -> Unit,
        onReady: () -> Unit,
        onFailed: () -> Unit
    ) {
        val loader = imageLoader
        if (loader != null) {
            // GIF загрузчика хоста SDK не трогает и не меряет (G8).
            loader(view, url)
            return
        }
        Glide.with(view).load(url).into(object : CustomViewTarget<ImageView, Drawable>(view) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (resource is GifDrawable) {
                    resource.setLoopCount(GifDrawable.LOOP_FOREVER)
                    onAnimation(StoriesGif.loopMs(resource.buffer), resource)
                }
                this.view.setImageDrawable(resource)
                onReady()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) = onFailed()

            override fun onResourceCleared(placeholder: Drawable?) {
                (this.view.drawable as? GifDrawable)?.stop()
                this.view.setImageDrawable(placeholder)
            }
        })
    }

    // --- Медиа -----------------------------------------------------------------------------

    /**
     * Часы слайда пойдут, когда медиа готово (C5): картинка загружена, видео играет.
     * Не готово за 10 с или ошибка — пауза и «Повторить». Время в фоне не считается (F5):
     * Glide на `onStop` хоста ставит загрузки на паузу без ошибки и сам продолжает их на
     * `onStart`, а ожидание готовности тем временем заморожено.
     */
    private fun startMedia(view: StorySlideView) {
        if (view.mediaStarted || dismissed) return
        view.mediaStarted = true
        applySystemBars(view)
        cancelMediaTimers()
        when {
            view.isVideo -> attachVideo(view)
            view.isMedia -> {
                view.onImageState = { onImageState(view) }
                onImageState(view)
            }
            else -> onMediaReady(view, null)
        }
        if (view.isMedia) readyTimer.start(READY_TIMEOUT_MS)
    }

    private fun onImageState(view: StorySlideView) {
        when (view.imageState) {
            StorySlideView.ImageState.READY -> onMediaReady(view, null)
            StorySlideView.ImageState.FAILED -> onMediaFailed(view)
            StorySlideView.ImageState.LOADING -> Unit
        }
    }

    private fun onMediaReady(view: StorySlideView, videoDurationMs: Long?) {
        if (view !== currentSlideView() || dismissed) return
        view.mediaReadyOnce = true
        cancelMediaTimers()
        view.showRetry(false)
        state.mediaReady(videoDurationMs, view.animationLoopMs)
        sync()
    }

    private fun onMediaFailed(view: StorySlideView) {
        if (view !== currentSlideView() || dismissed) return
        readyTimer.cancel()
        state.mediaFailed()
        view.showRetry(true)
        // Видео пробует ещё раз само; картинка — по кнопке.
        if (view.isVideo) videoRetryTimer.start(VIDEO_RETRY_MS)
        sync()
    }

    private fun onReadyTimeout() {
        val view = currentSlideView() ?: return
        if (!view.mediaReadyOnce && !state.hasError) onMediaFailed(view)
    }

    private fun retry(view: StorySlideView) {
        if (view !== currentSlideView() || dismissed) return
        cancelMediaTimers()
        if (view === playerSlide) detachPlayer()
        view.showRetry(false)
        view.mediaStarted = false
        view.mediaReadyOnce = false
        state.retry()
        if (!view.isVideo) view.loadImage()
        startMedia(view)
        sync()
    }

    private fun cancelMediaTimers() {
        readyTimer.cancel()
        videoRetryTimer.cancel()
    }

    /** Уход со слайда: таймеры и повторы отменяются, плеер отдан, GIF стоит. */
    private fun stopMedia() {
        cancelMediaTimers()
        detachPlayer()
        currentSlideView()?.stopAnimation()
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun obtainPlayer(): ExoPlayer = player ?: ExoPlayer.Builder(hostContext)
        .setMediaSourceFactory(StoriesMediaCache.mediaSourceFactory(hostContext))
        .build()
        .also { exo ->
            exo.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            exo.addListener(playerListener)
            player = exo
            applyVolume()
        }

    private fun attachVideo(view: StorySlideView) {
        val exo = obtainPlayer()
        detachPlayer()
        playerSlide = view
        view.playerView?.player = exo
        exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(view.slide.background))
        exo.prepare()
        exo.playWhenReady = state.playerShouldPlay
    }

    private fun detachPlayer() {
        val view = playerSlide ?: return
        playerSlide = null
        player?.let { exo ->
            exo.stop()
            exo.clearMediaItems()
        }
        view.playerView?.player = null
        updateAudioFocus()
    }

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val view = playerSlide ?: return
            val exo = player ?: return
            when (playbackState) {
                androidx.media3.common.Player.STATE_READY ->
                    if (!view.mediaReadyOnce) {
                        val duration = exo.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0 }
                        onMediaReady(view, duration)
                    } else {
                        state.buffering(false)
                        sync()
                    }
                // Подгрузка посреди ролика — прогресс стоит (C4).
                androidx.media3.common.Player.STATE_BUFFERING -> if (view.mediaReadyOnce) {
                    state.buffering(true)
                    sync()
                }
                androidx.media3.common.Player.STATE_ENDED -> if (view === currentSlideView()) apply(state.mediaEnded())
                else -> Unit
            }
        }

        override fun onRenderedFirstFrame() {
            playerSlide?.hidePoster()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            SDK.error("PersonalizationStoriesViewer: video failed", error)
            playerSlide?.let(::onMediaFailed)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Выдернули наушники: ролик идёт дальше без звука, выбор звука не меняется (F6).
            if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                silence()
                sync()
            }
        }
    }

    /** Звук включил или выключил пользователь: выбор запоминается на весь процесс (C7). */
    private fun setMuted(value: Boolean) {
        sound.choose(value)
        applySound()
    }

    /** Звонок, другой плеер, выдернутые наушники: глушится только это воспроизведение (F6). */
    private fun silence() {
        if (sound.silence()) applySound()
    }

    private fun applySound() {
        applyVolume()
        pages.values.forEach { it.slideView?.setMuted(sound.muted) }
        updateAudioFocus()
    }

    private fun applyVolume() {
        val exo = player ?: return
        exo.volume = if (sound.muted) 0f else 1f
        exo.setHandleAudioBecomingNoisy(!sound.muted)
    }

    /** Фокус берётся, только пока звук включён и видео на экране (C7). */
    private fun updateAudioFocus() {
        val focus = audioFocus ?: return
        val wanted = created && !dismissed && !sound.muted && playerSlide != null && !state.isPaused(Pause.BACKGROUND)
        if (wanted) focus.request() else focus.abandon()
    }

    /** Следующий слайд — заранее (C6): картинка или превью и начало видео. */
    private fun preloadNext() {
        val story = stories.getOrNull(state.storyIndex) ?: return
        val next = when {
            state.slideIndex + 1 < story.slidesCount -> story.getSlide(state.slideIndex + 1)
            state.storyIndex + 1 < stories.size ->
                stories[state.storyIndex + 1].getSlide(state.startSlideOf(state.storyIndex + 1))
            else -> return
        }
        if (StoriesMapping.isVideo(next)) {
            StoriesMediaCache.preloadVideo(hostContext, next.background)
            preloadImage(next.preview)
        } else if (StoriesMapping.isMedia(next)) {
            preloadImage(next.background)
        }
    }

    private fun preloadImage(url: String) {
        if (url.isEmpty() || imageLoader != null) return
        com.bumptech.glide.Glide.with(hostContext.applicationContext).load(url).preload()
    }

    private inner class StoriesAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = stories.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val page = StoryPage(hostContext)
            page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
            val page = holder.itemView as StoryPage
            pages.entries.removeAll { it.value === page }
            pages[position] = page
            page.bind(position)
            if (position == state.storyIndex && state.entered) page.slideView?.let(::startMedia)
        }

        override fun onViewRecycled(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
            val page = holder.itemView as StoryPage
            if (page.slideView != null && page.slideView === playerSlide) stopMedia()
            pages.entries.removeAll { it.value === page }
        }
    }

    /** Страница одной сторис: держит вьюху показанного слайда. */
    private inner class StoryPage(context: Context) : FrameLayout(context) {
        var storyIndex = -1
            private set
        var slideView: StorySlideView? = null
            private set

        fun bind(index: Int) {
            storyIndex = index
            showSlide(slideFor(index), force = true)
        }

        /** Слайд строится заново, если он другой или его медиа уже запускали. */
        fun showSlide(index: Int, force: Boolean = false) {
            val story = stories.getOrNull(storyIndex) ?: return
            if (index !in 0 until story.slidesCount) return
            val current = slideView
            if (!force && current != null && current.slideIndex == index && !current.mediaStarted) return
            if (current != null && current === playerSlide) stopMedia()
            removeAllViews()
            val view = StorySlideView(context, storyIndex, index, story.getSlide(index), story.slidesCount, slideHost)
            slideView = view
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    private companion object {
        /** Без слушателя хоста — поведение по умолчанию из контракта: SDK открывает сам. */
        val DEFAULT_CLICKS = object : OnClickListener {}

        const val READY_TIMEOUT_MS = 10_000L
        const val VIDEO_RETRY_MS = 15_000L
    }
}
