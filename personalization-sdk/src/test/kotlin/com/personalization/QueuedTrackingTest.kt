package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.TrackingApi
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.RecommendedBy
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * `tracking.queued`, as the bridges call it: each method sends what its twin of `tracking` sends — the cases
 * of the contract's fixtures (`contract/0/tracking.json`) and the methods they leave out, side by side with
 * the twin — and its listener hears, once, on the main thread, that the queue accepted the event, before the
 * server answered and whatever it answered. Refusals are the twin's; after `release()` and on the legacy
 * core the call is `notInitialized`, a refused shop's `shopBlocked`.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QueuedTrackingTest {

    private lateinit var context: Context
    private var server = MockWebServer()

    /** Each request the server got: its path and its body. */
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()

    /** Held answers to events while it is not null. */
    @Volatile
    private var holdEvents: CountDownLatch? = null

    /** The answer to every request but `/init` while it is not null. */
    @Volatile
    private var eventAnswer: MockResponse? = null

    /** The answer to `/init` while it is not null. */
    @Volatile
    private var initAnswer: MockResponse? = null

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
        server = MockWebServer()
        requests.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                if (path == "init") return initAnswer ?: MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                holdEvents?.await(5, TimeUnit.SECONDS)
                return eventAnswer ?: MockResponse().setBody("""{"answer":"$path"}""")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        holdEvents?.countDown()
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun sdk(coreV2: Boolean = true, state: SdkState = SdkState.READY): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(
                shopId = SHOP,
                apiDomain = server.url("/").toString().trimEnd('/'),
                autoSendPushToken = false,
                logLevel = LogLevel.DEBUG,
            ),
            coreV2,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == state } } }
        return sdk
    }

    /** What a listener heard: the result, and whether on the main thread. */
    private class Heard : CompletionListener<Unit> {
        val ends = CopyOnWriteArrayList<Pair<SdkResult<Unit>, Boolean>>()
        override fun onComplete(result: SdkResult<Unit>) {
            ends += result to (Looper.myLooper() == Looper.getMainLooper())
        }
    }

    /** What an old listener heard. */
    private class OldHeard : OnApiCallbackListener() {
        val calls = LinkedBlockingQueue<String>()
        override fun onSuccess(response: JSONObject?) {
            calls += "success"
        }

        override fun onError(code: Int, msg: String?) {
            calls += "error:$code"
        }
    }

    /** Runs the main looper until [heard] heard once, and a little after to catch a second time. */
    private fun await(heard: Heard): List<Pair<SdkResult<Unit>, Boolean>> {
        val until = System.currentTimeMillis() + 5_000
        while (heard.ends.isEmpty() && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        repeat(5) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        return heard.ends.toList()
    }

    /** The next request to [path], skipping others (`/init`). */
    private fun next(path: String): JSONObject {
        while (true) {
            val (got, body) = requests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no $path request")
            if (got == path) return body
        }
    }

    /** The events the server got once the queue was sent: their paths and bodies. */
    private fun sentEvents(sdk: SDK): List<Pair<String, JSONObject>> {
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        Thread.sleep(50)
        return generateSequence { requests.poll() }.filter { it.first in EVENT_PATHS }.toList()
    }

    /** [body] without what differs between two calls made one after the other: the time a custom event was made. */
    private fun comparable(body: JSONObject): String =
        JSONObject(body.toString()).apply { remove("time") }.toString()

    private fun failure(heard: List<Pair<SdkResult<Unit>, Boolean>>): SdkError? =
        (heard.singleOrNull()?.first as? SdkResult.Failure)?.error

    @Test
    fun `every queued method sends what its twin sends, and hears the queue's acceptance on the main thread`() {
        val sdk = sdk()
        val cases = ContractFixtures.cases("tracking") + OTHER_METHODS.map(::JSONObject)
        val methods = mutableSetOf<String>()

        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            val method = call.getString("method")
            val args = call.getJSONObject("args")
            methods += method
            eventAnswer = if (case.has("response")) ContractFixtures.mockResponse(case) else null

            val old = OldHeard()
            regular(sdk.tracking, method, args, old)
            val oldAnswer = old.calls.poll(5, TimeUnit.SECONDS)
            val twin = sentEvents(sdk)
            val heard = Heard()
            queued(sdk.tracking, method, args, heard)
            val ends = await(heard)
            val sent = sentEvents(sdk)

            when {
                case.has("error") -> {
                    // The twin's refusal, before the queue: nothing goes.
                    assertTrue("$name: $oldAnswer", oldAnswer?.startsWith("error:-") == true)
                    assertTrue("$name: $ends", failure(ends) is SdkError.InvalidArgument)
                    assertTrue(name, ends.single().second)
                    assertEquals(name, emptyList<Pair<String, JSONObject>>(), twin + sent)
                }
                case.has("request") && case.isNull("request") -> {
                    // Not sent, and no error.
                    assertEquals(name, "success", oldAnswer)
                    assertEquals(name, listOf(SdkResult.Success(Unit) to true), ends)
                    assertEquals(name, emptyList<Pair<String, JSONObject>>(), twin + sent)
                }
                else -> {
                    assertEquals(name, "success", oldAnswer)
                    assertEquals(name, listOf(SdkResult.Success(Unit) to true), ends)
                    assertEquals(name, 1, twin.size)
                    assertEquals(name, 1, sent.size)
                    val (path, body) = sent.single()
                    assertEquals(name, twin.single().first, path)
                    assertEquals(name, comparable(twin.single().second), comparable(body))
                    assertEquals(name, path == "push/custom", body.has("time"))
                    if (case.has("request")) {
                        val request = case.getJSONObject("request")
                        assertEquals(name, request.getString("path"), path)
                        assertSubset(name, request.getJSONObject("body"), body)
                    }
                    assertEquals(name, "D1", body.getString("did"))
                    assertEquals(name, "S1", body.getString("sid"))
                }
            }
        }
        assertEquals(EVENT_METHODS, methods)
    }

    @Test
    fun `the stored source, recommended_by and the popup of the answer go as for the twin`() {
        val sdk = sdk()
        sdk.tracking.setSource(TrackingSource(TrackingSourceType.CHAIN, "c1"))
        eventAnswer = MockResponse().setBody(POPUP_ANSWER)

        sdk.setRecommendedByUseCase(RecommendedBy(RecommendedBy.TYPE.RECOMMENDATION, "block-1"))
        sdk.tracking.productView("37")
        val twin = sentEvents(sdk).single().second
        val twinPopup = popupOutcomes()
        sdk.setRecommendedByUseCase(RecommendedBy(RecommendedBy.TYPE.RECOMMENDATION, "block-1"))
        sdk.tracking.queued.productView("37")
        val body = sentEvents(sdk).single().second
        val popup = popupOutcomes()

        assertEquals(comparable(twin), comparable(body))
        assertEquals("dynamic", body.getString("recommended_by"))
        assertEquals("block-1", body.getString("recommended_code"))
        assertEquals("chain", body.getJSONObject("source").getString("from"))
        assertEquals("c1", body.getJSONObject("source").getString("code"))
        // The popup of the answer was handled as for the twin.
        assertEquals(1, twinPopup.size)
        assertEquals(twinPopup, popup)
        // recommended_by was spent by the event, as the twin spends it.
        sdk.tracking.queued.productView("38")
        assertFalse(sentEvents(sdk).single().second.has("recommended_by"))
    }

    /** What became of the popups of the answers so far — the logs of a popup not shown, by reason — once the main thread ran. */
    private fun popupOutcomes(): List<Any?> {
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        val outcomes = logged.filter { it.code == LogCodes.POPUP_NOT_SHOWN }.map { it.context["reason"] }
        logged.clear()
        return outcomes
    }

    @Test
    fun `the listener hears the acceptance before the server answered, and a later 400 changes nothing`() {
        val sdk = sdk()
        val hold = CountDownLatch(1).also { holdEvents = it }
        eventAnswer = MockResponse().setResponseCode(400).setBody("""{"status":"error","message":"Unknown event"}""")
        val heard = Heard()

        sdk.tracking.queued.custom("unknown_event", listener = heard)

        assertEquals("unknown_event", next("push/custom").getString("event"))
        // The server holds its answer: the call is complete already.
        assertEquals(listOf(SdkResult.Success(Unit) to true), await(heard))

        hold.countDown()
        val until = System.currentTimeMillis() + 5_000
        while (logged.none { it.code == LogCodes.QUEUE_DROPPED } && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue("the 400 dropped the event", logged.any { it.code == LogCodes.QUEUE_DROPPED })
        assertEquals("once, and still a success", listOf(SdkResult.Success(Unit) to true), await(heard))

        // The twin hears the server's answer.
        holdEvents = null
        val old = OldHeard()
        sdk.tracking.custom("unknown_event", listener = old)
        assertEquals("error:400", old.calls.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `refusals are the twin's, invalidArgument with nothing sent`() {
        val sdk = sdk()
        val calls: List<Pair<String, (CompletionListener<Unit>) -> Unit>> = listOf(
            "empty item id" to { l -> sdk.tracking.queued.productView(" ", listener = l) },
            "empty category id" to { l -> sdk.tracking.queued.categoryView("", l) },
            "empty query" to { l -> sdk.tracking.queued.search("", listener = l) },
            "empty cart item id" to { l -> sdk.tracking.queued.addToCart(TrackingItem(""), listener = l) },
            "empty removed id" to { l -> sdk.tracking.queued.removeFromCart("", l) },
            "web push digest source" to { l -> sdk.tracking.queued.addToFavorites("37", TrackingSource(TrackingSourceType.WEB_PUSH_DIGEST, "d"), l) },
            "empty removed favorite" to { l -> sdk.tracking.queued.removeFromFavorites("", l) },
            "empty story id" to { l -> sdk.tracking.queued.storyView("", "34", "main_stories", l) },
            "empty slide id" to { l -> sdk.tracking.queued.storyClick("12", "", "main_stories", l) },
            "no stories code and no block loaded" to { l -> sdk.tracking.queued.storyView("12", "34", null, l) },
            "purchase without items" to { l -> sdk.tracking.queued.purchase(PurchaseTrackingRequest("A-1", 1.0, emptyList()), listener = l) },
            "transactional source on a purchase" to { l ->
                sdk.tracking.queued.purchase(PURCHASE, TrackingSource(TrackingSourceType.TRANSACTIONAL, "m"), l)
            },
            "empty custom event" to { l -> sdk.tracking.queued.custom("", listener = l) },
            "a reserved custom field" to { l -> sdk.tracking.queued.custom("e", customFields = mapOf("did" to "x"), listener = l) },
            "empty banner code" to { l -> sdk.tracking.queued.bannerView("101", "", l) },
        )

        calls.forEach { (name, call) ->
            val heard = Heard()
            call(heard)
            val ends = await(heard)
            assertTrue("$name: $ends", failure(ends) is SdkError.InvalidArgument)
            assertTrue(name, ends.single().second)
        }
        assertEquals(emptyList<Pair<String, JSONObject>>(), sentEvents(sdk))
    }

    @Test
    fun `a refused shop is shopBlocked and nothing goes`() {
        initAnswer = MockResponse().setResponseCode(402).setBody("""{"status":"error","message":"Restricted Mode"}""")
        val sdk = sdk(state = SdkState.REFUSED)
        val view = Heard()
        val custom = Heard()

        sdk.tracking.queued.productView("37", listener = view)
        sdk.tracking.queued.custom("e", listener = custom)

        assertEquals(listOf(SdkResult.Failure(SdkError.ShopBlocked) to true), await(view))
        assertEquals(listOf(SdkResult.Failure(SdkError.ShopBlocked) to true), await(custom))
        Thread.sleep(100)
        assertTrue(requests.none { it.first in EVENT_PATHS })
    }

    @Test
    fun `after release a queued event is notInitialized`() {
        val sdk = sdk()
        sdk.release()
        val view = Heard()
        val story = Heard()
        val popup = Heard()

        sdk.tracking.queued.productView("37", listener = view)
        sdk.tracking.queued.storyView("12", "34", "main_stories", story)
        sdk.tracking.queued.popupShown(181, popup)

        listOf(view, story, popup).forEach { assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(it)) }
        Thread.sleep(100)
        assertTrue(requests.none { it.first in EVENT_PATHS })
    }

    @Test
    fun `on the legacy core a queued event is notInitialized, sends nothing and says why in the log`() {
        val sdk = sdk(coreV2 = false)
        threads.awaitIdle()
        requests.clear()
        val view = Heard()
        val custom = Heard()

        sdk.tracking.queued.productView("37", listener = view)
        sdk.tracking.queued.custom("", listener = custom)

        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(view))
        // The core decides before the arguments.
        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(custom))
        threads.awaitIdle()
        Thread.sleep(100)
        assertTrue(requests.none { it.first in EVENT_PATHS })
        val mismatches = logged.filter { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context.containsKey("call") }.map { it.context["call"] }
        assertEquals(listOf("tracking.productView", "tracking.custom"), mismatches)
    }

    @Test
    fun `Java does not see the queued events`() {
        val getters = TrackingApi::class.java.methods.filter { it.name == "getQueued" }

        assertEquals(1, getters.size)
        assertTrue(getters.single().isSynthetic)
    }

    /** Calls the method of `tracking` with its contract arguments. */
    private fun regular(tracking: TrackingApi, method: String, args: JSONObject, listener: OnApiCallbackListener) = when (method) {
        "productView" -> tracking.productView(args.getString("itemId"), source(args), listener)
        "categoryView" -> tracking.categoryView(args.getString("categoryId"), listener)
        "search" -> tracking.search(args.getString("query"), results(args), listener)
        "addToCart" -> tracking.addToCart(item(args.getJSONObject("item")), source(args), listener)
        "syncCart" -> tracking.syncCart(args.getJSONArray("items").objects().map(::item), listener)
        "removeFromCart" -> tracking.removeFromCart(args.getString("itemId"), listener)
        "addToFavorites" -> tracking.addToFavorites(args.getString("itemId"), source(args), listener)
        "syncFavorites" -> tracking.syncFavorites(args.getJSONArray("itemIds").strings(), listener)
        "removeFromFavorites" -> tracking.removeFromFavorites(args.getString("itemId"), listener)
        "purchase" -> tracking.purchase(purchase(args.getJSONObject("request")), source(args), listener)
        "storyView" -> tracking.storyView(args.getString("storyId"), args.getString("slideId"), code(args), listener)
        "storyClick" -> tracking.storyClick(args.getString("storyId"), args.getString("slideId"), code(args), listener)
        "custom" -> tracking.custom(
            event = args.getString("event"),
            time = time(args),
            category = args.optString("category").ifEmpty { null },
            label = args.optString("label").ifEmpty { null },
            value = if (args.has("value")) args.getInt("value") else null,
            customFields = customFields(args),
            listener = listener,
        )
        "popupShown" -> tracking.popupShown(args.getInt("popupId"), listener)
        else -> fail("no call for $method")
    }

    /** Calls the method of `tracking.queued` with its contract arguments. */
    private fun queued(tracking: TrackingApi, method: String, args: JSONObject, listener: CompletionListener<Unit>) {
        val queued = tracking.queued
        when (method) {
            "productView" -> queued.productView(args.getString("itemId"), source(args), listener)
            "categoryView" -> queued.categoryView(args.getString("categoryId"), listener)
            "search" -> queued.search(args.getString("query"), results(args), listener)
            "addToCart" -> queued.addToCart(item(args.getJSONObject("item")), source(args), listener)
            "syncCart" -> queued.syncCart(args.getJSONArray("items").objects().map(::item), listener)
            "removeFromCart" -> queued.removeFromCart(args.getString("itemId"), listener)
            "addToFavorites" -> queued.addToFavorites(args.getString("itemId"), source(args), listener)
            "syncFavorites" -> queued.syncFavorites(args.getJSONArray("itemIds").strings(), listener)
            "removeFromFavorites" -> queued.removeFromFavorites(args.getString("itemId"), listener)
            "purchase" -> queued.purchase(purchase(args.getJSONObject("request")), source(args), listener)
            "storyView" -> queued.storyView(args.getString("storyId"), args.getString("slideId"), code(args), listener)
            "storyClick" -> queued.storyClick(args.getString("storyId"), args.getString("slideId"), code(args), listener)
            "custom" -> queued.custom(
                event = args.getString("event"),
                time = time(args),
                category = args.optString("category").ifEmpty { null },
                label = args.optString("label").ifEmpty { null },
                value = if (args.has("value")) args.getInt("value") else null,
                customFields = customFields(args),
                listener = listener,
            )
            "popupShown" -> queued.popupShown(args.getInt("popupId"), listener)
            else -> fail("no call for $method")
        }
    }

    private fun source(args: JSONObject) = args.optJSONObject("source")?.let { json ->
        TrackingSource(
            TrackingSourceType.values().first { it.name.replace("_", "").equals(json.getString("type"), ignoreCase = true) },
            json.getString("code"),
        )
    }

    private fun results(args: JSONObject) = args.optJSONArray("results")?.strings()
    private fun code(args: JSONObject) = args.optString("code").ifEmpty { null }
    private fun time(args: JSONObject) = if (args.has("time")) args.getInt("time") else null
    private fun customFields(args: JSONObject) =
        args.optJSONObject("customFields")?.let { fields -> fields.keys().asSequence().associateWith { fields.get(it) } }

    private fun item(json: JSONObject) = TrackingItem(
        id = json.getString("id"),
        quantity = json.optInt("quantity", 1),
        price = if (json.has("price")) json.getDouble("price") else null,
        fashionSize = json.optString("fashionSize").ifEmpty { null },
    )

    private fun purchase(json: JSONObject) = PurchaseTrackingRequest(
        orderId = json.getString("orderId"),
        orderPrice = json.getDouble("orderPrice"),
        items = json.getJSONArray("items").objects().map { PurchaseItemRequest(it.getString("id"), it.getInt("amount"), it.getDouble("price")) },
        isTaxFree = json.optBoolean("isTaxFree"),
    )

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    /** Each field of [expected] is in [actual] with the same value; objects in arrays compare the same way. */
    private fun assertSubset(name: String, expected: Any?, actual: Any?) {
        when (expected) {
            is JSONObject -> {
                assertTrue("$name: $actual is no object", actual is JSONObject)
                expected.keys().forEach { key ->
                    assertTrue("$name: no $key in $actual", (actual as JSONObject).has(key))
                    assertSubset("$name.$key", expected.get(key), actual.get(key))
                }
            }
            is JSONArray -> {
                assertTrue("$name: $actual is no array", actual is JSONArray && actual.length() == expected.length())
                for (index in 0 until expected.length()) assertSubset("$name[$index]", expected.get(index), (actual as JSONArray).get(index))
            }
            is Number -> {
                assertTrue("$name: $actual is no number", actual is Number)
                assertEquals(name, expected.toDouble(), (actual as Number).toDouble(), 0.0)
            }
            else -> assertEquals(name, expected, actual)
        }
    }

    private companion object {
        const val SHOP = "queued-tracking-shop"

        val EVENT_PATHS = setOf("push", "push/custom", "track/stories", "popup/showed")

        /** Every event method of `tracking.queued` but the banners, which `BannerEventsTest` covers. */
        val EVENT_METHODS = setOf(
            "productView", "categoryView", "search", "addToCart", "syncCart", "removeFromCart", "addToFavorites",
            "syncFavorites", "removeFromFavorites", "purchase", "storyView", "storyClick", "custom", "popupShown",
        )

        /** The methods and arguments the contract's fixtures leave out, in their format. */
        val OTHER_METHODS = listOf(
            """{"name":"search","call":{"method":"search","args":{"query":"boots","results":["37","42"]}}}""",
            """{"name":"search with an empty query","call":{"method":"search","args":{"query":""}},"error":{"type":"invalidArgument"}}""",
            """{"name":"removeFromCart","call":{"method":"removeFromCart","args":{"itemId":"37"}}}""",
            """{"name":"removeFromFavorites","call":{"method":"removeFromFavorites","args":{"itemId":"37"}}}""",
            """{"name":"storyClick","call":{"method":"storyClick","args":{"storyId":"12","slideId":"34","code":"main_stories"}}}""",
            """{"name":"storyClick of a virtual slide","call":{"method":"storyClick","args":{"storyId":"12","slideId":"12_1","code":"main_stories"}},"request":null}""",
            """{"name":"custom with its own time, label and value","call":{"method":"custom","args":{"event":"e","time":1700000000,"label":"l","value":3}}}""",
        )

        val PURCHASE = PurchaseTrackingRequest("A-1", 999.0, listOf(PurchaseItemRequest("37", 1, 999.0)))

        // components and popup_actions arrive as JSON strings, not objects.
        val POPUP_ANSWER = """
            {"status":"success","popup":{
              "id":7,"position":"centered",
              "components":"{\"header\":\"Title\",\"text\":\"Body\"}",
              "popup_actions":"{\"close\":{\"button_text\":\"Close\"}}"
            }}
        """.trimIndent()
    }
}
