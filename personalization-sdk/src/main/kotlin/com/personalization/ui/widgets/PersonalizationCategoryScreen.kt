package com.personalization.ui.widgets

import android.content.Context
import android.util.AttributeSet
import com.personalization.R
import com.personalization.SDK

/**
 * Экран категории с данными SDK — сестра [PersonalizationSearchResultsScreen]: тот же
 * заголовок с фильтрами, сортировкой и счётчиком, плитка ⇄ список, «загрузить ещё» или
 * бесконечная прокрутка, пустое состояние и ошибка с повтором, но источник —
 * `getCategory(categoryId)` с применёнными брендами, фасетами, ценой и сортировкой.
 * Всё, кроме источника, — общее, см. [PersonalizationListingScreen].
 *
 * Заголовок — название категории, его задаёт хост в [titleText]; под ним счётчик
 * «N products» ([foundPrefix] / [foundSuffix]). Цветов и размеров у категории в ответе нет,
 * поэтому и секций для них нет.
 *
 * Трекинг: `category` ([com.personalization.api.managers.TrackingApi.categoryView]) — один
 * раз на категорию, а не на каждую страницу, фильтр, сортировку или повтор. Источник
 * перехода при нажатии на товар не ставится.
 */
class PersonalizationCategoryScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PersonalizationListingScreen(context, attrs, defStyleAttr) {

    /** Id категории (slug из `categories` ответа); смена перезапрашивает первую страницу. */
    var categoryId: String? = null
        set(value) {
            val changed = field != value
            field = value
            if (changed) scheduleReload()
        }

    // Категория, просмотр которой уже отправлен этим экраном.
    private var trackedCategory: String? = null

    init {
        foundPrefix = null
        emptyText = "No products in this category yet."
        context.theme.obtainStyledAttributes(attrs, R.styleable.PersonalizationCategoryScreen, 0, 0).apply {
            try {
                shopId = getString(R.styleable.PersonalizationCategoryScreen_shop_id)
                categoryId = getString(R.styleable.PersonalizationCategoryScreen_category_id)
            } finally {
                recycle()
            }
        }
    }

    private val slug: String
        get() = categoryId?.trim().orEmpty()

    override val hasSource: Boolean
        get() = slug.isNotEmpty()

    override fun trackRequest(sdk: SDK) {
        if (slug == trackedCategory) return
        trackedCategory = slug
        sdk.tracking.categoryView(slug)
    }

    override fun fetch(sdk: SDK, page: Int, onPage: (ListingPage) -> Unit, onError: (Int, String?) -> Unit) {
        val facetValues = applied.facets.filterValues { it.isNotEmpty() }.mapValues { (_, values) -> values.toList() }
        sdk.categoryManager.getCategory(
            category = slug,
            limit = pageSize,
            page = page,
            brands = applied.brands.takeIf { it.isNotEmpty() }?.joinToString(","),
            locations = locations,
            filters = facetValues.takeIf { it.isNotEmpty() },
            priceMin = priceNumber(applied.priceMin),
            priceMax = priceNumber(applied.priceMax),
            sortBy = sortBy,
            sortDir = sortDir,
            onSuccess = { response -> onPage(response.toListingPage()) },
            onError = onError
        )
    }
}
