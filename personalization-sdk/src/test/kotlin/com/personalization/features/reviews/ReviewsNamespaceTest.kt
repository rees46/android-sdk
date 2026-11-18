package com.personalization.features.reviews

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.managers.ReviewsApi
import com.personalization.api.responses.reviews.NpsCategory
import com.personalization.api.responses.reviews.PollReview
import com.personalization.api.responses.reviews.ProductRatingSummary
import com.personalization.api.responses.reviews.ProductReviewsResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.HeldMain
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/**
 * `sdk.reviews` as a bridge calls it: on the new core the polls go to `Rees46Config.appDomain` and the reviews
 * of products to `apiDomain`, in both forms; on the legacy core, where apps run, every method fails with
 * `notInitialized` and sends nothing.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReviewsNamespaceTest {

    private lateinit var context: Context

    /** `apiDomain` and `appDomain`; [requests] gets what each received, as `api:<path>` or `app:<path>`. */
    private val api = MockWebServer()
    private val app = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, RecordedRequest>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        LegacyCore.threads = threads
        api.dispatcher = recording(API) { path ->
            when (path) {
                "init" -> MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                ReviewsApiImpl.PRODUCT_REVIEWS -> MockResponse().setBody(PRODUCT_REVIEWS)
                else -> MockResponse().setBody("{}")
            }
        }
        app.dispatcher = recording(APP) { path ->
            when (path) {
                ReviewsApiImpl.NPS_REVIEWS -> MockResponse().setBody("""{"status":"success","payload":{"review":{"id":1}}}""")
                ReviewsApiImpl.NPS_CATEGORIES -> MockResponse().setBody(CATEGORIES)
                else -> MockResponse().setResponseCode(404)
            }
        }
        api.start()
        app.start()
    }

    private fun recording(host: String, answer: (String) -> MockResponse) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath?.removePrefix("/") ?: return MockResponse().setResponseCode(400)
            requests += "$host:$path" to request
            return answer(path)
        }
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        api.shutdown()
        app.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = api.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
        appDomain = app.url("/").toString().trimEnd('/'),
    )

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    private fun <T> outcome(block: suspend () -> T): SdkResult<T> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { block() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** What the hosts got, `host:path` with the request, after the calls so far had time to arrive. */
    private fun sent(): List<Pair<String, RecordedRequest>> {
        threads.awaitIdle()
        Thread.sleep(200)
        return generateSequence { requests.poll() }.toList()
    }

    private fun bridgeSdk(): SDK = Rees46.initializeBridge(context, config()).also { awaitState(it, SdkState.READY) }

    @Test
    fun `a bridge's instance sends the polls to appDomain and the reviews of products to apiDomain`() {
        val sdk = bridgeSdk()
        sent()

        assertEquals(SdkResult.Success(PollReview(1)), outcome { sdk.reviews.createNps(9, "android_app", "delivery", orderId = "ORDER-1") })
        assertEquals(
            SdkResult.Success(ProductReviewsResponse(ProductRatingSummary(count = 0, averageRating = null, ratings = emptyMap()), emptyList())),
            outcome { sdk.reviews.getProductReviews("SKU-1024", limit = 20) },
        )

        // Whatever else the instance sends goes to apiDomain; nothing of the reviews goes twice or elsewhere.
        val sent = sent().filter { (path, _) -> path.startsWith("$APP:") || path.contains("reviews/") }.toMap()
        assertEquals(setOf("$APP:${ReviewsApiImpl.NPS_REVIEWS}", "$API:${ReviewsApiImpl.PRODUCT_REVIEWS}"), sent.keys)
        val poll = JSONObject(sent.getValue("$APP:${ReviewsApiImpl.NPS_REVIEWS}").body.readUtf8())
        assertEquals(9, poll.get("rate"))
        assertEquals("ORDER-1", poll.getString("order_id"))
        // The common fields go to the host of API v3 too, with the did of this instance's /init.
        assertEquals(SHOP, poll.getString("shop_id"))
        assertEquals("D1", poll.getString("did"))
        assertEquals("S1", poll.getString("sid"))
        val product = sent.getValue("$API:${ReviewsApiImpl.PRODUCT_REVIEWS}").requestUrl!!
        assertEquals("SKU-1024", product.queryParameter("product"))
        assertEquals("20", product.queryParameter("limit"))
        assertEquals("D1", product.queryParameter("did"))
    }

    @Test
    fun `the form with a listener ends once, on the main thread`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        awaitState(sdk, SdkState.READY)
        val ends = CopyOnWriteArrayList<Pair<SdkResult<List<NpsCategory>>, Boolean>>()

        sdk.reviews.getNpsCategories(CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        while (ends.isEmpty() && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(listOf(SdkResult.Success(listOf(NpsCategory("delivery", "Доставка", promoterQuestion = "Что понравилось?"))) to true), ends)
    }

    @Test
    fun `an argument the contract refuses is invalidArgument, logged, and nothing is sent`() {
        val sdk = bridgeSdk()
        sent()

        val outcome = outcome { sdk.reviews.createCsat(6, "android_app", "support") }

        assertEquals(SdkResult.Failure(SdkError.InvalidArgument("rate must be from 1 to 5")), outcome)
        assertTrue(sent().isEmpty())
        assertEquals(1, logged.count { it.code == LogCodes.INVALID_ARGUMENT && it.context["call"] == ReviewsApiImpl.CREATE_CSAT })
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized from every method in both forms and sends nothing`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = false)
        awaitState(sdk, SdkState.READY)
        sent()
        val reviews = sdk.reviews

        val thrown = SUSPENDING.map { (_, call) ->
            try {
                runBlocking { call(reviews) }
                null
            } catch (e: SdkException) {
                e
            }
        }
        val ends = CopyOnWriteArrayList<SdkResult<*>>()
        LISTENING.forEach { (_, call) -> call(reviews) { ends += it } }
        while (ends.size < LISTENING.size && main.runNext()) Unit

        assertTrue(thrown.all { it != null && it.error == SdkError.NotInitialized && it.message.orEmpty().contains("Rees46.initializeBridge") })
        assertEquals(List(LISTENING.size) { SdkResult.Failure(SdkError.NotInitialized) }, ends.toList())
        assertTrue(sent().none { (path, _) -> path.startsWith("$APP:") || path.contains("reviews/") })
        val mismatches = logged.filter { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"].toString().startsWith("reviews.") }
            .map { it.context["call"].toString() }
        assertEquals((SUSPENDING + LISTENING).map { it.first }.sorted(), mismatches.sorted())
        // The core decides before the arguments: a refused one is notInitialized here too.
        assertTrue(logged.none { it.code == LogCodes.INVALID_ARGUMENT })
    }

    @Test
    fun `an instance never initialized gets notInitialized`() {
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { SDK().reviews.getNpsChannels() })
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `Java does not see the namespace`() {
        val getters = SDK::class.java.methods.filter { it.name == "getReviews" }

        assertTrue(getters.isNotEmpty() && getters.all { it.isSynthetic })
    }

    private companion object {
        const val SHOP = "reviews-namespace-shop"
        const val API = "api"
        const val APP = "app"
        const val PRODUCT_REVIEWS =
            """{"status":"success","payload":{"summary":{"count":0,"average_rating":null,"ratings":{}},"reviews":[]}}"""
        const val CATEGORIES =
            """{"status":"success","payload":{"categories":[{"code":"delivery","name":"Доставка","promoter_question":"Что понравилось?"}]}}"""

        /** Every method's suspending form by its name in the log, one of them with an argument the contract refuses. */
        val SUSPENDING: List<Pair<String, suspend (ReviewsApi) -> Any>> = listOf(
            ReviewsApiImpl.CREATE_NPS to { it.createNps(9, "android_app", "delivery") },
            ReviewsApiImpl.CREATE_CSAT to { it.createCsat(4, "android_app", "support") },
            ReviewsApiImpl.CREATE_CES to { it.createCes(99, "", "") },
            ReviewsApiImpl.GET_NPS_CATEGORIES to { it.getNpsCategories() },
            ReviewsApiImpl.GET_NPS_CHANNELS to { it.getNpsChannels() },
            ReviewsApiImpl.GET_CSAT_CAMPAIGNS to { it.getCsatCampaigns() },
            ReviewsApiImpl.GET_CES_CAMPAIGNS to { it.getCesCampaigns() },
            ReviewsApiImpl.GET_PRODUCT_REVIEWS to { it.getProductReviews("SKU-1") },
            ReviewsApiImpl.GET_CLIENT_REVIEWS to { it.getClientReviews("ORDER-1") },
        )

        /** Every method's form with a listener by its name in the log; the listener gets what it ends with. */
        val LISTENING: List<Pair<String, (ReviewsApi, (SdkResult<*>) -> Unit) -> Unit>> = listOf(
            ReviewsApiImpl.CREATE_NPS to { r, end -> r.createNps(9, "android_app", "delivery", null, null, null, CompletionListener { end(it) }) },
            ReviewsApiImpl.CREATE_CSAT to { r, end -> r.createCsat(4, "android_app", "support", null, null, null, CompletionListener { end(it) }) },
            ReviewsApiImpl.CREATE_CES to { r, end -> r.createCes(5, "android_app", "returns", null, null, null, CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_NPS_CATEGORIES to { r, end -> r.getNpsCategories(CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_NPS_CHANNELS to { r, end -> r.getNpsChannels(CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_CSAT_CAMPAIGNS to { r, end -> r.getCsatCampaigns(CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_CES_CAMPAIGNS to { r, end -> r.getCesCampaigns(CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_PRODUCT_REVIEWS to { r, end -> r.getProductReviews("SKU-1", null, null, CompletionListener { end(it) }) },
            ReviewsApiImpl.GET_CLIENT_REVIEWS to { r, end -> r.getClientReviews("ORDER-1", CompletionListener { end(it) }) },
        )
    }
}
