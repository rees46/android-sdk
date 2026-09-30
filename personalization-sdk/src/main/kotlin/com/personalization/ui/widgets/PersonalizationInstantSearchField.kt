package com.personalization.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import com.personalization.Cancellable
import com.personalization.R
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Category
import com.personalization.api.responses.search.SearchBlankResponse
import com.personalization.api.responses.search.SearchInstantResponse
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationInstantSearch

/**
 * Мгновенный поиск с данными SDK: поле ввода, недавние запросы, подсказки, категории и
 * товары — виджет сам ходит в `searchInstant` / `searchBlank` и держит состояние.
 *
 * Паттерн InstantSearchField из Figma (страница 1:29): «компонент владеет вводом»,
 * источник — `searchInstant(query)`. Визуальный слой — [PersonalizationInstantSearch],
 * доступен через [view] для тонкой настройки; тексты подписей проксированы сюда.
 *
 * Инстанс SDK: [shopId] (атрибут `app:shop_id`) выбирает магазин при нескольких
 * зарегистрированных; без него берётся единственный. Инстанс резолвится при показе
 * через [Rees46.awaitInstance] — дожидается регистрации, если хост ещё не успел.
 * Явный инстанс можно передать в [attach].
 *
 * Пока запрос короче [minChars] показывается пустое состояние: история запросов
 * (локальная, по магазину), а из `searchBlank` — товары и популярные категории;
 * популярные фразы (`suggests`) подставляются тегами, только если истории ещё нет.
 * При вводе после [debounceMs] уходит `searchInstant`: фразы из `queries` — тегами,
 * категории и товары — строками. Ответ на устаревший запрос отбрасывается.
 *
 * Нажатие на товар или категорию запоминает источник `instant_search` в трекинге
 * ([TrackingSource]) и отдаёт объект хосту — навигация и `productView` за ним.
 * Отправка (кнопка поиска клавиатуры, тег истории или подсказки) кладёт фразу в
 * историю и вызывает [onSubmit] — экран полной выдачи открывает хост, событие
 * `search` трекает [PersonalizationSearchResultsScreen].
 */
