package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.PushCallbacks
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
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
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
 * The tap in the form before `PushEvent` (`Rees46.handlePush(payload, PushEventType.CLICKED)`) on the legacy
 * core, as 2.49.0 tracked it: `track/clicked` from the tap's extras `NOTIFICATION_TYPE` and `NOTIFICATION_ID`,
 * the 48-hour source of the next `/push` events, and the app's `onClicked` once; never `track/received`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyPushClickTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor

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
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                seen += path to body
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                return MockResponse().setBody("{}")
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
        QueueStore.reset()
        PushHub.reset()
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun partition() = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)

    /** The next request to one of [paths], skipping others; null when none came in five seconds. */
    private fun next(vararg paths: String): Pair<String, JSONObject>? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return null
            val request = requests.poll(left, TimeUnit.NANOSECONDS) ?: return null
            if (request.first in paths) return request
        }
    }

    private fun tap(code: String) = mapOf("NOTIFICATION_TYPE" to "bulk", "NOTIFICATION_ID" to code, "shop_id" to SHOP)

    @Suppress("DEPRECATION")
    @Test
    fun `a tap in the old form sends track clicked from its extras, sets the source and calls onClicked once`() {
        val sdk = Rees46.initialize(context, config())
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        val clicked = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onClicked = { clicked += "${it.shopId}:${it.code}:${it.type}" }))

        Rees46.handlePush(tap("m1"), PushEventType.CLICKED)

        val click = next("track/clicked")?.second ?: throw AssertionError("no track/clicked")
        assertEquals("m1", click.getString("code"))
        assertEquals("bulk", click.getString("type"))
        assertEquals("D1", click.getString("did"))
        assertEquals("bulk", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        assertEquals(listOf("$SHOP:m1:bulk"), clicked)
        threads.awaitIdle()
        assertEquals(1, seen.count { it.first == "track/clicked" })
        assertFalse(seen.any { it.first == "track/received" })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a push's own type and id are not a tap's extras, so the old form tracks nothing for them, as 2_49_0 did`() {
        val sdk = Rees46.initialize(context, config())
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }

        Rees46.handlePush(mapOf("type" to "bulk", "id" to "m2", "shop_id" to SHOP), PushEventType.CLICKED)
        threads.awaitIdle()

        assertFalse(seen.any { it.first == "track/clicked" || it.first == "track/received" })
        assertNull(partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        val dropped = logged.last { it.code == LogCodes.PUSH_DROPPED }
        assertEquals(LogReasons.NOT_SDK_PUSH, dropped.context["reason"])
        assertEquals(LogLevel.DEBUG, dropped.level)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a tap in the old form for a shop registered but not created goes with its stored did and no init`() {
        partition().edit().putString("did", "STORED").commit()
        Rees46.register(context, listOf(config()))
        val clicked = CopyOnWriteArrayList<String>()
        Rees46.setPushCallbacks(PushCallbacks(onClicked = { clicked += it.code }))

        Rees46.handlePush(tap("cold"), PushEventType.CLICKED)

        val click = next("track/clicked")?.second ?: throw AssertionError("no track/clicked")
        assertEquals("cold", click.getString("code"))
        assertEquals("STORED", click.getString("did"))
        assertEquals("bulk", partition().getString(StorageKeys.TRACKING_SOURCE_TYPE, null))
        assertEquals(listOf("cold"), clicked)
        threads.awaitIdle()
        assertTrue(SdkRegistry.byShopId(SHOP)?.pushOnly == true)
        assertFalse("no /init", seen.any { it.first == "init" })
        assertFalse(seen.any { it.first == "track/received" })
    }

    private companion object {
        const val SHOP = "legacy-click-shop"
    }
}
