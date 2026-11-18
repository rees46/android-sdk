package com.personalization.api.responses.reviews

/*
 * The models of the `reviews` namespace (`com.personalization.api.managers.ReviewsApi`): what the
 * questions and thanks of the polls say, a saved rating, and the reviews of products. A text the shop
 * left empty is null.
 */

/**
 * A rating saved by `createNps`, `createCsat` or `createCes`: [id] of the review the server saved, or of the
 * earlier one it updated. A 64-bit id: the server's ids of reviews are `bigint`.
 */
data class PollReview(val id: Long)

/**
 * An NPS category — what the customer rates (`checkout`, `delivery`) — with the question to ask after the
 * rating and the thanks to show once it is sent: `detractor…` for 0–6, `passive…` for 7–8, `promoter…` for 9–10.
 */
data class NpsCategory(
    val code: String,
    val name: String,
    val detractorQuestion: String? = null,
    val passiveQuestion: String? = null,
    val promoterQuestion: String? = null,
    val detractorSuccess: String? = null,
    val passiveSuccess: String? = null,
    val promoterSuccess: String? = null,
    /** The label of the button shown after the rating is sent. */
    val buttonLabel: String? = null,
)

/**
 * A CSAT campaign — the one interaction the customer rates — with its questions and thanks:
 * `dissatisfied…` for 1–2, `neutral…` for 3, `satisfied…` for 4–5.
 */
data class CsatCampaign(
    val code: String,
    val name: String,
    val dissatisfiedQuestion: String? = null,
    val neutralQuestion: String? = null,
    val satisfiedQuestion: String? = null,
    val dissatisfiedSuccess: String? = null,
    val neutralSuccess: String? = null,
    val satisfiedSuccess: String? = null,
    /** The label of the button shown after the rating is sent. */
    val buttonLabel: String? = null,
)

/**
 * A CES campaign — how much effort a task took the customer — with its questions and thanks:
 * `difficult…` for 1–3, `neutral…` for 4–5, `easy…` for 6–7.
 */
data class CesCampaign(
    val code: String,
    val name: String,
    val difficultQuestion: String? = null,
    val neutralQuestion: String? = null,
    val easyQuestion: String? = null,
    val difficultSuccess: String? = null,
    val neutralSuccess: String? = null,
    val easySuccess: String? = null,
    /** The label of the button shown after the rating is sent. */
    val buttonLabel: String? = null,
)

/** A channel a rating comes from (`android_app`, `email`), common to NPS, CSAT and CES. */
data class PollChannel(val code: String, val name: String)

/** The published reviews of a product and its rating over all of them. */
data class ProductReviewsResponse(
    val summary: ProductRatingSummary,
    /** The page of reviews `limit` and `offset` asked for, the newest first. */
    val reviews: List<ProductReview>,
)

/** A product's rating over all its published reviews, whatever page of them was asked for. */
data class ProductRatingSummary(
    /** How many published reviews the product has. */
    val count: Int,
    /** The average rating, rounded to 0.1; null without reviews. */
    val averageRating: Double? = null,
    /** How many reviews gave each rating, 1 to 5. */
    val ratings: Map<Int, Int> = emptyMap(),
)

/** One published review of a product; [id] is 64-bit, as the server's ids of reviews. */
data class ProductReview(
    val id: Long,
    /** 1 to 5. */
    val rating: Int,
    /** Null when the customer gave only a rating. */
    val comment: String? = null,
    /** The author's name in the CDP; null when it is not known. */
    val author: String? = null,
    /** The date and time of the review, ISO 8601, as the API sends it. */
    val createdAt: String,
)

/** A review the device's customer wrote of a product of an order, in any state of moderation; [id] is 64-bit. */
data class ClientReview(
    val id: Long,
    val itemId: String,
    val orderId: String,
    /** 1 to 5. */
    val rating: Int,
    val comment: String? = null,
    /** Null for a state this release does not know. */
    val state: ReviewState? = null,
    /** The date and time of the review, ISO 8601, as the API sends it. */
    val createdAt: String,
)

/** Where a review is in moderation; [value] is its name in the API. */
enum class ReviewState(val value: String) {
    MODERATION("moderation"),
    PUBLISHED("published"),
    REJECTED("rejected"),
}
