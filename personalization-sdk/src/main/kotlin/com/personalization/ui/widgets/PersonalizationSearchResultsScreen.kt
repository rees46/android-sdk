package com.personalization.ui.widgets

import android.content.Context
import android.util.AttributeSet
import com.personalization.R
import com.personalization.SDK
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.params.SearchParams

/**
 * Экран полной выдачи поиска с данными SDK: заголовок с числом найденного, плитка ⇄ список,
 * теги применённых фильтров, счётчик, «загрузить ещё» или бесконечная прокрутка,
 * пустое состояние и экран фильтров — виджет сам ходит в `searchFull` и держит состояние.
 *
 * Паттерн SearchResultsScreen из Figma (страница 1:30), источник — `searchFull(query)`.
 * Всё, кроме источника, — общее с экраном категории, см. [PersonalizationListingScreen]:
 * раскладка, фильтры, сортировка, страницы, ошибки и тексты.
 *
 * Заголовок — сама фраза, пока хост не задал [titleText]. Каждый новый запрос трекается
 * событием `search`; нажатие на карточку запоминает источник `full_search`.
 */
class PersonalizationSearchResultsScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PersonalizationListingScreen(context, attrs, defStyleAttr) {

    /** Поисковая фраза; смена перезапрашивает первую страницу. */
    var query: String? = null
        set(value) {
            val changed = field != value
            field = value
            applyTitle()
            if (changed) scheduleReload()
        }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.PersonalizationSearchResultsScreen, 0, 0).apply {
            try {
                shopId = getString(R.styleable.PersonalizationSearchResultsScreen_shop_id)
            } finally {
                recycle()
            }
        }
    }

    private val text: String
        get() = query?.trim().orEmpty()

    override val hasSource: Boolean
        get() = text.isNotEmpty()

    override val fallbackTitle: CharSequence?
        get() = query

    override fun trackRequest(sdk: SDK) {
        sdk.tracking.search(text)
    }

    override fun fetch(sdk: SDK, page: Int, onPage: (ListingPage) -> Unit, onError: (Int, String?) -> Unit) {
        sdk.searchManager.searchFull(
            query = text,
            searchParams = buildParams(page),
            onSearchFull = { response -> onPage(response.toListingPage()) },
            onError = onError
        )
    }

    override fun trackProductClick(sdk: SDK) {
        text.takeIf { it.isNotEmpty() }?.let {
            sdk.tracking.setSource(TrackingSource(TrackingSourceType.FULL_SEARCH, it))
        }
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
}
