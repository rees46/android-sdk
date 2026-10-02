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
import com.personalization.Product
import com.personalization.R
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationStoryNavigation
import com.personalization.ui.components.PersonalizationStoryUsedProducts
import com.personalization.ui.components.StorySupport

/** Что слайд просит у просмотра: загрузку картинок, навигацию, клики. */
internal interface StorySlideHost {
    val texts: StoriesTexts
    val muted: Boolean
    val sheetOpen: Boolean
    val insets: Insets
    fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)? = null, onFailed: (() -> Unit)? = null)
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
}

/**
 * Вёрстка одного слайда (Card/Story). Медиа-слайд строится в тёмном контексте (C1),
 * слайд без медиа — в теме хоста на Background/Card (C2). Слои снизу вверх: поля вокруг
 * медиа, фото/видео целиком (fit), скрим (C3), слой касаний, органы управления с отступами
 * от системных панелей и выреза, кнопка «Повторить» (C5). Медиа идёт под панели, органы
 * управления — нет.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@SuppressLint("ViewConstructor")
internal class StorySlideView(
    baseContext: Context,
    val storyIndex: Int,
    val slideIndex: Int,
    val slide: Slide,
    slideCount: Int,
    private val host: StorySlideHost
) : FrameLayout(if (StoriesMapping.isMedia(slide)) StorySupport.darkContext(baseContext) else baseContext) {

    enum class ImageState { LOADING, READY, FAILED }

    val isMedia = StoriesMapping.isMedia(slide)
    val isVideo = StoriesMapping.isVideo(slide)
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

    private val poster = StoryImageView(context)
    private val column = LinearLayout(context)
    private val retry = PersonalizationButton(context)
    private var usedProducts: PersonalizationStoryUsedProducts? = null
    private var usedHost: View? = null
    private var sheetTapPending = false
    private val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)

    init {
        setBackgroundColor(
            if (isMedia) StoriesMapping.letterboxColor(slide)
            else PersonalizationTheme.color(context, R.color.personalization_background_card)
        )
        if (isMedia) addMedia()
        addView(View(context).apply { background = scrim(StoriesMapping.scrimAlpha(slide)) }, matchParent())
        addView(StoryTouchLayer(context, host::onTap, host::onHold), matchParent())

        column.orientation = LinearLayout.VERTICAL
        navigation.closeContentDescription = host.texts.closeLabel
        navigation.onClose = { host.onClose() }
        navigation.progress.count = slideCount
        navigation.progress.current = slideIndex
        column.addView(navigation, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        buildContent()
        addView(column, matchParent())

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
        if (url.isEmpty()) {
            setImageState(if (isVideo) ImageState.READY else ImageState.FAILED)
            return
        }
        poster.visibility = View.VISIBLE
        poster.onDrawable = { setImageState(ImageState.READY) }
        host.loadImage(poster, url, { setImageState(ImageState.READY) }, { setImageState(ImageState.FAILED) })
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
            StoryProductCard.build(context, productElement, product, host)
        } else {
            StoryTextBlocks.build(context, slide, host)
        }
        column.addView(middle, columnParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = StoriesMapping.buttons(slide)
        buttons.forEach { element -> addButton(element.title) { host.onButton(element, product) } }
        // Без своих кнопок товар открывает кнопка хоста — «More info» (C11).
        if (buttons.isEmpty() && product != null) addButton(host.texts.productButtonText) { host.onProduct(product) }

        val products = StoriesMapping.products(slide)?.let { element -> element to element.getProducts() }
        if (products != null || isVideo) addUsedProducts(products?.first, products?.second.orEmpty())
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
        val used = usedHost
        column.setPadding(insets.left, insets.top, insets.right, if (used == null) insets.bottom + padding else 0)
        used?.setPadding(0, 0, 0, insets.bottom)
    }

    fun setProgress(progress: Float) {
        navigation.progress.progress = progress
    }

    fun setMuted(muted: Boolean) {
        usedProducts?.muted = muted
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

    /** При открытой шторке тап мимо неё только закрывает её (B5). */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && host.sheetOpen && !inside(usedHost, event)) {
            sheetTapPending = true
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        view ?: return false
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
