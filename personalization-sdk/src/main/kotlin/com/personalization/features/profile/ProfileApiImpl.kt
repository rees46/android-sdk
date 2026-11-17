package com.personalization.features.profile

import com.personalization.api.managers.ProfileApi
import com.personalization.api.models.products.Product
import com.personalization.api.models.profile.Gender
import com.personalization.api.models.profile.Profile
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requireContact
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

    override fun get(listener: CompletionListener<Profile>) = calls.complete(listener) { get() }

    override fun set(request: ProfileUpdateRequest, listener: CompletionListener<Unit>) = calls.complete(listener) { set(request) }

    override fun getLastOrder(listener: CompletionListener<List<Product>>) = calls.complete(listener) { getLastOrder() }

    internal companion object {
        const val GET = "profile.get"
        const val SET = "profile.set"
        const val GET_LAST_ORDER = "profile.getLastOrder"
        const val GET_PATH = "profile"
        const val SET_PATH = "profile/set"
        const val LAST_ORDER_PATH = "orders/last_for_user"

        private val CUSTOM_KEY = Regex("[a-z0-9_]+")
        private val DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

        /** The fields of the request itself and those the SDK adds: a custom property may not take their names. */
        private val RESERVED_KEYS = setOf(
            "email", "phone", "id", "external_id", "loyalty_id", "telegram_id", "fb_id", "vk_id", "first_name",
            "last_name", "gender", "birthday", "age", "location", "kids", "auto",
            "shop_id", "did", "sid", "seance", "segment", "stream",
        )

        /** The request's fields after their checks ([ProfileUpdateRequest]). */
        fun setParams(request: ProfileUpdateRequest): JSONObject = with(request) {
            requireContact(email, "email")
            requireContact(phone, "phone")
            requireContact(externalId, "externalId")
            requireContact(loyaltyId, "loyaltyId")
            requireContact(telegramId, "telegramId")
            birthday?.let { requireDate(it, "birthday") }
            requireAtLeast(age, 0, "age")
            kids?.forEach { kid -> kid.birthday?.let { requireDate(it, "a kid's birthday") } }
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

        /** Fails with `invalidArgument` unless [value] is a real date `YYYY-MM-DD`. */
        private fun requireDate(value: String, name: String) {
            val (year, month, day) = DATE.matchEntire(value)?.destructured?.toList()?.map { it.toInt() }
                ?: invalidArgument("$name must be a date YYYY-MM-DD")
            val days = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                else -> 0
            }
            if (day !in 1..days) invalidArgument("$name must be a date YYYY-MM-DD")
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
