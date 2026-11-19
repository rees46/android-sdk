package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.TrackingApi
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.logs.LogCodes
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * The old methods the contract replaces are deprecated and keep working as delegates: `tracking.search` without
 * `results`, `getSid`/`getDid` of `sessionId`/`deviceId`, the old push entry points and listeners, and
 * `showStories`, which without an attached `StoriesView` shows the block through `PersonalizationStories.show`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class DeprecatedDelegatesTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val threads = TrackingThreads()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

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
        LegacyCore.threads = threads
        server = MockWebServer()
        requests.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
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
        QueueStore.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun sdk(coreV2: Boolean = true): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(shopId = SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false),
            coreV2,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    /** The next request to [path], past the others. */
    private fun next(path: String): JSONObject {
        while (true) {
            val (got, body) = requests.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no $path request")
            if (got == path) return body
        }
    }

    @Test
    fun `tracking search without results sends the query alone, and the deprecated one still sends its results`() {
        val sdk = sdk()
        val heard = LinkedBlockingQueue<String>()
        val listener = object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                heard += "success"
            }
        }

        sdk.tracking.search("boots")
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        val plain = next("push")
        sdk.tracking.search("boots", listener = listener)
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        val withListener = next("push")
        sdk.tracking.search("boots", listOf("37", "42"), listener)
        runBlocking { withTimeout(5_000) { sdk.flush() } }
        val withResults = next("push")

        assertEquals("boots", plain.getString("search_query"))
        assertFalse(plain.has("results"))
        assertFalse(withListener.has("results"))
        assertEquals("37,42", withResults.getString("results"))
        assertEquals(listOf("success", "success"), List(2) { heard.poll(5, TimeUnit.SECONDS) })
    }

    @Test
    fun `getSid and getDid are sessionId and deviceId, empty without them`() {
        val fresh = SDK()
        assertEquals("", fresh.getSid())
        assertEquals("", fresh.getDid())

        val sdk = sdk()
        assertEquals(sdk.sessionId.orEmpty(), sdk.getSid())
        assertEquals("D1", sdk.deviceId)
        assertEquals(sdk.deviceId, sdk.getDid())
        val consumed = mutableListOf<String?>()
        sdk.getSid(Consumer { consumed += it })
        assertEquals(listOf(sdk.sessionId.orEmpty()), consumed)
    }

    @Test
    fun `the replaced methods are deprecated with their replacement`() {
        fun deprecation(owner: Class<*>, name: String, vararg parameters: Class<*>): String =
            owner.getMethod(name, *parameters).getAnnotation(Deprecated::class.java)?.replaceWith?.expression
                ?: throw AssertionError("$name is not deprecated")

        assertEquals("sessionId.orEmpty()", deprecation(SDK::class.java, "getSid"))
        assertEquals("deviceId.orEmpty()", deprecation(SDK::class.java, "getDid"))
        assertTrue(deprecation(SDK::class.java, "showStories", String::class.java).startsWith("PersonalizationStories.show"))
        assertTrue(deprecation(SDK::class.java, "setOnPushTokenListener", OnPushTokenListener::class.java).contains("onToken"))
        assertTrue(deprecation(SDK::class.java, "setOnMessageListener", OnMessageListener::class.java).contains("onDelivered"))
        assertTrue(deprecation(SDK.Companion::class.java, "onMessage", Map::class.java).startsWith("Rees46.handlePush"))
        assertTrue(deprecation(Rees46::class.java, "setOnMessageListener", OnShopMessageListener::class.java).contains("setPushCallbacks"))
        assertEquals(
            "search(query, listener)",
            deprecation(TrackingApi::class.java, "search", String::class.java, List::class.java, OnApiCallbackListener::class.java),
        )
        assertEquals(null, TrackingApi::class.java.getMethod("search", String::class.java, OnApiCallbackListener::class.java).getAnnotation(Deprecated::class.java))
    }

    @Test
    fun `the deprecated push entry point and listeners keep working`() {
        val sdk = sdk(coreV2 = false)
        val messages = mutableListOf<String?>()
        sdk.setOnMessageListener { data -> messages += data.title }

        SDK.onMessage(mapOf("type" to "bulk", "id" to "m-1", "title" to "Hello", "shop_id" to SHOP))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf<String?>("Hello"), messages)
        sdk.setOnPushTokenListener { _, _ -> }
    }

    @Test
    fun `showStories without an attached view shows the block in the activity on screen`() {
        val sdk = sdk(coreV2 = false)
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        sdk.showStories("main_stories")
        shadowOf(Looper.getMainLooper()).idle()
        threads.awaitIdle()

        assertNotNull(next("stories/main_stories"))
    }

    @Test
    fun `showStories with no activity on screen, or before initialize, says why and does not throw`() {
        SDK().showStories("main_stories")
        assertTrue(logged.any { it.code == LogCodes.STORIES && it.message.contains("not initialized") })

        val sdk = sdk(coreV2 = false)
        sdk.showStories("main_stories")
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(logged.any { it.code == LogCodes.STORIES && it.message.contains("no activity") })
    }

    private companion object {
        const val SHOP = "deprecated-delegates-shop"
    }
}
