package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.PushEvent
import com.personalization.core.SdkResult
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
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The instance as an app uses it on a local server, where the coverage audit found no test: two shops of one
 * app kept apart (`core/resilience.md`), a refused shop's customer deleted, two deletions at once, the eager
 * registration and the wait for a registered shop, the app's listeners that throw, the state after release,
 * a mismatched core flag of another shop, and the legacy core's repeated deliveries.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkCoreGapsTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultSources = PushTokenManager.sourceFactory

    /** The status of the answer to /init, by shop; 200 when not set. */
    private val initStatus = ConcurrentHashMap<String, Int>()

    /** The status of the answer to the events of a shop, by shop; 200 when not set. */
    private val eventStatus = ConcurrentHashMap<String, Int>()

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
        PushTokenManager.sourceFactory = { emptyList() }
        listOf(A, B).forEach { shop ->
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
        initStatus.clear()
        eventStatus.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                seen += path to body
                val shop = body.optString("shop_id")
                return when (path) {
                    "init" -> {
                        holdInit?.await(5, TimeUnit.SECONDS)
                        val status = initStatus[shop] ?: 200
                        if (status != 200) MockResponse().setResponseCode(status)
                        else MockResponse().setBody("""{"did":"D-$shop","seance":"S-$shop"}""")
                    }
                    "segments/get" -> MockResponse().setBody(JSONObject().put("shop", shop).toString())
                    "push" -> {
                        val status = eventStatus[shop] ?: 200
                        if (status == 503) MockResponse().setResponseCode(503).setHeader("Retry-After", "60")
                        else MockResponse().setResponseCode(status).setBody("{}")
                    }
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
        PushTokenManager.sourceFactory = defaultSources
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
        PushHub.reset()
    }

    private fun config(shop: String) = Rees46Config(
        shopId = shop,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun ready(sdk: SDK, state: SdkState = SdkState.READY) =
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == state } } }

    private fun sdk(shop: String, coreV2: Boolean = true): SDK = Rees46.initialize(context, config(shop), coreV2).also { ready(it) }

    private fun flush(sdk: SDK) = runBlocking { withTimeout(5_000) { sdk.flush() } }

    private fun partition(shop: String) = context.getSharedPreferences(StorageLayout(shop, null).partitionName, Context.MODE_PRIVATE)

    /** The views [shop] sent, by item, with the did and the sid each went with. */
    private fun views(shop: String): List<Triple<String, String, String>> = seen
        .filter { it.first == "push" && it.second.optString("shop_id") == shop }
        .map { (_, body) -> Triple(body.getJSONArray("items").getJSONObject(0).getString("id"), body.getString("did"), body.getString("sid")) }

    /** Waits until [condition] holds, on the server's requests; false when it did not within five seconds. */
    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() > deadline) return false
            requests.poll(50, TimeUnit.MILLISECONDS)
        }
        return true
    }

    // Two shops of one app (core/resilience.md, «разных магазинов»).

    @Test
    fun `two shops on the new core keep their own did, session, queue, pauses, refusal and customer`() {
        val a = sdk(A)
        val b = sdk(B)
        listOf("1", "2", "3").forEach {
            a.tracking.productView("a$it")
            b.tracking.productView("b$it")
        }
        flush(a)
        flush(b)
        assertEquals(listOf("a1", "a2", "a3").map { Triple(it, "D-$A", "S-$A") }, views(A))
        assertEquals(listOf("b1", "b2", "b3").map { Triple(it, "D-$B", "S-$B") }, views(B))
        val root = File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY)
        assertTrue(File(root, StorageLayout(A, null).partitionName).isDirectory)
        assertTrue(File(root, StorageLayout(B, null).partitionName).isDirectory)

        // A's server asks it to wait a minute: B's events go at once all the same.
        eventStatus[A] = 503
        a.tracking.productView("a-held")
        b.tracking.productView("b-free")
        flush(a)
        flush(b)
        assertTrue(views(B).any { it.first == "b-free" })
        assertEquals("tried once, then waits its minute", 1, views(A).count { it.first == "a-held" })

        // A's customer is deleted and A is refused: B keeps everything of its own.
        initStatus[A] = 403
        runBlocking { withTimeout(5_000) { a.deleteUserCredentials() } }
        ready(a, SdkState.REFUSED)
        assertEquals(SdkState.READY, b.state)
        assertEquals("D-$B", b.deviceId)
        assertEquals("S-$B", b.sessionId)
        assertTrue(ShopStorage(context, StorageLayout(B, null)).initAnswer() != null)
        b.tracking.productView("b-after")
        flush(b)
        assertTrue(views(B).any { it.first == "b-after" && it.second == "D-$B" })

        // Both shops asked at once from eight threads: each answer is its own shop's.
        val answers = CopyOnWriteArrayList<Pair<String, String>>()
        val done = CountDownLatch(16)
        val barrier = CyclicBarrier(8)
        val callers = List(8) {
            Thread {
                barrier.await()
                listOf(a, b).forEach { sdk ->
                    sdk.getCurrentSegment(object : OnApiCallbackListener() {
                        override fun onSuccess(response: JSONObject?) {
                            answers += sdk.shopId to response?.optString("shop").orEmpty()
                            done.countDown()
                        }

                        override fun onError(code: Int, msg: String?) {
                            answers += sdk.shopId to "error:$code"
                            done.countDown()
                        }
                    })
                }
            }.also { it.start() }
        }
        callers.forEach { it.join(5_000) }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        // The refused shop answers shopBlocked; the other its own.
        assertEquals(List(8) { B to B }, answers.filter { it.first == B })
        assertEquals(List(8) { A to "error:402" }, answers.filter { it.first == A })
    }

    // A refused shop's customer deleted (gap M11).

    @Test
    fun `deleting the customer of a refused shop asks init again, and its answer decides`() {
        initStatus[A] = 403
        val sdk = Rees46.initialize(context, config(A), coreV2 = true)
        ready(sdk, SdkState.REFUSED)
        assertTrue(partition(A).getBoolean(StorageKeys.REFUSED, false))

        initStatus[A] = 200
        val states = CopyOnWriteArrayList<SdkState>()
        sdk.instanceState.addListener { states += it }
        runBlocking { withTimeout(5_000) { sdk.deleteUserCredentials() } }
        ready(sdk)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(partition(A).getBoolean(StorageKeys.REFUSED, false))
        assertTrue(states.containsAll(listOf(SdkState.INITIALIZING, SdkState.READY)))
        sdk.tracking.productView("after")
        flush(sdk)
        assertEquals(listOf(Triple("after", "D-$A", "S-$A")), views(A))

        // Refused again: refused, and the queue takes no event.
        initStatus[A] = 403
        runBlocking { withTimeout(5_000) { sdk.deleteUserCredentials() } }
        ready(sdk, SdkState.REFUSED)
        val refused = LinkedBlockingQueue<Int>()
        sdk.tracking.productView("refused", listener = object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                refused += code
            }
        })
        assertEquals(402, refused.poll(5, TimeUnit.SECONDS))
        // Nothing to send: flush ends without an error.
        flush(sdk)
    }

    // Two deletions at once (gap M22).

    @Test
    fun `two deletions of the customer at once both end, and the instance ends ready with the server's did`() {
        listOf(false, true).forEach { coreV2 ->
            tearDown()
            setUp()
            val sdk = sdk(A, coreV2)
            holdInit = CountDownLatch(1)
            val both = runBlocking(Dispatchers.IO) {
                val first = async { sdk.deleteUserCredentials() }
                val second = async { sdk.deleteUserCredentials() }
                holdInit?.countDown()
                withTimeout(10_000) { awaitAll(first, second) }
            }
            assertEquals(2, both.size)
            ready(sdk)
            threads.awaitIdle()
            assertTrue("core $coreV2", eventually { sdk.deviceId == "D-$A" })
            // The start's /init, and at least one after the wipe: without a did.
            assertTrue("core $coreV2", seen.count { it.first == "init" && it.second.optString("did").isEmpty() } >= 2)
            assertFalse("core $coreV2", logged.any { it.level == LogLevel.ERROR && it.code == LogCodes.SDK_LOG })
            assertEquals("core $coreV2", 2, logged.count { it.code == LogCodes.DEVICE_ID_RESET && it.context["reason"] == "delete_user_credentials" })
        }
    }

    // The facade's eager registration and the wait for a registered shop (gap M12).

    @Test
    fun `an eager registration makes each shop's instance at once, with one init each, on either core`() {
        listOf(false, true).forEach { coreV2 ->
            tearDown()
            setUp()
            Rees46.register(context, listOf(config(A), config(B)), eagerInit = true, coreV2 = coreV2)

            assertTrue(Rees46.isInitialized(A))
            assertTrue(Rees46.isInitialized(B))
            assertTrue(Rees46.pendingShopIds().isEmpty())
            assertEquals(coreV2, CoreSelection.decided())
            ready(Rees46.instance(A))
            ready(Rees46.instance(B))
            threads.awaitIdle()
            assertTrue(eventually { seen.count { it.first == "init" } == 2 })
            assertEquals(setOf(A, B), seen.filter { it.first == "init" }.map { it.second.getString("shop_id") }.toSet())
        }
    }

    @Test
    fun `waiting for a shop registered but not created gets its full instance at once, with one init`() {
        Rees46.register(context, listOf(config(A)), coreV2 = true)
        var got: SDK? = null

        Rees46.awaitInstance(A) { got = it }

        val sdk = got ?: throw AssertionError("onReady was not called at once")
        assertFalse(sdk.pushOnly)
        assertTrue(Rees46.instance(A) === sdk)
        ready(sdk)
        assertTrue(eventually { seen.count { it.first == "init" } == 1 })
    }

    // The app's listeners that throw (gap M21).

    @Test
    fun `a session listener, a flush listener and the bridges' push listeners that throw are logged, and the others still hear`() {
        val sessions = CopyOnWriteArrayList<String>()
        val sdk = SDK()
        sdk.onSessionChange { throw IllegalStateException("the app's session listener") }
        sdk.onSessionChange { sessions += it }
        sdk.initializeWith(context, config(A), coreV2 = true)
        ready(sdk)
        assertTrue(eventually {
            shadowOf(Looper.getMainLooper()).idle()
            sessions.isNotEmpty()
        })
        assertEquals(listOf("S-$A"), sessions)
        assertEquals(1, logged.count { it.code == LogCodes.CALLBACK_FAILED && it.message == "A session listener threw" })

        val flushed = LinkedBlockingQueue<SdkResult<Unit>>()
        sdk.flush(CompletionListener { result ->
            flushed += result
            throw IllegalStateException("the app's flush listener")
        })
        assertTrue(eventually {
            shadowOf(Looper.getMainLooper()).idle()
            flushed.isNotEmpty()
        })
        assertEquals(SdkResult.Success(Unit), flushed.single())
        assertTrue(eventually { logged.any { it.code == LogCodes.CALLBACK_FAILED && it.message == "The app's CompletionListener threw" } })

        val events = CopyOnWriteArrayList<String>()
        val tokens = CopyOnWriteArrayList<String>()
        Rees46.onPushEvent { _, _ -> throw IllegalStateException("the bridge's push listener") }
        Rees46.onPushEvent { event, payload -> events += "${event.name}:${payload.code}" }
        Rees46.onPushToken { throw IllegalStateException("the bridge's token listener") }
        Rees46.onPushToken { tokens += it.token }
        Rees46.handlePush(mapOf("id" to "m", "type" to "bulk", "shop_id" to A), PushEvent.DELIVERED)
        sdk.awaitEventsWritten(5_000)
        SDK.onPushTokenReceived("t", PushProvider.FCM)

        assertEquals(listOf("DELIVERED:m"), events)
        assertEquals(listOf("t"), tokens)
        assertEquals(1, logged.count { it.message == "A bridge's push listener threw" })
        assertEquals(1, logged.count { it.message == "A bridge's token listener threw" })
    }

    // The state after release.

    @Test
    fun `a state listener hears notInitialized on release and nothing after it`() {
        listOf(false, true).forEach { coreV2 ->
            tearDown()
            setUp()
            val sdk = sdk(A, coreV2)
            val states = CopyOnWriteArrayList<SdkState>()
            sdk.onStateChange { states += it }
            shadowOf(Looper.getMainLooper()).idle()

            sdk.release()
            shadowOf(Looper.getMainLooper()).idle()
            Rees46.initialize(context, config(A), coreV2).also { ready(it) }
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("core $coreV2", listOf(SdkState.READY, SdkState.NOT_INITIALIZED), states)
        }
    }

    // The core flag of another shop (row 1.1 of the audit).

    @Test
    fun `a shop initialized with the other flag after a registration decided the process's core runs on that core and is warned once`() {
        Rees46.register(context, listOf(config(A)), coreV2 = true)

        val b = Rees46.initialize(context, config(B))
        Rees46.initialize(context, config(B))

        assertTrue(b.core?.isV2 == true)
        assertEquals(1, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.shopId == B })
    }

    // The legacy core's deliveries (gap L11).

    @Test
    fun `on the legacy core every delivery of one message is sent, as 2_49_0 did`() {
        sdk(A, coreV2 = false)
        val push = mapOf("id" to "m", "type" to "bulk", "shop_id" to A)

        Rees46.handlePush(push, PushEvent.DELIVERED)
        Rees46.handlePush(push, PushEvent.DELIVERED)
        threads.awaitIdle()

        assertTrue(eventually { seen.count { it.first == "track/received" } == 2 })
    }

    private companion object {
        const val A = "gaps-shop-a"
        const val B = "gaps-shop-b"
    }
}
