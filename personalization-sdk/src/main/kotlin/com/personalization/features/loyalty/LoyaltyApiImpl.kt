package com.personalization.features.loyalty

import com.personalization.api.managers.LoyaltyApi
import com.personalization.api.models.loyalty.LoyaltyLevel
import com.personalization.api.models.loyalty.LoyaltyMember
import com.personalization.api.models.loyalty.LoyaltyPromotion
import com.personalization.api.models.loyalty.LoyaltyRewardType
import com.personalization.api.models.loyalty.LoyaltyStatus
import com.personalization.api.models.profile.Gender
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.valueOfWire
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * [LoyaltyApi] over the namespaces' path ([NamespaceCalls]). Whether `members/join` and `members/status`
 * may be asked without the shop's secret is open (В17, В18 of the contract's questions): the server
 * answers them only for a shop that allows the loyalty methods from the front end, `403` otherwise.
 */
@OptIn(BridgeApi::class)
internal class LoyaltyApiImpl(private val calls: NamespaceCalls) : LoyaltyApi {

    override suspend fun join(
        phone: String,
        firstName: String?,
        lastName: String?,
        gender: Gender?,
        birthday: String?,
    ): LoyaltyMember {
        val problem = when {
            phone.isBlank() -> "phone is empty"
            birthday != null && !isDate(birthday) -> "birthday is not a date as YYYY-MM-DD"
            else -> null
        }
        calls.checkArguments(JOIN, problem)
        val params = JSONObject().put(PHONE, phone)
            .putIfGiven(FIRST_NAME, firstName)
            .putIfGiven(LAST_NAME, lastName)
            .putIfGiven(GENDER, gender?.value)
            .putIfGiven(BIRTHDAY, birthday)
        return calls.request(JOIN, NetworkMethod.POST(JOIN_PATH), params, read = ::readMember)
    }

    override suspend fun getStatus(phone: String): LoyaltyStatus {
        calls.checkArguments(GET_STATUS, if (phone.isBlank()) "phone is empty" else null)
        return calls.request(GET_STATUS, NetworkMethod.GET(STATUS_PATH), JSONObject().put(IDENTIFIER, phone), read = ::readStatus)
    }

    override suspend fun getPromotions(
        locationId: String?,
        promoCodes: List<String>?,
        chargeBonuses: Boolean?,
        currentTime: String?,
    ): List<LoyaltyPromotion> {
        val problem = when {
            currentTime != null && !TIME.matches(currentTime) -> "currentTime is not HH:MM"
            promoCodes?.any { it.isBlank() } == true -> "promoCodes has an empty code"
            else -> null
        }
        calls.checkArguments(GET_PROMOTIONS, problem)
        val params = JSONObject()
            .putIfGiven(LOCATION_ID, locationId)
            .putIfGiven(CURRENT_TIME, currentTime)
        // Only as a Rails array, `promo_codes[]=A&promo_codes[]=B`: the server drops a string with commas.
        if (!promoCodes.isNullOrEmpty()) params.put(PROMO_CODES, JSONArray(promoCodes))
        chargeBonuses?.let { params.put(CHARGE_BONUSES, it) }
        return calls.request(GET_PROMOTIONS, NetworkMethod.GET(PROMOTIONS_PATH), params, read = ::readPromotions)
    }

    override fun join(
        phone: String,
        firstName: String?,
        lastName: String?,
        gender: Gender?,
        birthday: String?,
        listener: CompletionListener<LoyaltyMember>,
    ) = calls.complete(listener) { join(phone, firstName, lastName, gender, birthday) }

    override fun getStatus(phone: String, listener: CompletionListener<LoyaltyStatus>) =
        calls.complete(listener) { getStatus(phone) }

    override fun getPromotions(
        locationId: String?,
        promoCodes: List<String>?,
        chargeBonuses: Boolean?,
        currentTime: String?,
        listener: CompletionListener<List<LoyaltyPromotion>>,
    ) = calls.complete(listener) { getPromotions(locationId, promoCodes, chargeBonuses, currentTime) }

    internal companion object {
        const val JOIN = "loyalty.join"
        const val GET_STATUS = "loyalty.getStatus"
        const val GET_PROMOTIONS = "loyalty.getPromotions"
        const val JOIN_PATH = "loyalty/members/join"
        const val STATUS_PATH = "loyalty/members/status"
        const val PROMOTIONS_PATH = "loyalty/promos/for-client"

        private const val PHONE = "phone"
        private const val FIRST_NAME = "first_name"
        private const val LAST_NAME = "last_name"
        private const val GENDER = "gender"
        private const val BIRTHDAY = "birthday"
        private const val IDENTIFIER = "identifier"
        private const val LOCATION_ID = "location_id"
        private const val PROMO_CODES = "promo_codes[]"
        private const val CHARGE_BONUSES = "charge_bonuses"
        private const val CURRENT_TIME = "current_time"
        private const val PAYLOAD = "payload"

        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")

        /** A real date of the calendar as `YYYY-MM-DD`: `2026-02-30` is not one. */
        fun isDate(value: String): Boolean {
            if (!DATE.matches(value)) return false
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            return format.parse(value, position) != null && position.index == value.length
        }

        private fun JSONObject.putIfGiven(key: String, value: String?): JSONObject = if (value == null) this else put(key, value)

        /** `payload.identifier`; null (`decoding`) without one. */
        fun readMember(body: Any?): LoyaltyMember? {
            val identifier = (body as? JSONObject)?.objectOrNull(PAYLOAD)?.nonBlankStringOrNull(IDENTIFIER) ?: return null
            return LoyaltyMember(identifier)
        }

        /** `payload.member` and `payload.level`; a level whose `code` is null is the base one: no level. */
        fun readStatus(body: Any?): LoyaltyStatus? {
            val payload = (body as? JSONObject)?.objectOrNull(PAYLOAD) ?: return null
            val member = payload.booleanOrNull("member") ?: return null
            val level = payload.objectOrNull("level")?.let { level ->
                level.nonBlankStringOrNull("code")?.let { code ->
                    LoyaltyLevel(code = code, name = level.nonBlankStringOrNull("name"), expirationDate = level.nonBlankStringOrNull("expiration_date"))
                }
            }
            return LoyaltyStatus(isMember = member, level = level)
        }

        /** `payload.promotions`; a promotion without a whole `id` is left out. */
        fun readPromotions(body: Any?): List<LoyaltyPromotion>? {
            val promotions = (body as? JSONObject)?.objectOrNull(PAYLOAD)?.arrayOrNull("promotions") ?: return null
            return promotions.mapObjects(::readPromotion)
        }

        private fun readPromotion(promotion: JSONObject): LoyaltyPromotion? {
            val id = promotion.intOrNull("id") ?: return null
            return LoyaltyPromotion(
                id = id,
                name = promotion.stringOrNull("name").orEmpty(),
                description = promotion.nonBlankStringOrNull("description"),
                startDate = promotion.nonBlankStringOrNull("start_date"),
                finishDate = promotion.nonBlankStringOrNull("finish_date"),
                stream = promotion.nonBlankStringOrNull("stream"),
                badgeLabel = promotion.nonBlankStringOrNull("badge_label"),
                badgeColor = promotion.nonBlankStringOrNull("badge_color"),
                rewardType = valueOfWire(promotion.stringOrNull("reward_type"), LoyaltyRewardType.entries) { it.value },
            )
        }
    }
}
