package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.PushCallbacks
import com.personalization.core.BridgeApi
import com.personalization.core.PushEvent
import com.personalization.core.PushPayload
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageKeys
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    /** The status of the answer to /init. */
    @Volatile
    private var initStatus = 200

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
        initStatus = 200
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
                    if (initStatus != 200) return MockResponse().setResponseCode(initStatus)
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

    private fun config(shop: String = SHOP, deviceId: String? = null) = Rees46Config(
        shopId = shop,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
        deviceId = deviceId,
    )

    private fun sdk(shop: String = SHOP, coreV2: Boolean = true): SDK {
        val sdk = Rees46.initialize(context, config(shop), coreV2)
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
        // A request the core sends directly passes the funnel first: none is a delivery's track/received.
        val handed = CopyOnWriteArrayList<String>()
        sdk.networkRepository.onRequest = { handed += it }
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
        // Flushed: every event of the queue went, and nothing went around it.
        assertFalse(seen.any { it.first == "track/received" })
        assertFalse(handed.contains("track/received"))
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

        // Flushed: every event of the queue went.
        assertEquals(listOf("track/clicked", "track/closed"), seen.map { it.first }.filter { it == "track/clicked" || it == "track/closed" })
        assertEquals(listOf("m-1"), clicked)
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a push event the queue refused counts as not sent and calls no callback, and goes when it comes again`() {
        val clicked = CopyOnWriteArrayList<String>()
        val bridge = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onClicked = { clicked += it.code }))
        Rees46.onPushEvent { _, payload -> bridge += payload.code }
        initStatus = 403
        val refused = Rees46.initialize(context, config(), coreV2 = true)
        runBlocking { withTimeout(5_000) { refused.instanceState.changes.first { it == SdkState.REFUSED } } }
        assertEquals(SHOP, Rees46.handlePush(push(code = "again"), PushEvent.CLICKED))
        // The queue's refusal comes off the caller's thread.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (logged.none { it.message == "The push event was not accepted" } && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(logged.any { it.message == "The push event was not accepted" })
        assertTrue("no callback for an event the queue refused", clicked.isEmpty() && bridge.isEmpty())
        assertNull("no source for a tap the queue refused", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        // What a bridge reports for the initialize of a refused shop.
        assertEquals(com.personalization.core.SdkError.ShopBlocked, com.personalization.core.BridgeErrors.errorOf(refused.state))
        refused.release()

        initStatus = 200
        val sdk = sdk()
        assertEquals(SHOP, Rees46.handlePush(push(code = "again"), PushEvent.CLICKED))
        flush(sdk)
        assertEquals("again", next("track/clicked")?.second?.getString("code"))
        assertEquals(listOf("again"), clicked)
        assertEquals(listOf("again"), bridge)
        assertEquals("bulk", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
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
        Rees46.register(context, listOf(config(SHOP).copy(autoSendPushToken = true)), coreV2 = true)
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

    @OptIn(BridgeApi::class)
    @Test
    fun `a shop a bridge registers sends its push with its stored did and no init, and the bridge's start makes the full instance`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.registerBridge(context, listOf(config(SHOP)))
        assertFalse(Rees46.isInitialized(SHOP))
        assertEquals("nothing goes at the registration", null, next("init", timeoutMs = 500))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        assertTrue(light.pushOnly)
        assertTrue("the push's instance runs on the new core", light.core is com.personalization.core.internal.CoreV2)
        flush(light)
        assertEquals("STORED", next("track/delivered")?.second?.getString("did"))
        assertFalse("no /init for the push", seen.any { it.first == "init" })

        // The bridge's code runs at last: its start makes the full instance, with its /init.
        val full = Rees46.initializeBridge(context, config(SHOP))
        assertTrue(full !== light)
        assertFalse(full.pushOnly)
        assertTrue(full.core is com.personalization.core.internal.CoreV2)
        assertEquals("STORED", next("init")?.second?.getString("did"))
        assertEquals(listOf("cold"), seen.filter { it.first == "track/delivered" }.map { it.second.getString("code") })
        assertFalse(logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH })
    }

    @Test
    fun `initializing the instance brought up for a push makes it a full one, and its push still goes`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)), coreV2 = true)
        Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED)
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")

        light.initializeWith(context, config(SHOP), coreV2 = true)

        assertFalse(light.pushOnly)
        assertTrue(SdkRegistry.byShopId(SHOP) === light)
        assertEquals("STORED", next("init")?.second?.getString("did"))
        flush(light)
        assertEquals(listOf("cold"), seen.filter { it.first == "track/delivered" }.map { it.second.getString("code") })
    }

    @Test
    fun `the instance brought up for a push never reaches the app, nor becomes the default`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)), coreV2 = true)
        Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED)
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        assertTrue(light.pushOnly)
        assertTrue("not the default", SdkRegistry.current() !== light)
        assertTrue("not SDK.instance", SDK.instance !== light)

        var awaited: SDK? = null
        Rees46.awaitInstance(SHOP) { awaited = it }
        val full = awaited ?: throw AssertionError("awaitInstance heard nothing")
        assertFalse(full.pushOnly)
        assertTrue(Rees46.instance(SHOP) === full)
        assertTrue(Rees46.instance() === full)
        assertTrue(Rees46.initialize(context, config(SHOP), coreV2 = true) === full)
        assertTrue(SdkRegistry.current() === full)
        assertTrue(SDK.instance === full)
    }

    @Test
    fun `initialize of a shop a push brought up gives the app a full instance`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)), coreV2 = true)
        Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED)
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")

        val full = Rees46.initialize(context, config(SHOP), coreV2 = true)

        assertTrue(full !== light)
        assertFalse(full.pushOnly)
        assertTrue(SdkRegistry.byShopId(SHOP) === full)
    }

    @Test
    fun `a push at a cold start while the app asks for its instance never hands the app the push-only one`() {
        repeat(RACE_ROUNDS) { round ->
            if (round > 0) {
                tearDown()
                setUp()
            }
            partition().edit().putString("did", "STORED").commit()
            Rees46.register(context, listOf(config(SHOP)), coreV2 = true)
            val barrier = CyclicBarrier(2)
            val app = AtomicReference<SDK>()
            val failure = AtomicReference<Throwable>()
            // FCM's thread hands the push over while the app's main thread asks for the shop's instance.
            val fcm = Thread {
                barrier.await()
                Rees46.handlePush(push(code = "race-$round"), PushEvent.DELIVERED)
            }
            val main = Thread {
                barrier.await()
                // Rounds start the app's call at different moments of the push's bring-up: before it, while it
                // holds the facade, and after the instance registered.
                Thread.sleep(MAIN_DELAYS_MS[round % MAIN_DELAYS_MS.size])
                try {
                    app.set(Rees46.instance(SHOP))
                } catch (e: Throwable) {
                    failure.set(e)
                }
            }
            fcm.start()
            main.start()
            fcm.join(10_000)
            main.join(10_000)

            failure.get()?.let { throw AssertionError("round $round", it) }
            val got = app.get() ?: throw AssertionError("round $round: no instance")
            assertFalse("round $round: the push-only instance reached the app", got.pushOnly)
            assertTrue("round $round", SdkRegistry.byShopId(SHOP) === got)
            assertTrue("round $round", Rees46.instance(SHOP) === got)
            // The push's event is not lost to the race: one of the two instances sends it.
            flush(got)
            assertTrue("round $round: the push's event went", awaitSeen("track/delivered", "race-$round"))
        }
    }

    @Test
    fun `a push event that reaches the instance a push brought up after it gave way goes to the app's instance`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)), coreV2 = true)
        Rees46.handlePush(push(code = "first"), PushEvent.DELIVERED)
        val light = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        val full = Rees46.instance(SHOP)
        assertTrue(full !== light)

        // The push's handling still holds the instance that gave way, and hands it the tap.
        val takers = LinkedBlockingQueue<SDK>()
        light.pushEvent(PushEvent.CLICKED, "late", "bulk") { takers += it }

        assertTrue("the app's instance took it", takers.poll(5, TimeUnit.SECONDS) === full)
        flush(full)
        assertEquals("late", next("track/clicked")?.second?.getString("code"))
        assertEquals("STORED", seen.last { it.first == "track/clicked" }.second.getString("did"))
        assertEquals("bulk", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        assertFalse(logged.any { it.message == "The push event was not accepted" })
    }

    /** Whether the server got [path] with [code], waiting up to five seconds for it. */
    private fun awaitSeen(path: String, code: String): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (seen.any { it.first == path && it.second.optString("code") == code }) return true
            Thread.sleep(10)
        }
        return false
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
        Rees46.register(context, listOf(config(SHOP)), coreV2 = true)

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
    fun `a shop a push brings up takes its did from the config or the saved init answer, not only the stored one`() {
        // Only the app's config brings it.
        Rees46.register(context, listOf(config(SHOP, deviceId = "CONFIGURED")), coreV2 = true)
        assertEquals(SHOP, Rees46.handlePush(push(code = "configured"), PushEvent.DELIVERED))
        flush(SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance"))
        assertEquals("CONFIGURED", next("track/delivered")?.second?.getString("did"))

        // Only the saved answer of an earlier /init has it.
        ShopStorage(context, StorageLayout(OTHER, null)).saveInitAnswer(JSONObject().put("did", "SAVED").put("seance", "S0"))
        Rees46.register(context, listOf(config(OTHER)), coreV2 = true)
        assertEquals(OTHER, Rees46.handlePush(push(code = "saved", shop = OTHER), PushEvent.DELIVERED))
        flush(SdkRegistry.byShopId(OTHER) ?: throw AssertionError("no instance"))
        assertEquals("SAVED", next("track/delivered")?.second?.getString("did"))

        assertFalse(logged.any { it.code == LogCodes.PUSH_DROPPED && it.context["reason"] == LogReasons.NO_DID })
        assertFalse("no /init went", seen.any { it.first == "init" })
    }

    @Test
    fun `on the legacy core a push for a shop without a did makes its full instance as before, with one init`() {
        Rees46.register(context, listOf(config(SHOP)))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        val made = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        assertFalse(made.pushOnly)
        assertEquals(SHOP, next("init")?.second?.getString("shop_id"))
        assertEquals("D1", next("track/received")?.second?.getString("did"))

        // The app gets it as it is: no second instance, no second /init.
        assertTrue(Rees46.instance(SHOP) === made)
        assertTrue(SdkRegistry.current() === made)
        threads.awaitIdle()
        assertEquals(1, seen.count { it.first == "init" })
        assertEquals(1, seen.count { it.first == "track/received" })
        assertEquals(SdkState.READY, made.state)
    }

    @Test
    fun `on the legacy core the push of a shop a push brought up does not keep its ended session alive`() {
        val ended = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
        partition().edit().putString("did", "STORED").putString("sid", "OLD").putLong("sid_last_act", ended).commit()
        Rees46.register(context, listOf(config(SHOP)))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        assertEquals("STORED", next("track/received")?.second?.getString("did"))
        threads.awaitIdle()
        assertEquals(ended, partition().getLong("sid_last_act", 0L))

        // The app's own start begins a new session.
        Rees46.instance(SHOP)
        assertTrue(partition().getString("sid", null) != "OLD")
    }

    @Test
    fun `on the legacy core a shop registered but not created sends its push with its stored did and no init, and without one starts as before`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        assertEquals("STORED", next("track/received")?.second?.getString("did"))
        // The legacy /init goes out on the caller's thread: had it gone, the server has it by now.
        threads.awaitIdle()
        assertFalse(drained().any { it.first in listOf("init", "mobile_push_tokens", "profile/set") })
        assertEquals(SdkState.READY, SdkRegistry.byShopId(SHOP)?.state)

        // Without a did it starts as it always did: /init for a did, then the push's event with it.
        Rees46.register(context, listOf(config(OTHER)))
        assertEquals(OTHER, Rees46.handlePush(push(code = "cold", shop = OTHER), PushEvent.DELIVERED))
        assertEquals(OTHER, next("init")?.second?.getString("shop_id"))
        assertEquals("D1", next("track/received")?.second?.getString("did"))
        assertFalse(logged.any { it.code == LogCodes.PUSH_DROPPED && it.context["reason"] == LogReasons.NO_DID })
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
        // The callbacks come once the queue accepted the events.
        sdk.awaitEventsWritten(5_000)
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
        sdk.awaitEventsWritten(5_000)

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
        val sdk = Rees46.initialize(context, config().copy(autoSendPushToken = true), coreV2 = true)
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
        assertEquals(SdkState.INITIALIZING, sdk.state)

        hold.countDown()
        val body = next("mobile_push_tokens")?.second ?: throw AssertionError("no token")
        assertEquals("D1", body.getString("did"))
        assertEquals("tok", body.getString("token"))
        assertEquals("firebase", body.getString("push_provider"))
        assertEquals("android", body.getString("platform"))
        assertEquals("success", answered.poll(5, TimeUnit.SECONDS))
        // Nothing of the token waits or is on its way any more: every send it made reached the server, once,
        // and none of them before the did.
        runBlocking { withTimeout(5_000) { sdk.pushTokenManager.core?.awaitIdle() } }
        assertEquals(listOf("D1"), seen.filter { it.first == "mobile_push_tokens" }.map { it.second.optString("did") })
        assertEquals("tok", sdk.getPushToken(PushProvider.FCM))
        assertEquals(listOf("tok"), heardTokens)
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `setPushToken after release answers notInitialized and keeps nothing`() {
        val sdk = sdk()
        sdk.release()
        val heard = LinkedBlockingQueue<Any>()

        sdk.setPushToken("after", PushProvider.FCM, object : com.personalization.api.OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                heard += code
                heard += com.personalization.core.BridgeErrors.currentError() ?: "none"
            }
        })

        assertEquals(-1, heard.poll(5, TimeUnit.SECONDS))
        assertEquals(com.personalization.core.SdkError.NotInitialized, heard.poll(5, TimeUnit.SECONDS))
        assertNull(DeviceStorage(context).pushToken("fcm"))
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a bridge that registers after the token came hears it once on registration`() {
        sdk()
        SDK.onPushTokenReceived("early", PushProvider.FCM)
        val tokens = CopyOnWriteArrayList<String>()

        Rees46.onPushToken { tokens += it.token }
        assertEquals(listOf("early"), tokens)

        SDK.onPushTokenReceived("early", PushProvider.FCM)
        SDK.onPushTokenReceived("next", PushProvider.FCM)
        assertEquals(listOf("early", "next"), tokens)
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

        // The checks of the tracking managers: invalidArgument with their message, off the caller's thread.
        val caller = Thread.currentThread()
        val threads = CopyOnWriteArrayList<Thread>()
        sdk.tracking.custom("e", null, null, null, null, mapOf("did" to "mine"), com.personalization.core.BridgeErrors.listener { threads += Thread.currentThread(); results += it })
        val reserved = results.poll(5, TimeUnit.SECONDS)
        assertTrue(reserved is com.personalization.core.SdkResult.Failure && reserved.error is com.personalization.core.SdkError.InvalidArgument)
        assertTrue(((reserved as com.personalization.core.SdkResult.Failure).error as com.personalization.core.SdkError.InvalidArgument).message.contains("did"))
        sdk.tracking.storyView("42", "3", null, com.personalization.core.BridgeErrors.listener { threads += Thread.currentThread(); results += it })
        val noCode = results.poll(5, TimeUnit.SECONDS)
        assertTrue(((noCode as com.personalization.core.SdkResult.Failure).error as com.personalization.core.SdkError.InvalidArgument).message.contains("no stories code"))
        assertTrue(threads.none { it === caller })

        // Without the core's error (the legacy core), one made from the code.
        assertEquals(com.personalization.core.SdkError.Http(500), com.personalization.core.BridgeErrors.errorOf(500, null))
        assertEquals(com.personalization.core.SdkError.Network, com.personalization.core.BridgeErrors.errorOf(-1, null))
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `on the legacy core a bridge gets invalidArgument with its message for a check of the tracking managers, on the caller's thread as before`() {
        val sdk = sdk(coreV2 = false)
        val results = CopyOnWriteArrayList<com.personalization.core.SdkResult<Any?>>()

        sdk.tracking.custom("e", null, null, null, null, mapOf("did" to "mine"), com.personalization.core.BridgeErrors.listener { results += it })

        val reserved = results.single()
        assertTrue(reserved is com.personalization.core.SdkResult.Failure && reserved.error is com.personalization.core.SdkError.InvalidArgument)
    }

    @OptIn(com.personalization.core.BridgeApi::class)
    @Test
    fun `a bridge's event completes once it is accepted, and its push and token streams hear everything once`() {
        // The completions are handed to the main thread: the test runs them on its own.
        val main = com.personalization.core.internal.HeldMain()
        val sdk = SDK().apply { dispatchers = com.personalization.core.internal.CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        val ends = LinkedBlockingQueue<com.personalization.core.SdkResult<Unit>>()
        val completions = com.personalization.core.CompletionListener<Unit> { ends += it }

        sdk.queueEvent("push/custom", JSONObject().put("event", "from_dart"), completions)
        assertEquals(com.personalization.core.SdkResult.Success(Unit), takeOnMain(main, ends))
        sdk.queueEvent("profile/set", JSONObject(), completions)
        val refused = takeOnMain(main, ends)
        assertTrue(refused is com.personalization.core.SdkResult.Failure && refused.error is com.personalization.core.SdkError.InvalidArgument)
        // A push event: the bridge hands the push to handlePush.
        listOf("track/delivered", "track/opened", "track/clicked", "track/closed", "track/received").forEach { path ->
            sdk.queueEvent(path, JSONObject().put("code", "x").put("type", "bulk"), completions)
            val pushPath = takeOnMain(main, ends)
            assertTrue(path, pushPath is com.personalization.core.SdkResult.Failure && pushPath.error is com.personalization.core.SdkError.InvalidArgument)
        }
        // Fields JSON cannot hold: refused through the listener, never thrown at the bridge.
        sdk.queueEvent("push/custom", JSONObject(mapOf("event" to "nan", "value" to Double.NaN)), completions)
        val notJson = takeOnMain(main, ends)
        assertTrue(notJson is com.personalization.core.SdkResult.Failure && notJson.error is com.personalization.core.SdkError.InvalidArgument)
        flush(sdk)
        assertEquals("from_dart", next("push/custom")?.second?.getString("event"))

        val events = CopyOnWriteArrayList<String>()
        val tokens = CopyOnWriteArrayList<String>()
        val pushes = Rees46.onPushEvent { event, payload -> events += "${event.name}:${payload.shopId}:${payload.code}" }
        Rees46.onPushToken { tokens += it.token }
        Rees46.handlePush(push(code = "b1"), PushEvent.DELIVERED)
        Rees46.handlePush(push(code = "b1"), PushEvent.DELIVERED)
        sdk.awaitEventsWritten(5_000)
        SDK.onPushTokenReceived("bridge-token", PushProvider.HMS)
        SDK.onPushTokenReceived("bridge-token", PushProvider.HMS)
        pushes.cancel()
        Rees46.handlePush(push(code = "b2"), PushEvent.DELIVERED)
        sdk.awaitEventsWritten(5_000)

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

        /** How often the race of a push and the app's first call is run. */
        const val RACE_ROUNDS = 14

        /** How long the app's call waits after the push's handling began, by round. */
        val MAIN_DELAYS_MS = longArrayOf(0, 1, 2, 3, 5, 8, 13)
    }
}
