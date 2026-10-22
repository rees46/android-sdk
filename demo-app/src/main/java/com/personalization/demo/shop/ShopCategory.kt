package com.personalization.demo.shop

import android.content.Context
import android.view.View
import com.personalization.R as SdkR
import com.personalization.api.responses.category.CategoryResponse
import com.personalization.api.responses.product.Product
import com.personalization.ui.components.PersonalizationButtonGroup
import com.personalization.ui.components.PersonalizationCatalog
import com.personalization.ui.components.PersonalizationProductsGrid

/**
 * A category: `getCategory(id, limit = 20, page)` page by page in the kit's catalog. The title row
 * carries back, the category name and the grid ⇄ list switch; under the products — "Showing N of M"
 * and "Load more". The catalog draws the empty state, and the error of a failed first page with
 * Retry; a failed next page turns "Load more" into Retry for that page. No filters or sorting in this
 * iteration.
 */
internal class ShopCategory(
    private val shop: DemoShop,
    private val route: ShopRoute.Category,
    trackView: Boolean
) : ShopScreen {

    private val context: Context = shop.context
    private val catalog = PersonalizationCatalog(context)
    private val loaded = mutableListOf<Product>()
    private var total = 0
    private var page = 0
    private var hasMore = false
    private var loading = false
    private var request = 0

    override val view: View = catalog

    init {
        val gap = context.spacing(SdkR.dimen.personalization_spacing_xl)
        catalog.setPadding(gap, gap, gap, gap)
        catalog.setBackgroundColor(context.pageBackground())
        catalog.setHeader(context.backTitle(route.name) { shop.close() }.apply {
            setTrailing(PersonalizationButtonGroup(context).apply {
                size = PersonalizationButtonGroup.Size.MD
                items = listOf(
                    PersonalizationButtonGroup.Item(SdkR.drawable.personalization_ic_grid_2x2, SdkR.drawable.personalization_ic_grid_2x2_fill),
                    PersonalizationButtonGroup.Item(SdkR.drawable.personalization_ic_list, SdkR.drawable.personalization_ic_list_fill)
                )
                onSelected = { index ->
                    catalog.view = if (index == 0) PersonalizationProductsGrid.View.GRID else PersonalizationProductsGrid.View.LIST
                }
            })
        })
        catalog.imageLoader = glideCardLoader
        catalog.retryText = RETRY_TEXT
        catalog.onRetry = { load(1) }
        catalog.onLoadMore = { load(page + 1) }
        catalog.onProductClick = { card -> shop.open(ShopRoute.Product(card.id)) }
        catalog.onProductAction = { card ->
            shop.addToCart(catalog, card.id, loaded.firstOrNull { it.id == card.id }?.price)
        }
        if (trackView) shop.sdk.tracking.categoryView(route.id)
        load(1)
    }

    private fun load(target: Int) {
        if (loading) return
        loading = true
        val seq = ++request
        catalog.errorText = null
        catalog.isLoading = true
        shop.sdk.categoryManager.getCategory(
            category = route.id,
            limit = PAGE_SIZE,
            page = target,
            onSuccess = { response -> onMain { if (seq == request) onPage(target, response) } },
            onError = { _, _ -> onMain { if (seq == request) onFailure() } }
        )
    }

    private fun onPage(target: Int, response: CategoryResponse) {
        loading = false
        // Gson leaves null in a missing list, the non-null type notwithstanding.
        val products: List<Product>? = response.products
        val items = products.orEmpty()
        page = target
        total = maxOf(response.productsTotal, loaded.size + items.size)
        loaded += items
        hasMore = items.isNotEmpty() && loaded.size < total
        catalog.isLoading = false
        // Empty state only for an answer: before it the catalog shows just the loader.
        catalog.emptyText = "No products in this category yet."
        catalog.products = loaded.map { it.toCard() }
        catalog.setCount(if (loaded.isEmpty()) null else "Showing", loaded.size, "of", total)
        catalog.loadMoreText = if (hasMore) "Load more" else null
    }

    private fun onFailure() {
        loading = false
        catalog.isLoading = false
        if (loaded.isEmpty()) {
            catalog.errorText = ERROR_TEXT
        } else {
            // The next page did not come: the button retries it.
            catalog.loadMoreText = RETRY_TEXT
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
