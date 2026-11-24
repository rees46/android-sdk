package com.personalization.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.Insets
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.personalization.Product
import com.personalization.R
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationStoryNavigation
import com.personalization.ui.components.PersonalizationStoryUsedProducts

/** Что слайд просит у просмотра: загрузку картинок, навигацию, клики. */
internal interface StorySlideHost {
    val texts: StoriesTexts
    val muted: Boolean
    val sheetOpen: Boolean
    val insets: Insets
    /** Картинка компонента — товар, постер видео: GIF стоит на первом кадре (G6). */
    fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)? = null, onFailed: (() -> Unit)? = null)

    /**
     * Фон слайда — GIF здесь играет (G1). [onAnimation] зовётся до того, как картинка встанет
     * во вьюху, и только для GIF, загруженного SDK: с проходом, мс, и самим GIF (G2, G5).
     */
    fun loadSlideImage(
        view: ImageView,
        url: String,
        onAnimation: (loopMs: Long?, gif: GifDrawable) -> Unit,
        onReady: () -> Unit,
        onFailed: () -> Unit
    )

    fun onTap(x: Float, width: Int, rtl: Boolean)
    fun onHold(hold: Boolean)
    fun onOutsideSheetTap()
    fun onSheet(open: Boolean)
    fun onButton(button: ButtonElement, product: Product?)
    fun onProduct(product: Product)
    fun onCarouselProduct(product: Product)
    fun onPromoCopied()
    fun onSoundToggle()
    fun onRetry(view: StorySlideView)
    fun onClose()

    /** «Поделиться» ссылкой [url] показанного слайда. */
    fun onShare(url: String)

    /** Опросы (API v3 или свой транспорт); `null` — элементы `poll` не рисуются. */
    val polls: StoryPollsContext? get() = null

    /** Опрос слайда [view] держит его: карточку трогают, пишут в поле или ждут ответа. */
    fun onPollHold(view: StorySlideView, hold: Boolean) = Unit

    /**
     * Сторис с API v3: слайд рисуется по правилам веб-SDK — медиа по `type`, цвет `background_color`,
     * шапка `header`, текстовые блоки по общему правилу; кнопка без ссылки ничего не открывает. На
     * старой ленте — как было.
     */
    val v3: Boolean get() = false

    /** Нажата шапка слайда (`header`, API v3). */
    fun onHeader(header: HeaderElement) = Unit
}

