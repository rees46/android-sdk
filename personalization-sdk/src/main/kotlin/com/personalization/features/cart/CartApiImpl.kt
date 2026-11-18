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
import com.personalization.core.internal.putCustomer
import com.personalization.core.internal.secretRequest
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [CartApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class CartApiImpl(private val calls: NamespaceCalls) : CartApi {

    override suspend fun get(): List<CartItem> =
        calls.request(GET, NetworkMethod.GET(PATH), read = ::readCart)

    override fun get(listener: CompletionListener<List<CartItem>>) = calls.complete(listener) { get() }

    override suspend fun clear(shopSecret: String, email: String?, phone: String?, loyaltyId: String?, externalId: String?, telegramId: String?) {
        val params = calls.checked(CLEAR) {
            secretRequest(shopSecret).putCustomer(
                Triple("email", "email", email),
                Triple("phone", "phone", phone),
                Triple("loyaltyId", "loyalty_id", loyaltyId),
                Triple("externalId", "external_id", externalId),
                Triple("telegramId", "telegram_id", telegramId),
            )
        }
        calls.request(CLEAR, NetworkMethod.GET(CLEAR_PATH), params, secret = true) { body ->
            Unit.takeIf { (body as? JSONObject)?.optString("status") == "success" }
        }
    }

    override fun clear(
        shopSecret: String,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        telegramId: String?,
        listener: CompletionListener<Unit>,
    ) = calls.complete(listener) { clear(shopSecret, email, phone, loyaltyId, externalId, telegramId) }

    internal companion object {
        const val GET = "cart.get"
        const val PATH = "products/cart"
        const val CLEAR = "cart.clear"
        const val CLEAR_PATH = "products/cart/clear"

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
