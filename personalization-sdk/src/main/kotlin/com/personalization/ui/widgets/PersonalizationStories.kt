package com.personalization.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.personalization.Cancellable
import com.personalization.Product
import com.personalization.R
import com.personalization.SDK
import com.personalization.Rees46
import com.personalization.stories.models.Story
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationStoriesBar

/**
 * Сторис (V2) с данными SDK: лента обложек и полноэкранный просмотр на компонентах кита.
 *
 * Паттерн Stories из Figma (страница 1:32): «сторис-бар + фулскрин-вьюер,
 * источник — сторис-блок по code». Блок грузится тем же запросом, что у старого
 * `StoriesView` (`stories/{code}`), — старый виджет остаётся как есть.
 *
 * Инстанс SDK: [shopId] (атрибут `app:shop_id`) выбирает магазин при нескольких
 * зарегистрированных; без него берётся единственный. Резолв — при показе через
 * [Rees46.awaitInstance]; явный инстанс можно передать в [attach].
 *
 * Пока блок грузится — скелетон; пустой блок прячет виджет целиком.
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

    /** Переход по ссылке; `true` — хост обработал сам. См. [PersonalizationStoriesViewer.onLinkClick]. */
    var onLinkClick: ((String) -> Boolean)? = null

    var onProductClick: ((Product) -> Unit)? = null
    var onError: ((code: Int, message: String?) -> Unit)? = null

    var promoCaption: CharSequence = "Promo code"
    var promoCopiedText: CharSequence = "Copied"

    /** Загруженные сторис — в порядке сервера. */
    var stories: List<Story> = emptyList()
        private set

    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var viewer: PersonalizationStoriesViewer? = null
    private var requestSeq = 0

    init {
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.PersonalizationStories)
            code = array.getString(R.styleable.PersonalizationStories_code)
            shopId = array.getString(R.styleable.PersonalizationStories_shop_id)
            array.recycle()
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.onItemClick = { index, _ -> open(index) }
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
        val sdk = sdk ?: return
        val blockCode = code?.takeIf { it.isNotEmpty() } ?: run {
            SDK.error("PersonalizationStories: code is not set")
            return
        }
        val seq = ++requestSeq
        if (stories.isEmpty()) bar.showSkeleton()
        sdk.storiesManager.loadStories(blockCode) { loaded ->
            MainThread.run {
                if (seq != requestSeq) return@run
                stories = loaded
                render()
            }
        }
    }

    /** Открыть просмотр с сторис [index]. */
    fun open(index: Int) {
        if (index !in stories.indices) return
        viewer?.dismiss()
        viewer = PersonalizationStoriesViewer(context, stories, index).also { viewer ->
            viewer.code = code
            viewer.sdk = sdk
            viewer.imageLoader = imageLoader
            viewer.onLinkClick = onLinkClick
            viewer.onProductClick = onProductClick
            viewer.promoCaption = promoCaption
            viewer.promoCopiedText = promoCopiedText
            viewer.onStoryProgress = { render() }
            viewer.setOnDismissListener {
                if (this.viewer === viewer) this.viewer = null
                render()
            }
            viewer.show()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sdk != null) return
        instanceHandle = try {
            Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
        } catch (throwable: Throwable) {
            SDK.error(
                "PersonalizationStories: cannot resolve an SDK for shopId=$shopId — " +
                    "set app:shop_id to pick a shop when several are registered.",
                throwable
            )
            null
        }
    }

    override fun onDetachedFromWindow() {
        instanceHandle?.cancel()
        instanceHandle = null
        viewer?.dismiss()
        viewer = null
        super.onDetachedFromWindow()
    }

    private fun render() {
        visibility = if (stories.isEmpty()) View.GONE else View.VISIBLE
        bar.imageLoader = { view, url ->
            val loader = imageLoader
            if (loader != null) loader(view, url) else SearchImages.load(view, url)
        }
        bar.items = stories.map(StoriesMapping::item)
    }
}
