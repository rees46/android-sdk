package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkState
import com.personalization.core.BridgeApi
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.HeldMain
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The instance and the facade on both cores: one instance per shop whoever asks and however often,
 * what `release()` leaves behind, the state an app sees, and the new core's `/init` as an app starts it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkInstanceTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** Every request the server got, `/init` among them. */
    private val requests = LinkedBlockingQueue<HttpUrl>()
    private val initRequests = AtomicInteger()

    /** The legacy path's requests run here, so a test waits until they are answered instead of sleeping. */
    private val threads = TrackingThreads()

    /** The answer to `/init`, given the did it carried. */
    @Volatile
    private var initAnswer: (did: String) -> MockResponse = { did ->
        MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "new-did" }).put("seance", "S").toString())
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        listOf(SHOP, OTHER).forEach { context.getSharedPreferences(StorageLayout(it, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit() }
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        // Robolectric's default network has no internet capability: give it one, as a device's has.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        // A test that runs both cores starts over in the middle: a server starts once.
        server = MockWebServer()
        requests.clear()
        initRequests.set(0)
        LegacyCore.threads = threads
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                requests += url
                if (url.encodedPath != "/init") return MockResponse().setBody("{}")
                initRequests.incrementAndGet()
                return initAnswer(url.queryParameter("did").orEmpty())
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
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config(shopId: String = SHOP, coreV2: Boolean = false, stream: String = "android", needReInitialization: Boolean = false, deviceId: String? = null) =
        Rees46Config(
            shopId = shopId,
            apiDomain = server.url("/").toString().trimEnd('/'),
            stream = stream,
            autoSendPushToken = false,
            needReInitialization = needReInitialization,
            logLevel = LogLevel.DEBUG,
            coreV2 = coreV2,
            deviceId = deviceId,
        )

    private fun nextRequest(path: String): HttpUrl {
        while (true) {
            val url = requests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no $path request")
            if (url.encodedPath == "/$path") return url
        }
    }

    /**
     * [sdk] sent one `/init` and has none on its way: the legacy core's requests have all been answered
     * (its threads are idle), the new core's `/init` is not running and went once.
     */
    private fun assertOneInit(sdk: SDK) {
        threads.awaitIdle()
        (sdk.core as? CoreV2)?.init?.let { init ->
            // The answer may still be handled after the state turned ready.
            runBlocking { withTimeout(5_000) { init.awaitIdle() } }
            assertFalse("an /init is on its way", init.isRunning)
            assertEquals(1, init.sent)
        }
        assertEquals(1, initRequests.get())
    }

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    private fun partition(shopId: String = SHOP) =
        context.getSharedPreferences(StorageLayout(shopId, null).partitionName, Context.MODE_PRIVATE)

    /** A request through the old callbacks, as an app makes it; its one answer. */
    @Suppress("DEPRECATION")
    private fun request(sdk: SDK): String {
        val answers = LinkedBlockingQueue<String>()
        sdk.getAsync("products/get", JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                answers += "success"
            }

            override fun onError(code: Int, msg: String?) {
                answers += "error:$code"
            }
        })
        return answers.poll(15, TimeUnit.SECONDS) ?: "none"
    }

    // The facade.

    @Test
    fun `initialize for a shop again returns its instance and sends no second init`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            val first = Rees46.initialize(context, config(coreV2 = coreV2))
            nextRequest("init")

            val second = Rees46.initialize(context, config(coreV2 = coreV2))

            assertSame(first, second)
            assertEquals(1, SdkRegistry.count())
            awaitState(first, SdkState.READY)
            assertOneInit(first)
            assertTrue(logged.none { it.code == LogCodes.INVALID_CONFIG })
        }
    }

    @Test
    fun `initialize with another config keeps the first one and says so`() {
        val first = Rees46.initialize(context, config(stream = "android"))

        val second = Rees46.initialize(context, config(stream = "flutter"))

        assertSame(first, second)
        assertEquals("android", first.config?.stream)
        val warning = logged.single { it.code == LogCodes.INVALID_CONFIG }
        assertEquals(LogLevel.WARNING, warning.level)
        assertEquals(SHOP, warning.shopId)
        assertEquals("already_initialized", warning.context["reason"])
    }

    @Test
    fun `initialize from several threads at once makes one instance`() {
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val made = CopyOnWriteArrayList<SDK>()
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                barrier.await()
                made += Rees46.initialize(context, config())
                done.countDown()
            }.start()
        }

        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, made.toSet().size)
        assertEquals(1, SdkRegistry.count())
        nextRequest("init")
        awaitState(made.first(), SdkState.READY)
        assertOneInit(made.first())
    }

    @Test
    fun `a lazy registration made from several threads at once makes one instance`() {
        Rees46.register(context, listOf(config(), config(OTHER)))
        val barrier = CyclicBarrier(4)
        val made = CopyOnWriteArrayList<SDK>()
        val done = CountDownLatch(4)
        repeat(4) {
            Thread {
                barrier.await()
                made += Rees46.instance(SHOP)
                done.countDown()
            }.start()
        }

        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, made.toSet().size)
        assertEquals(setOf(OTHER), Rees46.pendingShopIds())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the former names still work`() {
        Rees46.registerShops(context, listOf(config()))
        assertSame(Rees46.instance(SHOP), Rees46.getInstance(SHOP))
        assertSame(Rees46.instance(), Rees46.getInstance())
    }

    @Test
    fun `the facade's errors share Rees46Error and stay IllegalStateException`() {
        val unknown = assertThrows(Rees46Error::class.java) { Rees46.instance("nope") }
        assertTrue(unknown is UnknownShopIdException)
        Rees46.register(context, listOf(config(), config(OTHER)))
        val ambiguous: IllegalStateException = assertThrows(IllegalStateException::class.java) { Rees46.instance() }
        assertTrue(ambiguous is AmbiguousShopException && ambiguous is Rees46Error)
    }

    // release().

    @Test
    fun `after release every call fails with notInitialized and nothing is sent or deleted, on both cores`() {
        listOf(false, true).forEach { coreV2 ->
            if (coreV2) tearDown().also { setUp() }
            val sdk = Rees46.initialize(context, config(coreV2 = coreV2))
            nextRequest("init")
            awaitState(sdk, SdkState.READY)
            val did = sdk.deviceId

            sdk.release()

            assertEquals(SdkState.NOT_INITIALIZED, sdk.state)
            assertFalse(Rees46.isInitialized(SHOP))
            assertEquals("core $coreV2", "error:-1", request(sdk))
            try {
                runBlocking { sdk.deleteUserCredentials() }
                fail("expected SdkException")
            } catch (e: SdkException) {
                assertEquals(SdkError.NotInitialized, e.error)
            }
            // Nothing was deleted and no /init went.
            assertEquals(did, partition().getString("did", null))
            assertOneInit(sdk)
        }
    }

    @Test
    fun `a request waiting for a did when the instance is released fails with notInitialized`() {
        initAnswer = { MockResponse().setResponseCode(503) }
        val sdk = Rees46.initialize(context, config())
        nextRequest("init")
        val answers = LinkedBlockingQueue<Int>()
        @Suppress("DEPRECATION")
        sdk.getAsync("products/get", JSONObject(), object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                answers += code
            }
        })

        sdk.release()

        assertEquals(-1, answers.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `after release initialize makes a fresh instance`() {
        val first = Rees46.initialize(context, config(coreV2 = true))
        first.release()

        val second = Rees46.initialize(context, config(coreV2 = true))

        assertNotSame(first, second)
        assertSame(second, Rees46.instance(SHOP))
        awaitState(second, SdkState.READY)
        assertEquals(SdkState.NOT_INITIALIZED, first.state)
    }

    // The state an app sees.

    @Test
    @Suppress("DEPRECATION")
    fun `on the legacy core a new device goes from initializing to ready, heard once each on the main thread after the current state`() {
        val sdk = SDK()
        val heard = CopyOnWriteArrayList<Pair<SdkState, Boolean>>()
        sdk.onStateChange { heard += it to (Looper.myLooper() == Looper.getMainLooper()) }
        assertEquals(SdkState.NOT_INITIALIZED, sdk.state)

        sdk.initialize(context, SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false)
        assertEquals(SdkState.INITIALIZING, sdk.state)
        assertNull(sdk.deviceId)
        assertEquals("", sdk.getDid())
        awaitState(sdk, SdkState.READY)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(SdkState.NOT_INITIALIZED to true, SdkState.INITIALIZING to true, SdkState.READY to true), heard)
        assertEquals("new-did", sdk.deviceId)
        assertEquals("S", sdk.sessionId)
        assertEquals(SHOP, sdk.shopId)
    }

    @Test
    fun `on the legacy core a stored did is ready at once and a refusing init changes nothing`() {
        partition().edit().putString("did", "stored-did").commit()
        initAnswer = { MockResponse().setResponseCode(403) }

        val sdk = Rees46.initialize(context, config())

        assertEquals(SdkState.READY, sdk.state)
        nextRequest("init")
        assertEquals(SdkState.READY, sdk.state)
    }

    @Test
    fun `on the legacy core a failed init leaves a new device initializing, never refused`() {
        initAnswer = { MockResponse().setResponseCode(403) }
        val handled = CountDownLatch(1)
        SdkLogs.handler = SdkLogHandler { if (it.code == LogCodes.INIT_FAILED) handled.countDown() }

        val sdk = Rees46.initialize(context, config())

        // The legacy path logs the failure once it has handled it.
        assertTrue(handled.await(5, TimeUnit.SECONDS))
        assertEquals(SdkState.INITIALIZING, sdk.state)
    }

    @Test
    fun `on the legacy core a did of the config is taken while the shop has none`() {
        Rees46.initialize(context, config(deviceId = "did-from-rn"))

        assertEquals("did-from-rn", nextRequest("init").queryParameter("did"))
    }

    // The new core as an app starts it.

    @Test
    fun `the new core's init carries the stream of the config and tz, and no advertising id`() {
        val sdk = Rees46.initialize(context, config(coreV2 = true, stream = "flutter"))

        val init = nextRequest("init")
        assertTrue(sdk.core is CoreV2)
        assertEquals("flutter", init.queryParameter("stream"))
        assertTrue(init.queryParameter("tz")?.toIntOrNull() != null)
        assertNull(init.queryParameter("android_advertising_id"))
        assertNull(init.queryParameter("did"))
        awaitState(sdk, SdkState.READY)
        assertEquals("new-did", sdk.deviceId)
        // No profile/set with the advertising id either.
        assertNull(requests.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `on the new core a request waits for init and goes with the new did`() {
        val answer = CountDownLatch(1)
        initAnswer = { did ->
            answer.await(5, TimeUnit.SECONDS)
            MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "new-did" }).put("seance", "S").toString())
        }
        val sdk = Rees46.initialize(context, config(coreV2 = true))
        nextRequest("init")
        assertEquals(SdkState.INITIALIZING, sdk.state)

        val result = LinkedBlockingQueue<String>()
        Thread { result += request(sdk) }.start()
        assertNull(requests.poll(300, TimeUnit.MILLISECONDS))
        answer.countDown()

        assertEquals("new-did", nextRequest("products/get").queryParameter("did"))
        assertEquals("success", result.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `on the new core a refused shop fails every request with 402 and stays refused at the next launch`() {
        initAnswer = { MockResponse().setResponseCode(403) }
        val sdk = Rees46.initialize(context, config(coreV2 = true))
        awaitState(sdk, SdkState.REFUSED)

        assertEquals("error:402", request(sdk))
        assertTrue(logged.any { it.code == LogCodes.INIT_REFUSED && it.level == LogLevel.ERROR })

        sdk.release()
        initAnswer = { did -> MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "paid-did" }).toString()) }
        val next = Rees46.initialize(context, config(coreV2 = true))
        assertEquals(SdkState.REFUSED, next.state)
        awaitState(next, SdkState.READY)
        assertEquals("success", request(next))
    }

    @Test
    fun `on the new core needReInitialization gives a new did`() {
        partition().edit().putString("did", "old-did").putString("sid", "old-sid").commit()
        initAnswer = { did -> MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "fresh-did" }).put("seance", "S").toString()) }

        val sdk = Rees46.initialize(context, config(coreV2 = true, needReInitialization = true))

        assertNull(nextRequest("init").queryParameter("did"))
        awaitState(sdk, SdkState.READY)
        assertEquals("fresh-did", sdk.deviceId)
        assertTrue(logged.any { it.code == LogCodes.DEVICE_ID_RESET && it.context["reason"] == "need_reinitialization" })
    }

    @OptIn(BridgeApi::class)
    @Test
    fun `on the new core the session is the server's and the bridges hear it on the main thread`() {
        val heard = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.onSessionChange { heard += it to (Looper.myLooper() == Looper.getMainLooper()) }
        sdk.initializeWith(context, config(coreV2 = true))
        nextRequest("init")
        awaitState(sdk, SdkState.READY)
        // Handed to the main thread from the io dispatcher: run it here, on the test's main thread.
        while (heard.isEmpty() && main.runNext()) Unit

        assertEquals("S", sdk.sessionId)
        assertEquals(listOf("S" to true), heard)
    }

    @Test
    fun `on the new core a stored did is ready at once and its init still goes`() {
        partition().edit().putString("did", "stored-did").commit()

        val sdk = Rees46.initialize(context, config(coreV2 = true))

        assertEquals(SdkState.READY, sdk.state)
        assertEquals("stored-did", nextRequest("init").queryParameter("did"))
    }

    @Test
    fun `on the new core deleting the customer holds requests until the new did`() {
        val sdk = Rees46.initialize(context, config(coreV2 = true))
        awaitState(sdk, SdkState.READY)
        requests.clear()
        val second = CountDownLatch(1)
        initAnswer = { did ->
            second.await(5, TimeUnit.SECONDS)
            MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "next-did" }).put("seance", "S2").toString())
        }

        runBlocking { sdk.deleteUserCredentials() }
        assertEquals(SdkState.INITIALIZING, sdk.state)
        assertNull(nextRequest("init").queryParameter("did"))
        val result = LinkedBlockingQueue<String>()
        Thread { result += request(sdk) }.start()
        assertNull(requests.poll(300, TimeUnit.MILLISECONDS))
        second.countDown()

        assertEquals("next-did", nextRequest("products/get").queryParameter("did"))
        assertEquals("success", result.poll(5, TimeUnit.SECONDS))
    }

    private companion object {
        const val SHOP = "instance-shop"
        const val OTHER = "instance-other"
    }
}
