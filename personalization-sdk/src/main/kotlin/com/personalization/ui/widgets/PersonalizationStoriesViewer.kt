package com.personalization.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.personalization.Product
import com.personalization.R
import com.personalization.SDK
import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationStoryCard
import com.personalization.ui.components.PersonalizationStoryNavigation
import com.personalization.ui.components.PersonalizationStoryTextBlock
import com.personalization.ui.components.PersonalizationStoryUsedProducts
import com.personalization.ui.components.StorySupport
import kotlin.math.abs

/**
 * Полноэкранный просмотр сторис (V2) — на компонентах кита.
 *
 * Паттерн Stories из Figma (страница 1:32): слайд 402×874 с отступом 16 и шагом 16,
 * сверху [PersonalizationStoryNavigation], дальше по содержимому:
 * - слайд с товаром — [PersonalizationStoryCard] (бренд, название, фото, цена, кнопка);
 * - медиа-слайд — фон-картинка или видео, текстовые блоки на своей высоте
 *   (`y_offset` в процентах), кнопки внизу и [PersonalizationStoryUsedProducts].
 * Медиа-слайды рисуются тёмными токенами, как в макете; слайд без фона — в теме хоста.
 *
 * Поведение: слайд идёт [Slide.duration], тап слева — назад, справа — вперёд,
 * удержание — пауза, свайп вниз — закрыть, свайп вбок — соседняя сторис.
 * Показ слайда трекается `view`, нажатие на кнопку, товар или промокод — `click`.
 *
 * Модели — те же, что у старого `StoriesView`: V2 его не трогает.
 */
