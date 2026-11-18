package com.personalization.features.profile

import com.google.gson.Gson
import com.personalization.api.managers.ProfileApi
import com.personalization.api.models.products.Product
import com.personalization.api.models.profile.Gender
import com.personalization.api.models.profile.Profile
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.api.responses.orders.Order
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.isCalendarDate
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.putCustomer
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requireBirthday
import com.personalization.core.internal.requireContact
import com.personalization.core.internal.secretRequest
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.valueOfWire
import com.personalization.core.internal.valueOrNull
import com.personalization.features.products.CatalogJson
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONArray
import org.json.JSONObject

/**
 * [ProfileApi] over the namespaces' path ([NamespaceCalls]). [contextChanged] tells popups v3 that the
 * profile changed, once the server took it, so their list is asked for again.
 */
@OptIn(BridgeApi::class)
internal class ProfileApiImpl(
    private val calls: NamespaceCalls,
    private val contextChanged: (type: String, signature: String) -> Unit = { _, _ -> },
) : ProfileApi {

    override suspend fun get(): Profile = calls.request(GET, NetworkMethod.GET(GET_PATH), read = ::readProfile)

    override suspend fun set(request: ProfileUpdateRequest) {
        val params = calls.checked(SET) { setParams(request) }
        val signature = params.toString()
        calls.request(SET, NetworkMethod.POST(SET_PATH), params) { Unit }
        contextChanged(SET_PATH, signature)
    }

    override suspend fun getLastOrder(): List<Product> =
        calls.request(GET_LAST_ORDER, NetworkMethod.GET(LAST_ORDER_PATH), read = ::readLastOrder)

    override suspend fun getOrders(
        shopSecret: String,
        did: String?,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String?,
    ): List<Order> {
        val params = calls.checked(GET_ORDERS) {
            secretRequest(shopSecret).putCustomer(
                Triple("did", "did", did),
                Triple("email", "email", email),
                Triple("phone", "phone", phone),
                Triple("loyaltyId", "loyalty_id", loyaltyId),
                Triple("externalId", "external_id", externalId),
            ).also { request ->
                dateFrom?.let { if (!isCalendarDate(it)) invalidArgument("dateFrom must be a date YYYY-MM-DD") }
                request.putIfNotNull("date_from", dateFrom)
            }
        }
        return calls.request(GET_ORDERS, NetworkMethod.GET(ORDERS_PATH), params, secret = true, read = ::readOrders)
    }

    override fun get(listener: CompletionListener<Profile>) = calls.complete(listener) { get() }

    override fun set(request: ProfileUpdateRequest, listener: CompletionListener<Unit>) = calls.complete(listener) { set(request) }

    override fun getLastOrder(listener: CompletionListener<List<Product>>) = calls.complete(listener) { getLastOrder() }

    override fun getOrders(
        shopSecret: String,
        did: String?,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String?,
        listener: CompletionListener<List<Order>>,
    ) = calls.complete(listener) { getOrders(shopSecret, did, email, phone, loyaltyId, externalId, dateFrom) }

    internal companion object {
        const val GET = "profile.get"
        const val SET = "profile.set"
        const val GET_LAST_ORDER = "profile.getLastOrder"
        const val GET_PATH = "profile"
        const val SET_PATH = "profile/set"
        const val LAST_ORDER_PATH = "orders/last_for_user"
        const val GET_ORDERS = "profile.getOrders"
        const val ORDERS_PATH = "orders/by_user"

        /**
         * The orders of a `by_user` answer, `{"status": "success", "data": {"orders": [...]}}`, read into the
         * released [Order] as the root method reads them; null (`decoding`) without the list.
         */
        fun readOrders(body: Any?): List<Order>? {
            val orders = (body as? JSONObject)?.optJSONObject("data")?.optJSONArray("orders") ?: return null
            return Gson().fromJson(orders.toString(), Array<Order>::class.java)?.toList()
        }

        private val CUSTOM_KEY = Regex("[a-z0-9_]+")

        /** The fields of the request itself and those the SDK adds: a custom property may not take their names. */
        private val RESERVED_KEYS = setOf(
            "email", "phone", "id", "external_id", "loyalty_id", "telegram_id", "fb_id", "vk_id", "first_name",
            "last_name", "gender", "birthday", "age", "location", "kids", "auto",
            "shop_id", "did", "sid", "seance", "segment", "stream",
        )

        /**
         * The request's fields after their checks ([ProfileUpdateRequest]). An empty [ProfileUpdateRequest.externalId],
         * [ProfileUpdateRequest.telegramId] or [ProfileUpdateRequest.birthday] goes as `""`: the server erases
         * them so. It does not erase `email`, `phone` or `loyalty_id` so: an empty one is refused. Any other
         * string goes as it is.
         */
        fun setParams(request: ProfileUpdateRequest): JSONObject = with(request) {
            requireContact(email, "email")
            requireContact(phone, "phone")
            requireContact(loyaltyId, "loyaltyId")
            birthday?.takeIf { it.isNotEmpty() }?.let { requireBirthday(it, "birthday") }
            requireAtLeast(age, 0, "age")
            kids?.forEach { kid -> kid.birthday?.let { requireBirthday(it, "a kid's birthday") } }
            val params = JSONObject()
                .putIfNotNull("email", email)
                .putIfNotNull("phone", phone)
                // Both servers look the customer up by `id` and keep `external_id` in the profile.
                .putIfNotNull("id", externalId)
                .putIfNotNull("external_id", externalId)
                .putIfNotNull("loyalty_id", loyaltyId)
                .putIfNotNull("telegram_id", telegramId)
                .putIfNotNull("fb_id", fbId)
                .putIfNotNull("vk_id", vkId)
                .putIfNotNull("first_name", firstName)
                .putIfNotNull("last_name", lastName)
                .putIfNotNull("gender", gender?.value)
                .putIfNotNull("birthday", birthday)
                .putIfNotNull("age", age)
                .putIfNotNull("location", location)
                .putIfNotNull("kids", kids?.takeIf { it.isNotEmpty() }?.let { list ->
                    JSONArray(list.map { JSONObject().putIfNotNull("gender", it.gender?.value).putIfNotNull("birthday", it.birthday) })
                })
                .putIfNotNull("auto", cars?.takeIf { it.isNotEmpty() }?.let { list ->
                    JSONArray(list.map { JSONObject().putIfNotNull("brand", it.brand).putIfNotNull("model", it.model).putIfNotNull("vds", it.vds) })
                })
            customProperties?.forEach { (key, value) ->
                if (!CUSTOM_KEY.matches(key)) invalidArgument("customProperties: the key $key is not of a-z, 0-9 and _")
                if (key in RESERVED_KEYS) invalidArgument("customProperties: the key $key is a field of the request")
                params.put(key, customValue(value) ?: invalidArgument("customProperties: the value of $key is of a kind the API does not take"))
            }
            if (params.length() == 0) invalidArgument("The request sets nothing")
            params
        }

        /** [value] as JSON: a string, a finite number, a boolean, null, or a list or a map of these; null for anything else. */
        private fun customValue(value: Any?): Any? = when (value) {
            null -> JSONObject.NULL
            is String, is Boolean -> value
            is Int, is Long, is Short, is Byte -> value
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is List<*> -> JSONArray().apply { value.forEach { put(customValue(it) ?: return null) } }
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, item) -> put(key as? String ?: return null, customValue(item) ?: return null) }
            }
            else -> null
        }

        /** The profile; an answer that is not an object is null (`decoding`). */
        fun readProfile(body: Any?): Profile? {
            if (body !is JSONObject) return null
            return Profile(
                id = body.nonBlankStringOrNull("id"),
                hasEmail = body.booleanOrNull("has_email"),
                emailRegisteredAt = body.stringOrNull("email_registered_at"),
                gender = gender(body.stringOrNull("gender")),
                computedGender = gender(body.stringOrNull("computed_gender")),
                boughtSomething = body.booleanOrNull("bought_something"),
                customProperties = when (val properties = body.valueOrNull("custom_properties")) {
                    is JSONObject -> properties.keys().asSequence().associateWith { plain(properties.opt(it)) }
                    // The server sends an empty object as an empty array.
                    is JSONArray -> if (properties.length() == 0) emptyMap() else null
                    else -> null
                },
            )
        }

        /** The products of the last order: an array of products; an answer without a body is no order. */
        fun readLastOrder(body: Any?): List<Product>? = when (body) {
            null -> emptyList()
            is JSONArray -> body.mapObjects { CatalogJson.product(it) }
            else -> null
        }

        private fun gender(value: String?): Gender? = valueOfWire(value, Gender.entries) { it.value }

        /** A JSON value as plain Kotlin: maps, lists, strings, numbers, booleans and null. */
        private fun plain(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWith { plain(value.opt(it)) }
            is JSONArray -> List(value.length()) { plain(value.opt(it)) }
            else -> value
        }
    }
}
