package com.personalization.features.products

import com.personalization.api.managers.ProductsApi
import com.personalization.api.models.products.CategoryRequest
import com.personalization.api.models.products.CategoryResponse
import com.personalization.api.models.products.Product
import com.personalization.api.models.products.ProductsListRequest
import com.personalization.api.models.products.ProductsListResponse
import com.personalization.api.responses.products.counters.ProductCounter
import com.personalization.api.responses.products.counters.ProductCounterTriggers
import com.personalization.api.responses.products.counters.ProductCountersResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.requireNotBlank
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [ProductsApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class ProductsApiImpl(private val calls: NamespaceCalls) : ProductsApi {

    override suspend fun getInfo(itemId: String): Product? {
        val params = calls.checked(GET_INFO) { JSONObject().put("item_id", requireNotBlank(itemId, "itemId")) }
        return calls.request(GET_INFO, NetworkMethod.GET(INFO_PATH), params, read = ::readInfo).product
    }

    override suspend fun getList(request: ProductsListRequest): ProductsListResponse {
        val params = calls.checked(GET_LIST) {
            with(request) {
                CatalogJson.listingParams(categoryIds, brands, merchants, locations, priceMin, priceMax, filters, sortBy, sortDir, limit, page)
            }
        }
        return calls.request(GET_LIST, NetworkMethod.GET(LIST_PATH), params, read = ::readList)
    }

    override suspend fun getCounters(itemId: String): ProductCountersResponse {
        val params = calls.checked(GET_COUNTERS) { JSONObject().put("item", requireNotBlank(itemId, "itemId")) }
        return calls.request(GET_COUNTERS, NetworkMethod.GET(COUNTERS_PATH), params, read = ::readCounters)
    }

    /** A delegate of [getList] with the category: the server's `/category/{id}` is the same listing. */
    override suspend fun getCategory(categoryId: String, request: CategoryRequest): CategoryResponse {
        val params = calls.checked(GET_CATEGORY) {
            val category = listOf(requireNotBlank(categoryId, "categoryId"))
            with(request) {
                CatalogJson.listingParams(category, brands, null, locations, priceMin, priceMax, filters, sortBy, sortDir, limit, page)
            }
        }
        return calls.request(GET_CATEGORY, NetworkMethod.GET(LIST_PATH), params, read = ::readCategory)
    }

    override fun getInfo(itemId: String, listener: CompletionListener<Product?>) = calls.complete(listener) { getInfo(itemId) }

    override fun getList(request: ProductsListRequest, listener: CompletionListener<ProductsListResponse>) =
        calls.complete(listener) { getList(request) }

    override fun getCounters(itemId: String, listener: CompletionListener<ProductCountersResponse>) =
        calls.complete(listener) { getCounters(itemId) }

    override fun getCategory(categoryId: String, request: CategoryRequest, listener: CompletionListener<CategoryResponse>) =
        calls.complete(listener) { getCategory(categoryId, request) }

    /** A product card, or none: the path's result must not be null, so it carries the card. */
    internal class Card(val product: Product?)

    internal companion object {
        const val GET_INFO = "products.getInfo"
        const val GET_LIST = "products.getList"
        const val GET_COUNTERS = "products.getCounters"
        const val GET_CATEGORY = "products.getCategory"
        const val INFO_PATH = "products/get"
        const val LIST_PATH = "products"
        const val COUNTERS_PATH = "products/counters"

        /**
         * The product card: the id from `uniqid`, then `id`. An empty answer (`{}`, the server's answer for a
         * product it does not have) is no product; an answer without an id, or without another field every
         * product has, is null (`decoding`).
         */
        fun readInfo(body: Any?): Card? {
            if (body == null) return Card(null)
            if (body !is JSONObject) return null
            if (body.length() == 0) return Card(null)
            return CatalogJson.product(body, uniqidFirst = true)?.let(::Card)
        }

        /** A page of the listing; an answer without `products` is null (`decoding`). */
        fun readList(body: Any?): ProductsListResponse? {
            if (body !is JSONObject) return null
            return ProductsListResponse(
                productsTotal = body.intOrNull("products_total"),
                products = CatalogJson.products(body) ?: return null,
                categories = CatalogJson.categories(body),
                brands = CatalogJson.brands(body),
                locations = CatalogJson.locations(body),
                filters = CatalogJson.filters(body),
                industrialFilters = CatalogJson.industrialFilters(body),
                priceRange = CatalogJson.priceRange(body),
                priceRanges = CatalogJson.priceRanges(body),
                priceMedian = body.doubleOrNull("price_median"),
            )
        }

        /** A category's screen: the listing's answer without the fields a category has not; `products` as in [readList]. */
        fun readCategory(body: Any?): CategoryResponse? {
            if (body !is JSONObject) return null
            return CategoryResponse(
                productsTotal = body.intOrNull("products_total"),
                products = CatalogJson.products(body) ?: return null,
                categories = CatalogJson.categories(body),
                brands = CatalogJson.brands(body),
                filters = CatalogJson.filters(body),
                priceRange = CatalogJson.priceRange(body),
                priceRanges = CatalogJson.priceRanges(body),
                priceMedian = body.doubleOrNull("price_median"),
            )
        }

        /** The counters; a counter the answer leaves out is 0, as the model has it. Null for an answer that is not an object. */
        fun readCounters(body: Any?): ProductCountersResponse? {
            if (body !is JSONObject) return null
            return ProductCountersResponse(
                daily = body.objectOrNull("daily")?.let(::counter),
                now = body.objectOrNull("now")?.let(::counter),
                triggers = body.objectOrNull("triggers")?.let { triggers ->
                    ProductCounterTriggers(
                        backInStock = triggers.intOrNull("back_in_stock") ?: 0,
                        priceDrop = triggers.intOrNull("price_drop") ?: 0,
                    )
                },
            )
        }

        private fun counter(json: JSONObject) = ProductCounter(
            view = json.intOrNull("view") ?: 0,
            cart = json.intOrNull("cart") ?: 0,
            purchase = json.intOrNull("purchase") ?: 0,
        )
    }
}
