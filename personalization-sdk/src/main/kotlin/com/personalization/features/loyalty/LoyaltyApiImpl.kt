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
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.isCalendarDate
import com.personalization.core.internal.longOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.valueOfWire
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONArray
import org.json.JSONObject

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
        val params = calls.checked(JOIN) {
            if (phone.isBlank()) invalidArgument("phone is empty")
            // A birthday the server cannot read erases the one it has: only a real date goes.
            if (birthday != null && !isCalendarDate(birthday)) invalidArgument("birthday must be an existing date YYYY-MM-DD")
            JSONObject().put(PHONE, phone)
                .putIfNotNull(FIRST_NAME, firstName)
                .putIfNotNull(LAST_NAME, lastName)
                .putIfNotNull(GENDER, gender?.value)
                .putIfNotNull(BIRTHDAY, birthday)
        }
        return calls.request(JOIN, NetworkMethod.POST(JOIN_PATH), params, read = ::readMember)
    }

    override suspend fun getStatus(phone: String): LoyaltyStatus {
        val params = calls.checked(GET_STATUS) {
            if (phone.isBlank()) invalidArgument("phone is empty")
            JSONObject().put(IDENTIFIER, phone)
        }
        return calls.request(GET_STATUS, NetworkMethod.GET(STATUS_PATH), params, read = ::readStatus)
    }

    override suspend fun getPromotions(
        locationId: String?,
        promoCodes: List<String>?,
        chargeBonuses: Boolean?,
        currentTime: String?,
    ): List<LoyaltyPromotion> {
        val params = calls.checked(GET_PROMOTIONS) {
            if (currentTime != null && !TIME.matches(currentTime)) invalidArgument("currentTime is not HH:MM")
            if (promoCodes?.any { it.isBlank() } == true) invalidArgument("promoCodes has an empty code")
            val params = JSONObject()
                .putIfNotNull(LOCATION_ID, locationId)
                .putIfNotNull(CURRENT_TIME, currentTime)
            // Only as a Rails array, `promo_codes[]=A&promo_codes[]=B`: the server drops a string with commas.
            if (!promoCodes.isNullOrEmpty()) params.put(PROMO_CODES, JSONArray(promoCodes))
            params.putIfNotNull(CHARGE_BONUSES, chargeBonuses)
        }
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

        private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")

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
            val id = promotion.longOrNull("id") ?: return null
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