@InternalPersonalizationUiApi
class PersonalizationInstantSearchField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Визуальный слой. */
    val view = PersonalizationInstantSearch(context)

    /** Магазин; `null` — единственный зарегистрированный. Менять до показа. */
    var shopId: String? = null

    var debounceMs: Long = 300L
    var minChars: Int = 2
    var productsLimit: Int = 5
    var categoriesLimit: Int = 3
    var suggestionsLimit: Int = 8
    var recentLimit: Int = 10

    /** Сколько недавних запросов видно до тега «ещё». */
    var recentCollapsed: Int = 5

    var showRecent: Boolean = true
    var showSuggestions: Boolean = true
    var showCategories: Boolean = true
    var showProducts: Boolean = true

    /** Список id локаций через запятую для `searchInstant`. */
    var locations: String? = null

    var onSubmit: ((String) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onProductClick: ((Product) -> Unit)? = null
    var onCategoryClick: ((PersonalizationSearchCategory) -> Unit)? = null
    var onError: ((code: Int, message: String?) -> Unit)? = null

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    var query: CharSequence?
        get() = view.query
        set(value) {
            view.query = value
        }

    var placeholder: CharSequence?
        get() = view.placeholder
        set(value) {
            view.placeholder = value
        }

    var cancelText: CharSequence?
        get() = view.cancelText
        set(value) {
            view.cancelText = value
        }

    var recentLabel: CharSequence?
        get() = view.recentLabel
        set(value) {
            view.recentLabel = value
        }

    var clearText: CharSequence?
        get() = view.clearText
        set(value) {
            view.clearText = value
        }

    /** Подпись тега «ещё»; показывается, когда история длиннее [recentCollapsed]. */
    var moreText: CharSequence? = null
        set(value) {
            field = value
            renderRecent()
        }

    var categoriesLabel: CharSequence?
        get() = view.categoriesLabel
        set(value) {
            view.categoriesLabel = value
        }

    var productsLabel: CharSequence?
        get() = view.productsLabel
        set(value) {
            view.productsLabel = value
        }

    var showImages: Boolean
        get() = view.showImages
        set(value) {
            view.showImages = value
        }

    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var recent: RecentSearches? = null
    private var recentItems: List<String> = emptyList()
    private var recentExpanded = false
    private var blank: SearchBlankResponse? = null
    private var instant: SearchInstantResponse? = null
    private var requestSeq = 0
    private val debounce = Runnable { runInstant() }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.PersonalizationInstantSearchField, 0, 0).apply {
            try {
                shopId = getString(R.styleable.PersonalizationInstantSearchField_shop_id)
            } finally {
                recycle()
            }
        }
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        wireView()
    }

    private fun wireView() {
        view.imageLoader = { imageView, suggestion ->
            val url = suggestion.imageUrl
            val loader = imageLoader
            if (loader != null && url != null) loader(imageView, url) else SearchImages.load(imageView, url)
        }
        view.onQueryChanged = { text -> onTyped(text.toString()) }
        view.onSubmit = { text -> submit(text.toString()) }
        view.onCancel = { onCancel?.invoke() }
        view.onClearRecent = {
            recentItems = recent?.clear() ?: emptyList()
            renderRecent()
        }
        view.onRecentRemove = { item ->
            recentItems = recent?.remove(item.toString()) ?: recentItems
            renderRecent()
        }
        view.onRecentClick = { item ->
            view.query = item
            submit(item.toString())
        }
        view.onMoreRecent = {
            recentExpanded = true
            renderRecent()
        }
        view.onSuggestionClick = { item ->
            view.query = item
            submit(item.toString())
        }
        view.onCategoryClick = { suggestion -> categoryTapped(suggestion.id) }
        view.onProductClick = { suggestion -> productTapped(suggestion.id) }
    }

    /** Явный инстанс вместо резолва по [shopId]. */
    fun attach(sdk: SDK) {
        instanceHandle?.cancel()
        instanceHandle = null
        this.sdk = sdk
        recent = RecentSearches(context, storageKey(sdk, shopId), recentLimit)
        recentItems = recent?.load().orEmpty()
        renderRecent()
        loadBlank()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sdk != null) return
        // Резолв внутри колбэка жизненного цикла: ошибка конфигурации (несколько магазинов
        // без shop_id) должна попасть в лог, а не уронить экран.
        instanceHandle = try {
            Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
        } catch (throwable: Throwable) {
            SDK.error(
                "PersonalizationInstantSearchField: cannot resolve an SDK for shopId=$shopId — " +
                    "set app:shop_id to pick a shop when several are registered.",
                throwable
            )
            null
        }
    }

    override fun onDetachedFromWindow() {
        instanceHandle?.cancel()
        instanceHandle = null
        removeCallbacks(debounce)
        super.onDetachedFromWindow()
    }

    private fun loadBlank() {
        val sdk = sdk ?: return
        val seq = ++requestSeq
        sdk.searchManager.searchBlank(
            onSearchBlank = { response ->
                MainThread.run {
                    blank = response
                    if (seq == requestSeq) renderBlank()
                }
            },
            onError = { code, message -> MainThread.run { onError?.invoke(code, message) } }
        )
    }

    private fun onTyped(text: String) {
        removeCallbacks(debounce)
        if (text.trim().length < minChars) {
            requestSeq++
            instant = null
            renderBlank()
            return
        }
        postDelayed(debounce, debounceMs)
    }

    private fun runInstant() {
        val sdk = sdk ?: return
        val text = view.query?.toString()?.trim().orEmpty()
        if (text.length < minChars) return
        val seq = ++requestSeq
        sdk.searchManager.searchInstant(
            query = text,
            locations = locations,
            onSearchInstant = { response ->
                MainThread.run {
                    if (seq != requestSeq) return@run
                    instant = response
                    renderInstant(response)
                }
            },
            onError = { code, message -> MainThread.run { onError?.invoke(code, message) } }
        )
    }

    private fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        recentItems = recent?.add(trimmed) ?: recentItems
        recentExpanded = false
        onSubmit?.invoke(trimmed)
    }

    private fun typing(): Boolean = (view.query?.toString()?.trim()?.length ?: 0) >= minChars

    // --- Раскладка данных по визуальному слою --------------------------------------------------

    private fun renderRecent() {
        if (typing()) return
        val items = if (showRecent) recentItems else emptyList()
        val collapsed = !recentExpanded && items.size > recentCollapsed
        view.setRecentSearches(if (collapsed) items.take(recentCollapsed) else items)
        view.moreText = if (collapsed) moreText else null
        // Популярные фразы сервера заменяют пустую историю.
        val suggests = blank?.suggests?.mapNotNull { it.name }.orEmpty()
        view.setSuggestions(if (items.isEmpty() && showSuggestions) suggests.take(suggestionsLimit) else emptyList())
    }

    private fun renderBlank() {
        renderRecent()
        val response = blank
        val categories = if (showCategories) response?.popularCategories.orEmpty().take(categoriesLimit) else emptyList()
        view.setCategories(
            categories.map { PersonalizationInstantSearch.Suggestion(id = it.url ?: it.name, title = it.name) }
        )
        val products = if (showProducts) response?.products.orEmpty().take(productsLimit) else emptyList()
        view.setProducts(products.map { it.toSuggestion() })
    }

    private fun renderInstant(response: SearchInstantResponse) {
        view.setRecentSearches(emptyList())
        view.moreText = null
        val queries = if (showSuggestions) response.queries.orEmpty() else emptyList()
        view.setSuggestions(queries.mapNotNull { it.name }.take(suggestionsLimit))
        val categories = if (showCategories) response.categories.orEmpty() else emptyList()
        view.setCategories(
            categories.take(categoriesLimit).map { category ->
                PersonalizationInstantSearch.Suggestion(
                    id = category.id,
                    title = category.name,
                    subtitle = parentName(category, response.categories)
                )
            }
        )
        val products = if (showProducts) response.products.orEmpty() else emptyList()
        view.setProducts(products.take(productsLimit).map { it.toSuggestion() })
    }

    /** В ответе родитель — это id; имя ищем среди пришедших категорий. */
    private fun parentName(category: Category, all: List<Category>): CharSequence? {
        val parentId: String? = category.parent
        if (parentId.isNullOrEmpty()) return null
        return all.firstOrNull { it.id == parentId }?.name
    }

    private fun Product.toSuggestion(): PersonalizationInstantSearch.Suggestion {
        val card = toCardProduct(actionText = null)
        return PersonalizationInstantSearch.Suggestion(
            id = id,
            title = name,
            subtitle = card.price,
            imageUrl = card.imageUrl
        )
    }

    private fun productTapped(id: String) {
        val product = (instant?.products.orEmpty() + blank?.products.orEmpty()).firstOrNull { it.id == id } ?: return
        rememberSource()
        onProductClick?.invoke(product)
    }

    private fun categoryTapped(id: String) {
        val category = instant?.categories.orEmpty().firstOrNull { it.id == id }?.let {
            PersonalizationSearchCategory(id = it.id, name = it.name, url = it.url)
        } ?: blank?.popularCategories.orEmpty().firstOrNull { (it.url ?: it.name) == id }?.let {
            PersonalizationSearchCategory(id = null, name = it.name, url = it.url)
        } ?: return
        rememberSource()
        onCategoryClick?.invoke(category)
    }

    private fun rememberSource() {
        val text = view.query?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        sdk?.tracking?.setSource(TrackingSource(TrackingSourceType.INSTANT_SEARCH, text))
    }
}
