package com.personalization.api.managers

import com.personalization.api.models.loyalty.LoyaltyMember
import com.personalization.api.models.loyalty.LoyaltyPromotion
import com.personalization.api.models.loyalty.LoyaltyStatus
import com.personalization.api.models.profile.Gender
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The loyalty program on the customer's side — the `loyalty` namespace: `sdk.loyalty.join(phone)`,
 * `getStatus(phone)`, `getPromotions()`. The SDK never sends the shop's secret: [join] and [getStatus]
 * answer only for a shop that allows the loyalty methods from the front end (otherwise `403`), and
 * [getPromotions] finds the customer by the device's `did`.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 *
 * Every method fails with [SdkException] — [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.Network], [SdkError.Timeout], [SdkError.Http]
 * (`404` without a loyalty program, `402` when it is off or not paid, `403` when it needs the secret, `400`
 * for a wrong argument or an unknown customer), [SdkError.Decoding] or [SdkError.Cancelled] (`release()`) —
 * and with [SdkError.InvalidArgument], before any request, for the arguments it names.
 */
@BridgeApi
interface LoyaltyApi {

    /**
     * Makes [phone] a member of the loyalty program (`POST /loyalty/members/join`); the server finds or
     * creates the customer by it. A phone already registered is a success with the same member, and the
     * server updates the name, gender and birthday.
     *
     * [SdkError.InvalidArgument] for an empty [phone], or a [birthday] that is not an existing date as
     * `YYYY-MM-DD`: a birthday the server cannot read erases the one it has.
     * [gender] goes as `m` or `f`, the only values the server takes.
     */
    suspend fun join(
        phone: String,
        firstName: String? = null,
        lastName: String? = null,
        gender: Gender? = null,
        birthday: String? = null,
    ): LoyaltyMember

    /**
     * Whether [phone] is a member, and on which level (`GET /loyalty/members/status`). The server keeps the
     * digits only. Someone who is not a member is a success with `isMember = false`.
     *
     * [SdkError.InvalidArgument] for an empty [phone].
     */
    suspend fun getStatus(phone: String): LoyaltyStatus

    /**
     * The active promotions that suit the customer and their cart (`GET /loyalty/promos/for-client`); none
     * is an empty list. [locationId] is the point of sale or city, [promoCodes] the customer's promo codes,
     * [chargeBonuses] whether bonuses are spent (the server takes `false` without it), [currentTime] the
     * local time at the point of sale as `HH:MM`, for promotions by the hour.
     *
     * [SdkError.InvalidArgument] for a [currentTime] that is not `HH:MM`, or an empty promo code.
     */
    suspend fun getPromotions(
        locationId: String? = null,
        promoCodes: List<String>? = null,
        chargeBonuses: Boolean? = null,
        currentTime: String? = null,
    ): List<LoyaltyPromotion>

    /** [join] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun join(
        phone: String,
        firstName: String?,
        lastName: String?,
        gender: Gender?,
        birthday: String?,
        listener: CompletionListener<LoyaltyMember>,
    )

    /** [getStatus] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getStatus(phone: String, listener: CompletionListener<LoyaltyStatus>)

    /** [getPromotions] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getPromotions(
        locationId: String?,
        promoCodes: List<String>?,
        chargeBonuses: Boolean?,
        currentTime: String?,
        listener: CompletionListener<List<LoyaltyPromotion>>,
    )
}
