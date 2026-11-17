package com.personalization.features.cart

import com.personalization.api.managers.CartApi
import com.personalization.api.responses.cart.CartItem
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [CartApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class CartApiImpl(private val calls: NamespaceCalls) : CartApi {

    override suspend fun get(): List<CartItem> =
        calls.request(GET, NetworkMethod.GET(PATH), read = ::readCart)

    override fun get(listener: CompletionListener<List<CartItem>>) = calls.complete(listener) { get() }

    internal companion object {
        const val GET = "cart.get"
        const val PATH = "products/cart"

        /**
         * The items of a cart answer: `{"data": {"items": [...]}}`, as the server sends it, or
         * `{"payload": {"cart_items": [...]}}`, as the API's documentation says it will. An answer without
         * a body is an empty cart; any other answer is null (`decoding`).
         */
        fun readCart(body: Any?): List<CartItem>? {
            if (body == null) return emptyList()
            if (body !is JSONObject) return null
            val items = body.objectOrNull("data")?.arrayOrNull("items")
                ?: body.objectOrNull("payload")?.arrayOrNull("cart_items")
                ?: return null
            return items.mapObjects(::readItem)
        }

        /** An item by its product id (`uniqid`), left out without one; 1 when it has no quantity. */
        private fun readItem(item: JSONObject): CartItem? {
            val id = item.nonBlankStringOrNull("uniqid") ?: return null
            return CartItem(id = id, quantity = item.intOrNull("quantity") ?: 1)
        }
    }
}
