package com.personalization.features.subscriptions

import com.personalization.api.managers.SubscriptionsApi
import com.personalization.api.models.subscriptions.BackInStockProperties
import com.personalization.api.models.subscriptions.SubscriptionStatus
import com.personalization.api.models.subscriptions.SubscriptionsUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireContact
import com.personalization.core.internal.requireNotBlank
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/**
 * [SubscriptionsApi] over the namespaces' path ([NamespaceCalls]). [contextChanged] tells popups v3 that
 * the subscriptions changed, once the server took the change, so their list is asked for again.
 */
@OptIn(BridgeApi::class)
internal class SubscriptionsApiImpl(
    private val calls: NamespaceCalls,
    private val contextChanged: (type: String, signature: String) -> Unit = { _, _ -> },
) : SubscriptionsApi {

    override suspend fun manage(request: SubscriptionsUpdateRequest) {
        val params = calls.checked(MANAGE) { manageParams(request) }
        val signature = params.toString()
        send(MANAGE, MANAGE_PATH, params)
        contextChanged(MANAGE_PATH, signature)
    }

    override suspend fun check(): SubscriptionStatus =
        calls.request(CHECK, NetworkMethod.GET(CHECK_PATH), read = ::readStatus)

    override suspend fun subscribeForPriceDrop(itemId: String, currentPrice: Double, email: String?, phone: String?) {
        val params = calls.checked(SUBSCRIBE_PRICE) {
            if (!(currentPrice.isFinite() && currentPrice > 0)) invalidArgument("currentPrice must be above 0")
            itemParams(itemId, email, phone).put("price", currentPrice)
        }
        send(SUBSCRIBE_PRICE, SUBSCRIBE_PRICE_PATH, params)
    }

    override suspend fun unsubscribeFromPriceDrop(itemIds: List<String>, email: String?, phone: String?) {
        val params = calls.checked(UNSUBSCRIBE_PRICE) { itemIdsParams(itemIds, email, phone) }
        send(UNSUBSCRIBE_PRICE, UNSUBSCRIBE_PRICE_PATH, params)
    }

    override suspend fun subscribeForBackInStock(itemId: String, properties: BackInStockProperties?, email: String?, phone: String?) {
        val params = calls.checked(SUBSCRIBE_STOCK) { itemParams(itemId, email, phone) }
        properties?.let {
            JSONObject()
                .putIfNotNull("fashion_size", it.fashionSize)
                .putIfNotNull("fashion_color", it.fashionColor)
                .putIfNotNull("barcode", it.barcode)
        }?.takeIf { it.length() > 0 }?.let { params.put("properties", it) }
        send(SUBSCRIBE_STOCK, SUBSCRIBE_STOCK_PATH, params)
    }

    override suspend fun unsubscribeFromBackInStock(itemIds: List<String>, email: String?, phone: String?) {
        val params = calls.checked(UNSUBSCRIBE_STOCK) { itemIdsParams(itemIds, email, phone) }
        send(UNSUBSCRIBE_STOCK, UNSUBSCRIBE_STOCK_PATH, params)
    }

    override fun manage(request: SubscriptionsUpdateRequest, listener: CompletionListener<Unit>) =
        calls.complete(listener) { manage(request) }

    override fun check(listener: CompletionListener<SubscriptionStatus>) = calls.complete(listener) { check() }

    override fun subscribeForPriceDrop(itemId: String, currentPrice: Double, email: String?, phone: String?, listener: CompletionListener<Unit>) =
        calls.complete(listener) { subscribeForPriceDrop(itemId, currentPrice, email, phone) }

    override fun unsubscribeFromPriceDrop(itemIds: List<String>, email: String?, phone: String?, listener: CompletionListener<Unit>) =
        calls.complete(listener) { unsubscribeFromPriceDrop(itemIds, email, phone) }

    override fun subscribeForBackInStock(
        itemId: String,
        properties: BackInStockProperties?,
        email: String?,
        phone: String?,
        listener: CompletionListener<Unit>,
    ) = calls.complete(listener) { subscribeForBackInStock(itemId, properties, email, phone) }

    override fun unsubscribeFromBackInStock(itemIds: List<String>, email: String?, phone: String?, listener: CompletionListener<Unit>) =
        calls.complete(listener) { unsubscribeFromBackInStock(itemIds, email, phone) }

    /** Sends [params] to [path]; any `2xx` but `{"status": "error"}` is done. */
    private suspend fun send(call: String, path: String, params: JSONObject) {
        calls.request(call, NetworkMethod.POST(path), params) { Unit }
    }

    internal companion object {
        const val MANAGE = "subscriptions.manage"
        const val CHECK = "subscriptions.check"
        const val SUBSCRIBE_PRICE = "subscriptions.subscribeForPriceDrop"
        const val UNSUBSCRIBE_PRICE = "subscriptions.unsubscribeFromPriceDrop"
        const val SUBSCRIBE_STOCK = "subscriptions.subscribeForBackInStock"
        const val UNSUBSCRIBE_STOCK = "subscriptions.unsubscribeFromBackInStock"
        const val MANAGE_PATH = "subscriptions/manage"
        const val CHECK_PATH = "subscriptions/check"
        const val SUBSCRIBE_PRICE_PATH = "subscriptions/subscribe_for_product_price"
        const val UNSUBSCRIBE_PRICE_PATH = "subscriptions/unsubscribe_from_product_price"
        const val SUBSCRIBE_STOCK_PATH = "subscriptions/subscribe_for_product_available"
        const val UNSUBSCRIBE_STOCK_PATH = "subscriptions/unsubscribe_from_product_available"

        /** The 24 flags: a channel's kinds of mailings, as the API names them. */
        private fun flags(request: SubscriptionsUpdateRequest): List<Pair<String, Boolean?>> = with(request) {
            listOf(
                "email_bulk" to emailBulk, "email_chain" to emailChain, "email_transactional" to emailTransactional,
                "sms_bulk" to smsBulk, "sms_chain" to smsChain, "sms_transactional" to smsTransactional,
                "web_push_bulk" to webPushBulk, "web_push_chain" to webPushChain, "web_push_transactional" to webPushTransactional,
                "mobile_push_bulk" to mobilePushBulk, "mobile_push_chain" to mobilePushChain,
                "mobile_push_transactional" to mobilePushTransactional,
                "telegram_bulk" to telegramBulk, "telegram_chain" to telegramChain, "telegram_transactional" to telegramTransactional,
                "whatsapp_bulk" to whatsappBulk, "whatsapp_chain" to whatsappChain, "whatsapp_transactional" to whatsappTransactional,
                "max_bulk" to maxBulk, "max_chain" to maxChain, "max_transactional" to maxTransactional,
                "wallet_bulk" to walletBulk, "wallet_chain" to walletChain, "wallet_transactional" to walletTransactional,
            )
        }

        /** The fields of `manage` after their checks: a contact is not empty, and at least one flag is given. */
        private fun manageParams(request: SubscriptionsUpdateRequest): JSONObject = with(request) {
            requireContact(email, "email")
            requireContact(phone, "phone")
            requireContact(externalId, "externalId")
            requireContact(loyaltyId, "loyaltyId")
            requireContact(telegramId, "telegramId")
            val flags = flags(request).filter { it.second != null }
            if (flags.isEmpty()) invalidArgument("The request changes no subscription")
            val params = JSONObject()
                .putIfNotNull("email", email)
                .putIfNotNull("phone", phone)
                .putIfNotNull("external_id", externalId)
                .putIfNotNull("loyalty_id", loyaltyId)
                .putIfNotNull("telegram_id", telegramId)
            flags.forEach { (name, value) -> params.put(name, value) }
            params
        }

        /** A product's fields: its id (`item_id`), which must not be empty, and the contacts. */
        private fun itemParams(itemId: String, email: String?, phone: String?): JSONObject {
            val params = JSONObject().put("item_id", requireNotBlank(itemId, "itemId"))
            return contacts(params, email, phone)
        }

        /** The products' ids as one string joined with commas (`item_ids`); an empty list is every product, an empty id fails. */
        private fun itemIdsParams(itemIds: List<String>, email: String?, phone: String?): JSONObject {
            if (itemIds.any { it.isBlank() }) invalidArgument("itemIds must not hold an empty id")
            return contacts(JSONObject().put("item_ids", itemIds.joinToString(",")), email, phone)
        }

        private fun contacts(params: JSONObject, email: String?, phone: String?): JSONObject {
            requireContact(email, "email")
            requireContact(phone, "phone")
            return params.putIfNotNull("email", email).putIfNotNull("phone", phone)
        }

        /** The subscriptions; a flag the answer leaves out is false, and an answer that is not an object is null (`decoding`). */
        fun readStatus(body: Any?): SubscriptionStatus? {
            if (body !is JSONObject) return null
            fun flag(name: String) = body.booleanOrNull(name) ?: false
            return SubscriptionStatus(
                exists = flag("exists"),
                emailBulk = flag("email_bulk"), emailChain = flag("email_chain"), emailTransactional = flag("email_transactional"),
                smsBulk = flag("sms_bulk"), smsChain = flag("sms_chain"), smsTransactional = flag("sms_transactional"),
                webPushBulk = flag("web_push_bulk"), webPushChain = flag("web_push_chain"),
                webPushTransactional = flag("web_push_transactional"),
                mobilePushBulk = flag("mobile_push_bulk"), mobilePushChain = flag("mobile_push_chain"),
                mobilePushTransactional = flag("mobile_push_transactional"),
                telegramBulk = flag("telegram_bulk"), telegramChain = flag("telegram_chain"),
                telegramTransactional = flag("telegram_transactional"),
                whatsappBulk = flag("whatsapp_bulk"), whatsappChain = flag("whatsapp_chain"),
                whatsappTransactional = flag("whatsapp_transactional"),
                maxBulk = flag("max_bulk"), maxChain = flag("max_chain"), maxTransactional = flag("max_transactional"),
                walletBulk = flag("wallet_bulk"), walletChain = flag("wallet_chain"), walletTransactional = flag("wallet_transactional"),
                hasWebPush = flag("web_push"),
                hasMobilePush = flag("mobile_push"),
                emailInvalid = flag("email_invalid"),
                emailBlacklisted = flag("email_blacklisted"),
                emailBounced = flag("email_bounced"),
                emailSuppressed = flag("email_suppressed"),
                emailConfirmed = flag("email_confirmed"),
            )
        }
    }
}
