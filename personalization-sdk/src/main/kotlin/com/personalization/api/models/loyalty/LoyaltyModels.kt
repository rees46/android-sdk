package com.personalization.api.models.loyalty

/*
 * The loyalty program's models of `sdk.loyalty`, flat: the answers' `{status, payload}` envelope is gone.
 * Their own package: `LoyaltyLevel` of `com.personalization.api.responses.loyalty` is the old answer's.
 */

/** A member of the loyalty program (`loyalty.join`). */
data class LoyaltyMember(
    /** The member's id: the phone, digits only (`7XXXXXXXXXX`). [com.personalization.api.managers.LoyaltyApi.getStatus] takes it. */
    val identifier: String,
)

/** Whether the customer is a member, and on which level (`loyalty.getStatus`). */
data class LoyaltyStatus(
    val isMember: Boolean,
    /** Null on the base level, and for someone who is not a member. */
    val level: LoyaltyLevel?,
)

/** A level of the loyalty program above the base one. */
data class LoyaltyLevel(
    val code: String,
    val name: String?,
    /** `YYYY-MM-DD`: the level is counted anew after it. */
    val expirationDate: String?,
)

/** An active promotion that suits the customer and their cart (`loyalty.getPromotions`). */
data class LoyaltyPromotion(
    /** A 64-bit id: the server's ids of promotions are `bigint`. */
    val id: Long,
    val name: String,
    val description: String?,
    /** `YYYY-MM-DD`. */
    val startDate: String?,
    /** `YYYY-MM-DD`. */
    val finishDate: String?,
    /** Where the promotion works; null — everywhere. */
    val stream: String?,
    /** The badge's text. */
    val badgeLabel: String?,
    /** The badge's colour, `#RRGGBB`. */
    val badgeColor: String?,
    /** Null for a reward the SDK does not know yet. */
    val rewardType: LoyaltyRewardType?,
)

/** What a promotion gives. */
enum class LoyaltyRewardType(val value: String) {
    DISCOUNT("discount"),
    BONUSES_EARN("bonuses_earn"),
    BONUSES_USAGE("bonuses_usage"),
}
