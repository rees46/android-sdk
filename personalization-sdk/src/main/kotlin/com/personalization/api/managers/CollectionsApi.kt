package com.personalization.api.managers

import com.personalization.api.models.collections.CollectionResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Product collections the shop sets up in its account — the `collections` namespace:
 * `sdk.collections.get("12")`. The SDK adds `did` and the other common fields itself.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface CollectionsApi {

    /**
     * The products of the collection [collectionId] (`GET /collection/{collectionId}`).
     *
     * @throws SdkException with [SdkError.NotInitialized] (the legacy core, an instance not initialized or
     * released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (an empty [collectionId], before any
     * request), [SdkError.Network], [SdkError.Timeout], [SdkError.Http] (`400` for a collection the shop
     * does not have or that is off), [SdkError.Decoding] or [SdkError.Cancelled] (`release()`).
     */
    suspend fun get(collectionId: String): CollectionResponse

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(collectionId: String, listener: CompletionListener<CollectionResponse>)
}
