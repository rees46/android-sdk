package com.personalization.features.reviews

import com.personalization.api.managers.ReviewsApi
import com.personalization.api.responses.reviews.CesCampaign
import com.personalization.api.responses.reviews.ClientReview
import com.personalization.api.responses.reviews.CsatCampaign
import com.personalization.api.responses.reviews.NpsCategory
import com.personalization.api.responses.reviews.PollChannel
import com.personalization.api.responses.reviews.PollReview
import com.personalization.api.responses.reviews.ProductRatingSummary
import com.personalization.api.responses.reviews.ProductReview
import com.personalization.api.responses.reviews.ProductReviewsResponse
import com.personalization.api.responses.reviews.ReviewState
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.longOrNull
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireNotBlank
import com.personalization.core.internal.valueOfWire
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/**
 * [ReviewsApi] over the namespaces' path ([NamespaceCalls]). The polls' paths are under `api/v3/`, which
 * the core sends to the host of API v3 (`Rees46Config.appDomain`); the reviews of products go to the API's.
 */
@OptIn(BridgeApi::class)
internal class ReviewsApiImpl(private val calls: NamespaceCalls) : ReviewsApi {

    override suspend fun createNps(
        rate: Int, channel: String, category: String, orderId: String?, comment: String?, label: String?,
    ): PollReview = create(CREATE_NPS, NPS_REVIEWS, rate, NPS_RATES, channel, CATEGORY, category, orderId, comment, label)

    override suspend fun createCsat(
        rate: Int, channel: String, campaign: String, orderId: String?, comment: String?, label: String?,
    ): PollReview = create(CREATE_CSAT, CSAT_REVIEWS, rate, CSAT_RATES, channel, CAMPAIGN, campaign, orderId, comment, label)

    override suspend fun createCes(
        rate: Int, channel: String, campaign: String, orderId: String?, comment: String?, label: String?,
    ): PollReview = create(CREATE_CES, CES_REVIEWS, rate, CES_RATES, channel, CAMPAIGN, campaign, orderId, comment, label)

    override suspend fun getNpsCategories(): List<NpsCategory> =
        calls.request(GET_NPS_CATEGORIES, NetworkMethod.GET(NPS_CATEGORIES), read = listAt("categories", ::readNpsCategory))

    override suspend fun getNpsChannels(): List<PollChannel> =
        calls.request(GET_NPS_CHANNELS, NetworkMethod.GET(NPS_CHANNELS), read = listAt("channels", ::readChannel))

    override suspend fun getCsatCampaigns(): List<CsatCampaign> =
        calls.request(GET_CSAT_CAMPAIGNS, NetworkMethod.GET(CSAT_CAMPAIGNS), read = listAt("campaigns", ::readCsatCampaign))

    override suspend fun getCesCampaigns(): List<CesCampaign> =
        calls.request(GET_CES_CAMPAIGNS, NetworkMethod.GET(CES_CAMPAIGNS), read = listAt("campaigns", ::readCesCampaign))

    override suspend fun getProductReviews(itemId: String, limit: Int?, offset: Int?): ProductReviewsResponse {
        val params = calls.checked(GET_PRODUCT_REVIEWS) {
            requireNotBlank(itemId, "itemId")
            if (limit != null && limit !in LIMITS) invalidArgument("limit must be from ${LIMITS.first} to ${LIMITS.last}")
            if (offset != null && offset < 0) invalidArgument("offset must not be negative")
            JSONObject().put("product", itemId).putIfNotNull("limit", limit).putIfNotNull("offset", offset)
        }
        return calls.request(GET_PRODUCT_REVIEWS, NetworkMethod.GET(PRODUCT_REVIEWS), params, read = ::readProductReviews)
    }

    override suspend fun getClientReviews(orderId: String): List<ClientReview> {
        val params = calls.checked(GET_CLIENT_REVIEWS) { JSONObject().put("order", requireNotBlank(orderId, "orderId")) }
        return calls.request(GET_CLIENT_REVIEWS, NetworkMethod.GET(CLIENT_REVIEWS), params, read = listAt("reviews", ::readClientReview))
    }

    override fun createNps(
        rate: Int, channel: String, category: String, orderId: String?, comment: String?, label: String?,
        listener: CompletionListener<PollReview>,
    ) = calls.complete(listener) { createNps(rate, channel, category, orderId, comment, label) }

    override fun createCsat(
        rate: Int, channel: String, campaign: String, orderId: String?, comment: String?, label: String?,
        listener: CompletionListener<PollReview>,
    ) = calls.complete(listener) { createCsat(rate, channel, campaign, orderId, comment, label) }

    override fun createCes(
        rate: Int, channel: String, campaign: String, orderId: String?, comment: String?, label: String?,
        listener: CompletionListener<PollReview>,
    ) = calls.complete(listener) { createCes(rate, channel, campaign, orderId, comment, label) }

    override fun getNpsCategories(listener: CompletionListener<List<NpsCategory>>) = calls.complete(listener) { getNpsCategories() }

