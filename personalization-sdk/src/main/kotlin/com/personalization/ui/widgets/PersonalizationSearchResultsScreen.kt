package com.personalization.ui.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.personalization.Cancellable
import com.personalization.R
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.params.SearchParams
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationCatalog
import com.personalization.ui.components.PersonalizationFilters
import com.personalization.ui.components.PersonalizationProductImage
import com.personalization.ui.components.PersonalizationProductsGrid
import com.personalization.ui.components.PersonalizationSearchResultsTitle
import java.util.Locale

/**
 * Экран полной выдачи с данными SDK: заголовок с числом найденного, плитка ⇄ список,
 * теги применённых фильтров, счётчик, «загрузить ещё» или бесконечная прокрутка,
 * пустое состояние и экран фильтров — виджет сам ходит в `searchFull` и держит состояние.
 *
 * Паттерн SearchResultsScreen из Figma (страница 1:30), источник — `searchFull(query)`.
 * Визуальный слой — [PersonalizationSearchResultsTitle] в заголовке
 * [PersonalizationCatalog]; фильтры — [PersonalizationFilters] поверх выдачи внутри
 * этого же виджета, так что экран занимает весь отведённый ему экран хоста.
 *
 * Инстанс SDK: [shopId] (атрибут `app:shop_id`) или единственный зарегистрированный;
 * резолвится при показе через [Rees46.awaitInstance], явный — через [attach].
 *
 * Фасеты строятся из ответа: диапазон цены (`price_range`), бренды, цвета и размеры
 * (`industrial_filters`) и произвольные фасеты магазина (`filters`), из которых
 * показываются только те, где больше одного значения. Применённые значения идут
 * тегами в заголовок и параметрами в следующий запрос. Заголовок секции произвольного
 * фасета — его имя, приведённое через [facetTitle].
 *
 * Сортировки: кнопка в заголовке отдаёт [onSortClick] хосту (пикер в макете не
 * нарисован), выбранное хост кладёт в [sortBy] / [sortDir]. Каждый новый запрос
 * трекается событием `search`; нажатие на карточку запоминает источник `full_search`
 * и отдаёт товар хосту через [onProductClick], кнопка карточки — через [onProductAction].
 */