/**
 * Вёрстка одного слайда (Card/Story). Медиа-слайд строится в тёмном контексте (C1),
 * слайд без медиа — в теме хоста на Background/Card (C2). Слои снизу вверх: поля вокруг
 * медиа, фото/видео целиком (fit), скрим (C3), слой касаний, органы управления с отступами
 * от системных панелей и выреза, опросы, навигация (прогресс, крестик, «Поделиться» — поверх
 * опросов, как в веб-SDK), кнопка «Повторить» (C5). Медиа идёт под панели, органы управления — нет.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@SuppressLint("ViewConstructor")
internal class StorySlideView(
    baseContext: Context,
    val storyIndex: Int,
    val slideIndex: Int,
    val slide: Slide,
    slideCount: Int,
    private val host: StorySlideHost,
    /** Ссылка «Поделиться» слайда (API v3); `null` — кнопки нет. */
    val shareUrl: String? = null,
    /** Id сторис — для ответов на опросы. */
    private val storyId: Int = 0
) : FrameLayout(StoriesMapping.slideContext(baseContext, slide, host.v3)) {

    enum class ImageState { LOADING, READY, FAILED }

    val isMedia = StoriesMapping.isMedia(slide, host.v3)
    val isVideo = StoriesMapping.isVideo(slide, host.v3)

    /** Слайд в тёмных токенах: медиа или `background_color`, на котором светлый текст контрастнее (API v3) — значки панелей светлые. */
    val isDark = StoriesMapping.isDark(baseContext, slide, host.v3)

    /** API v3: свой цвет слайда, на котором тёмный текст контрастнее, — светлые токены и в ночной теме хоста. */
    val lightScheme = !isDark && host.v3 && StoriesMapping.ownColor(slide) != null
    val navigation = PersonalizationStoryNavigation(context)
    var playerView: PlayerView? = null
        private set

    /** Медиа слайда уже запускали — повторный показ строит слайд заново. */
    var mediaStarted = false

    /** Медиа хоть раз было готово: дальше подгрузка — буферизация, а не загрузка. */
    var mediaReadyOnce = false

    var imageState = ImageState.LOADING
        private set

    /** Картинка слайда загрузилась или нет. */
    var onImageState: (() -> Unit)? = null

    /** Проход GIF фона, мс; `null` — не GIF, проход не разобран или GIF грузил хост (G2, G8). */
    var animationLoopMs: Long? = null
        private set

    /** GIF фона, которым управляет SDK: идёт вместе с часами слайда (G5). */
    private var animation: GifDrawable? = null

    /** Следующий запуск GIF — с первого кадра: слайд начался или начат заново (G5). */
    private var animationFromStart = true

    private val poster = StoryImageView(context)
    private val column = LinearLayout(context)

    /** Ряд навигации поверх опросов: карточка может зайти под него, а он виден и нажимается (P8). */
    private val navigationBar = FrameLayout(context)
    private val retry = PersonalizationButton(context)
    private var usedProducts: PersonalizationStoryUsedProducts? = null
    private var usedHost: View? = null
    private var sheetTapPending = false
    private var pollLayer: StoryPollLayer? = null
    private val pollHolds = HashSet<StoryPollController>()
    private var keyboardTapPending = false
    private val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)

    /** Последние отступы системных панелей — ряд низа может пропасть позже ([setSoundAvailable]). */
    private var appliedInsets: Insets = Insets.NONE

    init {
        setBackgroundColor(StoriesMapping.slideColor(slide, host.v3, PersonalizationTheme.color(context, R.color.personalization_background_card)))
        if (isMedia) addMedia()
        addView(View(context).apply { background = scrim(StoriesMapping.scrimAlpha(slide, host.v3)) }, matchParent())
        addView(StoryTouchLayer(context, host::onTap, host::onHold), matchParent())

        column.orientation = LinearLayout.VERTICAL
        navigation.closeContentDescription = host.texts.closeLabel
        navigation.onClose = { host.onClose() }
        navigation.shareContentDescription = host.texts.shareLabel
        navigation.onShare = shareUrl?.let { url -> { host.onShare(url) } }
        navigation.progress.count = slideCount
        navigation.progress.current = slideIndex
        // Навигация в том же отступе 16, что и контент слайда (Figma: content padding 16). Сама она —
        // в ряду поверх опросов, а в колонке — её место той же высоты: шапка и контент стоят как прежде.
        navigationBar.addView(navigation, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = padding
            marginEnd = padding
            topMargin = padding
        })
        val slot = NavigationSlot(context, navigation)
        navigation.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> if (bottom - top != slot.height) slot.requestLayout() }
        column.addView(slot, columnParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (host.v3) StoriesMapping.header(slide)?.let { header ->
            column.addView(StoryHeader.build(context, header, host), columnParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        buildContent()
        addView(column, matchParent())
        // Опросы — над содержимым слайда и его кнопками, как в веб-SDK; навигация — над ними.
        buildPolls()?.let { layer -> addView(layer, matchParent()) }
        addView(navigationBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))

        retry.text = host.texts.retryText
        retry.buttonView = PersonalizationButton.ButtonView.PRIMARY
        retry.visibility = View.GONE
        retry.setOnClickListener { host.onRetry(this) }
        addView(retry, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        applyInsets(host.insets)
    }

    private fun addMedia() {
        poster.scaleType = ImageView.ScaleType.FIT_CENTER
        if (isVideo) {
            playerView = PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.TRANSPARENT)
                setBackgroundColor(Color.TRANSPARENT)
            }
            addView(playerView, matchParent())
        }
        addView(poster, matchParent())
        loadImage()
    }

    /** Картинка слайда или превью видео; у видео готовность решает плеер. */
    fun loadImage() {
        val url = if (isVideo) slide.preview else slide.background
        imageState = ImageState.LOADING
        animation?.stop()
        animation = null
        animationLoopMs = null
        if (url.isEmpty()) {
            setImageState(if (isVideo) ImageState.READY else ImageState.FAILED)
            return
        }
        poster.visibility = View.VISIBLE
        poster.onDrawable = { setImageState(ImageState.READY) }
        if (isVideo) {
            host.loadImage(poster, url, { setImageState(ImageState.READY) }, { setImageState(ImageState.FAILED) })
            return
        }
        host.loadSlideImage(
            poster,
            url,
            onAnimation = { loopMs, gif ->
                animationLoopMs = loopMs
                animation = gif
                animationFromStart = true
            },
            onReady = { setImageState(ImageState.READY) },
            onFailed = { setImageState(ImageState.FAILED) }
        )
    }

    /**
     * GIF фона идёт, только пока идут часы слайда (G5): пауза оставляет текущий кадр,
     * продолжение — с него же, новый или начатый заново слайд — с первого кадра.
     */
    fun setAnimationRunning(running: Boolean) {
        val gif = animation ?: return
        if (!running) {
            if (gif.isRunning) gif.stop()
            return
        }
        if (animationFromStart) {
            animationFromStart = false
            if (gif.isRunning) gif.stop()
            gif.startFromFirstFrame()
        } else if (!gif.isRunning) {
            gif.start()
        }
    }

    /** Слайд начат заново (B3): GIF пойдёт с первого кадра, без новой загрузки (F3). */
    fun restartAnimation() {
        animationFromStart = true
    }

    /** Слайд ушёл с экрана — GIF стоит. */
    fun stopAnimation() {
        animation?.stop()
        animationFromStart = true
    }

    private fun setImageState(state: ImageState) {
        if (imageState == state) return
        imageState = state
        onImageState?.invoke()
    }

    /**
     * Товарный слайд (C8) — карточка товара, все кнопки слайда и строка товаров/звука;
     * текстовые блоки на нём не рисуются. Медиа-слайд — текстовые блоки на своей высоте,
     * кнопки и та же строка.
     */
    private fun buildContent() {
        val productElement = StoriesMapping.product(slide)
        val product = productElement?.item
        val middle: View = if (productElement != null && product != null) {
            StoryProductCard.build(context, productElement, product, host, StoriesMapping.slidePromocode(slide))
        } else {
            StoryTextBlocks.build(context, slide, host)
        }
        column.addView(middle, columnParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = StoriesMapping.buttons(slide)
        buttons.forEach { element -> addButton(element.title) { host.onButton(element, product) } }
        // Без своих кнопок товар открывает кнопка хоста — «More info» (C11); на API v3 её нет, как в веб-SDK.
        if (buttons.isEmpty() && product != null && !host.v3) addButton(host.texts.productButtonText) { host.onProduct(product) }

        val products = StoriesMapping.products(slide)?.let { element -> element to element.getProducts() }
        if (products != null || isVideo) addUsedProducts(products?.first, products?.second.orEmpty())
    }

    /**
     * Опросы — слой во весь слайд, карточки по `y_offset` слайда, как в веб-SDK ([StoryPollLayer]); под
     * навигацией. Без транспорта (не API v3) их нет.
     */
    private fun buildPolls(): StoryPollLayer? {
        val polls = host.polls ?: return null
        if (slide.polls.isEmpty()) return null
        val layer = StoryPollLayer(context, ::bottomRowTop)
        slide.polls.forEach { poll ->
            lateinit var controller: StoryPollController
            controller = StoryPollController(poll, storyId, slide.id, polls.transport, polls.texts, onHold = { hold ->
                val before = pollHolds.isNotEmpty()
                if (hold) pollHolds.add(controller) else pollHolds.remove(controller)
                if (before != pollHolds.isNotEmpty()) host.onPollHold(this, pollHolds.isNotEmpty())
            }, answers = polls.answers, did = polls.did)
            layer.add(StoryPollView(context, controller))
        }
        pollLayer = layer
        return layer
    }

    /** Карточки опросов слайда. */
    val pollCards: List<StoryPollView> get() = pollLayer?.cards.orEmpty()

    /**
     * Верх ряда низа (кнопка звука, «Используемые товары») в слайде высотой [slideHeight], пока он виден:
     * над ним — карточка опроса. Ряд — последний в колонке, под ним только нижняя системная панель.
     */
    private fun bottomRowTop(slideHeight: Int): Int? {
        val wrapper = usedHost?.takeIf { it.visibility == View.VISIBLE } ?: return null
        val used = usedProducts ?: return null
        return slideHeight - wrapper.paddingBottom - used.rowHeight
    }

    /** Клавиатура окна: [bottom] — сколько она закрывает снизу. Слайд не сжимается — поднимается карточка. */
    fun applyIme(bottom: Int) {
        pollLayer?.setIme(bottom)
    }

    /** Убрать клавиатуру опроса — сворачивание, закрытие просмотра. */
    fun hidePollKeyboard() {
        pollLayer?.hideKeyboard()
    }

    private fun addButton(title: CharSequence, onClick: () -> Unit) {
        val button = PersonalizationButton(context).apply {
            size = PersonalizationButton.Size.LG
            buttonView = PersonalizationButton.ButtonView.PRIMARY
            text = title
            setOnClickListener { onClick() }
        }
        column.addView(button, columnParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun addUsedProducts(
        element: com.personalization.stories.models.elements.ProductsElement?,
        products: List<Product>
    ) {
        val used = PersonalizationStoryUsedProducts(context).apply {
            this.products = products.map(StoriesMapping::cardProduct)
            showText = element?.labelShow?.takeIf { it.isNotBlank() } ?: host.texts.showProductsText
            hideText = element?.labelHide?.takeIf { it.isNotBlank() } ?: host.texts.hideProductsText
            imageLoader = { view, item -> item.imageUrl?.let { host.loadImage(view, it) } }
            onProductClick = { item -> products.firstOrNull { it.id == item.id }?.let(host::onCarouselProduct) }
            onExpandedChange = { open -> host.onSheet(open) }
            // На API v3 кнопку звука спрячет [setSoundAvailable], если у ролика точно нет звуковой дорожки.
            showSoundButton = isVideo
            soundOnDescription = host.texts.soundOnLabel
            soundOffDescription = host.texts.soundOffLabel
            muted = host.muted
            onSoundClick = { host.onSoundToggle() }
        }
        usedProducts = used
        val wrapper = StoryNestedScrollHost(context, used.carousel)
        wrapper.addView(used, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        usedHost = wrapper
        column.addView(wrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun columnParams(width: Int, height: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(width, height, weight).apply {
            marginStart = padding
            marginEnd = padding
            topMargin = padding
        }

    /**
     * Отступы от системных панелей и выреза — только у органов управления (дефект
     * edge-to-edge). Шторка товаров уходит под панель навигации своим фоном.
     */
    fun applyInsets(insets: Insets) {
        appliedInsets = insets
        val used = usedHost?.takeIf { it.visibility == View.VISIBLE }
        column.setPadding(insets.left, insets.top, insets.right, if (used == null) insets.bottom + padding else 0)
        navigationBar.setPadding(insets.left, insets.top, insets.right, 0)
        used?.setPadding(0, 0, 0, insets.bottom)
        pollLayer?.setInsets(insets.top, insets.bottom)
    }

    fun setProgress(progress: Float) {
        navigation.progress.progress = progress
    }

    fun setMuted(muted: Boolean) {
        usedProducts?.muted = muted
    }

    /**
     * API v3: есть ли у видео звуковая дорожка. Точно нет — кнопки звука нет; без товаров пустой ряд низа
     * тогда убирается и места не занимает.
     */
    fun setSoundAvailable(available: Boolean) {
        if (!host.v3 || !isVideo) return
        val used = usedProducts ?: return
        used.showSoundButton = available
        val wrapper = usedHost ?: return
        val visibility = if (available || used.products.isNotEmpty()) View.VISIBLE else View.GONE
        if (wrapper.visibility == visibility) return
        wrapper.visibility = visibility
        applyInsets(appliedInsets)
        pollLayer?.requestLayout()
    }

    fun closeSheet() {
        usedProducts?.expanded = false
    }

    fun hidePoster() {
        if (isVideo) poster.visibility = View.GONE
    }

    fun showRetry(show: Boolean) {
        retry.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * TalkBack начинает слайд с навигации — крестика и «Поделиться», — как когда она стояла в колонке
     * первой: её ряд лежит поверх опросов, и по месту на экране чтец поставил бы его после содержимого.
     */
    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        val start = outChildren.size
        super.addChildrenForAccessibility(outChildren)
        val added = outChildren.subList(start, outChildren.size)
        val first = added.filter { isInNavigation(it) }
        if (first.isEmpty()) return
        added.removeAll(first.toSet())
        added.addAll(0, first)
    }

    private fun isInNavigation(view: View): Boolean {
        var node: View? = view
        while (node != null && node !== this) {
            if (node === navigationBar) return true
            node = node.parent as? View
        }
        return false
    }

    /**
     * При открытой шторке тап мимо неё только закрывает её (B5). Крестик и «Поделиться» не в
     * счёт: они работают всегда, и при открытой шторке тоже (F2).
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Клавиатура опроса открыта: тап мимо карточки только прячет её, слайд не листается.
        val typing = pollLayer?.focusedCard
        if (event.actionMasked == MotionEvent.ACTION_DOWN && typing != null && !inside(typing, event) &&
            !inside(navigation.closeButton, event) && !inside(navigation.shareButton, event)
        ) {
            keyboardTapPending = true
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && host.sheetOpen &&
            !inside(usedHost, event) && !inside(navigation.closeButton, event) &&
            !inside(navigation.shareButton, event)
        ) {
            sheetTapPending = true
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (keyboardTapPending) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    keyboardTapPending = false
                    pollLayer?.hideKeyboard()
                }
                MotionEvent.ACTION_CANCEL -> keyboardTapPending = false
            }
            return true
        }
        if (!sheetTapPending) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                sheetTapPending = false
                host.onOutsideSheetTap()
            }
            MotionEvent.ACTION_CANCEL -> sheetTapPending = false
        }
        return true
    }

    private fun inside(view: View?, event: MotionEvent): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return event.rawX >= location[0] && event.rawX <= location[0] + view.width &&
            event.rawY >= location[1] && event.rawY <= location[1] + view.height
    }

    private fun matchParent() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    private fun scrim(alpha: Int): GradientDrawable {
        val dark = Color.argb(alpha, 0, 0, 0)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(dark, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, dark)
        )
    }
}

/**
 * Картинка, которая сообщает, что ей поставили изображение: так готовность видна и при
 * загрузчике хоста, который о себе ничего не сообщает.
 */
internal class StoryImageView(context: Context) : AppCompatImageView(context) {
    var onDrawable: (() -> Unit)? = null

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) onDrawable?.invoke()
    }
}

/**
 * Место навигации в колонке слайда: высотой с саму навигацию, которая лежит в ряду поверх опросов
 * ([StorySlideView]). Ширина та же — отступы колонки и ряда совпадают.
 */
@SuppressLint("ViewConstructor")
private class NavigationSlot(context: Context, private val navigation: View) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        navigation.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), navigation.measuredHeight)
    }
}
