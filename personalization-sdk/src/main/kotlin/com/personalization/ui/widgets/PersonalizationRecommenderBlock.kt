package com.personalization.ui.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.personalization.Cancellable
import com.personalization.R
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.components.PersonalizationProductsBlock

/**
 * Блок рекомендаций с данными SDK: сам грузит товары и рисует их блоком товаров кита
 * ([PersonalizationProductsBlock]) — каруселью, плиткой или списком.
 *
 * Паттерн Recommender из Figma: Card — Recommender/Carousel (90:655), Recommender/Grid
 * (126:1944), Recommender/List (126:2551). Источник — ровно один:
 * - [code] (атрибут `app:code`) — блок рекомендаций, `getExtendedRecommendation`: заголовок
 *   и товары. Для «похожих» и «с этим покупают» — [itemId] и [categoryId], ещё [locations];
 * - [collectionId] — коллекция, `getCollection`: товары без заголовка.
 *
 * Инстанс SDK: [shopId] (атрибут `app:shop_id`) или единственный зарегистрированный;
 * резолвится при показе через [Rees46.awaitInstance], явный — через [attach].
 *
 * Скелетона нет: пока блок грузится, он скрыт ([View.GONE]) и места не занимает; товары
 * пришли — появляется с коротким fade. Скрыт он только на первой загрузке и при смене
 * источника или параметров: [reload] оставляет показанный блок на месте до ответа. Пустой
 * ответ или товаров меньше [minProducts] — блок скрыт, [onLoaded] получает 0. Ошибка, в том
 * числе упавший [reload], — блок скрыт и зовёт [onError].
 *
 * Ответы идут через общий кэш виджетов ([ResponseCache]): второй блок с теми же
 * параметрами (лента пересоздала ячейку) показывает товары сразу, без запроса и без fade.
 * [reload] идёт мимо кэша. Смена источника или его параметров — новая загрузка: свойства,
 * выставленные подряд, стоят одного запроса.
 *
 * Нажатие на товар блока рекомендаций и на кнопку его карточки запоминает источник `dynamic`
 * с кодом блока (`tracking.setSource`) и потом отдаёт товар хосту — в [onProductClick] и
 * [onProductAction]; у коллекции источник не ставится. Кнопка карточки есть, только когда
 * задан [actionText].
 */
class PersonalizationRecommenderBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Код блока рекомендаций. Задаётся он или [collectionId], не оба. */
    var code: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleLoad()
        }

    /** Id коллекции. Задаётся он или [code], не оба. */
    var collectionId: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleLoad()
        }

    /** Товар, к которому подбираются рекомендации («похожие», «с этим покупают»). */
    var itemId: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleLoad()
        }

    /** Категория, к которой подбираются рекомендации. */
    var categoryId: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleLoad()
        }

    /** Локации пользователя через запятую, как у `getExtendedRecommendation`. */
    var locations: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleLoad()
        }

    /** Магазин; `null` — единственный зарегистрированный. Менять до показа. */
    var shopId: String? = null

    var layout: PersonalizationProductsBlock.Layout
        get() = productsBlock.layout
        set(value) {
            if (productsBlock.layout != value) productsBlock.layout = value
        }

    /**
     * Заголовок блока. `null` — из ответа (у коллекции заголовка нет), пустая строка —
     * без заголовка.
     */
    var title: CharSequence? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** Сколько товаров показать из ответа; `null` — все. */
    var limit: Int? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** Меньше товаров — блок скрыт. */
    var minProducts: Int = 1
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    var imageAspect: PersonalizationProductImage.Aspect
        get() = productsBlock.imageAspect
        set(value) {
            if (productsBlock.imageAspect != value) productsBlock.imageAspect = value
        }

    /** Подпись кнопки на карточке; `null` — карточки без кнопки. */
    var actionText: CharSequence? = null
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** Подпись кнопки «показать все» у карусели; `null` — без кнопки. */
    var showAllText: CharSequence?
        get() = productsBlock.showAllText
        set(value) {
            if (productsBlock.showAllText != value) productsBlock.showAllText = value
        }

    var onShowAll: (() -> Unit)?
        get() = productsBlock.onShowAll
        set(value) {
            productsBlock.onShowAll = value
        }

    /** Точки под каруселью; по умолчанию выключены. */
    var showDots: Boolean
        get() = productsBlock.showDots
        set(value) {
            if (productsBlock.showDots != value) productsBlock.showDots = value
        }

    /**
     * Рейтинг на карточках. Оценки в ответе API пока нет, поэтому по умолчанию выключен;
     * включённый рисует её, когда она у товара есть.
     */
    var showRating: Boolean = false
        set(value) {
            field = value
            productsBlock.showRating = value
        }

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    /**
     * Нажатие на карточку — открыть товар: [PersonalizationProduct.deeplink] ведёт в
     * приложение, [PersonalizationProduct.url] — на сайт.
     */
    var onProductClick: ((PersonalizationProduct) -> Unit)? = null

    /** Кнопка карточки ([actionText]); источник `dynamic` к этому моменту уже запомнен. */
    var onProductAction: ((PersonalizationProduct) -> Unit)? = null

    /** Ответ пришёл: сколько товаров показано; 0 — блок скрыт. */
    var onLoaded: ((count: Int) -> Unit)? = null

    /** Блок не загрузился: код ошибки (HTTP или -1) и сообщение. Блок при этом скрыт. */
    var onError: ((code: Int, message: String?) -> Unit)? = null

    /** Показанные товары; пусто, пока блок скрыт. */
    var products: List<PersonalizationProduct> = emptyList()
        private set

    /** Блок товаров, который рисует ответ. */
    internal val productsBlock = PersonalizationProductsBlock(context)

    /** Кэш ответов; тесты подменяют общий своим. */
    internal var cache: ResponseCache = ResponseCache.shared

    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var requestHandle: Cancellable? = null

    // Запрос сняли при уходе с экрана, не дождавшись ответа, — при возвращении он повторится.
    private var interrupted = false

    // Источник последней загрузки и источник ответа на экране: пока они равны, новая загрузка
    // (повтор) не прячет показанные товары до ответа.
    private var requested: RecommenderSource? = null
    private var answered: RecommenderSource? = null
    private var answer: RecommenderAnswer? = null

    // Ответ пришёл, не дав load() вернуться, — значит, из кэша: показываем без fade.
    private var inLoadCall = false
    private val controller = LoadController<RecommenderAnswer>(onChange = ::onState)
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledLoad = Runnable { load(force = false) }

    init {
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.PersonalizationRecommenderBlock, defStyleAttr, 0)
            try {
                code = array.getString(R.styleable.PersonalizationRecommenderBlock_code)
                shopId = array.getString(R.styleable.PersonalizationRecommenderBlock_shop_id)
            } finally {
                array.recycle()
            }
        }
        productsBlock.showRating = showRating
        productsBlock.imageLoader = { view, product -> loadImage(view, product.imageUrl) }
        productsBlock.onProductClick = { product ->
            markSource()
            onProductClick?.invoke(product)
        }
        productsBlock.onProductAction = { product ->
            markSource()
            onProductAction?.invoke(product)
        }
        addView(productsBlock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        visibility = View.GONE
    }

    /** Явный инстанс SDK вместо резолва по [shopId]; блок загружается сразу. */
    fun attach(sdk: SDK) {
        instanceHandle?.cancel()
        instanceHandle = null
        this.sdk = sdk
        load(force = false)
    }

    /**
     * Загрузить блок заново — мимо кэша, сразу в сеть. Показанный блок остаётся на месте до
     * ответа; не загрузился — скрывается и зовёт [onError].
     */
    fun reload() {
        load(force = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sdk == null) {
            instanceHandle = try {
                Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
            } catch (throwable: Throwable) {
                fail(
                    ResponseCache.ERROR_CODE,
                    "PersonalizationRecommenderBlock: cannot resolve an SDK for shopId=$shopId — set " +
                        "app:shop_id to pick a shop when several are registered. ${throwable.message}"
                )
                null
            }
        } else if (interrupted) {
            load(force = false)
        }
    }

    override fun onDetachedFromWindow() {
        instanceHandle?.cancel()
        instanceHandle = null
        if (controller.state == LoadState.Loading) {
            requestHandle?.cancel()
            requestHandle = null
            interrupted = true
        }
        super.onDetachedFromWindow()
    }

    /** Скрытый блок не занимает места и в Compose: `AndroidView` меряет вьюху сам. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (visibility == View.GONE) {
            setMeasuredDimension(0, 0)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Источник и его параметры перезапрашивают на следующем проходе главного потока: хост,
     * выставивший код, товар и категорию подряд, получает один запрос, а не три.
     */
    private fun scheduleLoad() {
        handler.removeCallbacks(scheduledLoad)
        if (sdk != null) handler.post(scheduledLoad)
    }

    private fun load(force: Boolean) {
        handler.removeCallbacks(scheduledLoad)
        val sdk = sdk ?: return
        val source = source() ?: return
        // Прежний запрос снимается после того, как встал новый: тот же ключ в полёте
        // подхватывается, а не запрашивается заново.
        val previous = requestHandle
        interrupted = false
        requested = source
        val key = source.key(storageKey(sdk, shopId))
        val request = source.request(sdk)
        inLoadCall = true
        try {
            controller.load { onSuccess, onError ->
                requestHandle = cache.get(key, request, onSuccess, onError, force)
            }
        } finally {
            inLoadCall = false
        }
        previous?.cancel()
    }

    /** Источник из свойств; не задан или задан дважды — ошибка конфигурации. */
    private fun source(): RecommenderSource? {
        val blockCode = code.clean()
        val collection = collectionId.clean()
        return when {
            blockCode != null && collection != null -> {
                fail(ResponseCache.ERROR_CODE, "PersonalizationRecommenderBlock: set either code or collectionId, not both")
                null
            }
            blockCode != null -> RecommenderSource.Recommendation(blockCode, itemId.clean(), categoryId.clean(), locations.clean())
            collection != null -> RecommenderSource.Collection(collection)
            else -> {
                fail(ResponseCache.ERROR_CODE, "PersonalizationRecommenderBlock: code or collectionId is not set")
                null
            }
        }
    }

    private fun onState(state: LoadState<RecommenderAnswer>) {
        when (state) {
            // Другой источник: прежние товары к нему не относятся.
            LoadState.Loading -> if (answered != requested) {
                answer = null
                answered = null
                render()
            }
            is LoadState.Content -> {
                answer = state.data
                answered = requested
                render(animate = !inLoadCall)
                onLoaded?.invoke(products.size)
            }
            // Упал и повтор показанного блока: товары, которые не удалось обновить, не остаются.
            is LoadState.Error -> {
                answer = null
                answered = null
                render()
                report(state.error.code, state.error.message)
            }
            else -> Unit
        }
    }

    /** Ответ → карточки: обрезка по [limit], порог [minProducts], заголовок. */
    private fun render(animate: Boolean = false) {
        val data = answer
        val shown = data?.products.orEmpty()
            .let { all -> limit?.let { all.take(it.coerceAtLeast(0)) } ?: all }
            .map { it.toPersonalizationProduct(actionText) }
        products = if (shown.isNotEmpty() && shown.size >= minProducts) shown else emptyList()
        if (products.isEmpty()) {
            hide()
            return
        }
        productsBlock.text = title ?: data?.title
        productsBlock.products = products
        if (visibility == View.VISIBLE) return
        visibility = View.VISIBLE
        if (animate) {
            alpha = 0f
            animate().alpha(1f).setDuration(FADE_MS).start()
        }
    }

    private fun hide() {
        animate().cancel()
        alpha = 1f
        visibility = View.GONE
    }

    /** Ошибка конфигурации: поздний ответ уже не нужен, блок скрыт, хост узнаёт. */
    private fun fail(code: Int, message: String) {
        handler.removeCallbacks(scheduledLoad)
        requestHandle?.cancel()
        requestHandle = null
        interrupted = false
        controller.reset()
        requested = null
        answered = null
        answer = null
        render()
        report(code, message)
    }

    private fun report(code: Int, message: String?) {
        SDK.error("PersonalizationRecommenderBlock: $code $message")
        onError?.invoke(code, message)
    }

    /**
     * Нажатие на товар или на кнопку карточки («в корзину» прямо из блока) запоминает, что
     * товар пришёл из блока рекомендаций, — до колбэка хоста, чтобы его события уже несли
     * источник. У коллекции источника нет.
     */
    private fun markSource() {
        (answered as? RecommenderSource.Recommendation)?.let { source ->
            sdk?.tracking?.setSource(TrackingSource(TrackingSourceType.DYNAMIC, source.code))
        }
    }

    private fun loadImage(imageView: ImageView, url: String?) {
        val loader = imageLoader
        if (loader != null && url != null) loader(imageView, url) else SearchImages.load(imageView, url)
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        /** Появление блока, когда пришёл ответ из сети. */
        const val FADE_MS = 150L
    }
}
