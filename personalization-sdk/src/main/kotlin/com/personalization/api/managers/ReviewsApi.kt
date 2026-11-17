package com.personalization.api.managers

import com.personalization.api.responses.reviews.ClientReview
import com.personalization.api.responses.reviews.CesCampaign
import com.personalization.api.responses.reviews.CsatCampaign
import com.personalization.api.responses.reviews.NpsCategory
import com.personalization.api.responses.reviews.PollChannel
import com.personalization.api.responses.reviews.PollReview
import com.personalization.api.responses.reviews.ProductReviewsResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's feedback — the `reviews` namespace: NPS, CSAT and CES ratings with the dictionaries their
 * forms take questions and thanks from, and the reviews of products. The app collects a rating with its
 * own form and sends it with a `create…` method; the SDK adds `did` and the other common fields itself,
 * and the server finds the customer by the device's `did`. Nothing here takes a contact or the shop's secret.
 *
 * The polls go to the host of API v3, `Rees46Config.appDomain` (`app.rees46.ru` unless the shop's cluster
 * has its own); the reviews of products to the API's host, `Rees46Config.apiDomain`.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 *
 * Every method throws [SdkException] with [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (refused before a request,
 * as each method says), [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or
 * [SdkError.Cancelled] (`release()`). What an [SdkError.Http] code means:
 * - the polls: `400` — wrong parameters, or a rating the server refuses (`label` up to 255 characters,
 *   `comment` up to 5000); `402` — the project is not paid; `403` — the project is inactive, or the request
 *   has no `did`; `404` — no shop, customer, channel, category or campaign, or the order is another
 *   customer's; `429` — more than 30 ratings a minute from the device, or 120 from the IP;
 * - the reviews of products: `400` — no order; `403` — the reviews service is not enabled; `404` — no
 *   product, order or customer.
 *
 * None of them changes the instance's state: it may refuse one service, not the shop. Nothing repeats a
 * request.
 *
 * Each method has a twin for Java and for code outside coroutines that ends in a [CompletionListener]: its
 * result comes once, on the main thread.
 */
@BridgeApi
interface ReviewsApi {

    /**
     * Sends an NPS rating — how ready the customer is to recommend the shop
     * (`POST /api/v3/polls/nps/reviews`). Its question and thanks are those of the [NpsCategory]
     * ([getNpsCategories]) for the rating. An empty or blank [orderId], [comment] or [label] is not sent.
     *
     * Sent again the same day with the same channel, category, order and label, it does not make a new
     * review: the server updates the earlier one, and [PollReview.id] is that review's. A repeat with the
     * same rating and no comment keeps the earlier comment; a changed rating without a comment clears it.
     * A rating with a comment — the answer to the question after the rating — goes to the latest review of
     * the last 90 days with the same channel, category, order, label and rating and no comment, if there is
     * one, rather than making another.
     *
     * @param rate 0 to 10.
     * @param channel the code of the channel in the shop's account (`android_app`), not empty.
     * @param category the code of the category — what is rated —, not empty.
     * @param orderId the order rated, when an order is. Another customer's order is `http(404)`; one the
     * server does not know is dropped, and the rating is saved without it.
     * @param comment the customer's comment.
     * @param label a mark to group the reviews by in the report.
     * @throws SdkException with [SdkError.InvalidArgument] for [rate] outside 0–10, an empty [channel] or
     * [category]; otherwise as [ReviewsApi] says.
     */
    suspend fun createNps(
        rate: Int,
        channel: String,
        category: String,
        orderId: String? = null,
        comment: String? = null,
        label: String? = null,
    ): PollReview

    /**
     * Sends a CSAT rating — how satisfied the customer is with one interaction: delivery, support, checkout
     * (`POST /api/v3/polls/csat/reviews`). Its question and thanks are those of the [CsatCampaign]
     * ([getCsatCampaigns]); the rest is as [createNps] says.
     *
     * @param rate 1 to 5.
     * @param campaign the code of the campaign — what is rated —, not empty.
     * @throws SdkException with [SdkError.InvalidArgument] for [rate] outside 1–5, an empty [channel] or
     * [campaign]; otherwise as [ReviewsApi] says.
     */
    suspend fun createCsat(
        rate: Int,
        channel: String,
        campaign: String,
        orderId: String? = null,
        comment: String? = null,
        label: String? = null,
    ): PollReview

    /**
     * Sends a CES rating — how much effort a task took the customer (`POST /api/v3/polls/ces/reviews`).
     * Its question and thanks are those of the [CesCampaign] ([getCesCampaigns]); the rest is as
     * [createCsat] says.
     *
     * @param rate 1 to 7.
     * @throws SdkException with [SdkError.InvalidArgument] for [rate] outside 1–7, an empty [channel] or
     * [campaign]; otherwise as [ReviewsApi] says.
     */
    suspend fun createCes(
        rate: Int,
        channel: String,
        campaign: String,
        orderId: String? = null,
        comment: String? = null,
        label: String? = null,
    ): PollReview

    /** The shop's NPS categories (`GET /api/v3/polls/nps/categories`); none is an empty list. */
    suspend fun getNpsCategories(): List<NpsCategory>

    /**
     * The shop's channels of ratings (`GET /api/v3/polls/nps/channels`), common to NPS, CSAT and CES: the API
     * serves them on the path of NPS only. A form usually knows its channel already. None is an empty list.
     */
    suspend fun getNpsChannels(): List<PollChannel>

    /** The shop's CSAT campaigns (`GET /api/v3/polls/csat/campaigns`); none is an empty list. */
    suspend fun getCsatCampaigns(): List<CsatCampaign>

    /** The shop's CES campaigns (`GET /api/v3/polls/ces/campaigns`); none is an empty list. */
    suspend fun getCesCampaigns(): List<CesCampaign>

    /**
     * The published reviews of a product and its rating, for the product's card (`GET /reviews/product`).
     * The summary counts every published review; [limit] and [offset] page only the list, the newest first.
     * Without reviews the list is empty, `count` is 0 and `averageRating` null.
     *
     * @param itemId the product's id, not empty.
     * @param limit 1 to 100; the server gives 20 without it.
     * @param offset 0 or more; 0 without it.
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [itemId], [limit] outside 1–100 or a
     * negative [offset]; otherwise as [ReviewsApi] says.
     */
    suspend fun getProductReviews(itemId: String, limit: Int? = null, offset: Int? = null): ProductReviewsResponse

    /**
     * The reviews the device's customer wrote in one order, in every state of moderation
     * (`GET /reviews/client`) — say, to show a rating beside a product in the order history. No reviews is an
     * empty list; there are no pages. The order has to be the customer's: none, or another customer's, is
     * `http(404)`, and so is a customer the CDP does not know — the method makes none.
     *
     * @param orderId the order's id, not empty.
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [orderId]; otherwise as [ReviewsApi] says.
     */
    suspend fun getClientReviews(orderId: String): List<ClientReview>

    /** [createNps] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun createNps(
        rate: Int,
        channel: String,
        category: String,
        orderId: String?,
        comment: String?,
        label: String?,
        listener: CompletionListener<PollReview>,
    )

    /** [createCsat] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun createCsat(
        rate: Int,
        channel: String,
        campaign: String,
        orderId: String?,
        comment: String?,
        label: String?,
        listener: CompletionListener<PollReview>,
    )

    /** [createCes] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun createCes(
        rate: Int,
        channel: String,
        campaign: String,
        orderId: String?,
        comment: String?,
        label: String?,
        listener: CompletionListener<PollReview>,
    )

    /** [getNpsCategories] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getNpsCategories(listener: CompletionListener<List<NpsCategory>>)

    /** [getNpsChannels] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getNpsChannels(listener: CompletionListener<List<PollChannel>>)

    /** [getCsatCampaigns] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getCsatCampaigns(listener: CompletionListener<List<CsatCampaign>>)

    /** [getCesCampaigns] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getCesCampaigns(listener: CompletionListener<List<CesCampaign>>)

    /** [getProductReviews] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getProductReviews(itemId: String, limit: Int?, offset: Int?, listener: CompletionListener<ProductReviewsResponse>)

    /** [getClientReviews] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getClientReviews(orderId: String, listener: CompletionListener<List<ClientReview>>)
}
