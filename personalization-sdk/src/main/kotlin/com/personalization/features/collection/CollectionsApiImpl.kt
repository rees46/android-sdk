package com.personalization.features.collection

import com.personalization.api.managers.CollectionsApi
import com.personalization.api.models.collections.CollectionResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.pathSegment
import com.personalization.core.internal.requireNotBlank
import com.personalization.features.products.CatalogJson
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [CollectionsApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class CollectionsApiImpl(private val calls: NamespaceCalls) : CollectionsApi {

    override suspend fun get(collectionId: String): CollectionResponse {
        val path = calls.checked(GET) { PATH + pathSegment(requireNotBlank(collectionId, "collectionId")) }
        return calls.request(GET, NetworkMethod.GET(path), JSONObject(), read = ::readCollection)
    }

    override fun get(collectionId: String, listener: CompletionListener<CollectionResponse>) =
        calls.complete(listener) { get(collectionId) }

    internal companion object {
        const val GET = "collections.get"
        const val PATH = "collection/"

        /** The collection's products; an answer without `products` is null (`decoding`). */
        fun readCollection(body: Any?): CollectionResponse? {
            if (body !is JSONObject) return null
            return CollectionResponse(products = CatalogJson.products(body) ?: return null)
        }
    }
}
