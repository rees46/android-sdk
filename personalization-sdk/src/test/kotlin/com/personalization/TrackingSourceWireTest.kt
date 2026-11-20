package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
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
import org.junit.Assert.assertNotEquals
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
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/**
 * `TrackingSource.fromWire`, the bridges' source of the contract's type by its wire value: every value of the
 * contract goes as itself — `slider` and `agent_search` too, which `TrackingSourceType` gets in the major — in
 * an event's `recommended_by` on `tracking.queued` and in `source.from` of `setSource`; any other value is
 * `invalidArgument` before anything is sent.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingSourceWireTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
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
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8()) else JSONObject()
                requests += path to body
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                return MockResponse().setBody("""{"status":"success"}""")
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
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun sdk(): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(shopId = SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false),
            coreV2 = true,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private class Heard : CompletionListener<Unit> {
        val ends = CopyOnWriteArrayList<SdkResult<Unit>>()
        override fun onComplete(result: SdkResult<Unit>) {
            ends += result
        }
    }

    private fun await(heard: Heard): List<SdkResult<Unit>> {
        val until = System.currentTimeMillis() + 5_000
        while (heard.ends.isEmpty() && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return heard.ends.toList()
    }

    /** The `/push` events the server got once the queue was sent. */
    private fun sentPushes(sdk: SDK): List<JSONObject> {
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        Thread.sleep(50)
        return generateSequence { requests.poll() }.filter { it.first == "push" }.map { it.second }.toList()
    }

    @Test
    fun `each value of the contract goes as itself in recommended_by of every queued event with a source`() {
        val sdk = sdk()
        EVENT_TYPES.forEach { type ->
            val source = TrackingSource.fromWire(type, "code-$type")
            assertEquals(type, source.wireType)
            val calls: List<Pair<String, (CompletionListener<Unit>) -> Unit>> = listOf(
                "productView" to { l -> sdk.tracking.queued.productView("37", source, l) },
                "addToCart" to { l -> sdk.tracking.queued.addToCart(TrackingItem("37"), source, l) },
                "addToFavorites" to { l -> sdk.tracking.queued.addToFavorites("37", source, l) },
                "purchase" to { l -> sdk.tracking.queued.purchase(PURCHASE, source, l) },
            )
            calls.forEach { (method, call) ->
                val heard = Heard()
                call(heard)
                assertEquals("$type $method", listOf(SdkResult.Success(Unit)), await(heard))
                val body = sentPushes(sdk).single()
                assertEquals("$type $method", type, body.getString("recommended_by"))
                assertEquals("$type $method", "code-$type", body.getString("recommended_code"))
            }
        }
    }

    @Test
    fun `the regular methods send a raw source the same way`() {
        val sdk = sdk()
        sdk.tracking.productView("37", TrackingSource.fromWire("agent_search", "session-1"))
        val body = sentPushes(sdk).single()
        assertEquals("agent_search", body.getString("recommended_by"))
        assertEquals("session-1", body.getString("recommended_code"))
    }

    @Test
    fun `transactional and web_push_digest from the wire are refused as an event's source, as their cases are`() {
        val sdk = sdk()
        listOf("transactional", "web_push_digest").forEach { type ->
            val heard = Heard()
            sdk.tracking.queued.productView("37", TrackingSource.fromWire(type, "c"), heard)
            assertTrue("$type: ${heard.ends}", (await(heard).single() as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
        }
        assertEquals(emptyList<JSONObject>(), sentPushes(sdk))
    }

    @Test
    fun `setSource stores each value of the contract and the next push carries it as source from`() {
        val sdk = sdk()
        ALL_TYPES.forEach { type ->
            sdk.tracking.setSource(TrackingSource.fromWire(type, "stored-$type"))
            sdk.tracking.queued.categoryView("cat")
            val source = sentPushes(sdk).single().getJSONObject("source")
            assertEquals(type, source.getString("from"))
            assertEquals("stored-$type", source.getString("code"))
        }
    }

    @Test
    fun `an unknown or empty type is invalidArgument and nothing is made`() {
        listOf("unknown", "", "SLIDER", "agentSearch", "instantSearch", " dynamic").forEach { type ->
            try {
                TrackingSource.fromWire(type, "c")
                fail("$type was taken")
            } catch (e: SdkException) {
                assertTrue(type, e.error is SdkError.InvalidArgument)
            }
        }
    }

    @Test
    fun `a value with a case is that case, a raw one differs from its placeholder`() {
        TrackingSourceType.entries.forEach { case ->
            val source = TrackingSource.fromWire(case.value, "c")
            assertEquals(TrackingSource(case, "c"), source)
            assertEquals(TrackingSource(case, "c").hashCode(), source.hashCode())
            assertEquals(case.value, source.wireType)
        }
        val slider = TrackingSource.fromWire("slider", "s")
        assertEquals(TrackingSourceType.DYNAMIC, slider.type)
        assertNotEquals(TrackingSource(TrackingSourceType.DYNAMIC, "s"), slider)
        assertNotEquals(TrackingSource.fromWire("agent_search", "s"), slider)
        assertEquals(TrackingSource.fromWire("slider", "s"), slider)
        assertEquals(TrackingSource.fromWire("slider", "s").hashCode(), slider.hashCode())
        assertEquals("TrackingSource(type=slider, code=s)", slider.toString())
        assertEquals("TrackingSource(type=DYNAMIC, code=s)", TrackingSource(TrackingSourceType.DYNAMIC, "s").toString())
    }

    @Test
    fun `Java does not see the factory and the wire type`() {
        val factory = TrackingSource.Companion::class.java.declaredMethods.filter { it.name == "fromWire" }
        val getter = TrackingSource::class.java.declaredMethods.filter { it.name == "getWireType" }

        assertEquals(1, factory.size)
        assertTrue(factory.single().isSynthetic)
        assertEquals(1, getter.size)
        assertTrue(getter.single().isSynthetic)
        assertFalse(TrackingSource::class.java.declaredMethods.any { it.name == "fromWire" && !it.isSynthetic })
    }

    private companion object {
        const val SHOP = "tracking-source-wire-shop"

        /** The contract's values an event takes as its own source. */
        val EVENT_TYPES = listOf("dynamic", "chain", "bulk", "instant_search", "full_search", "stories", "slider", "agent_search")

        /** Every value of the contract's `TrackingSourceType`. */
        val ALL_TYPES = EVENT_TYPES + listOf("transactional", "web_push_digest")

        val PURCHASE = PurchaseTrackingRequest("A-1", 999.0, listOf(PurchaseItemRequest("37", 1, 999.0)))
    }
}
