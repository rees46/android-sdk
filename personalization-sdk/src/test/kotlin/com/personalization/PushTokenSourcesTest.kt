package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.BridgeApi
import com.personalization.core.PushCallbacks
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import com.personalization.push.PushTokenSource
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
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The tokens the providers give at the start and later (`core/push.md`, «Токен»), on both cores: the
 * instance asks every provider on the device for its token once it may send, sends it with the `did` of
 * `/init`, and tells the app once; a provider that fails is logged and sends nothing; FCM's `onNewToken`
 * reaches every live shop; and the instance's own `onToken` wins over the facade's.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushTokenSourcesTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val tokenRequests = LinkedBlockingQueue<JSONObject>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultSources = PushTokenManager.sourceFactory

    /** Holds the answer to /init while it is not null. */
    @Volatile
    private var holdInit: CountDownLatch? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        PushHub.reset()
        PushHub.main = Executor { it.run() }
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        listOf(SHOP, OTHER).forEach { shop ->
            context.getSharedPreferences(StorageLayout(shop, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        }
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        LegacyCore.threads = threads
        PushTokenManager.sourceFactory = { emptyList() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                seen += path to body
                if (path == "mobile_push_tokens") tokenRequests += body
                if (path == "init") {
                    holdInit?.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                }
                return MockResponse().setBody("{}")
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
        PushTokenManager.sourceFactory = defaultSources
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
        PushHub.reset()
    }

    private fun config(shop: String = SHOP, coreV2: Boolean = true, autoSend: Boolean = true) = Rees46Config(
        shopId = shop,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = autoSend,
        coreV2 = coreV2,
        logLevel = LogLevel.DEBUG,
    )

    /** A provider on the device whose fetch answers [result] at once; [asked] counts down once it did. */
    private class FakeSource(
        override val provider: PushProvider,
        private val result: Result<String?>,
    ) : PushTokenSource {
        val asked = CountDownLatch(1)

        override fun isAvailable(context: Context): Boolean = true

        override fun fetchToken(context: Context, callback: (Result<String?>) -> Unit) {
            callback(result)
            asked.countDown()
        }
    }

    private fun ready(sdk: SDK) = runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }

    /** Nothing of the instance's token waits or is on its way any more. */
    private fun idle(sdk: SDK) {
        (sdk.core as? CoreV2)?.let { runBlocking { withTimeout(5_000) { sdk.pushTokenManager.core?.awaitIdle() } } } ?: threads.awaitIdle()
    }

    @Test
    fun `on the new core the token a provider gives at the start goes once with the did of init, and the app hears it once`() {
        val source = FakeSource(PushProvider.FCM, Result.success("tok"))
        PushTokenManager.sourceFactory = { listOf(source) }
        val facade = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onToken = { facade += "${it.provider}:${it.token}" }))
        val host = LinkedBlockingQueue<String>()
        val hold = CountDownLatch(1).also { holdInit = it }

        val sdk = Rees46.initialize(context, config())
        sdk.setOnPushTokenListener { token, provider -> host += "$provider:$token" }
        hold.countDown()

        val body = tokenRequests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no token went")
        assertEquals("tok", body.getString("token"))
        assertEquals("firebase", body.getString("push_provider"))
        assertEquals("android", body.getString("platform"))
        assertEquals("the did of init", "D1", body.getString("did"))
        // The listener runs on the thread that fetched the token, right after the send was handed over.
        assertEquals("FCM:tok", host.poll(5, TimeUnit.SECONDS))
        idle(sdk)
        assertEquals("once", 1, seen.count { it.first == "mobile_push_tokens" })
        assertTrue(host.isEmpty())
        assertEquals(listOf("FCM:tok"), facade)
        assertEquals("tok", sdk.getPushToken(PushProvider.FCM))
    }

    @Test
    fun `on the legacy core the token a provider gives on a new device waits for the did of init and goes once`() {
        val source = FakeSource(PushProvider.FCM, Result.success("tok"))
        PushTokenManager.sourceFactory = { listOf(source) }
        val facade = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onToken = { facade += "${it.provider}:${it.token}" }))
        val host = LinkedBlockingQueue<String>()
        val hold = CountDownLatch(1).also { holdInit = it }

        val sdk = Rees46.initialize(context, config(coreV2 = false))
        sdk.setOnPushTokenListener { token, provider -> host += "$provider:$token" }
        hold.countDown()

        val body = tokenRequests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no token went")
        assertEquals("tok", body.getString("token"))
        assertEquals("firebase", body.getString("push_provider"))
        assertEquals("the did of init, not none", "D1", body.getString("did"))
        assertEquals("FCM:tok", host.poll(5, TimeUnit.SECONDS))
        threads.awaitIdle()
        assertEquals("once", 1, seen.count { it.first == "mobile_push_tokens" })
        // As 2.49.0, a new device asks the providers before /init and again after it: the app's own
        // listener may hear the token again, the facade's onToken once.
        assertTrue(host.all { it == "FCM:tok" })
        assertEquals(listOf("FCM:tok"), facade)
    }

    @Test
    fun `a provider whose fetch fails is logged and sends nothing`() {
        val source = FakeSource(PushProvider.FCM, Result.failure(IOException("no token")))
        PushTokenManager.sourceFactory = { listOf(source) }

        val sdk = Rees46.initialize(context, config())
        ready(sdk)
        assertTrue(source.asked.await(5, TimeUnit.SECONDS))
        idle(sdk)

        val failed = logged.single { it.code == LogCodes.PUSH_TOKEN_FAILED }
        assertEquals(LogLevel.WARNING, failed.level)
        assertEquals("fcm", failed.context["provider"])
        assertTrue(seen.none { it.first == "mobile_push_tokens" })
    }

    @Test
    fun `a provider that gives no token yet sends nothing and tells nobody`() {
        val source = FakeSource(PushProvider.HMS, Result.success(""))
        PushTokenManager.sourceFactory = { listOf(source) }
        val host = CopyOnWriteArrayList<String>()

        val sdk = SDK()
        sdk.setOnPushTokenListener { token, _ -> host += token }
        sdk.initializeWith(context, config(coreV2 = false))
        assertTrue(source.asked.await(5, TimeUnit.SECONDS))
        idle(sdk)

        assertTrue(host.isEmpty())
        assertTrue(seen.none { it.first == "mobile_push_tokens" })
    }

    @Test
    fun `on the legacy core a listener set before the instance is initialized hears each provider token once, and one set later too`() {
        PushTokenManager.sourceFactory = { listOf(FakeSource(PushProvider.FCM, Result.success("tok"))) }
        val early = CopyOnWriteArrayList<String>()
        // A device the server knows: the providers are asked once, at the start.
        context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().putString("did", "STORED").commit()
        val sdk = SDK()
        sdk.setOnPushTokenListener { token, provider -> early += "$provider:$token" }

        sdk.initializeWith(context, config(coreV2 = false))

        assertEquals("tok", tokenRequests.poll(5, TimeUnit.SECONDS)?.getString("token"))
        idle(sdk)

        val late = CopyOnWriteArrayList<String>()
        sdk.setOnPushTokenListener { token, provider -> late += "$provider:$token" }
        SDK.onPushTokenReceived("tok2", PushProvider.FCM)
        assertEquals("tok2", tokenRequests.poll(5, TimeUnit.SECONDS)?.getString("token"))
        idle(sdk)
        assertEquals(listOf("FCM:tok2"), late)
        assertEquals("the first listener was replaced", listOf("FCM:tok"), early)
    }

    @Test
    fun `a new token FCM hands its service reaches every live shop, and each sends it once`() {
        val shop = Rees46.initialize(context, config(SHOP))
        val other = Rees46.initialize(context, config(OTHER))
        listOf(shop, other).forEach { ready(it); idle(it) }

        MessagingService().onNewToken("t2")
        // Nothing to report: no shop hears an empty token.
        MessagingService().onNewToken("")

        val first = tokenRequests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no token went")
        val second = tokenRequests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("one shop sent nothing")
        listOf(shop, other).forEach(::idle)
        assertEquals(setOf(SHOP, OTHER), setOf(first.getString("shop_id"), second.getString("shop_id")))
        assertTrue(listOf(first, second).all { it.getString("token") == "t2" && it.getString("did") == "D1" })
        assertEquals(2, seen.count { it.first == "mobile_push_tokens" })
        assertEquals("t2", DeviceStorage(context).pushToken("fcm"))
    }

    @Test
    fun `the instance's own onToken wins over the facade's, which hears each token once through the shops without one`() {
        val shop = Rees46.initialize(context, config(SHOP, autoSend = false))
        ready(shop)
        val own = CopyOnWriteArrayList<String>()
        val facade = CopyOnWriteArrayList<String>()
        val bridge = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onToken = { facade += it.token }))
        shop.setPushCallbacks(PushCallbacks(onToken = { own += it.token }))
        Rees46.onPushToken { bridge += it.token }

        SDK.onPushTokenReceived("t", PushProvider.FCM)
        SDK.onPushTokenReceived("t", PushProvider.FCM)
        SDK.onPushTokenReceived("t2", PushProvider.FCM)

        assertEquals(listOf("t", "t2"), own)
        assertEquals("the shop's own callback took them", emptyList<String>(), facade)
        assertEquals(listOf("t", "t2"), bridge)

        // A shop without callbacks of its own: the facade hears each token once in the process.
        val other = Rees46.initialize(context, config(OTHER, autoSend = false))
        ready(other)
        SDK.onPushTokenReceived("t3", PushProvider.FCM)
        SDK.onPushTokenReceived("t3", PushProvider.FCM)

        assertEquals(listOf("t", "t2", "t3"), own)
        assertEquals(listOf("t3"), facade)
        assertEquals(listOf("t", "t2", "t3"), bridge)
    }

    private companion object {
        const val SHOP = "token-shop"
        const val OTHER = "token-other"
    }
}