@InternalPersonalizationUiApi
class PersonalizationSearchResultsScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Заголовок выдачи. */
    val title = PersonalizationSearchResultsTitle(context)

    /** Каталог с плиткой, счётчиком и «загрузить ещё». */
    val catalog = PersonalizationCatalog(context)

    /** Экран фильтров; показывается поверх выдачи. */
    val filters = PersonalizationFilters(context)

    /** Магазин; `null` — единственный зарегистрированный. Менять до показа. */
    var shopId: String? = null

    /** Поисковая фраза; смена перезапрашивает первую страницу. */
    var query: String? = null
        set(value) {
            val changed = field != value
            field = value
            title.text = titleText ?: value
            if (changed) scheduleReload()
        }

    /** Заголовок экрана; `null` — сама фраза. */
    var titleText: CharSequence? = null
        set(value) {
            field = value
            title.text = value ?: query
        }

    var pageSize: Int = 20

    /** `popular`, `price`, `discount`, `sales_rate`, `date`; `null` — релевантность. */
    var sortBy: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleReload()
        }

    /** `asc` / `desc`. */
    var sortDir: String? = null
        set(value) {
            if (field == value) return
            field = value
            scheduleReload()
        }

    /** Список id локаций через запятую. */
    var locations: String? = null

    /** Бесконечная прокрутка вместо кнопки «загрузить ещё». */
    var infiniteScroll: Boolean = false
        set(value) {
            field = value
            applyLoadMore()
        }

    var showFilters: Boolean = true
        set(value) {
            field = value
            title.showFiltersButton = value
        }

    var showSort: Boolean = true
        set(value) {
            field = value
            title.showSortButton = value
        }

    /** Какие фасеты из `filters` показывать; `null` — все с более чем одним значением. */
    var facets: Set<String>? = null

    /** Подпись кнопки на карточке; `null` — без кнопки. */
    var productActionText: CharSequence? = null
        set(value) {
            field = value
            renderProducts()
        }

    var view: PersonalizationProductsGrid.View
        get() = catalog.view
        set(value) {
            catalog.view = value
            title.selectedViewIndex = if (value == PersonalizationProductsGrid.View.GRID) 0 else 1
        }

    var imageAspect: PersonalizationProductImage.Aspect
        get() = catalog.imageAspect
        set(value) {
            catalog.imageAspect = value
        }

    /** Загрузчик картинок; `null` — Glide из SDK. */
    var imageLoader: ((ImageView, String) -> Unit)? = null

    var onBack: (() -> Unit)? = null
    var onSortClick: (() -> Unit)? = null
    var onProductClick: ((Product) -> Unit)? = null
    var onProductAction: ((Product) -> Unit)? = null
    var onError: ((code: Int, message: String?) -> Unit)? = null

    /** Загруженные товары в порядке выдачи. */
    val products: List<Product>
        get() = loaded

    // --- Тексты; локализация за хостом ------------------------------------------------------------

    var foundPrefix: CharSequence? = "Found"
    var foundSuffix: CharSequence? = "products"
    var countPrefix: CharSequence? = "Showing"
    var countSeparator: CharSequence = "of"
    var loadMoreText: CharSequence? = "Load more"
        set(value) {
            field = value
            applyLoadMore()
        }
    var emptyText: CharSequence? = "No results for your request."
        set(value) {
            field = value
            applyEmpty()
        }
    var filtersTitle: CharSequence? = "Filters"
    var resetText: CharSequence? = "Reset"
    var applyText: CharSequence? = "Apply"
    var showMoreText: CharSequence? = "Show more"
    var showLessText: CharSequence? = "Show less"
    var priceTitle: CharSequence = "Price"
    var fromLabel: CharSequence = "From"
    var toLabel: CharSequence = "to"
    var brandsTitle: CharSequence = "Brand"
    var colorsTitle: CharSequence = "Color"
    var sizesTitle: CharSequence = "Size"

    /** Заголовок произвольного фасета по его имени в ответе. */
    var facetTitle: (String) -> CharSequence = { name ->
        name.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /** Значения фильтров, ушедшие в запрос. */
    data class Applied(
        val brands: Set<String> = emptySet(),
        val priceMin: String? = null,
        val priceMax: String? = null,
        val colors: Set<String> = emptySet(),
        val sizes: Set<String> = emptySet(),
        val facets: Map<String, Set<String>> = emptyMap()
    ) {
        val isEmpty: Boolean
            get() = brands.isEmpty() && priceMin.isNullOrEmpty() && priceMax.isNullOrEmpty() &&
                colors.isEmpty() && sizes.isEmpty() && facets.values.all { it.isEmpty() }
    }

    var applied: Applied = Applied()
        set(value) {
            field = value
            scheduleReload()
        }

    private val filtersScroll = NestedScrollView(context)
    private var sdk: SDK? = null
    private var instanceHandle: Cancellable? = null
    private var loaded: List<Product> = emptyList()
    private var total = 0
    private var page = 0
    private var loading = false
    private var facetSource: SearchFullResponse? = null
    private var requestSeq = 0

    // Пришёл ли ответ на первую страницу текущей фразы. Пустое состояние — только после него:
    // до ответа товаров нет потому, что они ещё грузятся, а не потому, что ничего не нашлось.
    private var answered = false
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledReload = Runnable { reload() }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.PersonalizationSearchResultsScreen, 0, 0).apply {
            try {
                shopId = getString(R.styleable.PersonalizationSearchResultsScreen_shop_id)
            } finally {
                recycle()
            }
        }
        val padding = resources.getDimensionPixelSize(R.dimen.personalization_spacing_xl)
        val background = PersonalizationTheme.color(context, R.color.personalization_background_generic)

        catalog.setHeader(title)
        catalog.setPadding(padding, padding, padding, padding)
        catalog.imageLoader = { imageView, product -> loadImage(imageView, product.imageUrl) }
        catalog.onProductClick = { card -> loaded.firstOrNull { it.id == card.id }?.let { productTapped(it) } }
        catalog.onProductAction = { card -> loaded.firstOrNull { it.id == card.id }?.let { onProductAction?.invoke(it) } }
        catalog.onLoadMore = { loadMore() }
        // Каталог прокручивается сам, а не во внешнем скролле: там его лента раскладывалась бы
        // целиком, и каждая догруженная страница оставалась бы в памяти всеми карточками.
        catalog.setBackgroundColor(background)
        catalog.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Следующая страница — за полэкрана до конца ленты.
                val left = recyclerView.computeVerticalScrollRange() -
                    recyclerView.computeVerticalScrollOffset() - recyclerView.computeVerticalScrollExtent()
                if (infiniteScroll && dy > 0 && left <= recyclerView.height / 2) loadMore()
            }
        })
        addView(catalog, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        title.onBack = { onBack?.invoke() }
        title.onViewChanged = { index ->
            catalog.view = if (index == 0) PersonalizationProductsGrid.View.GRID else PersonalizationProductsGrid.View.LIST
        }
        title.onFilters = { openFilters() }
        title.onSort = { onSortClick?.invoke() }

        filters.setPadding(padding, padding, padding, padding)
        filters.onClose = { closeFilters() }
        filters.onReset = {
            closeFilters()
            applied = Applied()
        }
        filters.onApply = {
            closeFilters()
            applied = fromSections(filters.sections)
        }
        filtersScroll.isFillViewport = true
        filtersScroll.setBackgroundColor(background)
        filtersScroll.isVisible = false
        filtersScroll.addView(filters, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(filtersScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        applyLoadMore()
    }

    /** Явный инстанс вместо резолва по [shopId]. */
    fun attach(sdk: SDK) {
        instanceHandle?.cancel()
        instanceHandle = null
        this.sdk = sdk
        reload()
    }

    /**
     * Свойства, от которых зависит запрос, перезапрашивают на следующем проходе главного
     * потока, а не сразу: хост, выставивший sortBy и sortDir подряд, получает один запрос
     * и одно событие `search`, а не два.
     */
    private fun scheduleReload() {
        handler.removeCallbacks(scheduledReload)
        handler.post(scheduledReload)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sdk != null) return
        instanceHandle = try {
            Rees46.awaitInstance(shopId) { resolved -> MainThread.run { attach(resolved) } }
        } catch (throwable: Throwable) {
            SDK.error(
                "PersonalizationSearchResultsScreen: cannot resolve an SDK for shopId=$shopId — " +
                    "set app:shop_id to pick a shop when several are registered.",
                throwable
            )
            null
        }
    }

    override fun onDetachedFromWindow() {
        instanceHandle?.cancel()
        instanceHandle = null
        super.onDetachedFromWindow()
    }

    val isFiltersOpen: Boolean
        get() = filtersScroll.isVisible

    fun openFilters() {
        filters.text = filtersTitle
        filters.resetText = resetText
        filters.applyText = applyText
        filters.setSections(buildSections())
        filtersScroll.scrollTo(0, 0)
        filtersScroll.isVisible = true
    }

    fun closeFilters() {
        filtersScroll.isVisible = false
    }

    /** Первая страница заново — сразу, отменяя перезапрос, запланированный свойствами. */
    fun reload() {
        handler.removeCallbacks(scheduledReload)
        val sdk = sdk ?: return
        val text = query?.trim().orEmpty()
        // Ответ на прошлый запрос больше не нужен, и снимать лоадер он уже не будет — его
        // отбросит проверка requestSeq. Поэтому лоадер снимается здесь, иначе при пустой фразе
        // он крутился бы вечно, а loadMore так и стоял бы заблокированным.
        requestSeq++
        loading = false
        catalog.isLoading = false
        answered = false
        loaded = emptyList()
        total = 0
        page = 0
        renderProducts()
        // Новая выдача — с начала ленты, а не с места, где пользователь бросил прошлую.
        catalog.scrollToPosition(0)
        if (text.isEmpty()) return
        sdk.tracking.search(text)
        request(sdk, text, nextPage = 1)
    }

    /** Следующая страница, если она есть и запрос не в полёте. */
    fun loadMore() {
        val sdk = sdk ?: return
        val text = query?.trim().orEmpty()
        if (text.isEmpty() || loading || loaded.size >= total) return
        request(sdk, text, nextPage = page + 1)
    }

    private fun request(sdk: SDK, text: String, nextPage: Int) {
        val seq = ++requestSeq
        loading = true
        catalog.isLoading = true
        sdk.searchManager.searchFull(
            query = text,
            searchParams = buildParams(nextPage),
            onSearchFull = { response ->
                MainThread.run {
                    if (seq != requestSeq) return@run
                    loading = false
                    catalog.isLoading = false
                    page = nextPage
                    total = response.productsTotal
                    val items = response.products.orEmpty()
                    loaded = if (nextPage == 1) items else loaded + items
                    if (nextPage == 1) {
                        facetSource = response
                        answered = true
                    }
                    renderProducts()
                }
            },
            onError = { code, message ->
                MainThread.run {
                    if (seq != requestSeq) return@run
                    loading = false
                    catalog.isLoading = false
                    onError?.invoke(code, message)
                }
            }
        )
    }

    private fun buildParams(nextPage: Int): SearchParams = SearchParams().apply {
        put(SearchParams.Parameter.PAGE, nextPage)
        put(SearchParams.Parameter.LIMIT, pageSize)
        sortBy?.let { put(SearchParams.Parameter.SORT_BY, it) }
        sortDir?.let { put(SearchParams.Parameter.SORT_DIR, it) }
        locations?.let { put(SearchParams.Parameter.LOCATIONS, it) }
        if (applied.brands.isNotEmpty()) put(SearchParams.Parameter.BRANDS, applied.brands.joinToString(","))
        applied.priceMin?.takeIf { it.isNotBlank() }?.let { put(SearchParams.Parameter.PRICE_MIN, it.trim()) }
        applied.priceMax?.takeIf { it.isNotBlank() }?.let { put(SearchParams.Parameter.PRICE_MAX, it.trim()) }
        if (applied.colors.isNotEmpty()) put(SearchParams.Parameter.COLORS, applied.colors.joinToString(","))
        if (applied.sizes.isNotEmpty()) put(SearchParams.Parameter.FASHION_SIZES, applied.sizes.joinToString(","))
        val facetValues = applied.facets.filterValues { it.isNotEmpty() }
        if (facetValues.isNotEmpty()) {
            val searchFilters = SearchParams.SearchFilters()
            facetValues.forEach { (name, values) -> searchFilters.put(name, values.toTypedArray()) }
            put(SearchParams.Parameter.FILTERS, searchFilters)
        }
    }

    // --- Раскладка ------------------------------------------------------------------------------

    private fun renderProducts() {
        catalog.products = loaded.map { it.toCardProduct(productActionText) }
        title.setResults(foundPrefix, total, foundSuffix)
        if (loaded.isEmpty()) catalog.setCount(null, 0, countSeparator, 0)
        else catalog.setCount(countPrefix, loaded.size, countSeparator, total)
        applyLoadMore()
        applyEmpty()
        renderAppliedTags()
    }

    private fun applyEmpty() {
        catalog.emptyText = if (answered && loaded.isEmpty()) emptyText else null
    }

    private fun applyLoadMore() {
        val hasMore = loaded.isNotEmpty() && loaded.size < total
        catalog.loadMoreText = if (hasMore && !infiniteScroll) loadMoreText else null
    }

    private fun renderAppliedTags() {
        val tags = mutableListOf<PersonalizationSearchResultsTitle.Filter>()
        applied.brands.forEach { brand ->
            tags += PersonalizationSearchResultsTitle.Filter(brand) { applied = applied.copy(brands = applied.brands - brand) }
        }
        if (!applied.priceMin.isNullOrBlank() || !applied.priceMax.isNullOrBlank()) {
            val label = listOfNotNull(applied.priceMin?.takeIf { it.isNotBlank() }, applied.priceMax?.takeIf { it.isNotBlank() })
                .joinToString(" – ")
            tags += PersonalizationSearchResultsTitle.Filter("$priceTitle $label") {
                applied = applied.copy(priceMin = null, priceMax = null)
            }
        }
        applied.colors.forEach { color ->
            tags += PersonalizationSearchResultsTitle.Filter(color) { applied = applied.copy(colors = applied.colors - color) }
        }
        applied.sizes.forEach { size ->
            tags += PersonalizationSearchResultsTitle.Filter(size) { applied = applied.copy(sizes = applied.sizes - size) }
        }
        applied.facets.forEach { (name, values) ->
            values.forEach { value ->
                tags += PersonalizationSearchResultsTitle.Filter(value) {
                    applied = applied.copy(facets = applied.facets + (name to (values - value)))
                }
            }
        }
        title.setFilters(tags)
    }

    private fun loadImage(imageView: ImageView, url: String?) {
        val loader = imageLoader
        if (loader != null && url != null) loader(imageView, url) else SearchImages.load(imageView, url)
    }

    private fun productTapped(product: Product) {
        query?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sdk?.tracking?.setSource(TrackingSource(TrackingSourceType.FULL_SEARCH, it))
        }
        onProductClick?.invoke(product)
    }

    // --- Фасеты ⇄ секции экрана фильтров -----------------------------------------------------------

    private fun options(values: Collection<String>, checked: Set<String>): List<PersonalizationFilters.Option> =
        (values + checked).distinct().map { PersonalizationFilters.Option(id = it, label = it, checked = it in checked) }

    private fun buildSections(): List<PersonalizationFilters.Section> {
        val source = facetSource
        val sections = mutableListOf<PersonalizationFilters.Section>()
        val range = source?.priceRange
        if (range != null || applied.priceMin != null || applied.priceMax != null) {
            // Границы диапазона — подсказками: в запрос уходит только то, что ввёл пользователь.
            sections += PersonalizationFilters.Section.Range(
                id = SECTION_PRICE, title = priceTitle, fromLabel = fromLabel, toLabel = toLabel,
                from = applied.priceMin, to = applied.priceMax,
                fromPlaceholder = range?.let { bound(it.min) }, toPlaceholder = range?.let { bound(it.max) }
            )
        }
        val brands = source?.brands.orEmpty().mapNotNull { it.name }
        if (brands.isNotEmpty() || applied.brands.isNotEmpty()) {
            sections += PersonalizationFilters.Section.Options(
                id = SECTION_BRANDS, title = brandsTitle, options = options(brands, applied.brands),
                showMoreText = showMoreText, showLessText = showLessText
            )
        }
        val colors = source?.industrialFilters?.colors.orEmpty().map { it.color }
        if (colors.isNotEmpty() || applied.colors.isNotEmpty()) {
            sections += PersonalizationFilters.Section.Options(
                id = SECTION_COLORS, title = colorsTitle, options = options(colors, applied.colors),
                showMoreText = showMoreText, showLessText = showLessText
            )
        }
        val sizes = source?.industrialFilters?.fashionSizes.orEmpty().map { it.size }
        if (sizes.isNotEmpty() || applied.sizes.isNotEmpty()) {
            sections += PersonalizationFilters.Section.Options(
                id = SECTION_SIZES, title = sizesTitle, options = options(sizes, applied.sizes),
                showMoreText = showMoreText, showLessText = showLessText
            )
        }
        val wanted = facets
        source?.filters.orEmpty().forEach { (name, facet) ->
            val values = facet.values?.keys.orEmpty()
            val checked = applied.facets[name].orEmpty()
            val visible = if (wanted != null) name in wanted else values.size > 1
            if (!visible && checked.isEmpty()) return@forEach
            sections += PersonalizationFilters.Section.Options(
                id = SECTION_FACET + name, title = facetTitle(name), options = options(values, checked),
                showMoreText = showMoreText, showLessText = showLessText
            )
        }
        return sections
    }

    /** Граница цены без хвоста «.0»: сервер отдаёт число, форматированной строки у него нет. */
    private fun bound(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    private fun fromSections(sections: List<PersonalizationFilters.Section>): Applied {
        var next = Applied()
        val facetValues = mutableMapOf<String, Set<String>>()
        sections.forEach { section ->
            when (section) {
                is PersonalizationFilters.Section.Range -> if (section.id == SECTION_PRICE) {
                    next = next.copy(
                        priceMin = section.from?.toString()?.takeIf { it.isNotBlank() },
                        priceMax = section.to?.toString()?.takeIf { it.isNotBlank() }
                    )
                }
                is PersonalizationFilters.Section.Options -> {
                    val checked = section.options.filter { it.checked }.map { it.id }.toSet()
                    when {
                        section.id == SECTION_BRANDS -> next = next.copy(brands = checked)
                        section.id == SECTION_COLORS -> next = next.copy(colors = checked)
                        section.id == SECTION_SIZES -> next = next.copy(sizes = checked)
                        section.id.startsWith(SECTION_FACET) -> facetValues[section.id.removePrefix(SECTION_FACET)] = checked
                    }
                }
            }
        }
        return next.copy(facets = facetValues)
    }

    private companion object {
        const val SECTION_PRICE = "price"
        const val SECTION_BRANDS = "brands"
        const val SECTION_COLORS = "colors"
        const val SECTION_SIZES = "sizes"
        const val SECTION_FACET = "facet:"
    }
}
