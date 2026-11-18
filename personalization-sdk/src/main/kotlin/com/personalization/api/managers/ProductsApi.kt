package com.personalization.api.managers

import com.personalization.api.models.products.CategoryRequest
import com.personalization.api.models.products.CategoryResponse
import com.personalization.api.models.products.Product
import com.personalization.api.models.products.ProductsListRequest
import com.personalization.api.models.products.ProductsListResponse
import com.personalization.api.responses.products.counters.ProductCountersResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The shop's catalog — the `products` namespace: `sdk.products.getInfo("37")`. Each method asks the API
 * once and answers with a model; the SDK adds `did` and the other common fields itself.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 *
 * Every method throws [SdkException] with [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (before any request),
 * [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or [SdkError.Cancelled]
 * (`release()`). Nothing repeats a request.
 */
@BridgeApi
interface ProductsApi {

    /**
     * The product card of [itemId] (`GET /products/get`); null for a product the catalog does not have.
     * An empty [itemId] is `invalidArgument`.
     */
    suspend fun getInfo(itemId: String): Product?

    /**
     * A page of the catalog's listing with its facets (`GET /products`); without a [request], the first
     * page of the whole catalog.
     */
    suspend fun getList(request: ProductsListRequest = ProductsListRequest()): ProductsListResponse

    /**
     * How much interest [itemId] draws (`GET /products/counters`): views, carts and purchases today and in
     * the last 30 minutes, and subscriptions to its price and its stock. An empty [itemId] is
     * `invalidArgument`.
     */
    suspend fun getCounters(itemId: String): ProductCountersResponse

    /**
     * The screen of the category [categoryId]: its products and facets. It asks for the listing of that
     * category (`GET /products` with `categories`), which is what the server's `/category/{id}` does.
     * An empty [categoryId] is `invalidArgument`.
     */
    suspend fun getCategory(categoryId: String, request: CategoryRequest = CategoryRequest()): CategoryResponse

    /** [getInfo] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getInfo(itemId: String, listener: CompletionListener<Product?>)

    /** [getList] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getList(request: ProductsListRequest, listener: CompletionListener<ProductsListResponse>)

    /** [getCounters] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getCounters(itemId: String, listener: CompletionListener<ProductCountersResponse>)

    /** [getCategory] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getCategory(categoryId: String, request: CategoryRequest, listener: CompletionListener<CategoryResponse>)
}