@InternalPersonalizationUiApi
class PersonalizationStoriesViewer(
    context: Context,
    private val stories: List<Story>,
    private val startStory: Int = 0
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    /** Контекст хоста: вьюхи строятся в его теме, чтобы работал брендинг кита. */
    private val hostContext = context

    /** Код блока — для трекинга. */
    var code: String? = null

    /** Инстанс SDK — трекинг `view` / `click`. Без него сторис просто показываются. */
    var sdk: SDK? = null

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    /**
     * Переход по ссылке кнопки или товара. Вернуть `true`, если хост обработал сам;
     * иначе ссылка открывается системой. После перехода просмотр закрывается.
     */
    var onLinkClick: ((String) -> Boolean)? = null

    /** Нажатие на товар (карточка слайда или карусель). */
    var onProductClick: ((Product) -> Unit)? = null

    /** Сторис досмотрена или просмотр закрыт на её середине — лента обновляет кольцо. */
    var onStoryProgress: ((story: Story) -> Unit)? = null

    var promoCaption: CharSequence = "Promo code"

    /** Кнопка товарного слайда, если своей кнопки у слайда нет, — открывает товар. */
    var productButtonText: CharSequence = "More info"
    var promoCopiedText: CharSequence = "Copied"
    var usedProductsShowText: CharSequence = "Used products"
    var usedProductsHideText: CharSequence = "Hide products"

    private lateinit var pager: ViewPager2
    private lateinit var root: StoryDismissLayout
    private val pages = HashMap<Int, StoryPage>()
    private var currentStory = -1
    /** Звук выключен, пока его не включат, — как у старых сторис. */
    private var muted = true
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root = StoryDismissLayout(hostContext).apply {
            onDismissDrag = { dragging -> if (dragging) currentPage()?.pause() else currentPage()?.resume() }
            onDismiss = { dismiss() }
        }
        pager = ViewPager2(hostContext).apply {
            adapter = StoriesAdapter()
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = selectStory(position)
            })
        }
        root.addView(pager, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        setContentView(root)
        pager.setCurrentItem(startStory.coerceIn(0, (stories.size - 1).coerceAtLeast(0)), false)
        // Для нулевой страницы пейджер onPageSelected может не прислать.
        selectStory(pager.currentItem)
    }

    override fun onStart() {
        super.onStart()
        currentPage()?.resume()
    }

    override fun onStop() {
        currentPage()?.pause()
        super.onStop()
    }

    override fun dismiss() {
        currentPage()?.leave()
        pages.values.forEach { it.release() }
        pages.clear()
        player?.release()
        player = null
        super.dismiss()
    }

    private fun currentPage(): StoryPage? = pages[currentStory]

    private fun selectStory(position: Int) {
        if (position == currentStory) return
        pages[currentStory]?.leave()
        currentStory = position
        pages[position]?.enter()
    }

    private fun nextStory() {
        if (currentStory + 1 < stories.size) pager.currentItem = currentStory + 1 else dismiss()
    }

    private fun previousStory() {
        if (currentStory > 0) pager.currentItem = currentStory - 1 else currentPage()?.restart()
    }

    private fun obtainPlayer(): ExoPlayer =
        player ?: ExoPlayer.Builder(hostContext).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.volume = if (muted) 0f else 1f
            player = it
        }

    private fun loadImage(view: ImageView, url: String) {
        val loader = imageLoader
        if (loader != null) loader(view, url) else Glide.with(view).load(url).into(view)
    }

    private fun track(event: String, story: Story, slide: Slide) {
        val blockCode = code ?: return
        runCatching { sdk?.storiesManager?.trackStory(event, blockCode, story.id, slide.id) }
    }

    private fun openLink(link: String) {
        if (link.isEmpty()) return
        val handled = onLinkClick?.invoke(link) ?: false
        if (!handled) {
            try {
                hostContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                SDK.error("PersonalizationStoriesViewer: no app can open $link")
            }
        }
        dismiss()
    }

    private inner class StoriesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = stories.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val page = StoryPage(hostContext)
            page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return object : RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = holder.itemView as StoryPage
            pages.entries.removeAll { it.value === page }
            pages[position] = page
            page.bind(position)
            if (position == currentStory) page.enter()
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            val page = holder.itemView as StoryPage
            page.release()
            pages.entries.removeAll { it.value === page }
        }
    }

    /**
     * Одна сторис: слайды по очереди, таймер слайда, видео. Слайд каждый раз
     * собирается заново — в своём контексте (тёмном для медиа).
     */
    @SuppressLint("ViewConstructor")
    private inner class StoryPage(context: Context) : FrameLayout(context) {

        private var storyIndex = -1
        private val story: Story get() = stories[storyIndex]
        private var slideIndex = 0
        private var slideView: StorySlideView? = null
        private var animator: ValueAnimator? = null
        private var entered = false
        private var fingerHold = false
        private var sheetOpen = false
        private val held get() = fingerHold || sheetOpen
        private var waitingForVideo = false

        /** Плеер у просмотра один: останавливать его может только страница, которая его заняла. */
        private var ownsVideo = false

        fun bind(index: Int) {
            release()
            storyIndex = index
            slideIndex = StoriesMapping.startSlide(story, emptySet())
            showSlide(start = false)
        }

        fun enter() {
            // Страницу входят и из привязки адаптера, и из смены страницы пейджера —
            // второй вход перезапустил бы слайд и оборвал бы подготовку видео.
            if (storyIndex < 0 || entered) return
            entered = true
            showSlide(start = true)
        }

        fun leave() {
            if (!entered) return
            entered = false
            stopTimer()
            detachVideo()
            // Недосмотренная сторис откроется с того же слайда.
            if (!story.isViewed) story.startPosition = slideIndex
            onStoryProgress?.invoke(story)
        }

        fun release() {
            stopTimer()
            detachVideo()
            entered = false
        }

        fun pause() {
            animator?.pause()
            if (ownsVideo) player?.pause()
        }

        fun resume() {
            if (!entered || held) return
            if (ownsVideo) player?.play()
            // Пока видео не готово, время слайда стоит — таймер запустит сам плеер.
            if (waitingForVideo) return
            animator?.resume() ?: startTimer()
        }

        fun restart() {
            slideIndex = 0
            showSlide(start = entered)
        }

        fun next() {
            if (slideIndex + 1 < story.slidesCount) {
                slideIndex++
                showSlide(start = true)
            } else {
                story.isViewed = true
                story.startPosition = 0
                onStoryProgress?.invoke(story)
                nextStory()
            }
        }

        fun previous() {
            if (slideIndex > 0) {
                slideIndex--
                showSlide(start = true)
            } else {
                previousStory()
            }
        }

        private fun showSlide(start: Boolean) {
            stopTimer()
            detachVideo()
            removeAllViews()
            if (story.slidesCount == 0) return
            val slide = story.getSlide(slideIndex)
            val view = StorySlideView(this@StoryPage.context, story, slide, this)
            slideView = view
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            view.navigation.progress.count = story.slidesCount
            view.navigation.progress.current = slideIndex
            view.navigation.progress.progress = 0f
            preloadNext()
            if (start && entered) {
                track("view", story, slide)
                if (StoriesMapping.isVideo(slide)) attachVideo(view, slide) else startTimer()
            }
        }

        private fun preloadNext() {
            val next = slideIndex + 1
            if (next >= story.slidesCount) return
            val slide = story.getSlide(next)
            val url = if (StoriesMapping.isVideo(slide)) slide.preview else slide.background
            if (url.isNotEmpty() && imageLoader == null) Glide.with(hostContext).load(url).preload()
        }

        private fun startTimer() {
            stopTimer()
            val slide = story.getSlide(slideIndex)
            val navigation = slideView?.navigation ?: return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = slide.duration.coerceAtLeast(MIN_DURATION_MS)
                interpolator = LinearInterpolator()
                addUpdateListener { navigation.progress.progress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) next()
                    }
                })
                start()
            }
        }

        private fun stopTimer() {
            animator?.cancel()
            animator = null
        }

        private fun attachVideo(view: StorySlideView, slide: Slide) {
            val exo = obtainPlayer()
            ownsVideo = true
            waitingForVideo = true
            exo.addListener(videoListener)
            view.playerView?.player = exo
            exo.setMediaItem(MediaItem.fromUri(slide.background))
            exo.playWhenReady = !held
            exo.prepare()
        }

        private val videoListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Время слайда идёт, только когда видео реально играет.
                if (playbackState == Player.STATE_READY && waitingForVideo) {
                    waitingForVideo = false
                    slideView?.hidePoster()
                    if (entered && !held) startTimer()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Видео не играет — слайд остаётся картинкой-превью и идёт по своему времени.
                if (!waitingForVideo) return
                waitingForVideo = false
                SDK.error("PersonalizationStoriesViewer: video failed, showing the preview", error)
                if (entered && !held) startTimer()
            }
        }

        private fun detachVideo() {
            waitingForVideo = false
            if (!ownsVideo) return
            ownsVideo = false
            player?.let { exo ->
                exo.removeListener(videoListener)
                exo.stop()
                exo.clearMediaItems()
            }
            slideView?.playerView?.player = null
        }

        // --- вызовы слайда ---

        fun onTap(x: Float) {
            if (x < width * PREVIOUS_ZONE) previous() else next()
        }

        fun onHold(hold: Boolean) {
            // Смена слайда удаляет вьюху под пальцем, и она получает ACTION_CANCEL —
            // без этой проверки «отпускание» запускало бы второй таймер.
            if (fingerHold == hold) return
            fingerHold = hold
            if (held) pause() else resume()
        }

        /** Открытая шторка товаров держит слайд, пока её не закроют. */
        fun onSheet(open: Boolean) {
            sheetOpen = open
            if (held) pause() else resume()
        }

        fun onSoundToggle() {
            muted = !muted
            player?.volume = if (muted) 0f else 1f
            slideView?.setMuted(muted)
        }

        fun isMuted() = muted

        fun onButton(element: ButtonElement) {
            track("click", story, story.getSlide(slideIndex))
            openLink(element.link)
        }

        fun onProduct(product: Product) {
            track("click", story, story.getSlide(slideIndex))
            val handler = onProductClick
            if (handler != null) {
                handler(product)
                dismiss()
            } else {
                openLink(product.deeplink.ifEmpty { product.url })
            }
        }

        fun onPromoCopied() {
            track("click", story, story.getSlide(slideIndex))
        }

        fun onClose() = dismiss()
    }

    /**
     * Вёрстка одного слайда. Строится в [slideContext]: для медиа-слайда — тёмном.
     */
    // resizeMode и цвет шаттера PlayerView в media3 помечены UnstableApi — это только вид.
    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class StorySlideView(
        baseContext: Context,
        story: Story,
        slide: Slide,
        private val page: StoryPage
    ) : FrameLayout(if (StoriesMapping.isMedia(slide)) StorySupport.darkContext(baseContext) else baseContext) {

        val navigation: PersonalizationStoryNavigation
        var playerView: PlayerView? = null
            private set
        private val poster = ImageView(context)
        private var usedProducts: PersonalizationStoryUsedProducts? = null

        init {
            val media = StoriesMapping.isMedia(slide)
            val video = StoriesMapping.isVideo(slide)
            val fallback = StoriesMapping.parseColor(slide.backgroundColor)
            setBackgroundColor(
                if (media) fallback ?: Color.BLACK
                else PersonalizationTheme.color(context, R.color.personalization_background_card)
            )

            if (media) {
                poster.scaleType = ImageView.ScaleType.CENTER_CROP
                addView(poster, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                val posterUrl = if (video) slide.preview else slide.background
                if (posterUrl.isNotEmpty()) loadImage(poster, posterUrl)
                if (video) {
                    playerView = PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(Color.TRANSPARENT)
                    }
                    addView(playerView, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                    poster.bringToFront()
                }
            }
            // Скрим сверху и снизу — у всех видов Card/Story, и на белом фоне тоже:
            // у фото без текста чёрный 50%, у слайдов с товаром и текстом — 30%.
            val strong = media && StoriesMapping.product(slide) == null && StoriesMapping.textBlocks(slide).isEmpty()
            addView(
                View(context).apply { background = scrim(if (strong) SCRIM_STRONG else SCRIM_LIGHT) },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            // Слой касаний: тап — листать, удержание — пауза. Кнопки лежат выше и забирают свои касания.
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    page.onTap(e.x)
                    return true
                }

                override fun onLongPress(e: MotionEvent) = page.onHold(true)
            })
            addView(View(context).apply {
                setOnTouchListener { _, event ->
                    detector.onTouchEvent(event)
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        page.onHold(false)
                    }
                    true
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
            }
            navigation = PersonalizationStoryNavigation(context).apply { onClose = { page.onClose() } }
            column.addView(navigation, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            val product = StoriesMapping.product(slide)?.item
            val buttons = StoriesMapping.buttons(slide)
            val productsElement = StoriesMapping.products(slide)

            if (product != null) {
                column.addView(productCard(product, buttons.firstOrNull()), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply {
                    topMargin = padding
                    bottomMargin = padding
                })
                addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            } else {
                // Текстовые блоки — на своей высоте поверх слайда, как их расставила админка.
                val texts = FrameLayout(context)
                column.addView(texts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                StoriesMapping.textBlocks(slide).forEach { element ->
                    val block = PersonalizationStoryTextBlock(context).apply {
                        text = element.textInput
                        textSizeSp = element.fontSize.toFloat()
                        lineHeightSp = if (element.textLineSpacing > 0) element.fontSize * element.textLineSpacing.toFloat() else null
                        blockColor = StoriesMapping.textBlockColor(element)
                        textColorOverride = StoriesMapping.parseColor(element.textColor)
                        align = StoriesMapping.textAlign(element)
                        bold = element.isBold
                        italic = element.isItalic
                        fontFamily = element.fontType.takeIf { it.isNotEmpty() }
                    }
                    texts.addView(block, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                    texts.post { block.y = texts.height * element.yOffset / 100f }
                }
                addBottom(productsElement, buttons, video, extraBottom = padding)
            }
        }

        /** Низ медиа-слайда: кнопки и строка товаров/звука. */
        private fun addBottom(
            productsElement: com.personalization.stories.models.elements.ProductsElement?,
            buttons: List<ButtonElement>,
            video: Boolean,
            extraBottom: Int
        ) {
            val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
            val bottom = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            buttons.forEach { element ->
                val button = PersonalizationButton(context).apply {
                    size = PersonalizationButton.Size.LG
                    buttonView = PersonalizationButton.ButtonView.PRIMARY
                    text = element.title
                    setOnClickListener { page.onButton(element) }
                }
                bottom.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = padding
                    marginEnd = padding
                    topMargin = padding
                })
            }
            val products = productsElement?.getProducts().orEmpty()
            if (products.isNotEmpty() || video) {
                val used = PersonalizationStoryUsedProducts(context).apply {
                    this.products = products.map(StoriesMapping::cardProduct)
                    showText = productsElement?.labelShow?.takeIf { it.isNotEmpty() } ?: usedProductsShowText
                    hideText = productsElement?.labelHide?.takeIf { it.isNotEmpty() } ?: usedProductsHideText
                    imageLoader = { view, item -> item.imageUrl?.let { loadImage(view, it) } }
                    onProductClick = { item -> products.firstOrNull { it.id == item.id }?.let(page::onProduct) }
                    onExpandedChange = { open -> page.onSheet(open) }
                    showSoundButton = video
                    muted = page.isMuted()
                    onSoundClick = { page.onSoundToggle() }
                }
                usedProducts = used
                bottom.addView(used, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            } else if (buttons.isNotEmpty()) {
                bottom.setPadding(0, 0, 0, extraBottom)
            }
            addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }

        private fun productCard(product: Product, button: ButtonElement?): View {
            val price = StoriesMapping.price(product)
            return PersonalizationStoryCard(context).apply {
                title = product.brand.takeIf { it.isNotEmpty() }
                subtitle = product.name
                if (product.image.isNotEmpty()) loadImage(imageView, product.image)
                this.price.type = price.type
                this.price.price = price.price
                this.price.oldPrice = price.oldPrice
                this.price.discount = price.discount
                this.price.promoCode = price.promoCode
                this.price.promoCaption = promoCaption
                this.price.copiedText = promoCopiedText
                this.price.onPromoCopied = { page.onPromoCopied() }
                buttonText = button?.title ?: productButtonText
                // Товар открывается кнопкой: фото занимает середину слайда, и тап по нему
                // должен листать, как по любому другому месту.
                onButtonClick = { if (button != null) page.onButton(button) else page.onProduct(product) }
            }
        }

        fun hidePoster() {
            poster.visibility = View.GONE
        }

        fun setMuted(muted: Boolean) {
            usedProducts?.muted = muted
        }

        private fun scrim(alpha: Int): GradientDrawable {
            val dark = Color.argb(alpha, 0, 0, 0)
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(dark, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, dark)
            )
        }
    }

    private companion object {
        const val MIN_DURATION_MS = 1000L
        const val SCRIM_STRONG = 128
        const val SCRIM_LIGHT = 77
        const val PREVIOUS_ZONE = 0.3f
    }
}

/**
 * Корень просмотра: свайп вниз утаскивает сторис и закрывает её, если протянуть
 * дальше пятой части экрана. Горизонтальные жесты остаются пейджеру и карусели.
 */
@SuppressLint("ViewConstructor")
internal class StoryDismissLayout(context: Context) : FrameLayout(context) {

    var onDismissDrag: ((Boolean) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (dy > touchSlop && dy > abs(dx) * 1.5f) {
                    dragging = true
                    onDismissDrag?.invoke(true)
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> translationY = (event.rawY - downY).coerceAtLeast(0f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (translationY > height * DISMISS_FRACTION) {
                    animate().translationY(height.toFloat()).setDuration(ANIMATION_MS).withEndAction { onDismiss?.invoke() }.start()
                } else {
                    animate().translationY(0f).setDuration(ANIMATION_MS).withEndAction { onDismissDrag?.invoke(false) }.start()
                }
            }
        }
        return true
    }

    private companion object {
        const val DISMISS_FRACTION = 0.2f
        const val ANIMATION_MS = 200L
    }
}
