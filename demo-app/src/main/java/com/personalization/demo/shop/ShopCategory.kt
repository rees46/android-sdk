package com.personalization.demo.shop

import android.view.View
import android.widget.FrameLayout
import com.personalization.R as SdkR
import com.personalization.ui.widgets.PersonalizationCategoryScreen
import com.personalization.ui.widgets.PersonalizationListingScreen

/**
 * A category: the kit's category screen as it is. The screen runs `getCategory` itself — pages,
 * filters, sorting, the empty state and errors with Retry — and tracks the category view once. Its
 * title is the category name, its back arrow goes down the shop's stack, sorting is the shop's sheet
 * ([pickSort]), "Add to cart" tracks the cart and shows the toast. Search is on Home.
 */
internal class ShopCategory(
    private val shop: DemoShop,
    route: ShopRoute.Category
) : ShopScreen {

    private val screen = PersonalizationCategoryScreen(shop.context).apply {
        shopId = shop.shopId
        titleText = route.name
        productActionText = ADD_TO_CART_TEXT
        onBack = { shop.close() }
        onSortClick = { pickSort(context, this) }
        useShopFacetTitles()
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id, product.price) }
        categoryId = route.id
    }

    override val view: View = listingFrame(screen)

    override fun onBack(): Boolean = screen.closeOpenFilters()
}

/**
 * A listing screen of the kit (category, search results) in the shop: under the status bar with the
 * shop's top gap of 8, clear of the navigation bar.
 */
internal fun listingFrame(screen: PersonalizationListingScreen): View = FrameLayout(screen.context).apply {
    setBackgroundColor(context.pageBackground())
    addView(screen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    val top = context.spacing(SdkR.dimen.personalization_spacing_md)
    onSystemBars { barTop, barBottom -> setPadding(0, barTop + top, 0, barBottom) }
}

/** System back on a listing: closes its open filters. `false` — there were none. */
internal fun PersonalizationListingScreen.closeOpenFilters(): Boolean {
    if (!isFiltersOpen) return false
    closeFilters()
    return true
}
