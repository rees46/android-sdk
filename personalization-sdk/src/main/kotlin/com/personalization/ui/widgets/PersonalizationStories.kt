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
 *
 * С [apiV3Domain] (своим или глобальным из companion) блок, события и кнопка «Поделиться» —
 * на API v3; ссылку «Поделиться» открывает [handleLink].
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

    /**
     * Хост API v3 этого виджета, без схемы (`app.rees46.ru`): блок, события и «Поделиться» —
     * на v3. `null` — глобальный [Companion.apiV3Domain], а без него старое API. Соседние
     * виджеты друг на друга не влияют. Менять до показа или вызвать [reload].
     */
    @get:JvmName("getWidgetApiV3Domain")
    @set:JvmName("setWidgetApiV3Domain")
    var apiV3Domain: String? = null

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    /**
     * Ссылка слайда → то, что уходит в окно «Поделиться» (например, своя схема приложения).
     * `null` — ссылка как есть. Событие `share` не меняется.
     */
    var shareUrlTransform: ((String) -> String)? = null

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
    var shareLabel: CharSequence

    /** Загруженные сторис — в порядке сервера, без пустых. */
    var stories: List<Story> = emptyList()
        private set

    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var viewer: PersonalizationStoriesViewer? = null
    private var requestSeq = 0
    private var pendingOpen: (() -> Unit)? = null

    /** Хост API v3, с которого пришли [stories]; `null` — старая лента. */
    private var storiesDomain: String? = null

    /** Показ кампании по ссылке «Поделиться» ([handleLink]) вместо блока. */
    private var sharedLink: SharedLink? = null
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
        shareLabel = defaults.shareLabel
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.PersonalizationStories)
            code = array.getString(R.styleable.PersonalizationStories_code)
            shopId = array.getString(R.styleable.PersonalizationStories_shop_id)
            array.recycle()
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.onItemClick = { index, _ -> open(index) }
        // Загрузчик ставится один раз: каждая установка перерисовывает ленту.
        bar.imageLoader = { view, url -> imageLoader?.invoke(view, url) ?: SearchImages.load(view, url, firstFrame = true) }
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
        val shared = sharedLink
        val domain = shared?.domain ?: v3Domain(apiV3Domain)
        val onLoaded = { loaded: List<Story> ->
            if (seq == requestSeq) {
                removeCallbacks(giveUp)
                stories = loaded
                storiesDomain = domain
                render()
                pendingOpen?.also { pendingOpen = null }?.invoke()
            }
        }
        val onFailed = { code: Int, message: String? -> if (seq == requestSeq) fail(code, message) }
        if (shared != null) {
            StoriesApiV3.story(sdk, shared.domain, shared.link, onLoaded, onFailed)
        } else {
            StoriesLoader.load(sdk, blockCode, domain, onLoaded, onFailed)
        }
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

    /** То же по id строкой — как его отдают другие платформы (F12). Не число — ничего. */
    fun openStory(storyId: String) {
        storyId.trim().toIntOrNull()?.let { openStory(it) }
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
        retryText, closeLabel, soundOnLabel, soundOffLabel, shareLabel
    )

    private fun newViewer(context: Context, stories: List<Story>, index: Int) =
        PersonalizationStoriesViewer(context, stories, index).also { viewer ->
            viewer.code = code
            viewer.sdk = sdk
            viewer.shopId = shopId
            viewer.imageLoader = imageLoader
            viewer.shareUrlTransform = shareUrlTransform
            viewer.onClickListener = this@PersonalizationStories.onClickListener
            viewer.apiV3Domain = storiesDomain
            viewer.applyTexts(texts())
        }

    companion object {
        /** Сколько ждать блок, прежде чем сдаться и спрятать виджет. */
        internal const val LOAD_TIMEOUT_MS = 15_000L

        /**
         * Хост API v3 по умолчанию, без схемы, как `Rees46Config.apiDomain`, например
         * `app.rees46.ru`: для виджетов, [show] и [handleLink] без своего `apiV3Domain`. Задан —
         * их запросы (лента, кампания по ссылке, события) идут на `https://{apiV3Domain}`, а у
         * слайдов со ссылкой появляется «Поделиться». `null` — старые `stories/{code}` и
         * `track/stories`. Задавать до показа сторис.
         */
        @JvmStatic
        @Volatile
        var apiV3Domain: String? = null

        /** Хост v3 экземпляра, без него — глобальный; пустой — нет. */
        internal fun v3Domain(own: String? = null): String? =
            own?.trim()?.takeIf { it.isNotEmpty() } ?: apiV3Domain?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * Показать сторис блока [code] без ленты (B8): загрузить блок и открыть просмотр
         * со сторис [startStoryId] (нет такой — с первой). SDK — [sdk], иначе инстанс
         * магазина [shopId]. Тексты `null` — по умолчанию из ресурсов.
         *
         * Пустой блок, ошибка загрузки, магазин не найден или 15 с без ответа — [onError],
         * и показ на этом заканчивается. Просмотр закрыли — [onClose] (F12; на RN и Flutter
         * так завершаются Promise и Future). [apiV3Domain] — как у виджета, `null` — глобальный;
         * [shareUrlTransform] — как у виджета. Возвращает отмену ещё не открытого показа.
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
            onClose: (() -> Unit)? = null,
            imageLoader: ((ImageView, String) -> Unit)? = null,
            promoCaption: CharSequence? = null,
            promoCopiedText: CharSequence? = null,
            productButtonText: CharSequence? = null,
            showProductsText: CharSequence? = null,
            hideProductsText: CharSequence? = null,
            retryText: CharSequence? = null,
            closeLabel: CharSequence? = null,
            soundOnLabel: CharSequence? = null,
            soundOffLabel: CharSequence? = null,
            shareLabel: CharSequence? = null,
            apiV3Domain: String? = null,
            shareUrlTransform: ((String) -> String)? = null
        ): Cancellable {
            val texts = hostTexts(
                context, promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
                retryText, closeLabel, soundOnLabel, soundOffLabel, shareLabel
            )
            val widget = detached(context, code, shopId, onClickListener, onError, imageLoader, texts)
            widget.apiV3Domain = apiV3Domain
            widget.shareUrlTransform = shareUrlTransform
            return widget.showDetached(sdk, startStoryId, onClose)
        }

        /**
         * Открыть кампанию по ссылке «Поделиться»
         * `https://<сайт>/<путь>?r46_stories=<code>&r46_story=<id>[&r46_slide=<id>]` (другие
         * параметры не мешают). Параметры — как у [show], без `code` и `startStoryId`.
         *
         * `false` — это не ссылка сторис или нет хоста v3 ([apiV3Domain], без него глобальный
         * [Companion.apiV3Domain]): ничего не происходит.
         * `true` — дальше асинхронно, как [show]: дождаться SDK, загрузить кампанию, открыть
         * просмотр с её слайда из ссылки (`start_position` ответа), закрыв другой открытый
         * просмотр V2, и отправить `shared_open`. Ошибка, 404, пусто, таймаут — [onError], ничего
         * не показано. Та же ссылка, пока грузится или её просмотр открыт, — игнорируется.
         */
        @JvmStatic
        @JvmOverloads
        fun handleLink(
            context: Context,
            url: String,
            sdk: SDK? = null,
            shopId: String? = null,
            onClickListener: com.personalization.OnClickListener? = null,
            onError: ((code: Int, message: String?) -> Unit)? = null,
            onClose: (() -> Unit)? = null,
            imageLoader: ((ImageView, String) -> Unit)? = null,
            promoCaption: CharSequence? = null,
            promoCopiedText: CharSequence? = null,
            productButtonText: CharSequence? = null,
            showProductsText: CharSequence? = null,
            hideProductsText: CharSequence? = null,
            retryText: CharSequence? = null,
            closeLabel: CharSequence? = null,
            soundOnLabel: CharSequence? = null,
            soundOffLabel: CharSequence? = null,
            shareLabel: CharSequence? = null,
            apiV3Domain: String? = null,
            shareUrlTransform: ((String) -> String)? = null
        ): Boolean {
            val domain = v3Domain(apiV3Domain) ?: return false
            val link = StoriesShare.parseLink(url) ?: return false
            if (link == SharedLinks.current) return true
            SharedLinks.pending?.cancel()
            SharedLinks.current = link
            val texts = hostTexts(
                context, promoCaption, promoCopiedText, productButtonText, showProductsText, hideProductsText,
                retryText, closeLabel, soundOnLabel, soundOffLabel, shareLabel
            )
            val failed: (Int, String?) -> Unit = { code, message ->
                SharedLinks.finish(link)
                onError?.invoke(code, message)
            }
            val widget = detached(context, link.code, shopId, onClickListener, failed, imageLoader, texts)
            widget.sharedLink = SharedLink(link, domain)
            widget.apiV3Domain = domain
            widget.shareUrlTransform = shareUrlTransform
            // Отмена уже открытого показа ничего не делает — её можно держать и после показа.
            val pending = widget.showDetached(sdk, null) {
                SharedLinks.finish(link)
                onClose?.invoke()
            }
            if (SharedLinks.current == link) SharedLinks.pending = pending
            return true
        }

        /** Тексты хоста; `null` — по умолчанию из ресурсов. */
        private fun hostTexts(
            context: Context,
            promoCaption: CharSequence?,
            promoCopiedText: CharSequence?,
            productButtonText: CharSequence?,
            showProductsText: CharSequence?,
            hideProductsText: CharSequence?,
            retryText: CharSequence?,
            closeLabel: CharSequence?,
            soundOnLabel: CharSequence?,
            soundOffLabel: CharSequence?,
            shareLabel: CharSequence?
        ): StoriesTexts {
            val defaults = StoriesTexts.defaults(context)
            return StoriesTexts(
                promoCaption ?: defaults.promoCaption, promoCopiedText ?: defaults.promoCopiedText,
                productButtonText ?: defaults.productButtonText, showProductsText ?: defaults.showProductsText,
                hideProductsText ?: defaults.hideProductsText, retryText ?: defaults.retryText,
                closeLabel ?: defaults.closeLabel, soundOnLabel ?: defaults.soundOnLabel,
                soundOffLabel ?: defaults.soundOffLabel, shareLabel ?: defaults.shareLabel
            )
        }

        private fun detached(
            context: Context,
            code: String,
            shopId: String?,
            onClickListener: com.personalization.OnClickListener?,
            onError: ((code: Int, message: String?) -> Unit)?,
            imageLoader: ((ImageView, String) -> Unit)?,
            texts: StoriesTexts
        ) = PersonalizationStories(context).also { widget ->
            widget.code = code
            widget.shopId = shopId
            widget.onClickListener = onClickListener
            widget.onError = onError
            widget.imageLoader = imageLoader
            widget.applyTexts(texts)
        }
    }

    private fun applyTexts(texts: StoriesTexts) {
        promoCaption = texts.promoCaption
        promoCopiedText = texts.promoCopiedText
        productButtonText = texts.productButtonText
        showProductsText = texts.showProductsText
        hideProductsText = texts.hideProductsText
        retryText = texts.retryText
        closeLabel = texts.closeLabel
        soundOnLabel = texts.soundOnLabel
        soundOffLabel = texts.soundOffLabel
        shareLabel = texts.shareLabel
    }

    /**
     * Показ без ленты: виджет не на экране, поэтому SDK и таймаут ведутся вручную,
     * а просмотр открывается, как только блок пришёл. Кампания по ссылке ([sharedLink])
     * открывается с первой сторис и слайда `start_position` в обход A4, закрыв другие
     * просмотры V2, и шлёт `shared_open` один раз.
     */
    private fun showDetached(explicitSdk: SDK?, startStoryId: Int?, onClose: (() -> Unit)?): Cancellable {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(giveUp, LOAD_TIMEOUT_MS)
        pendingOpen = open@{
            handler.removeCallbacks(giveUp)
            if (stories.isEmpty()) {
                onError?.invoke(StoriesLoader.ERROR_CODE, "PersonalizationStories.show: block $code is empty")
                return@open
            }
            val shared = sharedLink
            val index = if (shared != null) 0 else startStoryId?.let { id -> stories.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
            val viewer = newViewer(context, stories, index)
            viewer.setOnDismissListener { onClose?.invoke() }
            if (shared == null) {
                viewer.show()
                return@open
            }
            val story = stories[0]
            val slide = story.startPosition.takeIf { it in 0 until story.slidesCount } ?: 0
            viewer.startSlide = slide
            OpenStoriesViewers.dismissAll()
            viewer.show()
            sdk?.let { StoriesApiV3.track(it, shared.domain, shared.link.code, "shared_open", story.id, story.getSlide(slide).id) }
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

/** Кампания по ссылке «Поделиться» и хост API v3, на котором её искать. */
private class SharedLink(val link: StoriesShare.Link, val domain: String)

/** Ссылка, которая грузится или чей просмотр открыт: повтор той же — игнор. Главный поток. */
private object SharedLinks {
    var current: StoriesShare.Link? = null
    var pending: Cancellable? = null

    fun finish(link: StoriesShare.Link) {
        if (current != link) return
        current = null
        pending = null
    }
}
