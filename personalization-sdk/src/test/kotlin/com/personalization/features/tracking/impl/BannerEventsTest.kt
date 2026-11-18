package com.personalization.features.tracking.impl

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
import com.personalization.api.managers.TrackingApi
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.NamespaceFixtures
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
import org.junit.Assert.assertFalse
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
import java.util.concurrent.TimeUnit

/**
 * `tracking.bannerView` and `bannerClick` as a bridge calls them: the cases of the contract's fixtures
 * (`contract/0/sliders.json`) go to `track/slider` through the new core's queue, and the listener hears,
 * on the main thread, that the queue took the event. On the legacy core, where apps run, they fail with
 * `notInitialized` and send nothing.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BannerEventsTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
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
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8()) else JSONObject()
                requests += path to body
                return when (path) {
                    "init" -> MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                    SLIDER -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setBody("{}")
                }
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

    private fun sdk(coreV2: Boolean): SDK {
        val config = Rees46Config(
            shopId = SHOP,
            apiDomain = server.url("/").toString().trimEnd('/'),
            autoSendPushToken = false,
            logLevel = LogLevel.DEBUG,
        )
        val sdk = Rees46.initialize(context, config, coreV2)
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    /** What a listener heard: the result, and whether on the main thread. */
    private class Heard : CompletionListener<Unit> {
        val ends = CopyOnWriteArrayList<Pair<SdkResult<Unit>, Boolean>>()
        override fun onComplete(result: SdkResult<Unit>) {
            ends += result to (Looper.myLooper() == Looper.getMainLooper())
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

    private fun call(tracking: TrackingApi, method: String, args: JSONObject, listener: CompletionListener<Unit>?) = when (method) {
        "bannerView" -> tracking.bannerView(args.getString("bannerId"), args.getString("code"), listener)
        "bannerClick" -> tracking.bannerClick(args.getString("bannerId"), args.getString("code"), listener)
        else -> error("unknown method $method")
    }

    /** The banner cases of the fixtures that pair with [kind]. */
    private fun cases(kind: String) = ContractFixtures.cases("sliders").filter {
        it.has(kind) && it.getJSONObject("call").getString("namespace") == "tracking"
    }

    /** The bodies of `track/slider` the server got once the queue was sent. */
    private fun sentEvents(sdk: SDK): List<JSONObject> {
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        threads.awaitIdle()
        Thread.sleep(100)
        return generateSequence { requests.poll() }.filter { it.first == SLIDER }.map { it.second }.toList()
    }

    @Test
    fun `the banner events of the fixtures go to track slider through the queue, accepted on the main thread`() {
        val sdk = sdk(coreV2 = true)
        val cases = cases("request")
        assertEquals(2, cases.size)

        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            val heard = Heard()

            call(sdk.tracking, call.getString("method"), call.getJSONObject("args"), heard)

            assertEquals(name, listOf(SdkResult.Success(Unit) to true), await(heard))
            val expected = case.getJSONObject("request")
            assertEquals(name, "POST", expected.getString("method"))
            assertEquals(name, SLIDER, expected.getString("path"))
            val body = sentEvents(sdk).single()
            val own = body.keys().asSequence().filter { it !in NamespaceFixtures.COMMON_FIELDS }.associateWith { NamespaceFixtures.normalize(body.get(it)) }
            assertEquals(name, NamespaceFixtures.normalize(expected.getJSONObject("body")), own)
            assertTrue(name, body.get("banner_id") is Number)
            assertFalse(name, body.has("recommended_by"))
            assertEquals(name, "D1", body.getString("did"))
            assertEquals(name, "S1", body.getString("sid"))
            assertEquals(name, SHOP, body.getString("shop_id"))
        }
    }

    @Test
    fun `a wrong banner id or an empty code is invalidArgument and sends nothing`() {
        val sdk = sdk(coreV2 = true)
        val cases = cases("error")
        assertEquals(3, cases.size)

        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            val heard = Heard()

            call(sdk.tracking, call.getString("method"), call.getJSONObject("args"), heard)

            val ends = await(heard)
            assertEquals(name, 1, ends.size)
            assertTrue("$name: ${ends.single()}", (ends.single().first as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
            assertTrue(name, ends.single().second)
        }
        assertEquals(emptyList<JSONObject>(), sentEvents(sdk))
        assertEquals(3, logged.count { it.code == LogCodes.INVALID_ARGUMENT && it.context["call"].toString().startsWith("tracking.banner") })
    }

    @Test
    fun `banner ids outside an unsigned 32-bit number are refused`() {
        val sdk = sdk(coreV2 = true)
        listOf("0", "-1", "4294967296", "1.5", " 101", "١٠١").forEach { id ->
            val heard = Heard()

            sdk.tracking.bannerView(id, "homepage_mobile", heard)

            assertTrue(id, (await(heard).single().first as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
        }
        val heard = Heard()
        sdk.tracking.bannerView("4294967295", "homepage_mobile", heard)
        assertEquals(listOf(SdkResult.Success(Unit) to true), await(heard))
        assertEquals(4294967295L, sentEvents(sdk).single().getLong("banner_id"))
    }

    @Test
    fun `events without a listener go in the order of the calls`() {
        val sdk = sdk(coreV2 = true)

        sdk.tracking.bannerView("101", "homepage_mobile")
        sdk.tracking.bannerClick("101", "homepage_mobile")
        sdk.tracking.bannerView("102", "homepage_mobile")

        val sent = sentEvents(sdk).map { it.getString("event") + ":" + it.getLong("banner_id") }
        assertEquals(listOf("view:101", "click:101", "view:102"), sent)
    }

    @Test
    fun `on the legacy core a banner event is notInitialized, sends nothing and says why in the log`() {
        val sdk = sdk(coreV2 = false)
        threads.awaitIdle()
        requests.clear()
        val view = Heard()
        val click = Heard()

        sdk.tracking.bannerView("101", "homepage_mobile", view)
        sdk.tracking.bannerClick("101", "", click)

        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(view))
        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(click))
        threads.awaitIdle()
        Thread.sleep(100)
        assertTrue(requests.none { it.first == SLIDER })
        val mismatches = logged.filter { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context.containsKey("call") }.map { it.context["call"] }
        assertEquals(listOf("tracking.bannerView", "tracking.bannerClick"), mismatches)
    }

    @Test
    fun `after release a banner event is notInitialized`() {
        val sdk = sdk(coreV2 = true)
        sdk.release()
        val heard = Heard()

        sdk.tracking.bannerClick("101", "homepage_mobile", heard)

        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), await(heard))
    }

    @Test
    fun `Java does not see the banner events`() {
        val methods = TrackingApi::class.java.methods.filter { it.name == "bannerView" || it.name == "bannerClick" }

        assertEquals(2, methods.size)
        assertTrue(methods.all { it.isSynthetic })
    }

    private companion object {
        const val SHOP = "banner-events-shop"
        const val SLIDER = "track/slider"
    }
}