    override fun getNpsChannels(listener: CompletionListener<List<PollChannel>>) = calls.complete(listener) { getNpsChannels() }

    override fun getCsatCampaigns(listener: CompletionListener<List<CsatCampaign>>) = calls.complete(listener) { getCsatCampaigns() }

    override fun getCesCampaigns(listener: CompletionListener<List<CesCampaign>>) = calls.complete(listener) { getCesCampaigns() }

    override fun getProductReviews(itemId: String, limit: Int?, offset: Int?, listener: CompletionListener<ProductReviewsResponse>) =
        calls.complete(listener) { getProductReviews(itemId, limit, offset) }

    override fun getClientReviews(orderId: String, listener: CompletionListener<List<ClientReview>>) =
        calls.complete(listener) { getClientReviews(orderId) }

    /**
     * One rating: [rate] on the scale [rates], what is rated as [subject] (`category` or `campaign`) with the
     * code [code]. An empty or blank [orderId], [comment] or [label] is not sent; an empty channel or code is
     * refused.
     */
    private suspend fun create(
        call: String, path: String, rate: Int, rates: IntRange, channel: String, subject: String, code: String,
        orderId: String?, comment: String?, label: String?,
    ): PollReview {
        val params = calls.checked(call) {
            if (rate !in rates) invalidArgument("rate must be from ${rates.first} to ${rates.last}")
            JSONObject().put("rate", rate).put("channel", requireNotBlank(channel, "channel")).put(subject, requireNotBlank(code, subject))
        }
        listOf("order_id" to orderId, "comment" to comment, "label" to label).forEach { (key, value) ->
            if (!value.isNullOrBlank()) params.put(key, value)
        }
        return calls.request(call, NetworkMethod.POST(path), params, read = ::readPollReview)
    }

