package com.personalization.features.notifications

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.PushProvider
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushToken
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.HeldMain
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageKeys
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
 * `sdk.notifications` as a bridge calls it: on the new core the push events go through the queue once per
 * message, without the push callbacks, and the token by the rules of `core/push.md`; on the legacy core,
 * where apps run, every call sends nothing and fails with `notInitialized`.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationsNamespaceTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultSources = PushTokenManager.sourceFactory
    private val threads = TrackingThreads()

    @Volatile
    private var initAnswer: MockResponse = MockResponse().setBody("""{"did":"D1","seance":"S1"}""")

    @Volatile
    private var tokenAnswer: MockResponse = MockResponse().setBody("""{"status":"success"}""")

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
        PushTokenManager.sourceFactory = { emptyList() }
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
                val text = request.body.readUtf8()
                val body = if (request.method == "POST" && text.isNotEmpty()) JSONObject(text) else JSONObject()
                requests += path to body
                seen += path to body
                return when (path) {
                    "init" -> initAnswer
                    "mobile_push_tokens" -> tokenAnswer
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
        PushTokenManager.sourceFactory = defaultSources
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
        PushHub.reset()
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    /** An instance whose listeners end on [main]: the test runs it, or they end on the calling thread. */
    private fun sdk(coreV2: Boolean = true, main: HeldMain? = null): SDK {
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main ?: Dispatchers.Unconfined }) }
        sdk.initializeWith(context, config(), coreV2 = coreV2)
        awaitState(sdk, SdkState.READY)
        return sdk
    }

    private fun flush(sdk: SDK) = runBlocking { withTimeout(5_000) { sdk.flush() } }

    /** Calls [track] with a listener and returns how it ended. */
    private fun ended(track: (CompletionListener<Unit>) -> Unit): SdkResult<Unit> {
        val ends = LinkedBlockingQueue<SdkResult<Unit>>()
        track(CompletionListener { ends += it })
        return ends.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("the call did not end")
    }

    private fun setPushToken(sdk: SDK, token: String, provider: PushProvider): SdkResult<Unit> = try {
        runBlocking { withTimeout(10_000) { sdk.notifications.setPushToken(token, provider) } }
        SdkResult.Success(Unit)
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The next request to one of [paths], skipping others; null when none came in [timeoutMs]. */
    private fun next(vararg paths: String, timeoutMs: Long = 5_000): Pair<String, JSONObject>? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return null
            val request = requests.poll(left, TimeUnit.NANOSECONDS) ?: return null
            if (request.first in paths) return request
        }
    }

    private fun partition() = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)

    // The new core: push events.

    @Test
    fun `each event goes through the queue to its own path with code, type, shop and did, and no sid`() {
        val sdk = sdk()
        val calls = listOf<Pair<String, (String, CompletionListener<Unit>) -> Unit>>(
            "track/delivered" to { code, listener -> sdk.notifications.trackDelivered(code, "bulk", listener) },
            "track/opened" to { code, listener -> sdk.notifications.trackOpened(code, "bulk", listener) },
            "track/clicked" to { code, listener -> sdk.notifications.trackClicked(code, "bulk", listener) },
            "track/closed" to { code, listener -> sdk.notifications.trackClosed(code, "bulk", listener) },
        )
        calls.forEach { (path, track) ->
            assertEquals(path, SdkResult.Success(Unit), ended { track("m-$path", it) })
            flush(sdk)

            val (sent, body) = next(*calls.map { it.first }.toTypedArray(), "track/received") ?: throw AssertionError("no request for $path")
            assertEquals(path, sent)
            assertEquals("m-$path", body.getString("code"))
            assertEquals("bulk", body.getString("type"))
            assertEquals(SHOP, body.getString("shop_id"))
            assertEquals("D1", body.getString("did"))
            assertFalse("no sid in a push event", body.has("sid") || body.has("seance"))
        }
    }

    @Test
    fun `an event goes without a listener too`() {
        val sdk = sdk()

        sdk.notifications.trackClosed("m-quiet", "chain")
        flush(sdk)

        assertEquals("m-quiet", next("track/closed")?.second?.getString("code"))
    }

    @Test
    fun `a repeat of an event sends nothing and ends in success, as one handlePush took`() {
        val sdk = sdk()

        assertEquals(SHOP, Rees46.handlePush(mapOf("id" to "m-1", "type" to "bulk", "shop_id" to SHOP), PushEvent.CLICKED))
        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackClicked("m-1", "bulk", it) })
        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackClicked("m-1", "bulk", it) })
        // Another event of the message is its own.
        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackDelivered("m-1", "bulk", it) })
        flush(sdk)

        val events = seen.map { it.first }.filter { it.startsWith("track/") }
        assertEquals(listOf("track/clicked", "track/delivered"), events)
    }

    @Test
    fun `the events the app reports call no push callback and no bridge listener`() {
        val sdk = sdk()
        val heard = CopyOnWriteArrayList<String>()
        sdk.notifications.setCallbacks(PushCallbacks(onDelivered = { heard += "own" }, onClicked = { heard += "own" }))
        Rees46.setPushCallbacks(PushCallbacks(onDelivered = { heard += "facade" }, onClicked = { heard += "facade" }))
        val bridge = Rees46.onPushEvent { _, _ -> heard += "bridge" }

        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackDelivered("m-2", "bulk", it) })
        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackClicked("m-2", "bulk", it) })
        flush(sdk)
        bridge.cancel()

        assertEquals(emptyList<String>(), heard)
    }

    @Test
    fun `a tap sets the source of the next tracking events for a type the source knows`() {
        val sdk = sdk()

        assertEquals(SdkResult.Success(Unit), ended { sdk.notifications.trackClicked("m-9", "chain", it) })
        sdk.tracking.productView("37")
        flush(sdk)

        val view = next("push")?.second ?: throw AssertionError("no push")
        assertEquals("chain", view.getJSONObject("source").getString("from"))
        assertEquals("m-9", view.getJSONObject("source").getString("code"))
        assertFalse(view.has("from") || view.has("code"))
    }

    @Test
    fun `an empty code or type is invalidArgument, logged, and nothing goes`() {
        val sdk = sdk()

        val emptyCode = ended { sdk.notifications.trackDelivered(" ", "bulk", it) }
        val emptyType = ended { sdk.notifications.trackOpened("m-3", "", it) }
        flush(sdk)

        assertTrue("$emptyCode", (emptyCode as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
        assertTrue("$emptyType", (emptyType as? SdkResult.Failure)?.error is SdkError.InvalidArgument)
        assertTrue(seen.none { it.first.startsWith("track/") })
        val refusals = logged.filter { it.code == LogCodes.INVALID_ARGUMENT }
        assertEquals(listOf("empty_code", "empty_type"), refusals.map { it.context["reason"] })
        assertEquals(listOf(NotificationsApiImpl.TRACK_DELIVERED, NotificationsApiImpl.TRACK_OPENED), refusals.map { it.context["call"] })
    }

    @Test
    fun `a refused shop gets shopBlocked and nothing goes`() {
        initAnswer = MockResponse().setResponseCode(402).setBody("""{"status":"error","message":"Restricted Mode"}""")
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { Dispatchers.Unconfined }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        awaitState(sdk, SdkState.REFUSED)

        assertEquals(SdkResult.Failure(SdkError.ShopBlocked), ended { sdk.notifications.trackClicked("m-4", "bulk", it) })
        assertNull("no source for a tap the queue refused", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        assertEquals(SdkResult.Failure(SdkError.ShopBlocked), setPushToken(sdk, "t-refused", PushProvider.FCM))
        assertTrue(seen.none { it.first.startsWith("track/") || it.first == "mobile_push_tokens" })
    }

    @Test
    fun `after release every call fails with notInitialized and sends nothing`() {
        val sdk = sdk()
        sdk.notifications.setPushToken("t-before", PushProvider.FCM, CompletionListener { })
        assertEquals("t-before", next("mobile_push_tokens")?.second?.getString("token"))
        sdk.release()

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), ended { sdk.notifications.trackClicked("m-5", "bulk", it) })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), setPushToken(sdk, "t-after", PushProvider.FCM))
        assertNull(sdk.notifications.getPushToken(PushProvider.FCM))
        assertTrue(seen.none { it.first.startsWith("track/") })
        assertTrue(seen.none { it.second.optString("token") == "t-after" })
    }

    @Test
    fun `the form with a listener ends once, on the main thread`() {
        val main = HeldMain()
        val sdk = sdk(main = main)
        val ends = CopyOnWriteArrayList<Pair<SdkResult<Unit>, Boolean>>()

        sdk.notifications.trackClicked("m-6", "bulk", CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        sdk.notifications.setPushToken("t-main", PushProvider.FCM, CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        while (ends.size < 2 && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(listOf(SdkResult.Success(Unit) to true, SdkResult.Success(Unit) to true), ends)
    }

    // The new core: the token and the callbacks.

    @Test
    fun `setPushToken sends the token of its provider at once, and the same token again this week sends nothing`() {
        val sdk = sdk()

        assertEquals(SdkResult.Success(Unit), setPushToken(sdk, "fcm-1", PushProvider.FCM))
        val fcm = next("mobile_push_tokens")?.second ?: throw AssertionError("no token")
        assertEquals("fcm-1", fcm.getString("token"))
        assertEquals("android", fcm.getString("platform"))
        assertEquals("firebase", fcm.getString("push_provider"))
        assertEquals("D1", fcm.getString("did"))

        assertEquals(SdkResult.Success(Unit), setPushToken(sdk, "hms-1", PushProvider.HMS))
        assertEquals("huawei", next("mobile_push_tokens")?.second?.getString("push_provider"))

        assertEquals(SdkResult.Success(Unit), setPushToken(sdk, "fcm-1", PushProvider.FCM))
        assertNull("sent this week", next("mobile_push_tokens", timeoutMs = 300))
        assertEquals(PushToken("fcm-1", PushProvider.FCM), sdk.notifications.getPushToken(PushProvider.FCM))
        assertEquals(PushToken("hms-1", PushProvider.HMS), sdk.notifications.getPushToken(PushProvider.HMS))
    }

    @Test
    fun `setPushToken on a 2xx answer that says error is decoding, and the same token goes again since the week stays free`() {
        val sdk = sdk()
        tokenAnswer = MockResponse().setBody("""{"status":"error","message":"Something went wrong"}""")

        assertEquals(SdkResult.Failure(SdkError.Decoding), setPushToken(sdk, "fcm-2", PushProvider.FCM))
        assertEquals("fcm-2", next("mobile_push_tokens")?.second?.getString("token"))
        assertNull("not recorded as sent", partition().getString(StorageKeys.pushToken("fcm"), null))

        tokenAnswer = MockResponse().setBody("""{"status":"success"}""")
        assertEquals(SdkResult.Success(Unit), setPushToken(sdk, "fcm-2", PushProvider.FCM))
        assertEquals("sent again", "fcm-2", next("mobile_push_tokens")?.second?.getString("token"))
        assertEquals("fcm-2", partition().getString(StorageKeys.pushToken("fcm"), null))
    }

    @Test
    fun `getPushToken without a provider is the one the shop sent last, or the device's, FCM first`() {
        val sdk = sdk()
        assertNull("no token yet", sdk.notifications.getPushToken())

        DeviceStorage(context).savePushToken("hms", "hms-device")
        assertEquals(PushToken("hms-device", PushProvider.HMS), sdk.notifications.getPushToken())
        DeviceStorage(context).savePushToken("fcm", "fcm-device")
        assertEquals("none sent: FCM before HMS", PushToken("fcm-device", PushProvider.FCM), sdk.notifications.getPushToken())

        // The shop sent its HMS token after its FCM one.
        partition().edit()
            .putString(StorageKeys.pushToken("fcm"), "fcm-device").putLong(StorageKeys.pushTokenDate("fcm"), 1_000L)
            .putString(StorageKeys.pushToken("hms"), "hms-device").putLong(StorageKeys.pushTokenDate("hms"), 2_000L)
            .commit()
        assertEquals(PushToken("hms-device", PushProvider.HMS), sdk.notifications.getPushToken())
        assertEquals(PushToken("fcm-device", PushProvider.FCM), sdk.notifications.getPushToken(PushProvider.FCM))
    }

    @Test
    fun `setCallbacks sets the shop's push callbacks, which a push then reaches`() {
        val sdk = sdk()
        val clicked = CopyOnWriteArrayList<String>()
        val callbacks = PushCallbacks(onClicked = { clicked += it.code })

        sdk.notifications.setCallbacks(callbacks)
        assertSame(callbacks, sdk.pushCallbacks)
        assertEquals(SHOP, Rees46.handlePush(mapOf("id" to "m-7", "type" to "bulk", "shop_id" to SHOP), PushEvent.CLICKED))
        flush(sdk)

        assertEquals(listOf("m-7"), clicked)
    }

    // The legacy core.

    @Test
    fun `an app's instance on the legacy core refuses every call, logs why and sends nothing`() {
        val main = HeldMain()
        val sdk = sdk(coreV2 = false, main = main)
        threads.awaitIdle()
        seen.clear()
        val ends = CopyOnWriteArrayList<SdkResult<Unit>>()

        sdk.notifications.trackDelivered("m-8", "bulk", CompletionListener { ends += it })
        sdk.notifications.trackOpened("m-8", "bulk", CompletionListener { ends += it })
        sdk.notifications.trackClicked("m-8", "bulk", CompletionListener { ends += it })
        sdk.notifications.trackClosed("m-8", "bulk", CompletionListener { ends += it })
        sdk.notifications.setPushToken("t-legacy", PushProvider.FCM, CompletionListener { ends += it })
        while (ends.size < 5 && main.runNext()) Unit
        val thrown = try {
            runBlocking { sdk.notifications.setPushToken("t-legacy", PushProvider.FCM) }
            null
        } catch (e: SdkException) {
            e
        }
        val token = sdk.notifications.getPushToken()
        sdk.notifications.setCallbacks(PushCallbacks(onClicked = { }))
        threads.awaitIdle()
        Thread.sleep(200)

        assertEquals(List(5) { SdkResult.Failure(SdkError.NotInitialized) }, ends)
        assertEquals(SdkError.NotInitialized, thrown?.error)
        assertTrue("${thrown?.message}", thrown?.message.orEmpty().contains("Rees46.initializeBridge"))
        assertNull(token)
        assertNull("the legacy core keeps its callbacks as they were", sdk.pushCallbacks)
        assertTrue("$seen", seen.none { it.first.startsWith("track/") || it.first == "mobile_push_tokens" })
        val mismatches = logged.filter { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"].toString().startsWith("notifications.") }
        val calls = mismatches.map { it.context["call"] }.toSet()
        assertEquals(
            setOf(
                NotificationsApiImpl.TRACK_DELIVERED, NotificationsApiImpl.TRACK_OPENED, NotificationsApiImpl.TRACK_CLICKED,
                NotificationsApiImpl.TRACK_CLOSED, NotificationsApiImpl.SET_PUSH_TOKEN, NotificationsApiImpl.GET_PUSH_TOKEN,
                NotificationsApiImpl.SET_CALLBACKS,
            ),
            calls,
        )
        assertTrue(mismatches.all { it.level == LogLevel.WARNING })
    }

    @Test
    fun `an instance never initialized refuses every call`() {
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { Dispatchers.Unconfined }) }

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), ended { sdk.notifications.trackDelivered("m-9", "bulk", it) })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), setPushToken(sdk, "t-none", PushProvider.FCM))
        assertNull(sdk.notifications.getPushToken())
        sdk.notifications.setCallbacks(PushCallbacks())
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `Java does not see the namespace`() {
        val getters = SDK::class.java.methods.filter { it.name == "getNotifications" }

        assertTrue(getters.isNotEmpty() && getters.all { it.isSynthetic })
    }

    private companion object {
        const val SHOP = "notifications-namespace-shop"
    }
}
