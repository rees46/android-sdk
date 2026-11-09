package com.personalization.demo.shop

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.personalization.OnClickListener
import com.personalization.R as SdkR
import com.personalization.Product
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.demo.openGameLink
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationToast
import com.personalization.ui.widgets.PersonalizationSearchResultsScreen
import org.json.JSONObject

/**
 * "Shop" tab — a small shop put together from the UI kit and the public SDK methods, the way a host
 * app would build one. It is the live integration example of the kit:
 * - Home ([ShopHome]) — search behind a button, the stories bar, a recommender block and the shop's
 *   top-level categories;
 * - Category ([ShopCategory]) — the kit's category screen: `getCategory` with filters and sorting;
 * - Product ([ShopProduct]) — `getProductInfo`, the view tracked, similar products under it;
 * - Search ([ShopSearch]) — the kit's search results screen.
 *
 * Home has the instant search field behind its search button ([ShopSearchBar]); Search and Category sort
 * with the shop's sheet ([pickSort]). There is no app bar over the shop: every screen starts under the
 * status bar.
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
    private val navigator = ShopNavigator(container, ::screen) { route -> onScreen?.invoke(route.screen) }

    /** Code of the shop screen on top, for popups v3. */
    val currentScreen: String get() = navigator.top.screen

    /** The shop screen on top changed — its code goes to `sdk.popups.screenOpened`. */
    var onScreen: ((String) -> Unit)? = null

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

    /** Colour of the top edge of the screen on top: the status bar over the shop takes it. */
    val topColor: Int
        get() = PersonalizationTheme.color(
            context, navigator.topScreen?.topSurface ?: SdkR.color.personalization_background_generic
        )

    /**
     * Clicks of the shop's stories, also of a campaign opened by a link: links stay with the SDK (it
     * opens them itself); the game link opens the game and a product its page here — `false`, the host
     * handled it, the viewer closes.
     */
    val storiesClicks: OnClickListener = object : OnClickListener {
        override fun onClick(url: String): Boolean = !openGameLink(context, url)

        override fun onClick(product: Product): Boolean {
            open(ShopRoute.Product(product.id))
            return false
        }
    }

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
        is ShopRoute.Category -> ShopCategory(this, route)
        is ShopRoute.Product -> ShopProduct(this, route, trackView = !restored)
        is ShopRoute.Search -> ShopSearch(this, route)
    }

    private companion object {
        const val TAG = "DemoShop"
    }
}

/**
 * Search results for a submitted phrase: the kit's screen as it is. The screen runs the search, its
 * paging and filters itself and tracks `search`; a tap remembers the `full_search` source and opens the
 * product page. Sorting is the shop's sheet.
 */
internal class ShopSearch(private val shop: DemoShop, route: ShopRoute.Search) : ShopScreen {

    private val screen = PersonalizationSearchResultsScreen(shop.context).apply {
        shopId = shop.shopId
        productActionText = ADD_TO_CART_TEXT
        onSortClick = { pickSort(context, this) }
        useShopFacetTitles()
        onBack = { shop.close() }
        onProductClick = { product -> shop.open(ShopRoute.Product(product.id)) }
        onProductAction = { product -> shop.addToCart(this, product.id, product.price) }
        query = route.query
    }

    override val view: View = listingFrame(screen)

    override fun onBack(): Boolean = screen.closeOpenFilters()
}
