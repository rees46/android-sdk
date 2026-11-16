package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.PushEvent
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
import com.personalization.core.internal.testCoreDispatchers
import com.personalization.features.notification.domain.model.NotificationConstants
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import com.personalization.push.PushTokenSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A push that brings a shop up, and the app that takes the shop meanwhile, on a local server (`core/push.md`):
 * the legacy core's cold start for a shop without a `did` as 2.49.0 had it, the app's first call overtaking the
 * push's instance, the tap's source on the way to the instance that took the push's place, `SDK.instance` and
 * the deprecated `initialize` while only the push's instance lives, the push's instance giving way on the legacy
 * core, and a repeat of an event that comes while the first is handed over.
 *
 * An instance whose own work the test holds runs on a test scheduler: what it does after the call waits for
 * the test, not for the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushBringUpTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()

    /** Every request the server got, in order, whatever the test took from [requests]. */
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
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
        // No provider of the device answers: a test that needs a token hands its own.
        PushTokenManager.sourceFactory = { emptyList() }
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

    private fun config(shop: String = SHOP, coreV2: Boolean = true, deviceId: String? = null) = Rees46Config(
        shopId = shop,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        coreV2 = coreV2,
        logLevel = LogLevel.DEBUG,
        deviceId = deviceId,
    )

    private fun push(code: String, type: String = "bulk", shop: String = SHOP) =
        mapOf("id" to code, "type" to type, "title" to "Title", "body" to "Body", "shop_id" to shop)

    private fun tap(code: String, type: String = "bulk") = Bundle().apply {
        putString(NotificationConstants.NOTIFICATION_TYPE, type)
        putString(NotificationConstants.NOTIFICATION_ID, code)
    }

    /** The partition of [shop], where an earlier launch left its did. */
    private fun partition(shop: String = SHOP) =
        context.getSharedPreferences(StorageLayout(shop, null).partitionName, Context.MODE_PRIVATE)

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

    /** A provider of the device that gives [token] each time the SDK asks. */
    private class TokenSource(private val token: String) : PushTokenSource {
        override val provider: PushProvider = PushProvider.FCM
        override fun isAvailable(context: Context): Boolean = true
        override fun fetchToken(context: Context, callback: (Result<String?>) -> Unit) = callback(Result.success(token))
    }

    /** A push-only instance of [config]'s shop whose own work waits for [scheduler], as the push's handling brings it up. */
    private fun heldPushInstance(config: Rees46Config, scheduler: TestCoroutineScheduler): SDK {
        val light = SDK().apply { dispatchers = testCoreDispatchers(scheduler) }
        light.initializeForPush(context, config)
        assertTrue(light.pushOnly)
        return light
    }

    // The legacy core's cold push for a shop without a did, as 2.49.0 had it.

    @Test
    fun `on the legacy core a cold push for a shop without a did sends only init and track received, and the token and profile go once the app takes the instance`() {
        PushTokenManager.sourceFactory = { listOf(TokenSource("tok")) }
        Rees46.register(context, listOf(config(SHOP, coreV2 = false).copy(autoSendPushToken = true)))

        assertEquals(SHOP, Rees46.handlePush(push(code = "cold"), PushEvent.DELIVERED))
        val made = SdkRegistry.byShopId(SHOP) ?: throw AssertionError("no instance")
        assertFalse(made.pushOnly)
        assertEquals("D1", next("track/received")?.second?.getString("did"))
        threads.awaitIdle()
        // The profile reads the advertising id off the push's thread first: had it been sent, it would be here by now.
        assertNull("no token and no profile for a push, as 2.49.0", next("mobile_push_tokens", "profile/set", timeoutMs = 1_000))
        assertEquals(listOf("init", "track/received"), seen.map { it.first })

        // The app takes the instance: its token and its profile go, once each, with the did of /init.
        assertTrue(Rees46.instance(SHOP) === made)
        val taken = listOf(next("mobile_push_tokens", "profile/set"), next("mobile_push_tokens", "profile/set"))
        assertEquals(setOf("mobile_push_tokens", "profile/set"), taken.map { it?.first }.toSet())
        assertTrue(taken.all { it?.second?.getString("did") == "D1" })
        assertEquals("tok", taken.single { it?.first == "mobile_push_tokens" }?.second?.getString("token"))

        // Taken again, however: nothing more goes.
        assertTrue(Rees46.instance(SHOP) === made)
        assertTrue(SDK.instance === made)
        assertTrue(Rees46.initialize(context, config(SHOP, coreV2 = false).copy(autoSendPushToken = true)) === made)
        threads.awaitIdle()
        assertNull(next("mobile_push_tokens", "profile/set", timeoutMs = 1_000))
        assertEquals(listOf("init", "track/received"), seen.map { it.first }.filter { it != "mobile_push_tokens" && it != "profile/set" })
        assertEquals(1, seen.count { it.first == "mobile_push_tokens" })
        assertEquals(1, seen.count { it.first == "profile/set" })
    }

    // The app's first call overtaking the push's instance before it read its did.

    @Test
    fun `a push the app's first call overtakes before the push's instance read its did goes to the app's instance, not dropped for want of a did`() {
        val scheduler = TestCoroutineScheduler()
        Rees46.register(context, listOf(config(SHOP, deviceId = "CONFIGURED")))
        // The push's instance, whose start has not read the disk yet: only the config brings a did.
        val light = heldPushInstance(config(SHOP, deviceId = "CONFIGURED"), scheduler)
        // The app's own instance takes its place; its /init has not answered yet.
        holdInit = CountDownLatch(1)
        val full = Rees46.initialize(context, config(SHOP))
        assertTrue(full !== light)
        assertEquals(SdkState.NOT_INITIALIZED, light.state)
        assertEquals("", partition().getString(StorageKeys.DID, "").orEmpty())

        // The push's handling goes on with the instance it had.
        assertFalse("not dropped as no_did", light.dropsPushWithoutDid())
        val takers = LinkedBlockingQueue<SDK>()
        light.pushEvent(PushEvent.DELIVERED, "race", "bulk") { takers += it }
        scheduler.runCurrent()

        assertTrue("the app's instance took it", takers.poll(5, TimeUnit.SECONDS) === full)
        holdInit?.countDown()
        flush(full)
        assertEquals("D1", next("track/delivered")?.second?.getString("did"))
        assertFalse(logged.any { it.code == LogCodes.PUSH_DROPPED && it.context["reason"] == LogReasons.NO_DID })
    }

    // The tap's source on the way to the instance that took the push's place.

    @Test
    fun `a tap handed to the app's instance sets its source before the app's next view goes`() {
        val scheduler = TestCoroutineScheduler()
        partition().edit().putString(StorageKeys.DID, "STORED").commit()
        Rees46.register(context, listOf(config(SHOP)))
        val light = heldPushInstance(config(SHOP), scheduler)
        val full = Rees46.instance(SHOP)
        assertTrue(full !== light)

        // The push's handling hands the tap to the instance that gave way, whose refusal waits on its own work;
        // meanwhile the app tracks a view.
        val takers = LinkedBlockingQueue<SDK>()
        light.pushEvent(PushEvent.CLICKED, "tap", "chain") { takers += it }
        full.tracking.productView("37")
        flush(full)
        val view = next("push")?.second ?: throw AssertionError("no push")
        assertEquals("the tap's source", "chain", view.getJSONObject("source").getString("from"))
        assertEquals("tap", view.getJSONObject("source").getString("code"))

        scheduler.runCurrent()
        assertTrue(takers.poll(5, TimeUnit.SECONDS) === full)
        flush(full)
        assertEquals("tap", next("track/clicked")?.second?.getString("code"))
    }

    @Test
    fun `a tap no queue takes puts the source before it back`() {
        val savedAt = System.currentTimeMillis() - 60_000
        partition().edit()
            .putString(StorageKeys.TRACKING_SOURCE_TYPE, "bulk")
            .putString(StorageKeys.TRACKING_SOURCE_CODE, "kept")
            .putLong(StorageKeys.TRACKING_SOURCE_SAVED_AT, savedAt)
            .commit()
        val sdk = Rees46.initialize(context, config(SHOP))
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        sdk.release()

        // The tap reaches the released instance: no queue takes it.
        val heard = CopyOnWriteArrayList<SDK>()
        sdk.pushEvent(PushEvent.CLICKED, "refused", "chain") { heard += it }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (logged.none { it.message == "The push event was not accepted" } && System.nanoTime() < deadline) Thread.sleep(10)

        assertTrue(logged.any { it.message == "The push event was not accepted" })
        assertTrue(heard.isEmpty())
        assertEquals("bulk", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        assertEquals("kept", partition().getString(StorageKeys.TRACKING_SOURCE_CODE, null))
        assertEquals(savedAt, partition().getLong(StorageKeys.TRACKING_SOURCE_SAVED_AT, 0L))
    }

    private companion object {
        const val SHOP = "bring-up-shop"
        const val OTHER = "bring-up-other"
    }
}
