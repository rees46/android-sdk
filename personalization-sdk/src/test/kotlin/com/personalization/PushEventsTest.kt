package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushPayload
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.features.notification.domain.model.NotificationConstants
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.models.dto.notification.NotificationData
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
 * The push events of `core/push.md` and decision 0004 through `Rees46.handlePush` and the SDK, on a local
 * server: each event through the new core's queue to its own `track/…`, never `track/received`, once per
 * message; the shop the push names, or the only one; the callbacks of the shop and of the facade; and the
 * legacy core as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushEventsTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** Holds the answer to /init while it is not null. */
    @Volatile
    private var holdInit: java.util.concurrent.CountDownLatch? = null

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
        server = MockWebServer()
        requests.clear()
        logged.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                if (path == "init") {
                    holdInit?.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                }
                if (path == "segments/get") return MockResponse().setResponseCode(404)
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
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
        PushHub.reset()
    }

    private fun config(shop: String = SHOP, coreV2: Boolean = true) = Rees46Config(
        shopId = shop,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        coreV2 = coreV2,
        logLevel = LogLevel.DEBUG,
    )

    private fun sdk(shop: String = SHOP, coreV2: Boolean = true): SDK {
        val sdk = Rees46.initialize(context, config(shop, coreV2))
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private fun push(code: String = "m-1", type: String = "bulk", shop: String? = SHOP) = buildMap {
        put("id", code)
        put("type", type)
        put("title", "Title")
        put("body", "Body")
        shop?.let { put("shop_id", it) }
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

    private fun flush(sdk: SDK) = runBlocking { withTimeout(5_000) { sdk.flush() } }

    // The new core.

    @Test
    fun `each event goes through the queue to its own path with code, type, shop and did, and no sid`() {
        val sdk = sdk()
        listOf(PushEvent.DELIVERED, PushEvent.OPENED, PushEvent.CLICKED, PushEvent.CLOSED).forEach { event ->
            assertEquals(SHOP, Rees46.handlePush(push(code = "m-${event.name}"), event))
            flush(sdk)
            val (path, body) = next("track/delivered", "track/opened", "track/clicked", "track/closed", "track/received")
                ?: throw AssertionError("no request for $event")
            assertEquals("track/${event.name.lowercase()}", path)
            assertEquals("m-${event.name}", body.getString("code"))
            assertEquals("bulk", body.getString("type"))
            assertEquals(SHOP, body.getString("shop_id"))
            assertEquals("D1", body.getString("did"))
            assertEquals("android", body.getString("stream"))
            assertFalse("no sid in a push event", body.has("sid") || body.has("seance"))
            assertFalse("no empty field", body.keys().asSequence().any { body.opt(it) == "" })
        }
    }

    @Test
    fun `the old forms go to the new paths on the new core, never track received`() {
        val sdk = sdk()
        @Suppress("DEPRECATION")
        Rees46.handlePush(push(code = "a"), PushEventType.RECEIVED)
        sdk.notificationReceived(push(code = "b"))
        sdk.notificationClicked(Bundle().apply {
            putString(NotificationConstants.NOTIFICATION_TYPE, "chain")
            putString(NotificationConstants.NOTIFICATION_ID, "c")
        })
        @Suppress("DEPRECATION")
        Rees46.handlePush(mapOf("NOTIFICATION_TYPE" to "bulk", "NOTIFICATION_ID" to "d", "shop_id" to SHOP), PushEventType.CLICKED)
        flush(sdk)

        val got = List(4) { next("track/delivered", "track/clicked", "track/received") }
        assertEquals(
            listOf("track/delivered" to "a", "track/delivered" to "b", "track/clicked" to "c", "track/clicked" to "d"),
            got.map { it?.first to it?.second?.getString("code") },
        )
        assertNull(next("track/received", timeoutMs = 300))
    }

    @Test
    fun `one event of one message goes once in a process, a repeat returns the shop and calls nothing`() {
        val sdk = sdk()
        val clicked = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onClicked = { clicked += it.code }))

        assertEquals(SHOP, Rees46.handlePush(push(code = "m-1"), PushEvent.CLICKED))
        assertEquals(SHOP, Rees46.handlePush(push(code = "m-1"), PushEvent.CLICKED))
        sdk.notificationClicked(Bundle().apply {
            putString(NotificationConstants.NOTIFICATION_TYPE, "bulk")
            putString(NotificationConstants.NOTIFICATION_ID, "m-1")
        })
        // Another event of the message is its own.
        assertEquals(SHOP, Rees46.handlePush(push(code = "m-1"), PushEvent.CLOSED))
        flush(sdk)

        assertEquals("track/clicked", next("track/clicked", "track/closed")?.first)
        assertEquals("track/closed", next("track/clicked", "track/closed")?.first)
        assertNull(next("track/clicked", timeoutMs = 300))
        assertEquals(listOf("m-1"), clicked)
    }

    @Test
    fun `a tap sets the source of the next push events for a known type, and no from or code goes on the others`() {
        val sdk = sdk()
        Rees46.handlePush(push(code = "m-9", type = "chain"), PushEvent.CLICKED)
        sdk.tracking.productView("37")
        flush(sdk)

        val view = next("push")?.second ?: throw AssertionError("no push")
        assertEquals("chain", view.getJSONObject("source").getString("from"))
        assertEquals("m-9", view.getJSONObject("source").getString("code"))
        assertFalse(view.has("from"))

        // A type the source does not know (digest) sets none.
        sdk.tracking.setSource(com.personalization.api.models.tracking.TrackingSource(com.personalization.api.models.tracking.TrackingSourceType.BULK, "kept"))
        Rees46.handlePush(push(code = "m-10", type = "digest"), PushEvent.CLICKED)
        sdk.tracking.productView("38")
        flush(sdk)
        assertEquals("kept", next("push")?.second?.getJSONObject("source")?.getString("code"))
    }

    // Routing.

    @Test
    fun `the push goes to the shop it names, or the only one, and is dropped otherwise`() {
        sdk(SHOP)
        assertEquals(SHOP, Rees46.handlePush(push(shop = null), PushEvent.DELIVERED))
        assertNull(Rees46.handlePush(push(shop = "unknown"), PushEvent.DELIVERED))
        assertEquals("unknown_shop", logged.last { it.code == LogCodes.PUSH_DROPPED }.context["reason"])

        sdk(OTHER)
        assertEquals(OTHER, Rees46.handlePush(push(code = "x", shop = OTHER), PushEvent.DELIVERED))
        assertNull(Rees46.handlePush(push(code = "y", shop = null), PushEvent.DELIVERED))
        assertEquals("ambiguous_shop", logged.last { it.code == LogCodes.PUSH_DROPPED }.context["reason"])

        // Not the SDK's: no id or no type.
        assertNull(Rees46.handlePush(mapOf("shop_id" to SHOP, "type" to "bulk"), PushEvent.DELIVERED))
        val notOurs = logged.last { it.code == LogCodes.PUSH_DROPPED }
        assertEquals("not_sdk_push", notOurs.context["reason"])
        assertEquals(LogLevel.DEBUG, notOurs.level)
    }

    @Test
    fun `a shop registered but not created takes its push with its stored did`() {
        sdk(SHOP).release()
        requests.clear()
        Rees46.register(context, listOf(config(SHOP)))
        assertFalse(Rees46.isInitialized(SHOP))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        val (path, body) = next("track/delivered") ?: throw AssertionError("no track/delivered")
        assertEquals("track/delivered", path)
        assertEquals("D1", body.getString("did"))
    }

    // Callbacks.

    @Test
    fun `a shop's own callback wins over the facade's for its event, on the main thread, and one that throws is logged`() {
        PushHub.main = Executor { task -> android.os.Handler(Looper.getMainLooper()).post(task) }
        val sdk = sdk()
        val heard = CopyOnWriteArrayList<String>()
        val onMain = CopyOnWriteArrayList<Boolean>()
        fun note(name: String, payload: PushPayload) {
            heard += "$name:${payload.shopId}:${payload.code}"
            onMain += Looper.myLooper() == Looper.getMainLooper()
        }
        Rees46.setPushCallbacks(PushCallbacks(
            onDelivered = { note("facade-delivered", it) },
            onClicked = { note("facade-clicked", it) },
            onClosed = { throw IllegalStateException("the app's callback") },
        ))
        sdk.setPushCallbacks(PushCallbacks(onClicked = { note("shop-clicked", it) }))

        Rees46.handlePush(push(code = "1", shop = null), PushEvent.DELIVERED)
        Rees46.handlePush(push(code = "1"), PushEvent.CLICKED)
        Rees46.handlePush(push(code = "1"), PushEvent.CLOSED)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("facade-delivered:$SHOP:1", "shop-clicked:$SHOP:1"), heard)
        assertTrue(onMain.all { it })
        assertTrue(logged.any { it.code == LogCodes.CALLBACK_FAILED })
    }

    @Test
    fun `the old message listeners keep hearing deliveries beside the callbacks`() {
        val sdk = sdk()
        val global = CopyOnWriteArrayList<String>()
        val own = CopyOnWriteArrayList<NotificationData>()
        val delivered = CopyOnWriteArrayList<String>()
        Rees46.setOnMessageListener { shopId, data -> global += "$shopId:${data.id}" }
        @Suppress("DEPRECATION")
        sdk.setOnMessageListener { own += it }
        Rees46.setPushCallbacks(PushCallbacks(onDelivered = { delivered += it.code }))

        Rees46.handlePush(push(code = "1"), PushEvent.DELIVERED)
        @Suppress("DEPRECATION")
        Rees46.handlePush(push(code = "2"), PushEventType.RECEIVED)

        assertEquals(listOf("$SHOP:1", "$SHOP:2"), global)
        assertEquals(listOf("1", "2"), own.map { it.id })
        assertEquals(listOf("1", "2"), delivered)
    }

    // The legacy core.

    @Test
    fun `the legacy core sends a delivery to track received, as it did, and the new events directly`() {
        sdk(coreV2 = false)
        @Suppress("DEPRECATION")
        Rees46.handlePush(push(code = "old"), PushEventType.RECEIVED)
        assertEquals("old", next("track/received", "track/delivered")?.takeIf { it.first == "track/received" }?.second?.getString("code"))

        assertEquals(SHOP, Rees46.handlePush(push(code = "new"), PushEvent.DELIVERED))
        assertEquals("track/received", next("track/received", "track/delivered")?.first)
        Rees46.handlePush(push(code = "new"), PushEvent.OPENED)
        assertEquals("track/opened", next("track/opened")?.first)
        // No once-per-process rule on the legacy core.
        Rees46.handlePush(push(code = "new"), PushEvent.OPENED)
        assertEquals("track/opened", next("track/opened")?.first)
        assertNull(next("track/delivered", timeoutMs = 300))
    }

    // The token.

    @Test
    fun `on a new device the token waits for the did of init, goes once, and onToken hears it once`() {
        val hold = java.util.concurrent.CountDownLatch(1).also { holdInit = it }
        val heardTokens = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onToken = { heardTokens += it.token }))
        val sdk = Rees46.initialize(context, config().copy(autoSendPushToken = true))
        val answered = LinkedBlockingQueue<String>()

        SDK.onPushTokenReceived("tok", PushProvider.FCM)
        SDK.onPushTokenReceived("tok", PushProvider.FCM)
        sdk.setPushToken("tok", PushProvider.FCM, object : com.personalization.api.OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                answered += "success"
            }

            override fun onError(code: Int, msg: String?) {
                answered += "error:$code"
            }
        })
        assertNull("no token without a did", next("mobile_push_tokens", timeoutMs = 300))

        hold.countDown()
        val body = next("mobile_push_tokens")?.second ?: throw AssertionError("no token")
        assertEquals("D1", body.getString("did"))
        assertEquals("tok", body.getString("token"))
        assertEquals("firebase", body.getString("push_provider"))
        assertEquals("android", body.getString("platform"))
        assertEquals("success", answered.poll(5, TimeUnit.SECONDS))
        assertNull("once", next("mobile_push_tokens", timeoutMs = 300))
        assertEquals("tok", sdk.getPushToken(PushProvider.FCM))
        assertEquals(listOf("tok"), heardTokens)
    }

    // A notification the SDK draws.

    @Test
    fun `a notification the SDK draws is reported as opened, and its dismissal as closed`() {
        val sdk = sdk()
        val data = NotificationData(id = "drawn", title = "T", body = "B", icon = null, type = "bulk", actions = null, actionUrls = null, image = null, event = null)
        sdk.notificationHelper.createNotification(context, data, images = null)
        flush(sdk)
        val opened = next("track/opened")?.second ?: throw AssertionError("no track/opened")
        assertEquals("drawn", opened.getString("code"))

        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        val dismiss = shadowOf(notification.deleteIntent).savedIntent
        com.personalization.push.PushDismissReceiver().onReceive(context, dismiss)
        flush(sdk)
        val closed = next("track/closed")?.second ?: throw AssertionError("no track/closed")
        assertEquals("drawn", closed.getString("code"))
        assertEquals(SHOP, closed.getString("shop_id"))
    }

    private companion object {
        const val SHOP = "push-shop"
        const val OTHER = "push-other"
    }
}
