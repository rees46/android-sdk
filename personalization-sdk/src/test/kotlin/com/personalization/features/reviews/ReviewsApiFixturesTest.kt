package com.personalization.features.reviews

import androidx.test.core.app.ApplicationProvider
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
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.FakeConnectivity
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.startedCoreInit
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * `sdk.reviews` against the contract's fixtures (`contract/0/reviews.json`): the request each call makes and
 * on which host — the polls on the host of API v3, the reviews of products on the API's —, the calls it
 * refuses before a request, and how it reads each answer.
 *
 * `reviews.json` is written in the fixtures' format from `contract/reviews.md` and the API's pages of
 * `feedback/` ahead of the contract's own file, which takes it over as it is; the iOS SDK tests by the same
 * file.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReviewsApiFixturesTest {

    /** The API's host (`apiDomain`) and the host of API v3 (`appDomain`). */
    private val api = MockWebServer()
    private val app = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns SHOP
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getStream() } returns "android"
        every { getSegmentForABTesting() } returns "B"
    }
    private lateinit var core: CoreV2
    private lateinit var reviews: ReviewsApiImpl

    /** What both hosts answer. */
    @Volatile
    private var answer: MockResponse = MockResponse().setBody("{}")

    @Before
    fun setUp() {
        SdkLogs.reset()
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer
        }
        api.dispatcher = dispatcher
        app.dispatcher = dispatcher
        api.start()
        app.start()
        reviews = reviewsWith { app.url("/").toString() }
    }

    private fun reviewsWith(appUrl: () -> String): ReviewsApiImpl {
        val client = CoreHttpClient({ api.url("/").toString() }, settings, connectivity, SHOP, appUrl = appUrl)
        core = CoreV2(client, SHOP).also {
            it.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity, SHOP)
        }
        return ReviewsApiImpl(NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { SHOP }))
    }

    @After
    fun tearDown() {
        core.release()
        api.close()
        app.close()
        SdkLogs.reset()
    }

    private fun outcome(case: JSONObject): SdkResult<Any> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { invoke(case.getJSONObject("call")) } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The method a case calls, with its arguments by the contract's names. */
    private suspend fun invoke(call: JSONObject): Any {
        assertEquals("reviews", call.getString("namespace"))
        val args = call.getJSONObject("args")
        return when (val method = call.getString("method")) {
            "createNps" -> reviews.createNps(
                args.getInt("rate"), args.getString("channel"), args.getString("category"),
                args.text("orderId"), args.text("comment"), args.text("label"),
            )
            "createCsat" -> reviews.createCsat(
                args.getInt("rate"), args.getString("channel"), args.getString("campaign"),
                args.text("orderId"), args.text("comment"), args.text("label"),
            )
            "createCes" -> reviews.createCes(
                args.getInt("rate"), args.getString("channel"), args.getString("campaign"),
                args.text("orderId"), args.text("comment"), args.text("label"),
            )
            "getNpsCategories" -> reviews.getNpsCategories()
            "getNpsChannels" -> reviews.getNpsChannels()
            "getCsatCampaigns" -> reviews.getCsatCampaigns()
            "getCesCampaigns" -> reviews.getCesCampaigns()
            "getProductReviews" -> reviews.getProductReviews(args.getString("itemId"), args.number("limit"), args.number("offset"))
            "getClientReviews" -> reviews.getClientReviews(args.getString("orderId"))
            else -> error("unknown method reviews.$method")
        }
    }

    @Test
    fun `every call makes the contract's request on its host, with no field of its own beside the common ones`() {
        val cases = ContractFixtures.requests("reviews")
        assertTrue(cases.size >= 12)
        var onApp = 0
        var onApi = 0
        cases.forEach { case ->
            val name = case.getString("name")
            val expected = case.getJSONObject("request")
            val method = case.getJSONObject("call").getString("method")
            val host = if (method in ON_APP_DOMAIN) app.also { onApp++ } else api.also { onApi++ }

            outcome(case)

            val recorded = requireNotNull(host.takeRequest(5, TimeUnit.SECONDS)) { "$name: no request on its host" }
            val url = recorded.requestUrl!!
            assertEquals(name, expected.getString("method"), recorded.method)
            assertEquals(name, "/" + expected.getString("path"), url.encodedPath)
            if (expected.getString("method") == "GET") {
                val query = expected.getJSONObject("query")
                assertEquals(name, query.keys().asSequence().toSet(), url.queryParameterNames - COMMON_FIELDS)
                query.keys().forEach { key -> assertEquals(name, query.getString(key), url.queryParameter(key)) }
                // The core adds the common fields; the core's own tests check their values.
                assertEquals(name, COMMON_FIELDS, url.queryParameterNames intersect COMMON_FIELDS)
            } else {
                val body = JSONObject(recorded.body.readUtf8())
                val fields = expected.getJSONObject("body")
                assertEquals(name, fields.keys().asSequence().toSet(), body.keys().asSequence().toSet() - COMMON_FIELDS)
                // The JSON types too: the rating goes as a number.
                fields.keys().forEach { key -> assertEquals("$name: $key", fields.get(key), body.get(key)) }
                assertEquals(name, COMMON_FIELDS, body.keys().asSequence().toSet() intersect COMMON_FIELDS)
            }
        }
        // Nothing went to the other host.
        assertEquals(onApp, app.requestCount)
        assertEquals(onApi, api.requestCount)
    }

    @Test
    fun `a call the contract refuses fails with invalidArgument and sends nothing`() {
        val cases = ContractFixtures.cases("reviews").filter { it.has("error") }
        assertTrue(cases.size >= 10)
        cases.forEach { case ->
            assertEquals(case.getString("name"), "invalidArgument", case.getJSONObject("error").getString("type"))

            val outcome = outcome(case)

            assertTrue("${case.getString("name")}: $outcome", (outcome as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
        }
        assertEquals(0, app.requestCount + api.requestCount)
    }

    @Test
    fun `every answer of the contract reads into its result or its error`() {
        val cases = ContractFixtures.answers("reviews")
        assertTrue(cases.size >= 20)
        cases.forEach { case ->
            val name = case.getString("name")
            val response = ContractFixtures.mockResponse(case)
            // No answer: the request finds no network.
            connectivity.set(response != null)
            response?.let { answer = it }
            val expect = case.getJSONObject("expect")

            val outcome = outcome(case)

            if (expect.has("result")) {
                val method = case.getJSONObject("call").getString("method")
                assertEquals(name, SdkResult.Success(expected(method, expect.get("result"))), outcome)
            } else {
                assertEquals(name, SdkResult.Failure(ContractFixtures.errorOf(expect.getJSONObject("error"))), outcome)
            }
        }
    }

    @Test
    fun `an answer without a body is an empty result, and decoding for a rating, which has none`() {
        answer = MockResponse().setResponseCode(200)

        assertEquals(emptyList<NpsCategory>(), runBlocking { reviews.getNpsCategories() })
        assertEquals(emptyList<PollChannel>(), runBlocking { reviews.getNpsChannels() })
        assertEquals(emptyList<CsatCampaign>(), runBlocking { reviews.getCsatCampaigns() })
        assertEquals(emptyList<CesCampaign>(), runBlocking { reviews.getCesCampaigns() })
        assertEquals(emptyList<ClientReview>(), runBlocking { reviews.getClientReviews("ORDER-1") })
        assertEquals(
            ProductReviewsResponse(ProductRatingSummary(count = 0, averageRating = null, ratings = emptyMap()), emptyList()),
            runBlocking { reviews.getProductReviews("SKU-1") },
        )
        assertEquals(SdkError.Decoding, failure { reviews.createNps(9, "android_app", "delivery") })
    }

    @Test
    fun `the host of API v3 is app rees46 ru unless appDomain names another`() {
        assertEquals("https://app.rees46.ru/", CoreHttpClient.appUrlOf(null))
        assertEquals("https://app.rees46.ru/", CoreHttpClient.appUrlOf(" "))
        assertEquals("https://app.cluster.example/", CoreHttpClient.appUrlOf("app.cluster.example"))
        assertEquals("https://app.cluster.example/", CoreHttpClient.appUrlOf("https://app.cluster.example/"))
    }

    @Test
    fun `an appDomain that makes no URL fails the poll's request with invalidArgument, and the API's requests still go`() {
        core.release()
        reviews = reviewsWith { CoreHttpClient.appUrlOf("ftp://app.example") }
        answer = MockResponse().setBody("""{"status":"success","payload":{"reviews":[]}}""")

        val error = failure { reviews.createNps(9, "android_app", "delivery") }

        assertTrue("$error", error is SdkError.InvalidArgument)
        assertEquals(emptyList<ClientReview>(), runBlocking { reviews.getClientReviews("ORDER-1") })
        assertEquals(0, app.requestCount)
        assertNotNull(api.takeRequest(5, TimeUnit.SECONDS))
    }

    private fun failure(block: suspend () -> Any): SdkError? = try {
        runBlocking { withTimeout(10_000) { block() } }
        null
    } catch (e: SdkException) {
        e.error
    }

    /** The value of a method by the contract's field names in a case's `result`. */
    private fun expected(method: String, result: Any): Any = when (method) {
        "createNps", "createCsat", "createCes" -> PollReview((result as JSONObject).getInt("id"))
        "getNpsCategories" -> (result as JSONArray).objects().map {
            NpsCategory(
                it.getString("code"), it.getString("name"),
                it.text("detractorQuestion"), it.text("passiveQuestion"), it.text("promoterQuestion"),
                it.text("detractorSuccess"), it.text("passiveSuccess"), it.text("promoterSuccess"), it.text("buttonLabel"),
            )
        }
        "getNpsChannels" -> (result as JSONArray).objects().map { PollChannel(it.getString("code"), it.getString("name")) }
        "getCsatCampaigns" -> (result as JSONArray).objects().map {
            CsatCampaign(
                it.getString("code"), it.getString("name"),
                it.text("dissatisfiedQuestion"), it.text("neutralQuestion"), it.text("satisfiedQuestion"),
                it.text("dissatisfiedSuccess"), it.text("neutralSuccess"), it.text("satisfiedSuccess"), it.text("buttonLabel"),
            )
        }
        "getCesCampaigns" -> (result as JSONArray).objects().map {
            CesCampaign(
                it.getString("code"), it.getString("name"),
                it.text("difficultQuestion"), it.text("neutralQuestion"), it.text("easyQuestion"),
                it.text("difficultSuccess"), it.text("neutralSuccess"), it.text("easySuccess"), it.text("buttonLabel"),
            )
        }
        "getProductReviews" -> (result as JSONObject).let { response ->
            val summary = response.getJSONObject("summary")
            val ratings = summary.getJSONObject("ratings")
            ProductReviewsResponse(
                summary = ProductRatingSummary(
                    count = summary.getInt("count"),
                    averageRating = if (summary.isNull("averageRating")) null else summary.getDouble("averageRating"),
                    ratings = ratings.keys().asSequence().associate { it.toInt() to ratings.getInt(it) },
                ),
                reviews = response.getJSONArray("reviews").objects().map {
                    ProductReview(it.getInt("id"), it.getInt("rating"), it.text("comment"), it.text("author"), it.getString("createdAt"))
                },
            )
        }
        "getClientReviews" -> (result as JSONArray).objects().map {
            ClientReview(
                id = it.getInt("id"), itemId = it.getString("itemId"), orderId = it.getString("orderId"),
                rating = it.getInt("rating"), comment = it.text("comment"),
                state = it.text("state")?.let { state -> ReviewState.entries.single { value -> value.value == state } },
                createdAt = it.getString("createdAt"),
            )
        }
        else -> error("unknown method reviews.$method")
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.number(key: String): Int? = if (isNull(key)) null else getInt(key)

    private companion object {
        const val SHOP = "reviews-shop"
        val COMMON_FIELDS = setOf("shop_id", "did", "sid", "seance", "segment", "stream")

        /** The methods the contract sends to `appDomain`; the rest go to `apiDomain`. */
        val ON_APP_DOMAIN = setOf(
            "createNps", "createCsat", "createCes", "getNpsCategories", "getNpsChannels", "getCsatCampaigns", "getCesCampaigns",
        )
    }
}
