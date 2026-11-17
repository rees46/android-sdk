package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.BridgeApi
import com.personalization.core.BridgeErrors
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.CoreV2
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
import okhttp3.HttpUrl
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
import org.junit.Assert.assertSame
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
 * The API apps called before the facade, on both cores: the deprecated `SDK.initialize` called again on
 * the same object (2.49.0 started everything over; now a live shop stays as it was made), an object never
 * initialized, the legacy request around a deletion of the customer, and the bridges' results of the old
 * callbacks and of `queueEvent` on the legacy core.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkLegacyApiTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

    /** Every request the server got, `/init` among them, in order. */
    private val requests = LinkedBlockingQueue<HttpUrl>()
    private val bodies = CopyOnWriteArrayList<Pair<String, JSONObject>>()

    /** Holds the answer to the `/init` of [SHOP] while it is not null. */
    @Volatile
    private var holdInit: CountDownLatch? = null

    /** The dids `/init` hands out to a device without one, in turn. */
    private val newDids = LinkedBlockingQueue<String>()

    /** The status of the answer to `push/custom`. */
    @Volatile
    private var eventStatus = 200

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
        listOf(SHOP, OTHER).forEach { context.getSharedPreferences(StorageLayout(it, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit() }
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        // A test that runs both cores starts over in the middle: a server starts once.
        server = MockWebServer()
        requests.clear()
        bodies.clear()
        logged.clear()
        newDids.clear()
        holdInit = null
        eventStatus = 200
        LegacyCore.threads = threads
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                requests += url
                val path = url.encodedPath.removePrefix("/")
                if (request.method == "POST") bodies += path to JSONObject(request.body.readUtf8())
                return when (path) {
                    "init" -> {
                        if (url.queryParameter("shop_id") == SHOP) holdInit?.await(5, TimeUnit.SECONDS)
                        val did = url.queryParameter("did").orEmpty().ifEmpty { newDids.poll() ?: "new-did" }
                        MockResponse().setBody(JSONObject().put("did", did).put("seance", "S").toString())
                    }
                    "object" -> MockResponse().setBody("""{"x":1}""")
                    "array" -> MockResponse().setBody("[1]")
                    "push/custom" -> MockResponse().setResponseCode(eventStatus).setBody("{}")
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        holdInit?.countDown()
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
    }

    private val apiDomain: String get() = server.url("/").toString().trimEnd('/')

    @Suppress("DEPRECATION")
    private fun SDK.initializeOld(shopId: String = SHOP, stream: String = "android") =
        initialize(this@SdkLegacyApiTest.context, shopId, apiDomain = apiDomain, stream = stream, autoSendPushToken = false)

    /** Decides the process's core as the app's first call would. */
    private fun processCore(coreV2: Boolean) {
        CoreSelection.resolve(coreV2, "the-app")
    }

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    /** Every request has been answered: the legacy core's threads are idle, the new core's `/init` too. */
    private fun settle(sdk: SDK) {
        threads.awaitIdle()
        (sdk.core as? CoreV2)?.init?.let { init -> runBlocking { withTimeout(5_000) { init.awaitIdle() } } }
    }

    private fun taken(): List<HttpUrl> = buildList { while (true) add(requests.poll() ?: break) }

    /** A request through the old callbacks, as an app makes it; its one answer. */
    @Suppress("DEPRECATION")
    private fun request(sdk: SDK, path: String = "products/get"): String {
        val answers = LinkedBlockingQueue<String>()
        sdk.getAsync(path, JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                answers += "success"
            }

            override fun onError(code: Int, msg: String?) {
                answers += "error:$code"
            }
        })
        return answers.poll(15, TimeUnit.SECONDS) ?: "none"
    }

    // The deprecated initialize called again on the same object (H4).

    @Test
    fun `the deprecated initialize called twice the same way starts the object once, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            processCore(coreV2)
            val sdk = SDK()

            sdk.initializeOld()
            awaitState(sdk, SdkState.READY)
            sdk.initializeOld()
            settle(sdk)

            assertEquals("core $coreV2", coreV2, sdk.core is CoreV2)
            assertEquals("core $coreV2", 1, taken().count { it.encodedPath == "/init" })
            assertEquals(1, SdkRegistry.count())
            assertSame(sdk, SdkRegistry.byShopId(SHOP))
            assertTrue(logged.none { it.code == LogCodes.INVALID_CONFIG })
        }
    }

    @Test
    fun `the deprecated initialize after a start on the new core runs on it and says the flag differs, once`() {
        processCore(true)
        val sdk = SDK()

        sdk.initializeOld()
        sdk.initializeOld()

        assertTrue(sdk.core is CoreV2)
        val mismatch = logged.single { it.code == LogCodes.CORE_FLAG_MISMATCH }
        assertEquals(SHOP, mismatch.shopId)
        assertEquals(false, mismatch.context["requested"])
        assertEquals(true, mismatch.context["process"])
    }

    @Test
    fun `the deprecated initialize again with another stream keeps the first and says so, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            processCore(coreV2)
            val sdk = SDK()
            sdk.initializeOld(stream = "android")
            awaitState(sdk, SdkState.READY)

            sdk.initializeOld(stream = "flutter")

            val warning = logged.single { it.code == LogCodes.INVALID_CONFIG }
            assertEquals("already_initialized", warning.context["reason"])
            assertEquals("success", request(sdk))
            settle(sdk)
            val sent = taken()
            assertEquals("core $coreV2", 1, sent.count { it.encodedPath == "/init" })
            assertEquals("core $coreV2", "android", sent.single { it.encodedPath == "/products/get" }.queryParameter("stream"))
        }
    }

    @Test
    fun `the deprecated initialize after release starts the object over, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            processCore(coreV2)
            val sdk = SDK()
            sdk.initializeOld()
            awaitState(sdk, SdkState.READY)
            settle(sdk)
            sdk.release()
            assertEquals(SdkState.NOT_INITIALIZED, sdk.state)

            sdk.initializeOld()

            awaitState(sdk, SdkState.READY)
            assertEquals("core $coreV2", "success", request(sdk))
            settle(sdk)
            assertEquals("core $coreV2", 2, taken().count { it.encodedPath == "/init" })
            assertSame(sdk, SdkRegistry.byShopId(SHOP))
        }
    }

    @Test
    fun `the deprecated initialize for another shop stops the old start and moves the object to that shop, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            processCore(coreV2)
            holdInit = CountDownLatch(1)
            val sdk = SDK()
            sdk.initializeOld(SHOP)
            // A new device: the request waits for the did of the /init held on its way.
            val answers = LinkedBlockingQueue<Int>()
            @Suppress("DEPRECATION")
            sdk.getAsync("products/get", JSONObject(), object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    answers += 200
                }

                override fun onError(code: Int, msg: String?) {
                    answers += code
                }
            })

            sdk.initializeOld(OTHER)

            assertEquals("core $coreV2: the old start's request", -1, answers.poll(5, TimeUnit.SECONDS))
            assertSame(sdk, SdkRegistry.byShopId(OTHER))
            assertNull("core $coreV2: no longer the old shop's", SdkRegistry.byShopId(SHOP))
            assertEquals(setOf(OTHER), SdkRegistry.shopIds())
            assertEquals(OTHER, sdk.shopId)
            awaitState(sdk, SdkState.READY)
            assertEquals("success", request(sdk))
            holdInit?.countDown()
            settle(sdk)
            assertNull("core $coreV2: nothing more", answers.poll())
        }
    }

    // An object never initialized (L19).

    @Test
    fun `an object never initialized has no ids and fails what it is asked with notInitialized`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }

        assertEquals("", sdk.getDid())
        assertEquals("", sdk.getSid())
        assertNull(sdk.deviceId)
        assertNull(sdk.sessionId)
        assertEquals("", sdk.shopId)
        assertEquals(SdkState.NOT_INITIALIZED, sdk.state)
        listOf<suspend () -> Unit>({ sdk.flush() }, { sdk.deleteUserCredentials() }).forEach { call ->
            try {
                runBlocking { call() }
                fail("expected SdkException")
            } catch (e: SdkException) {
                assertEquals(SdkError.NotInitialized, e.error)
            }
        }
        val ends = LinkedBlockingQueue<SdkResult<Unit>>()
        sdk.flush(CompletionListener { ends += it })
        sdk.deleteUserCredentials(CompletionListener { ends += it })
        while (ends.size < 2 && main.runNext()) Unit
        assertEquals(List(2) { SdkResult.Failure(SdkError.NotInitialized) }, ends.toList())
        assertEquals(0, server.requestCount)
    }

    // The legacy request around a deletion (L18).

    @Test
    fun `on the legacy core a request made as a deletion begins goes with the old did, one after the wipe waits for the new did`() {
        val sdk = Rees46.initialize(context, Rees46Config(shopId = SHOP, apiDomain = apiDomain, autoSendPushToken = false))
        awaitState(sdk, SdkState.READY)
        settle(sdk)
        assertEquals("new-did", sdk.deviceId)
        taken()
        newDids += "next-did"

        // Before the wipe: the did is still there, and the request takes it.
        val start = sdk.registerManager.beginRestart()
        assertEquals("success", request(sdk))
        assertEquals("new-did", taken().single { it.encodedPath == "/products/get" }.queryParameter("did"))

        // After the wipe and before the new did: the request waits for it.
        sdk.storage?.deleteUserCredentials(includeQueue = false)
        val waiting = LinkedBlockingQueue<String>()
        Thread { waiting += request(sdk) }.start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (sdk.networkRepository.waitingForDid == 0 && System.nanoTime() < deadline) Thread.yield()
        assertEquals(1, sdk.networkRepository.waitingForDid)
        sdk.registerManager.restartAsNewDevice(start)

        assertEquals("success", waiting.poll(10, TimeUnit.SECONDS))
        settle(sdk)
        val sent = taken()
        assertEquals("the restart's /init asks for a new did", "", sent.single { it.encodedPath == "/init" }.queryParameter("did").orEmpty())
        assertEquals("next-did", sent.single { it.encodedPath == "/products/get" }.queryParameter("did"))
    }

    // The bridges' results (M13, L12).

    @Test
    fun `a bridge's listener gets the answer as a success, an object or an array, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            val sdk = Rees46.initialize(context, Rees46Config(shopId = SHOP, apiDomain = apiDomain, autoSendPushToken = false), coreV2)
            awaitState(sdk, SdkState.READY)
            val results = LinkedBlockingQueue<SdkResult<Any?>>()

            @Suppress("DEPRECATION")
            sdk.getAsync("object", JSONObject(), BridgeErrors.listener { results += it })
            val objectResult = results.poll(5, TimeUnit.SECONDS)
            @Suppress("DEPRECATION")
            sdk.getAsync("array", JSONObject(), BridgeErrors.listener { results += it })
            val arrayResult = results.poll(5, TimeUnit.SECONDS)

            assertEquals("""{"x":1}""", ((objectResult as SdkResult.Success).value as JSONObject).toString())
            assertEquals("[1]", ((arrayResult as SdkResult.Success).value as JSONArray).toString())
        }
    }

    @Test
    fun `on the legacy core a bridge's event goes at once with the common fields and completes with the server's answer on main`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, Rees46Config(shopId = SHOP, apiDomain = apiDomain, autoSendPushToken = false))
        awaitState(sdk, SdkState.READY)
        val ends = LinkedBlockingQueue<Pair<SdkResult<Unit>, Boolean>>()
        val listener = CompletionListener<Unit> { ends += it to (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) }
        fun next(): Pair<SdkResult<Unit>, Boolean>? {
            while (ends.isEmpty() && main.runNext()) Unit
            return ends.poll()
        }

        sdk.queueEvent("push/custom", JSONObject().put("event", "from_dart"), listener)
        assertEquals(SdkResult.Success(Unit) to true, next())
        threads.awaitIdle()
        val body = bodies.single { it.first == "push/custom" }.second
        assertEquals("from_dart", body.getString("event"))
        assertEquals(SHOP, body.getString("shop_id"))
        assertEquals("new-did", body.getString("did"))
        assertEquals("S", body.getString("sid"))

        eventStatus = 500
        sdk.queueEvent("push/custom", JSONObject().put("event", "failed"), listener)
        assertEquals(SdkResult.Failure(SdkError.Http(500)) to true, next())

        sdk.release()
        sdk.queueEvent("push/custom", JSONObject().put("event", "after"), listener)
        assertEquals(SdkResult.Failure(SdkError.NotInitialized) to true, next())
        threads.awaitIdle()
        assertFalse(bodies.any { it.second.optString("event") == "after" })
    }

    @Test
    fun `the error a bridge reports for each state`() {
        assertEquals(SdkError.NotInitialized, BridgeErrors.errorOf(SdkState.NOT_INITIALIZED))
        assertNull(BridgeErrors.errorOf(SdkState.INITIALIZING))
        assertNull(BridgeErrors.errorOf(SdkState.READY))
        assertNull(BridgeErrors.errorOf(SdkState.RETRYING))
        assertEquals(SdkError.ShopBlocked, BridgeErrors.errorOf(SdkState.REFUSED))
        // Outside an onError the SDK calls, a code alone tells the error.
        assertEquals(SdkError.Http(404), BridgeErrors.errorOf(404, null))
        assertEquals(SdkError.Network, BridgeErrors.errorOf(-1, null))
        assertEquals(SdkError.Network, BridgeErrors.errorOf(42, null))
    }

    private companion object {
        const val SHOP = "legacy-api-shop"
        const val OTHER = "legacy-api-other"
    }
}
