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
import com.personalization.features.notification.data.mapper.toNotificationData
import com.personalization.features.notification.domain.model.NotificationConstants
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationNavigationHelper
import com.personalization.features.notification.service.mapper.toNotificationData
import com.personalization.push.PushDismissReceiver
import com.personalization.push.PushExtras
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
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

    /** Every request the server got, in order, whatever the test took from [requests]. */
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultWork = PushDismissReceiver.work

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
        seen.clear()
        logged.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                seen += path to body
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
        PushDismissReceiver.work = defaultWork
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

    /** What the server got and the test has not taken yet. */
    private fun drained(): List<Pair<String, JSONObject>> = buildList { while (true) add(requests.poll() ?: break) }

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

    /** The partition of [shop], where an earlier launch left its did. */
    private fun partition(shop: String = SHOP) =
        context.getSharedPreferences(StorageLayout(shop, null).partitionName, Context.MODE_PRIVATE)

    @Test
    fun `a shop registered but not created sends its push with its stored did and no init, and becomes a full instance on use`() {
        // An earlier launch's did; the server would hand out another one.
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP).copy(autoSendPushToken = true)))
        assertFalse(Rees46.isInitialized(SHOP))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        assertTrue(light.pushOnly)
        assertEquals(SdkState.READY, light.state)
        val init = (light.core as com.personalization.core.internal.CoreV2).init ?: throw AssertionError("no init")
        runBlocking { withTimeout(5_000) { init.awaitPrepared() } }
        flush(light)

        assertEquals("STORED", next("track/delivered")?.second?.getString("did"))
        assertEquals("no /init went, nor will", 0, init.sent)
        assertFalse(init.isRunning)
        assertEquals(emptyList<String>(), drained().map { it.first }.filter { it in listOf("init", "mobile_push_tokens", "profile/set") })

        // The app's first use: a full instance with a full start takes its place.
        val full = Rees46.instance(SHOP)
        assertTrue(full !== light)
        assertFalse(full.pushOnly)
        assertEquals(SdkState.NOT_INITIALIZED, light.state)
        assertEquals("STORED", next("init")?.second?.getString("did"))
        assertTrue(Rees46.instance(SHOP) === full)
    }

    @Test
    fun `initializing the instance brought up for a push makes it a full one, and its push still goes`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)))
        Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED)
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")

        light.initializeWith(context, config(SHOP))

        assertFalse(light.pushOnly)
        assertTrue(SdkRegistry.byShopId(SHOP) === light)
        assertEquals("STORED", next("init")?.second?.getString("did"))
        flush(light)
        assertEquals(listOf("cold"), seen.filter { it.first == "track/delivered" }.map { it.second.getString("code") })
    }

    @Test
    fun `a push live two hours after the last event goes without an init`() {
        val sdk = sdk()
        val init = (sdk.core as com.personalization.core.internal.CoreV2).init ?: throw AssertionError("no init")
        runBlocking { withTimeout(5_000) { init.awaitIdle() } }
        assertEquals(1, init.sent)
        // The app sat in the background past the session's window.
        partition().edit().putLong("sid_last_act", System.currentTimeMillis() - com.personalization.core.internal.SessionWindow.WINDOW_MS - 1).commit()
        assertNull(sdk.sessionId)

        assertEquals(SHOP, Rees46.handlePush(push(code = "late"), PushEvent.DELIVERED))
        flush(sdk)

        assertEquals("D1", next("track/delivered")?.second?.getString("did"))
        assertEquals(1, init.sent)
        assertFalse(init.isRunning)
        assertFalse(drained().any { it.first == "init" })
    }

    @Test
    fun `a shop registered but not created and without a did drops its push`() {
        Rees46.register(context, listOf(config(SHOP)))

        assertNull(Rees46.handlePush(push(code = "no-did"), PushEvent.DELIVERED))
        val dropped = logged.last { it.code == LogCodes.PUSH_DROPPED }
        assertEquals(LogReasons.NO_DID, dropped.context["reason"])
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        val init = (light.core as com.personalization.core.internal.CoreV2).init ?: throw AssertionError("no init")
        runBlocking { withTimeout(5_000) { init.awaitPrepared() } }
        assertEquals(0, init.sent)
        assertEquals(SdkState.INITIALIZING, light.state)
    }

    @Test
    fun `on the legacy core a shop registered but not created sends its push with its stored did and no init`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP, coreV2 = false)))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        assertEquals("STORED", next("track/received")?.second?.getString("did"))
        // The legacy /init goes out on the caller's thread: had it gone, the server has it by now.
        threads.awaitIdle()
        assertFalse(drained().any { it.first in listOf("init", "mobile_push_tokens", "profile/set") })
        assertEquals(SdkState.READY, SdkRegistry.byShopId(SHOP)?.state)

        // Without a did the push is dropped.
        Rees46.register(context, listOf(config(OTHER, coreV2 = false)))
        assertNull(Rees46.handlePush(push(code = "cold", shop = OTHER), PushEvent.DELIVERED))
        assertEquals(LogReasons.NO_DID, logged.last { it.code == LogCodes.PUSH_DROPPED }.context["reason"])
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
    fun `the legacy core sends a delivery to track received and a tap as it did, and reports no shown or dismissed notification`() {
        val sdk = sdk(coreV2 = false)
        val handed = CopyOnWriteArrayList<String>()
        sdk.networkRepository.onRequest = { handed += it }
        @Suppress("DEPRECATION")
        Rees46.handlePush(push(code = "old"), PushEventType.RECEIVED)
        assertEquals("old", next("track/received")?.second?.getString("code"))

        assertEquals(SHOP, Rees46.handlePush(push(code = "new"), PushEvent.DELIVERED))
        assertEquals("new", next("track/received")?.second?.getString("code"))
        // Neither event reaches the network: nothing is handed to the funnel for them.
        assertEquals(SHOP, Rees46.handlePush(push(code = "new"), PushEvent.OPENED))
        assertEquals(SHOP, Rees46.handlePush(push(code = "new"), PushEvent.CLOSED))
        assertEquals(SHOP, Rees46.handlePush(push(code = "new"), PushEvent.CLICKED))
        assertEquals("new", next("track/clicked")?.second?.getString("code"))

        // A notification the SDK draws: no dismissal intent, nothing reported, as in 2.49.0.
        val data = NotificationData(id = "drawn", title = "T", body = "B", icon = null, type = "bulk", actions = null, actionUrls = null, image = null, event = null)
        sdk.notificationHelper.createNotification(context, data, images = null)
        val notification = shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications.single()
        assertNull(notification.deleteIntent)

        assertEquals(listOf("track/received", "track/received", "track/clicked"), handed.filter { it.startsWith("track/") })
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

    /** A push of [shop] with every field a callback reads. */
    private fun fullPush(code: String, shop: String) = mapOf(
        "id" to code,
        "type" to "bulk",
        "shop_id" to shop,
        "title" to "Sale",
        "body" to "Today only",
        "image" to "https://example.com/i.png",
        "actions" to """[{"action":"buy","title":"Buy"}]""",
        "action_urls" to """["https://example.com/buy"]""",
        "event" to """{"type":"product","uri":"37","payload":{"k":"v"}}""",
        "custom" to "kept",
    )

    private fun assertWhole(payload: PushPayload?, shop: String) {
        if (payload == null) throw AssertionError("no payload")
        assertEquals(shop, payload.shopId)
        assertEquals("Sale", payload.title)
        assertEquals("Today only", payload.body)
        assertEquals(com.personalization.core.PushTargetType.PRODUCT, payload.target?.type)
        assertEquals("37", payload.target?.uri)
        assertEquals("v", payload.target?.payload?.get("k"))
        assertEquals("buy", payload.actions.single().id)
        assertEquals("https://example.com/buy", payload.actions.single().url)
        assertEquals("kept", payload.data["custom"])
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a notification the SDK draws is reported as opened and its dismissal as closed, for the push's own shop, whole`() {
        val shop = sdk(SHOP)
        val other = sdk(OTHER)
        val opened = CopyOnWriteArrayList<PushPayload>()
        val closed = CopyOnWriteArrayList<PushPayload>()
        val bridge = CopyOnWriteArrayList<Pair<PushEvent, PushPayload>>()
        Rees46.setPushCallbacks(PushCallbacks(onOpened = { opened += it }, onClosed = { closed += it }))
        Rees46.onPushEvent { event, payload -> bridge += event to payload }
        val push = fullPush("drawn", OTHER)
        Rees46.handlePush(push, PushEvent.DELIVERED)

        // Drawn by the helper of another shop's instance: the push names its own.
        shop.notificationHelper.createNotification(context, push.toNotificationData(), images = null)
        flush(other)
        val shown = next("track/opened")?.second ?: throw AssertionError("no track/opened")
        assertEquals("drawn", shown.getString("code"))
        assertEquals(OTHER, shown.getString("shop_id"))
        assertWhole(opened.single(), OTHER)
        assertWhole(bridge.single { it.first == PushEvent.OPENED }.second, OTHER)

        // The dismissal is handled off the receiver's thread, and the broadcast ends once the event is written.
        val work = LinkedBlockingQueue<Runnable>()
        PushDismissReceiver.work = Executor { work += it }
        val notification = shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications.single()
        PushDismissReceiver().onReceive(context, shadowOf(notification.deleteIntent).savedIntent)
        assertTrue("nothing handled on the receiver's thread", closed.isEmpty())
        work.single().run()
        flush(other)
        val dismissed = next("track/closed")?.second ?: throw AssertionError("no track/closed")
        assertEquals(OTHER, dismissed.getString("shop_id"))
        assertWhole(closed.single(), OTHER)
        assertWhole(bridge.single { it.first == PushEvent.CLOSED }.second, OTHER)
    }

    @Test
    fun `a carousel drawn again in a new process still reports its dismissal for the push's shop, whole`() {
        sdk(SHOP)
        val other = sdk(OTHER)
        val closed = CopyOnWriteArrayList<PushPayload>()
        Rees46.setPushCallbacks(PushCallbacks(onClosed = { closed += it }))
        val push = fullPush("carousel", OTHER)
        val data = push.toNotificationData()
        // The carousel's button carries the push.
        val button = shadowOf(
            NotificationNavigationHelper.createNavigationPendingIntent(context, data, 1, NotificationConstants.ACTION_NEXT_IMAGE, push)
        ).savedIntent

        // A new process: nothing of the push in memory, and the helper the service makes belongs to no instance.
        PushHub.forgetPushes()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        NotificationHelper(manager).redraw(context, button.toNotificationData(), images = null, currentImageIndex = 1, hasError = false, push = PushExtras.read(button))
        PushDismissReceiver.work = Executor { it.run() }
        PushDismissReceiver().onReceive(context, shadowOf(shadowOf(manager).allNotifications.single().deleteIntent).savedIntent)
        flush(other)

        val dismissed = next("track/closed")?.second ?: throw AssertionError("no track/closed")
        assertEquals("carousel", dismissed.getString("code"))
        assertEquals(OTHER, dismissed.getString("shop_id"))
        assertWhole(closed.single(), OTHER)
        // Drawn again, not shown again: flushed, the queue sent everything it had.
        assertFalse(drained().any { it.first == "track/opened" })
    }

    @Test
    fun `a push larger than an intent carries keeps what routes it and what a callback shows first`() {
        val big = fullPush("big", OTHER) + ("extra" to "x".repeat(PushExtras.MAX_CHARS))
        assertEquals(setOf("id", "type", "shop_id", "title", "body"), PushExtras.fit(big).keys)
        assertEquals(fullPush("small", OTHER), PushExtras.fit(fullPush("small", OTHER)))
    }

    // The bridges.

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a bridge gets the whole error behind an old onError, and a result instead of a code`() {
        val sdk = sdk()
        val results = LinkedBlockingQueue<com.personalization.core.SdkResult<Any?>>()
        val current = LinkedBlockingQueue<Any>()

        sdk.getCurrentSegment(object : com.personalization.api.OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                current += com.personalization.core.BridgeErrors.currentError() ?: "none"
            }
        })
        assertEquals(com.personalization.core.SdkError.Http(404), current.poll(5, TimeUnit.SECONDS))
        assertNull("only inside onError", com.personalization.core.BridgeErrors.currentError())

        sdk.getCurrentSegment(com.personalization.core.BridgeErrors.listener { results += it })
        assertEquals(com.personalization.core.SdkResult.Failure(com.personalization.core.SdkError.Http(404)), results.poll(5, TimeUnit.SECONDS))

        sdk.tracking.categoryView("", com.personalization.core.BridgeErrors.listener { results += it })
        assertEquals(
            com.personalization.core.SdkResult.Failure(com.personalization.core.SdkError.InvalidArgument("categoryId is empty")),
            results.poll(5, TimeUnit.SECONDS),
        )

        // Without the core's error (the legacy core), one made from the code.
        assertEquals(com.personalization.core.SdkError.Http(500), com.personalization.core.BridgeErrors.errorOf(500, null))
        assertEquals(com.personalization.core.SdkError.Network, com.personalization.core.BridgeErrors.errorOf(-1, null))
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a bridge's event completes once it is accepted, and its push and token streams hear everything once`() {
        // The completions are handed to the main thread: the test runs them on its own.
        val main = com.personalization.core.internal.HeldMain()
        val sdk = SDK().apply { dispatchers = com.personalization.core.internal.CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config())
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        val ends = LinkedBlockingQueue<com.personalization.core.SdkResult<Unit>>()
        val completions = com.personalization.core.CompletionListener<Unit> { ends += it }

        sdk.queueEvent("push/custom", JSONObject().put("event", "from_dart"), completions)
        assertEquals(com.personalization.core.SdkResult.Success(Unit), takeOnMain(main, ends))
        sdk.queueEvent("profile/set", JSONObject(), completions)
        val refused = takeOnMain(main, ends)
        assertTrue(refused is com.personalization.core.SdkResult.Failure && refused.error is com.personalization.core.SdkError.InvalidArgument)
        flush(sdk)
        assertEquals("from_dart", next("push/custom")?.second?.getString("event"))

        val events = CopyOnWriteArrayList<String>()
        val tokens = CopyOnWriteArrayList<String>()
        val pushes = Rees46.onPushEvent { event, payload -> events += "${event.name}:${payload.shopId}:${payload.code}" }
        Rees46.onPushToken { tokens += it.token }
        Rees46.handlePush(push(code = "b1"), PushEvent.DELIVERED)
        Rees46.handlePush(push(code = "b1"), PushEvent.DELIVERED)
        SDK.onPushTokenReceived("bridge-token", PushProvider.HMS)
        SDK.onPushTokenReceived("bridge-token", PushProvider.HMS)
        pushes.cancel()
        Rees46.handlePush(push(code = "b2"), PushEvent.DELIVERED)

        assertEquals(listOf("DELIVERED:$SHOP:b1"), events)
        assertEquals(listOf("bridge-token"), tokens)

        sdk.release()
        sdk.queueEvent("push", JSONObject(), completions)
        assertEquals(com.personalization.core.SdkResult.Failure(com.personalization.core.SdkError.NotInitialized), takeOnMain(main, ends))
    }

    /** The next of [queue], running what [main] was handed until it comes. */
    private fun <T> takeOnMain(main: com.personalization.core.internal.HeldMain, queue: LinkedBlockingQueue<T>): T? {
        while (queue.isEmpty() && main.runNext()) Unit
        return queue.poll()
    }

    private companion object {
        const val SHOP = "push-shop"
        const val OTHER = "push-other"
    }
}
