package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.SessionWindow
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.utils.AdvertisingUtils
import com.personalization.sdk.domain.repositories.AdvertisingRepository
import com.personalization.sdk.domain.usecases.userSettings.InitializeAdvertisingIdUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * The new core's `/init` as an app starts it, beside its answer: the advertising id read only with
 * `sendAdvertisingId`, once a launch, and never the empty one; no cookie, even with the app's
 * `CookieHandler` installed and after the customer is deleted; and an `/init` that cannot be built or
 * whose answer is JSON cut short, repeated as `retrying`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkInitRequestTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultCookies: CookieHandler? = CookieHandler.getDefault()

    /** Every request the server got: its path, query and `Cookie` header. */
    private val requests = LinkedBlockingQueue<RecordedRequest>()

    /** The body of the answer to `/init`. */
    @Volatile
    private var initBody: (did: String) -> String = { did -> JSONObject().put("did", did.ifEmpty { "new-did" }).put("seance", "S").toString() }

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
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        server = MockWebServer()
        requests.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                requests += request
                if (url.encodedPath != "/init") return MockResponse().setBody("{}")
                return MockResponse()
                    .setBody(initBody(url.queryParameter("did").orEmpty()))
                    .addHeader("Set-Cookie", "did=COOKIE; Max-Age=31536000; Path=/")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        CookieHandler.setDefault(defaultCookies)
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
    }

    private fun config(sendAdvertisingId: Boolean = false, apiDomain: String = server.url("/").toString().trimEnd('/')) = Rees46Config(
        shopId = SHOP,
        apiDomain = apiDomain,
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
        coreV2 = true,
        sendAdvertisingId = sendAdvertisingId,
    )

    /**
     * Holds the core's background work until [open]: the test puts its own advertising id reader in before
     * the first `/init` reads it, then lets everything go on [Dispatchers.IO].
     */
    private class Gate : CoroutineDispatcher() {
        private val held = ArrayList<Pair<CoroutineContext, Runnable>>()
        private var opened = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val now = synchronized(held) { opened.also { if (!it) held += context to block } }
            if (now) Dispatchers.IO.dispatch(context, block)
        }

        fun open() {
            val tasks = synchronized(held) {
                opened = true
                held.toList().also { held.clear() }
            }
            tasks.forEach { (context, block) -> Dispatchers.IO.dispatch(context, block) }
        }
    }

    /** The device's advertising id as the app's provider gives it: [answer], counting the reads. */
    private class Advertising(private val answer: () -> String) : AdvertisingRepository {
        val reads = AtomicInteger()

        override suspend fun fetchAdvertisingId(): String {
            reads.incrementAndGet()
            return answer()
        }
    }

    /** An instance whose advertising id comes from [advertising]; it is in place before `/init` reads it. */
    private fun start(sendAdvertisingId: Boolean, advertising: Advertising): SDK {
        val gate = Gate()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(io = gate) }
        sdk.initializeWith(context, config(sendAdvertisingId))
        sdk.initializeAdvertisingIdUseCase = InitializeAdvertisingIdUseCase(advertising, sdk.userSettingsRepository, context)
        gate.open()
        return sdk
    }

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    /** The `/init`s [sdk] sent are all answered and none is on its way. */
    private fun settle(sdk: SDK) {
        val init = (sdk.core as? CoreV2)?.init ?: throw AssertionError("not the new core")
        runBlocking { withTimeout(5_000) { init.awaitIdle() } }
    }

    private fun next(path: String): RecordedRequest {
        while (true) {
            val request = requests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no $path request")
            if (request.requestUrl?.encodedPath == "/$path") return request
        }
    }

    private fun taken(): List<RecordedRequest> = buildList { while (true) add(requests.poll() ?: break) }

    /** A request through the old callbacks; its one answer. */
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

    private fun partition() = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)

    // The advertising id (M10).

    @Test
    fun `with sendAdvertisingId the id goes with init, read once for every init of the launch`() {
        val advertising = Advertising { "AAA" }
        val sdk = start(sendAdvertisingId = true, advertising = advertising)

        assertEquals("AAA", next("init").requestUrl?.queryParameter("android_advertising_id"))
        awaitState(sdk, SdkState.READY)
        settle(sdk)
        // The window ends: the next request's new session sends /init again, with the id it read.
        partition().edit().putLong("sid_last_act", System.currentTimeMillis() - SessionWindow.WINDOW_MS - 1).commit()
        assertEquals("success", request(sdk))

        assertEquals("AAA", next("init").requestUrl?.queryParameter("android_advertising_id"))
        assertEquals(1, advertising.reads.get())
        assertEquals("AAA", DeviceStorage(context).advertisingId())
        assertTrue(logged.any { it.code == LogCodes.ADVERTISING_ID_SENT && it.level == LogLevel.DEBUG })
        assertTrue("never in the log", logged.none { "AAA" in it.message || "AAA" in it.context.toString() })
    }

    @Test
    fun `an advertising id of zeros or a blank one does not go`() {
        listOf(AdvertisingUtils.DEFAULT_ADVERTISING_ID, " ").forEach { value ->
            tearDown()
            setUp()
            val advertising = Advertising { value }
            val sdk = start(sendAdvertisingId = true, advertising = advertising)

            assertNull("'$value'", next("init").requestUrl?.queryParameter("android_advertising_id"))
            awaitState(sdk, SdkState.READY)
            assertEquals(1, advertising.reads.get())
        }
    }

    @Test
    fun `without sendAdvertisingId the id is never read and no profile carries it`() {
        val advertising = Advertising { "AAA" }
        val sdk = start(sendAdvertisingId = false, advertising = advertising)

        assertNull(next("init").requestUrl?.queryParameter("android_advertising_id"))
        awaitState(sdk, SdkState.READY)
        settle(sdk)
        assertEquals("success", request(sdk))

        assertEquals(0, advertising.reads.get())
        assertEquals(listOf("/products/get"), taken().map { it.requestUrl?.encodedPath })
    }

    @Test
    fun `an advertising id that cannot be read is logged and init goes without it`() {
        val advertising = Advertising { throw IllegalStateException("no Google Play services") }
        val sdk = start(sendAdvertisingId = true, advertising = advertising)

        assertNull(next("init").requestUrl?.queryParameter("android_advertising_id"))
        awaitState(sdk, SdkState.READY)
        val failed = logged.single { it.code == LogCodes.ADVERTISING_ID_FAILED }
        assertEquals(LogLevel.WARNING, failed.level)
        assertEquals(SHOP, failed.shopId)
    }

    // Cookies (M15).

    @Test
    fun `the new core sends no cookie, with the app's CookieHandler installed, nor after the customer is deleted`() {
        listOf<CookieHandler?>(null, CookieManager(null, CookiePolicy.ACCEPT_ALL)).forEach { handler ->
            tearDown()
            setUp()
            CookieHandler.setDefault(handler)
            val sdk = Rees46.initialize(context, config())
            next("init")
            awaitState(sdk, SdkState.READY)

            assertEquals("success", request(sdk))
            runBlocking { withTimeout(5_000) { sdk.deleteUserCredentials() } }
            awaitState(sdk, SdkState.READY)
            settle(sdk)

            val after = taken()
            assertEquals(listOf("/products/get", "/init"), after.map { it.requestUrl?.encodedPath })
            assertTrue("handler $handler", after.all { it.getHeader("Cookie") == null })
            assertNull("the new device's /init has no did", after.last().requestUrl?.queryParameter("did"))
        }
    }

    // An /init that cannot be built or whose answer is cut short (L7, L9).

    @Test
    fun `an init that cannot be built is logged as such, nothing goes, and it is repeated as retrying`() {
        val sdk = Rees46.initialize(context, config(apiDomain = "bad domain"))

        awaitState(sdk, SdkState.RETRYING)

        val built = logged.first { it.code == LogCodes.REQUEST_BUILD_FAILED }
        assertEquals(LogLevel.ERROR, built.level)
        assertEquals("init", built.context["path"])
        assertEquals("failed", logged.first { it.code == LogCodes.INIT_RETRY }.context["error"])
        assertTrue(logged.none { it.code == LogCodes.INIT_REFUSED })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an init answered with JSON cut short is decoding, and repeated as retrying without a did`() {
        initBody = { """{"did":"D""" }
        val sdk = Rees46.initialize(context, config())

        awaitState(sdk, SdkState.RETRYING)

        assertNull(sdk.deviceId)
        assertTrue(logged.any { it.code == LogCodes.DECODING_FAILED && it.context["path"] == "init" })
        assertEquals("decoding", logged.first { it.code == LogCodes.INIT_RETRY }.context["error"])
    }

    private companion object {
        const val SHOP = "init-request-shop"
    }
}
