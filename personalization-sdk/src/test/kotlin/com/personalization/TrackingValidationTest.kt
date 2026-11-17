package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.core.BridgeApi
import com.personalization.core.BridgeErrors
import com.personalization.core.SdkError
import com.personalization.core.SdkState
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The tracking methods' own checks (`contract/tracking.md`): on the new core an empty id or query, and
 * the error cases of the shared fixtures, are `invalidArgument` with the field's message, off the caller's
 * thread, and nothing reaches the server; the legacy core sends what it always sent.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingValidationTest {

    private lateinit var context: Context
    private var server = MockWebServer()

    /** Every request the server got, `/init` among them: its path and its body (or query). */
    private val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val threads = TrackingThreads()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
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
        // A test that runs both cores starts over in the middle: a server starts once.
        server = MockWebServer()
        requests.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                return MockResponse().setBody("{}")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
    }

    private fun sdk(coreV2: Boolean): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(shopId = SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false),
            coreV2,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    /** What an old listener heard: the code, the error behind it, and whether it ran off the caller's thread. */
    private class Refusal(val code: Int, val error: SdkError?, val offCaller: Boolean)

    private class Heard(private val caller: Thread) : OnApiCallbackListener() {
        val calls = LinkedBlockingQueue<Any>()

        override fun onSuccess(response: JSONObject?) {
            calls += "success"
        }

        override fun onError(code: Int, msg: String?) {
            calls += Refusal(code, BridgeErrors.currentError(), Thread.currentThread() !== caller)
        }

        fun refusal(name: String): Refusal =
            calls.poll(5, TimeUnit.SECONDS) as? Refusal ?: throw AssertionError("$name: no refusal")
    }

    /** The events the server got, without `/init`: on the new core once the queue was tried. */
    private fun events(sdk: SDK): List<Pair<String, JSONObject>> {
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        threads.awaitIdle()
        return requests.filter { it.first != "init" }
    }

    @Test
    fun `on the new core the error cases of the tracking fixtures are invalidArgument and send nothing`() {
        val sdk = sdk(coreV2 = true)
        val cases = ContractFixtures.cases("tracking").filter { it.has("error") }
        assertTrue(cases.isNotEmpty())

        cases.forEach { case ->
            val name = case.getString("name")
            assertEquals(name, "invalidArgument", case.getJSONObject("error").getString("type"))
            val call = case.getJSONObject("call")
            val heard = Heard(Thread.currentThread())
            call(sdk, call.getString("method"), call.getJSONObject("args"), heard)

            val refusal = heard.refusal(name)
            assertTrue("$name: ${refusal.error}", refusal.error is SdkError.InvalidArgument)
            assertTrue("$name: the old code stays negative", refusal.code < 0)
            assertTrue("$name: off the caller's thread", refusal.offCaller)
            assertEquals(name, emptyList<Pair<String, JSONObject>>(), events(sdk))
        }
    }

    @Test
    fun `on the new core every tracking method refuses an empty id or query with its field's name, and sends nothing`() {
        val sdk = sdk(coreV2 = true)
        val tracking = sdk.tracking
        val calls: List<Pair<String, (OnApiCallbackListener) -> Unit>> = listOf(
            "query is empty" to { listener -> tracking.search(query = "", listener = listener) },
            "itemId is empty" to { listener -> tracking.addToCart(TrackingItem(id = ""), listener = listener) },
            "itemId is empty" to { listener -> tracking.removeFromCart("", listener) },
            "itemId is empty" to { listener -> tracking.addToFavorites("", listener = listener) },
            "itemId is empty" to { listener -> tracking.removeFromFavorites("", listener) },
            "itemId is empty" to { listener -> tracking.productView(" ", listener = listener) },
            "slideId is empty" to { listener -> tracking.storyClick("1", "", "main_stories", listener) },
            "storyId is empty" to { listener -> tracking.storyClick("", "1", "main_stories", listener) },
        )

        calls.forEachIndexed { index, (message, call) ->
            val heard = Heard(Thread.currentThread())
            call(heard)

            val refusal = heard.refusal("call $index")
            assertEquals("call $index", SdkError.InvalidArgument(message), refusal.error)
            assertEquals("call $index", -1, refusal.code)
            assertTrue("call $index", refusal.offCaller)
        }
        assertEquals(emptyList<Pair<String, JSONObject>>(), events(sdk))
    }

    @Test
    fun `on the legacy core an empty id or query still goes, as before`() {
        val sdk = sdk(coreV2 = false)
        val tracking = sdk.tracking

        tracking.search(query = "")
        tracking.addToCart(TrackingItem(id = ""))
        tracking.removeFromCart("")
        tracking.addToFavorites("")
        tracking.removeFromFavorites("")
        tracking.storyClick("1", "", "main_stories")

        // The legacy core sends each request on a thread of its own: they reach the server in any order.
        val sent = events(sdk)
        val pushes = sent.filter { it.first == "push" }.map { it.second.getString("event") }
        assertEquals(listOf("cart", "remove_from_cart", "remove_wish", "search", "wish"), pushes.sorted())
        assertTrue(sent.filter { it.first == "push" && it.second.getString("event") != "search" }.all { body ->
            body.second.getJSONArray("items").getJSONObject(0).getString("id") == ""
        })
        val story = sent.single { it.first == "track/stories" }.second
        assertEquals("", story.getString("slide_id"))
        assertFalse(sent.any { it.first == "push" && it.second.optString("event") == "view" })
    }

    @Test
    fun `on both cores an event's transactional or webPushDigest source is invalidArgument and sends nothing, and setSource still takes them`() {
        listOf(true, false).forEach { coreV2 ->
            if (!coreV2) tearDown().also { setUp() }
            val sdk = sdk(coreV2)
            val tracking = sdk.tracking
            val order = PurchaseTrackingRequest(orderId = "A-1", orderPrice = 10.0, items = listOf(PurchaseItemRequest("37", 1, 10.0)))
            val refused = listOf(TrackingSourceType.TRANSACTIONAL, TrackingSourceType.WEB_PUSH_DIGEST).flatMap { type ->
                val source = TrackingSource(type, "c-1")
                val message = "source ${type.value} is not accepted for an event"
                listOf<Triple<String, Int, (OnApiCallbackListener) -> Unit>>(
                    Triple(message, -1) { listener -> tracking.productView("37", source, listener) },
                    Triple(message, -1) { listener -> tracking.addToCart(TrackingItem("37"), source, listener) },
                    Triple(message, -1) { listener -> tracking.addToFavorites("37", source, listener) },
                    // The code of purchase's other argument errors.
                    Triple(message, -2) { listener -> tracking.purchase(order, source, listener) },
                )
            }

            refused.forEachIndexed { index, (message, code, call) ->
                val name = "core v2 $coreV2, call $index"
                val heard = Heard(Thread.currentThread())
                call(heard)

                val refusal = heard.refusal(name)
                assertEquals(name, SdkError.InvalidArgument(message), refusal.error)
                assertEquals(name, code, refusal.code)
                // Off the caller's thread on the new core, as its other refusals; the legacy core answers at once.
                assertEquals(name, coreV2, refusal.offCaller)
            }
            assertEquals("core v2 $coreV2", emptyList<Pair<String, JSONObject>>(), events(sdk).filter { it.first == "push" })

            // The same source kept for 48 hours goes as before, in its own field and not as recommended_by;
            // an accepted source of the event goes as recommended_by.
            tracking.setSource(TrackingSource(TrackingSourceType.TRANSACTIONAL, "t-1"))
            tracking.productView("37", TrackingSource(TrackingSourceType.DYNAMIC, "block-1"))
            events(sdk)
            // The legacy core hands the request to its threads a moment later.
            val deadline = System.currentTimeMillis() + 5_000
            while (requests.none { it.first == "push" } && System.currentTimeMillis() < deadline) Thread.sleep(20)
            val view = requests.singleOrNull { it.first == "push" }?.second ?: throw AssertionError("core v2 $coreV2: no single push in $requests")
            assertEquals("core v2 $coreV2", "transactional", view.getJSONObject("source").getString("from"))
            assertEquals("core v2 $coreV2", "t-1", view.getJSONObject("source").getString("code"))
            assertEquals("core v2 $coreV2", "dynamic", view.getString("recommended_by"))
            assertEquals("core v2 $coreV2", "block-1", view.getString("recommended_code"))
        }
    }

    /** Calls the `tracking` method of a fixture with its contract arguments. */
    private fun call(sdk: SDK, method: String, args: JSONObject, listener: OnApiCallbackListener) {
        val tracking = sdk.tracking
        when (method) {
            "productView" -> tracking.productView(args.getString("itemId"), listener = listener)
            "purchase" -> {
                val request = args.getJSONObject("request")
                val items = request.getJSONArray("items")
                tracking.purchase(
                    PurchaseTrackingRequest(
                        orderId = request.getString("orderId"),
                        orderPrice = request.getDouble("orderPrice"),
                        items = List(items.length()) { items.getJSONObject(it) }.map { PurchaseItemRequest(it.getString("id"), it.getInt("amount"), it.getDouble("price")) },
                    ),
                    listener = listener,
                )
            }
            "custom" -> tracking.custom(
                event = args.getString("event"),
                customFields = args.optJSONObject("customFields")?.let { fields -> fields.keys().asSequence().associateWith { fields.get(it) } },
                listener = listener,
            )
            else -> fail("no call for the error case of $method")
        }
    }

    private companion object {
        const val SHOP = "validation-shop"
    }
}
