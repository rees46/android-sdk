package com.personalization.demo.shop

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.ui.components.PersonalizationToast
import com.personalization.ui.widgets.PersonalizationSearchResultsScreen
import org.json.JSONObject

/**
 * "Shop" tab — a small shop put together from the UI kit and the public SDK methods, the way a host
 * app would build one. It is the live integration example of the kit:
 * - Home ([ShopHome]) — the instant search field, the stories bar, a recommender block and the
 *   shop's top-level categories;
 * - Category ([ShopCategory]) — the kit's catalog over `getCategory`, page by page;
 * - Product ([ShopProduct]) — `getProductInfo`, the view tracked, similar products under it;
 * - Search ([ShopSearch]) — the kit's search results screen.
 *
 * There is no cart screen: "Add to cart" anywhere tracks the event and shows the kit's toast.
 * Attribution of the recommender block is set by the block itself before it hands over a product.
 */
internal class DemoShop(
    val context: Context,
    val sdk: SDK,
    val shopId: String,
    val storiesCode: String,
    container: FrameLayout
) {
    private val navigator = ShopNavigator(container, ::screen)

    /** Shows the saved stack, or Home. */
    fun start(state: Bundle?) = navigator.restore(state)

    fun save(): Bundle = navigator.save()

    fun open(route: ShopRoute) = navigator.push(route)

    /** A screen's own back button. */
    fun close() {
        navigator.pop()
    }

    /** System back; `false` — the shop is at Home with nothing open. */
    fun back(): Boolean = navigator.back()

    /** The cart of the demo: the `cart` event and a toast, no cart screen. */
    fun addToCart(anchor: View, itemId: String, price: Double? = null) {
        sdk.tracking.addToCart(
            item = TrackingItem(id = itemId, price = price),
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    Log.d(TAG, "addToCart($itemId) sent")
                }

                override fun onError(code: Int, msg: String?) {
                    Log.w(TAG, "addToCart($itemId) failed: $code $msg")
                }
            }
        )
        PersonalizationToast.show(anchor, "Added to cart")
    }

    private fun screen(route: ShopRoute, restored: Boolean): ShopScreen = when (route) {
        ShopRoute.Home -> ShopHome(this)
        is ShopRoute.Category -> ShopCategory(this, route, trackView = !restored)
        is ShopRoute.Product -> ShopProduct(this, route, trackView = !restored)
        is ShopRoute.Search -> ShopSearch(this, route)
    }

    private companion object {
        const val TAG = "DemoShop"
    }
}

/**
 * Search results for a phrase submitted on Home. The screen runs the search, its paging and filters
 * itself and tracks `search`; a tap remembers the `full_search` source and opens the product page.
 * No sort picker in this iteration, so the sort button is off.
 */
internal class ShopSearch(private val shop: DemoShop, route: ShopRoute.Search) : ShopScreen {

    private val screen = PersonalizationSearchResultsScreen(shop.context).apply {
        shopId = shop.shopId
        showSort = false
        productActionText = ADD_TO_CART_TEXT
        onBack = { shop.close() }
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id, product.price) }
        query = route.query
    }

    override val view: View = screen

    override fun onBack(): Boolean {
        if (!screen.isFiltersOpen) return false
        screen.closeFilters()
        return true
    }
}
