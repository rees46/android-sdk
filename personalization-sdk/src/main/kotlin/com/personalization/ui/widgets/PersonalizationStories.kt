package com.personalization.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.personalization.Cancellable

import com.personalization.R
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.stories.models.Story
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationStoriesBar

/**
 * Сторис (V2) с данными SDK: лента обложек и полноэкранный просмотр на компонентах кита.
 *
 * Паттерн Stories из Figma (страница 1:32): «сторис-бар + фулскрин-вьюер, источник —
 * сторис-блок по code». Блок грузится тем же запросом, что у старого `StoriesView`
 * (`stories/{code}`), — старый виджет остаётся как есть.
 *
 * Инстанс SDK: [shopId] (атрибут `app:shop_id`) выбирает магазин при нескольких
 * зарегистрированных; без него берётся единственный. Резолв — при показе через
 * [Rees46.awaitInstance]; явный инстанс можно передать в [attach].
 *
 * Пока блок грузится — скелетон. Пустой блок, ошибка загрузки, пустой [code],
 * неоднозначный магазин или 15 с без ответа прячут виджет и зовут [onError].
 * Управление из кода — [open], [openStory], [pause], [resume]; без ленты — [show].
 */
@InternalPersonalizationUiApi
class PersonalizationStories @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Лента — доступна для тонкой настройки (размер обложек, отступы). */
    val bar = PersonalizationStoriesBar(context)

    /** Код сторис-блока. Менять до показа или вызвать [reload]. */
    var code: String? = null

    /** Магазин; `null` — единственный зарегистрированный. Менять до показа. */
    var shopId: String? = null

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    /** Клики по ссылкам и товарам — контракт старых сторис ([com.personalization.OnClickListener]). */
    var onClickListener: com.personalization.OnClickListener? = null

    /** Блок не загрузился: код ошибки (HTTP или -1) и сообщение. Виджет при этом скрыт. */
    var onError: ((code: Int, message: String?) -> Unit)? = null

    var promoCaption: CharSequence
    var promoCopiedText: CharSequence
    var productButtonText: CharSequence
    var showProductsText: CharSequence
    var hideProductsText: CharSequence
    var retryText: CharSequence
    var closeLabel: CharSequence
    var soundOnLabel: CharSequence
    var soundOffLabel: CharSequence

    /** Загруженные сторис — в порядке сервера, без пустых. */
    var stories: List<Story> = emptyList()
        private set

    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var viewer: PersonalizationStoriesViewer? = null
    private var requestSeq = 0
    private var pendingOpen: (() -> Unit)? = null
    private val giveUp = Runnable { fail(StoriesLoader.ERROR_CODE, "stories: no response in ${LOAD_TIMEOUT_MS / 1000} s") }

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
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.PersonalizationStories)
            code = array.getString(R.styleable.PersonalizationStories_code)
            shopId = array.getString(R.styleable.PersonalizationStories_shop_id)
            array.recycle()
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.onItemClick = { index, _ -> open(index) }
        // Загрузчик ставится один раз: каждая установка перерисовывает ленту.
        bar.imageLoader = { view, url -> imageLoader?.invoke(view, url) ?: SearchImages.load(view, url) }
        bar.showSkeleton()
    }

    /** Явный инстанс SDK; блок загружается сразу. */
    fun attach(sdk: SDK) {
        instanceHandle?.cancel()
        instanceHandle = null
        this.sdk = sdk
        reload()
    }

    /** Перезагрузить блок [code]. */
    fun reload() {
        val blockCode = code?.takeIf { it.isNotBlank() } ?: run {
            fail(StoriesLoader.ERROR_CODE, "PersonalizationStories: code is not set")
            return
        }
        val sdk = sdk ?: return
        val seq = ++requestSeq
        if (stories.isEmpty()) {
            visibility = View.VISIBLE
            bar.showSkeleton()
        }
        startGiveUp()
        StoriesLoader.load(
            sdk,
            blockCode,
            onLoaded = { loaded ->
                if (seq == requestSeq) {
                    removeCallbacks(giveUp)
                    stories = loaded
                    render()
                    pendingOpen?.also { pendingOpen = null }?.invoke()
                }
            },
            onFailed = { code, message -> if (seq == requestSeq) fail(code, message) }
        )
    }

    /**
     * Открыть просмотр со сторис [index]. До загрузки блока — откроется, когда он придёт.
     * Просмотр уже открыт — листает к ней в нём, не пересоздавая его (F11).
     */
    fun open(index: Int) {
        if (stories.isEmpty()) {
            pendingOpen = { open(index) }
            return
        }
        val story = stories.getOrNull(index) ?: return
        if (viewer?.showStory(story.id) == true) return
        viewer?.dismiss()
        viewer = newViewer(context, stories, index).also { viewer ->
            viewer.setOnDismissListener {
                if (this.viewer === viewer) this.viewer = null
                render()
            }
            viewer.show()
        }
    }

    /** Открыть просмотр со сторис с id [storyId]. */
    fun openStory(storyId: Int) {
        if (stories.isEmpty()) {
            pendingOpen = { openStory(storyId) }
            return
        }
        stories.indexOfFirst { it.id == storyId }.takeIf { it >= 0 }?.let(::open)
    }

    /** Пауза открытого просмотра (B8); без просмотра — ничего. */
    fun pause() {
        viewer?.pause()
    }

    fun resume() {
        viewer?.resume()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sdk != null || stories.isNotEmpty()) return
        startGiveUp()
        instanceHandle = try {
            Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
        } catch (throwable: Throwable) {
            fail(
                StoriesLoader.ERROR_CODE,
                "PersonalizationStories: cannot resolve an SDK for shopId=$shopId — set app:shop_id " +
                    "to pick a shop when several are registered. ${throwable.message}"
            )
            null
        }
    }

    override fun onDetachedFromWindow() {
        instanceHandle?.cancel()
        instanceHandle = null
        removeCallbacks(giveUp)
        viewer?.dismiss()
        viewer = null
        super.onDetachedFromWindow()
    }

    private fun startGiveUp() {
        removeCallbacks(giveUp)
        postDelayed(giveUp, LOAD_TIMEOUT_MS)
    }

    /** Ошибка: поздний ответ уже не нужен, пустой блок прячется, хост узнаёт. */
    private fun fail(code: Int, message: String?) {
        removeCallbacks(giveUp)
        requestSeq++
        instanceHandle?.cancel()
        instanceHandle = null
        pendingOpen = null
        SDK.error("PersonalizationStories: $code $message")
        if (stories.isEmpty()) visibility = View.GONE
        onError?.invoke(code, message)
    }

    private fun render() {
        visibility = if (stories.isEmpty()) View.GONE else View.VISIBLE
        val seen = StoriesSeenStore.forSdk(context, sdk, shopId)
        bar.items = stories.map { StoriesMapping.item(it, seen.seen(it.id)) }
    }

    private fun texts() = StoriesTexts(
        promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
        retryText, closeLabel, soundOnLabel, soundOffLabel
    )

    private fun newViewer(context: Context, stories: List<Story>, index: Int) =
        PersonalizationStoriesViewer(context, stories, index).also { viewer ->
            viewer.code = code
            viewer.sdk = sdk
            viewer.shopId = shopId
            viewer.imageLoader = imageLoader
            viewer.onClickListener = this@PersonalizationStories.onClickListener
            viewer.applyTexts(texts())
        }

    companion object {
        /** Сколько ждать блок, прежде чем сдаться и спрятать виджет. */
        internal const val LOAD_TIMEOUT_MS = 15_000L

        /**
         * Показать сторис блока [code] без ленты (B8): загрузить блок и открыть просмотр
         * со сторис [startStoryId] (или с первой). SDK — [sdk], иначе инстанс магазина
         * [shopId]. Тексты `null` — по умолчанию из ресурсов. Ошибки — в [onError].
         * Возвращает отмену ещё не открытого показа.
         */
        @JvmStatic
        @JvmOverloads
        fun show(
            context: Context,
            code: String,
            sdk: SDK? = null,
            shopId: String? = null,
            startStoryId: Int? = null,
            onClickListener: com.personalization.OnClickListener? = null,
            onError: ((code: Int, message: String?) -> Unit)? = null,
            imageLoader: ((ImageView, String) -> Unit)? = null,
            promoCaption: CharSequence? = null,
            promoCopiedText: CharSequence? = null,
            productButtonText: CharSequence? = null,
            showProductsText: CharSequence? = null,
            hideProductsText: CharSequence? = null,
            retryText: CharSequence? = null,
            closeLabel: CharSequence? = null,
            soundOnLabel: CharSequence? = null,
            soundOffLabel: CharSequence? = null
        ): Cancellable {
            val widget = PersonalizationStories(context).also { widget ->
                widget.code = code
                widget.shopId = shopId
                widget.onClickListener = onClickListener
                widget.onError = onError
                widget.imageLoader = imageLoader
                promoCaption?.let { widget.promoCaption = it }
                promoCopiedText?.let { widget.promoCopiedText = it }
                productButtonText?.let { widget.productButtonText = it }
                showProductsText?.let { widget.showProductsText = it }
                hideProductsText?.let { widget.hideProductsText = it }
                retryText?.let { widget.retryText = it }
                closeLabel?.let { widget.closeLabel = it }
                soundOnLabel?.let { widget.soundOnLabel = it }
                soundOffLabel?.let { widget.soundOffLabel = it }
            }
            return widget.showDetached(sdk, startStoryId)
        }
    }

    /**
     * Показ без ленты: виджет не на экране, поэтому SDK и таймаут ведутся вручную,
     * а просмотр открывается, как только блок пришёл.
     */
    private fun showDetached(explicitSdk: SDK?, startStoryId: Int?): Cancellable {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(giveUp, LOAD_TIMEOUT_MS)
        pendingOpen = open@{
            handler.removeCallbacks(giveUp)
            if (stories.isEmpty()) {
                onError?.invoke(StoriesLoader.ERROR_CODE, "PersonalizationStories.show: block $code is empty")
                return@open
            }
            val index = startStoryId?.let { id -> stories.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
            newViewer(context, stories, index).show()
        }
        val previousError = onError
        onError = { code, message ->
            handler.removeCallbacks(giveUp)
            previousError?.invoke(code, message)
        }
        if (explicitSdk != null) {
            attach(explicitSdk)
        } else {
            instanceHandle = try {
                Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
            } catch (throwable: Throwable) {
                fail(StoriesLoader.ERROR_CODE, "PersonalizationStories.show: cannot resolve an SDK for shopId=$shopId. ${throwable.message}")
                null
            }
        }
        return Cancellable {
            handler.removeCallbacks(giveUp)
            requestSeq++
            pendingOpen = null
            instanceHandle?.cancel()
            instanceHandle = null
        }
    }
}
