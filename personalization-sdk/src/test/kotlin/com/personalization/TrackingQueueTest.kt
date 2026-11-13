package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
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
import com.personalization.logs.SdkLogs
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
import org.junit.Assert.assertNull
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tracking through the SDK on the new core, as an app calls it: the shared contract fixtures
 * (`contract/0/tracking.json`) on the wire, the old listeners hearing the server's answer once the
 * event went, the stored source on `/push` only, and `flush()` on both cores.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingQueueTest {

    private lateinit var context: Context
    private var server = MockWebServer()

    /** Each request the server got: its path and its body (or query, for a GET). */
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()

    /** Held answers to events while it is not null. */
    @Volatile
    private var holdEvents: CountDownLatch? = null
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
                holdEvents?.await(5, TimeUnit.SECONDS)
                return MockResponse().setBody("""{"answer":"$path"}""")
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
    }

    private fun sdk(coreV2: Boolean = true): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(
                shopId = SHOP,
                apiDomain = server.url("/").toString().trimEnd('/'),
                autoSendPushToken = false,
                coreV2 = coreV2,
            )
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    /** The next request to [path], skipping others (`/init`, the legacy `profile/set`). */
    private fun next(path: String): JSONObject {
        while (true) {
            val (got, body) = requests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no $path request")
            if (got == path) return body
        }
    }

    private class Heard : OnApiCallbackListener() {
        val calls = LinkedBlockingQueue<String>()
        override fun onSuccess(response: JSONObject?) {
            calls += "success:$response"
        }

        override fun onError(code: Int, msg: String?) {
            calls += "error:$code"
        }
    }

    @Test
    fun `every case of the tracking fixtures on the new core`() {
        val sdk = sdk()
        ContractFixtures.cases("tracking").forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            val heard = Heard()
            call(sdk, call.getString("method"), call.getJSONObject("args"), heard)
            if (case.has("error")) {
                // Refused before the queue, at once: the old callbacks' client-side codes are negative.
                val answer = heard.calls.poll(1, TimeUnit.SECONDS)
                assertTrue("$name: $answer", answer?.startsWith("error:-") == true)
                return@forEach
            }
            val request = case.getJSONObject("request")
            val body = next(request.getString("path"))
            assertSubset(name, request.getJSONObject("body"), body)
            // The core's own fields, put on when it went.
            assertEquals(name, SHOP, body.getString("shop_id"))
            assertEquals(name, "D1", body.getString("did"))
            assertEquals(name, "S1", body.getString("sid"))
            assertEquals(name, "S1", body.getString("seance"))
            assertEquals(name, "android", body.getString("stream"))
            assertTrue(name, body.getString("segment").isNotEmpty())
            assertFalse(name, body.has("source"))
            assertEquals(name, request.getString("path") == "push/custom", body.has("time"))
            assertTrue(name, heard.calls.poll(5, TimeUnit.SECONDS)?.startsWith("success") == true)
        }
    }

    @Test
    fun `an old listener hears the server's answer once the event went`() {
        val sdk = sdk()
        val hold = CountDownLatch(1).also { holdEvents = it }
        val heard = Heard()

        sdk.tracking.productView("37", listener = heard)
        next("push")
        assertNull("not before the answer", heard.calls.poll(200, TimeUnit.MILLISECONDS))
        hold.countDown()

        assertEquals("""success:{"answer":"push"}""", heard.calls.poll(5, TimeUnit.SECONDS))
        assertNull("once", heard.calls.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `the stored source goes on push when it is sent, and not on push custom`() {
        val sdk = sdk()
        sdk.tracking.setSource(TrackingSource(TrackingSourceType.CHAIN, "c1"))

        sdk.tracking.productView("37")
        sdk.tracking.custom("added_to_compare")

        val push = next("push")
        assertEquals("chain", push.getJSONObject("source").getString("from"))
        assertEquals("c1", push.getJSONObject("source").getString("code"))
        assertFalse(next("push/custom").has("source"))
    }

    @Test
    fun `flush sends the queue on the new core and returns at once on the legacy one, after release notInitialized`() {
        listOf(true, false).forEach { coreV2 ->
            if (!coreV2) tearDown().also { setUp() }
            val sdk = sdk(coreV2)
            sdk.tracking.productView("37")

            runBlocking { withTimeout(5_000) { sdk.flush() } }

            next("push")
            threads.awaitIdle()
            sdk.release()
            try {
                runBlocking { sdk.flush() }
                fail("expected SdkException")
            } catch (e: SdkException) {
                assertEquals("core v2: $coreV2", SdkError.NotInitialized, e.error)
            }
        }
    }

    @Test
    fun `the Java form of flush ends once on the main thread`() {
        val sdk = sdk()
        val ends = LinkedBlockingQueue<Pair<SdkResult<Unit>, Boolean>>()

        sdk.flush(CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        val deadline = System.currentTimeMillis() + 5_000
        while (ends.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        assertEquals(SdkResult.Success(Unit) to true, ends.single())
    }

    /** Calls the `tracking` method of a fixture with its contract arguments. */
    private fun call(sdk: SDK, method: String, args: JSONObject, listener: OnApiCallbackListener) {
        val tracking = sdk.tracking
        when (method) {
            "productView" -> tracking.productView(args.getString("itemId"), args.optJSONObject("source")?.let(::source), listener)
            "categoryView" -> tracking.categoryView(args.getString("categoryId"), listener)
            "addToCart" -> tracking.addToCart(item(args.getJSONObject("item")), listener = listener)
            "syncCart" -> tracking.syncCart(args.getJSONArray("items").objects().map(::item), listener)
            "syncFavorites" -> tracking.syncFavorites(args.getJSONArray("itemIds").strings(), listener)
            "purchase" -> tracking.purchase(purchase(args.getJSONObject("request")), listener = listener)
            "storyView" -> tracking.storyView(args.getString("storyId"), args.getString("slideId"), args.optString("code").ifEmpty { null }, listener)
            "custom" -> tracking.custom(
                event = args.getString("event"),
                category = args.optString("category").ifEmpty { null },
                customFields = args.optJSONObject("customFields")?.let { fields -> fields.keys().asSequence().associateWith { fields.get(it) } },
                listener = listener,
            )
            else -> fail("no call for $method")
        }
    }

    private fun source(json: JSONObject) = TrackingSource(
        TrackingSourceType.values().first { it.name.replace("_", "").equals(json.getString("type"), ignoreCase = true) },
        json.getString("code"),
    )

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
            // The SDK has always sent an item's amount as a string ("2"): the server reads both.
            is Number -> assertEquals(name, expected.toDouble(), actual.toString().toDouble(), 0.0)
            else -> assertEquals(name, expected, actual)
        }
    }

    private companion object {
        const val SHOP = "tracking-shop"
    }
}