    internal companion object {
        const val CREATE_NPS = "reviews.createNps"
        const val CREATE_CSAT = "reviews.createCsat"
        const val CREATE_CES = "reviews.createCes"
        const val GET_NPS_CATEGORIES = "reviews.getNpsCategories"
        const val GET_NPS_CHANNELS = "reviews.getNpsChannels"
        const val GET_CSAT_CAMPAIGNS = "reviews.getCsatCampaigns"
        const val GET_CES_CAMPAIGNS = "reviews.getCesCampaigns"
        const val GET_PRODUCT_REVIEWS = "reviews.getProductReviews"
        const val GET_CLIENT_REVIEWS = "reviews.getClientReviews"

        const val NPS_REVIEWS = "api/v3/polls/nps/reviews"
        const val CSAT_REVIEWS = "api/v3/polls/csat/reviews"
        const val CES_REVIEWS = "api/v3/polls/ces/reviews"
        const val NPS_CATEGORIES = "api/v3/polls/nps/categories"
        const val NPS_CHANNELS = "api/v3/polls/nps/channels"
        const val CSAT_CAMPAIGNS = "api/v3/polls/csat/campaigns"
        const val CES_CAMPAIGNS = "api/v3/polls/ces/campaigns"
        const val PRODUCT_REVIEWS = "reviews/product"
        const val CLIENT_REVIEWS = "reviews/client"

        private const val CATEGORY = "category"
        private const val CAMPAIGN = "campaign"
        private val NPS_RATES = 0..10
        private val CSAT_RATES = 1..5
        private val CES_RATES = 1..7
        private val LIMITS = 1..100

        /** The review a rating saved or updated: `payload.review.id`; null (`decoding`) without it. */
        fun readPollReview(body: Any?): PollReview? {
            val id = (body as? JSONObject)?.objectOrNull("payload")?.objectOrNull("review")?.longOrNull("id") ?: return null
            return PollReview(id)
        }

        /**
         * A reader of a list answer, `{"payload": {"<key>": [...]}}`, its elements read with [element]: one it
         * turns down is left out. An answer without a body is an empty list; one without the list is null
         * (`decoding`).
         */
        fun <T : Any> listAt(key: String, element: (JSONObject) -> T?): (Any?) -> List<T>? = { body ->
            when (body) {
                null -> emptyList()
                is JSONObject -> body.objectOrNull("payload")?.arrayOrNull(key)?.mapObjects(element)
                else -> null
            }
        }

        /** A category by its code and name, left out without one of them. */
        fun readNpsCategory(json: JSONObject): NpsCategory? {
            val code = json.nonBlankStringOrNull("code") ?: return null
            val name = json.nonBlankStringOrNull("name") ?: return null
            return NpsCategory(
                code = code,
                name = name,
                detractorQuestion = json.nonBlankStringOrNull("detractor_question"),
                passiveQuestion = json.nonBlankStringOrNull("passive_question"),
                promoterQuestion = json.nonBlankStringOrNull("promoter_question"),
                detractorSuccess = json.nonBlankStringOrNull("detractor_success"),
                passiveSuccess = json.nonBlankStringOrNull("passive_success"),
                promoterSuccess = json.nonBlankStringOrNull("promoter_success"),
                buttonLabel = json.nonBlankStringOrNull("button_label"),
            )
        }

        /** A channel by its code and name, left out without one of them. */
        fun readChannel(json: JSONObject): PollChannel? {
            val code = json.nonBlankStringOrNull("code") ?: return null
            val name = json.nonBlankStringOrNull("name") ?: return null
            return PollChannel(code = code, name = name)
        }

        /** A CSAT campaign by its code and name, left out without one of them. */
        fun readCsatCampaign(json: JSONObject): CsatCampaign? {
            val code = json.nonBlankStringOrNull("code") ?: return null
            val name = json.nonBlankStringOrNull("name") ?: return null
            return CsatCampaign(
                code = code,
                name = name,
                dissatisfiedQuestion = json.nonBlankStringOrNull("dissatisfied_question"),
                neutralQuestion = json.nonBlankStringOrNull("neutral_question"),
                satisfiedQuestion = json.nonBlankStringOrNull("satisfied_question"),
                dissatisfiedSuccess = json.nonBlankStringOrNull("dissatisfied_success"),
                neutralSuccess = json.nonBlankStringOrNull("neutral_success"),
                satisfiedSuccess = json.nonBlankStringOrNull("satisfied_success"),
                buttonLabel = json.nonBlankStringOrNull("button_label"),
            )
        }

        /** A CES campaign by its code and name, left out without one of them. */
        fun readCesCampaign(json: JSONObject): CesCampaign? {
            val code = json.nonBlankStringOrNull("code") ?: return null
            val name = json.nonBlankStringOrNull("name") ?: return null
            return CesCampaign(
                code = code,
                name = name,
                difficultQuestion = json.nonBlankStringOrNull("difficult_question"),
                neutralQuestion = json.nonBlankStringOrNull("neutral_question"),
                easyQuestion = json.nonBlankStringOrNull("easy_question"),
                difficultSuccess = json.nonBlankStringOrNull("difficult_success"),
                neutralSuccess = json.nonBlankStringOrNull("neutral_success"),
                easySuccess = json.nonBlankStringOrNull("easy_success"),
                buttonLabel = json.nonBlankStringOrNull("button_label"),
            )
        }

        /**
         * `payload.summary` and `payload.reviews`; without either, or without the summary's count, the answer is
         * null (`decoding`). An answer without a body is a product without reviews.
         */
        fun readProductReviews(body: Any?): ProductReviewsResponse? {
            if (body == null) return ProductReviewsResponse(ProductRatingSummary(count = 0), emptyList())
            val payload = (body as? JSONObject)?.objectOrNull("payload") ?: return null
            val summary = payload.objectOrNull("summary")?.let(::readSummary) ?: return null
            val reviews = payload.arrayOrNull("reviews")?.mapObjects(::readProductReview) ?: return null
            return ProductReviewsResponse(summary, reviews)
        }

        /** The summary; `ratings` keeps the keys that are whole numbers (`"1"`…`"5"` in the answer) with a whole count. */
        private fun readSummary(json: JSONObject): ProductRatingSummary? {
            val count = json.intOrNull("count") ?: return null
            val ratings = json.objectOrNull("ratings")?.let { counts ->
                counts.keys().asSequence().mapNotNull { key ->
                    val rating = key.trim().toIntOrNull() ?: return@mapNotNull null
                    counts.intOrNull(key)?.let { rating to it }
                }.toMap()
            }.orEmpty()
            return ProductRatingSummary(count = count, averageRating = json.doubleOrNull("average_rating"), ratings = ratings)
        }

        /** A review, left out without its id, rating or time. */
        private fun readProductReview(json: JSONObject): ProductReview? {
            val id = json.longOrNull("id") ?: return null
            val rating = json.intOrNull("rating") ?: return null
            val createdAt = json.nonBlankStringOrNull("created_at") ?: return null
            return ProductReview(
                id = id,
                rating = rating,
                comment = json.nonBlankStringOrNull("comment"),
                author = json.nonBlankStringOrNull("author"),
                createdAt = createdAt,
            )
        }

        /** A review of the customer, left out without its id, product, order, rating or time; a state this release does not know is null. */
        fun readClientReview(json: JSONObject): ClientReview? {
            val id = json.longOrNull("id") ?: return null
            val itemId = json.nonBlankStringOrNull("product") ?: return null
            val orderId = json.nonBlankStringOrNull("order") ?: return null
            val rating = json.intOrNull("rating") ?: return null
            val createdAt = json.nonBlankStringOrNull("created_at") ?: return null
            return ClientReview(
                id = id,
                itemId = itemId,
                orderId = orderId,
                rating = rating,
                comment = json.nonBlankStringOrNull("comment"),
                state = valueOfWire(json.nonBlankStringOrNull("state"), ReviewState.entries) { it.value },
                createdAt = createdAt,
            )
        }
    }
}
