package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.BridgeApi
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
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
import okhttp3.HttpUrl
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The new core is the bridges': [Rees46.initializeBridge] starts a shop on it, with the A/B group the bridge
 * kept for the device, while [Rees46.initialize], [Rees46.register] and [Rees46Config] give apps no way to it.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BridgeInitTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val requests = LinkedBlockingQueue<HttpUrl>()
    private val threads = TrackingThreads()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        logged.clear()
        wipeStorage()
        // Robolectric's default network has no internet capability: give it one, as a device's has.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        server = MockWebServer()
        requests.clear()
        LegacyCore.threads = threads
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                requests += url
                if (url.encodedPath != "/init") return MockResponse().setBody("{}")
                return MockResponse().setBody(JSONObject().put("did", "new-did").put("seance", "S").toString())
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
    }

    /** A device that never ran the SDK: no partition, no device level, no files. */
    private fun wipeStorage() {
        listOf(SHOP, OTHER).forEach { partition(it).edit().clear().commit() }
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
    }

    private fun config(shopId: String = SHOP) = Rees46Config(
        shopId = shopId,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun partition(shopId: String = SHOP) =
        context.getSharedPreferences(StorageLayout(shopId, null).partitionName, Context.MODE_PRIVATE)

    private fun deviceSegment(): String? = DeviceStorage(context).segment()

    private fun awaitReady(sdk: SDK) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } }
    }

    /** Starts over on a device that never ran the SDK, as a new launch of a new install. */
    private fun freshDevice() {
        tearDown()
        setUp()
    }

    private fun logs(code: String, reason: String) =
        logged.filter { it.code == code && it.context["reason"] == reason }

    // The core.

    @Test
    fun `a bridge's shop runs on the new core, and decides the process's`() {
        val sdk = Rees46.initializeBridge(context, config())

        assertTrue(sdk.core is CoreV2)
        assertEquals(true, CoreSelection.decided())
        assertTrue(Rees46.instance(SHOP) === sdk)
        awaitReady(sdk)
        assertFalse(logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH })
    }

    @Test
    fun `an app's initialize and register stay on the legacy core`() {
        val sdk = Rees46.initialize(context, config())
        Rees46.register(context, listOf(config(OTHER)))

        assertTrue(sdk.core is LegacyCore)
        assertTrue(Rees46.instance(OTHER).core is LegacyCore)
        assertEquals(false, CoreSelection.decided())
        assertFalse(logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH })
    }

    @Test
    fun `the process keeps the core its first instance asked for, on either side, and warns once`() {
        Rees46.initialize(context, config())
        val bridge = Rees46.initializeBridge(context, config(OTHER))

        assertTrue(bridge.core is LegacyCore)
        assertEquals(1, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.shopId == OTHER })

        freshDevice()
        Rees46.initializeBridge(context, config())
        val app = Rees46.initialize(context, config(OTHER))

        assertTrue(app.core is CoreV2)
        assertEquals(1, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.shopId == OTHER })
    }

    @Test
    fun `a bridge asking again for a shop the app made gets that instance, on its core, and the mismatch is logged`() {
        val app = Rees46.initialize(context, config())

        val bridge = Rees46.initializeBridge(context, config())

        assertTrue(bridge === app)
        assertTrue(bridge.core is LegacyCore)
        assertEquals(1, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.shopId == SHOP })
    }

    // The A/B group a bridge kept.

    @Test
    fun `a device without an A B group takes the bridge's, and the shop's requests carry it`() {
        // Twice each: a random pick would match a value by chance, not every time.
        listOf("A", "B", "A", "B").forEach { segment ->
            freshDevice()

            val sdk = Rees46.initializeBridge(context, config(), segment)

            assertEquals(segment, deviceSegment())
            assertEquals(segment, partition().getString(StorageKeys.SEGMENT, null))
            assertEquals(segment, sdk.getSegment())
            assertTrue(logged.none { it.code == LogCodes.INVALID_CONFIG || it.code == LogCodes.INVALID_ARGUMENT })
        }
        val sdk = Rees46.instance(SHOP)
        awaitReady(sdk)
        val answered = LinkedBlockingQueue<String>()
        @Suppress("DEPRECATION")
        sdk.getAsync("products/get", JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                answered += "success"
            }

            override fun onError(code: Int, msg: String?) {
                answered += "error:$code"
            }
        })
        assertEquals("success", answered.poll(10, TimeUnit.SECONDS))
        val request = generateSequence { requests.poll(5, TimeUnit.SECONDS) }.first { it.encodedPath == "/products/get" }
        assertEquals("B", request.queryParameter("segment"))
    }

    @Test
    fun `a device with its own group keeps it, and a different group from the bridge is logged`() {
        context.getSharedPreferences(DeviceStorage.NAME, Context.MODE_PRIVATE).edit().putString(DeviceStorage.SEGMENT, "A").commit()

        val sdk = Rees46.initializeBridge(context, config(), "B")

        assertEquals("A", sdk.getSegment())
        assertEquals("A", deviceSegment())
        val kept = logs(LogCodes.INVALID_CONFIG, LogReasons.SEGMENT_KEPT).single()
        assertEquals(LogLevel.WARNING, kept.level)
        assertEquals(SHOP, kept.shopId)
    }

    @Test
    fun `the bridge's group equal to the device's is taken silently`() {
        context.getSharedPreferences(DeviceStorage.NAME, Context.MODE_PRIVATE).edit().putString(DeviceStorage.SEGMENT, "B").commit()

        val sdk = Rees46.initializeBridge(context, config(), "B")

        assertEquals("B", sdk.getSegment())
        assertTrue(logs(LogCodes.INVALID_CONFIG, LogReasons.SEGMENT_KEPT).isEmpty())
    }

    @Test
    fun `a shop that has a group from an earlier release keeps it and gives it to the device`() {
        partition().edit().putString(StorageKeys.SEGMENT, "A").commit()

        val sdk = Rees46.initializeBridge(context, config(), "B")

        assertEquals("A", sdk.getSegment())
        assertEquals("A", deviceSegment())
        assertEquals(1, logs(LogCodes.INVALID_CONFIG, LogReasons.SEGMENT_KEPT).size)
    }

    @Test
    fun `a later launch keeps the group the device took, whatever the bridge brings then`() {
        Rees46.initializeBridge(context, config(), "B")
        SdkRegistry.all().forEach { it.release() }
        CoreSelection.reset()

        val again = Rees46.initializeBridge(context, config(), "A")

        assertEquals("B", again.getSegment())
        assertEquals("B", deviceSegment())
        assertEquals(1, logs(LogCodes.INVALID_CONFIG, LogReasons.SEGMENT_KEPT).size)
    }

    @Test
    fun `the device's group taken from one shop's bridge goes to the other shops`() {
        Rees46.initializeBridge(context, config(), "B")

        val other = Rees46.initializeBridge(context, config(OTHER))

        assertEquals("B", other.getSegment())
    }

    @Test
    fun `a group the SDK never gives is refused with a log, and the shop starts with a group of its own`() {
        listOf("", " ", "C", "a", " A", "AB", "null").forEach { segment ->
            freshDevice()

            val sdk = Rees46.initializeBridge(context, config(), segment)

            assertTrue("'$segment'", sdk.core is CoreV2)
            val refused = logs(LogCodes.INVALID_ARGUMENT, LogReasons.INVALID_SEGMENT).single()
            assertEquals(LogLevel.WARNING, refused.level)
            assertEquals(SHOP, refused.shopId)
            assertTrue("'$segment'", sdk.getSegment() in DeviceStorage.SEGMENTS)
            assertTrue("'$segment'", deviceSegment() in DeviceStorage.SEGMENTS)
        }
    }

    @Test
    fun `without a group from the bridge nothing is logged and the device gets one`() {
        val sdk = Rees46.initializeBridge(context, config())

        assertTrue(sdk.getSegment() in DeviceStorage.SEGMENTS)
        assertTrue(logged.none { it.code == LogCodes.INVALID_CONFIG || it.code == LogCodes.INVALID_ARGUMENT })
    }

    // What an app compiled against the releases with Rees46Config.coreV2 still finds.

    private val oldParameters = listOf(
        String::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType, LogLevel::class.java,
        Boolean::class.javaPrimitiveType, String::class.java, String::class.java, Boolean::class.javaPrimitiveType,
    )

    private val newParameters = oldParameters.filterIndexed { index, _ -> index != 9 }

    @Test
    fun `apps get no way to the new core, no constructor or copy takes it and Java does not see the bridges' entry`() {
        val visible = Rees46Config::class.java.constructors.filterNot { it.isSynthetic }
        visible.forEach { constructor ->
            assertEquals(newParameters.take(constructor.parameterCount), constructor.parameterTypes.toList())
        }
        assertEquals((1..12).toList(), visible.map { it.parameterCount }.sorted())
        val copies = Rees46Config::class.java.methods.filter { it.name == "copy" && !it.isSynthetic }
        assertEquals(listOf(newParameters), copies.map { it.parameterTypes.toList() })
        val bridgeEntries = Rees46::class.java.methods.filter { it.name.startsWith("initializeBridge") }
        assertTrue(bridgeEntries.isNotEmpty() && bridgeEntries.all { it.isSynthetic })
    }

    @Test
    fun `the constructor of the releases with coreV2 is still there for compiled apps, and ignores the core`() {
        val full = Rees46Config::class.java.getConstructor(*oldParameters.toTypedArray())
        assertTrue(full.isSynthetic)

        val made = full.newInstance("shop", "api.rees46.ru", "android", true, false, true, "SDK", true, LogLevel.WARNING, true, null, null, false)

        assertEquals(Rees46Config("shop"), made)
        assertEquals(1, logs(LogCodes.INVALID_CONFIG, LogReasons.BRIDGE_ONLY).size)
    }

    @Test
    fun `the defaults of the releases with coreV2 are still there for compiled Kotlin apps`() {
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val withDefaults = Rees46Config::class.java.getDeclaredConstructor(*(oldParameters + Int::class.javaPrimitiveType + marker).toTypedArray())
        // Every parameter but the shop and the core left to its default, as `Rees46Config("shop", coreV2 = …)` compiled.
        val mask = ((1 shl 13) - 1) and (1 shl 0).inv() and (1 shl 9).inv()

        listOf(false, true).forEach { coreV2 ->
            val made = withDefaults.newInstance("shop", null, null, false, false, false, null, false, null, coreV2, null, null, false, mask, null)
            assertEquals(Rees46Config("shop"), made)
        }
        assertEquals(1, logs(LogCodes.INVALID_CONFIG, LogReasons.BRIDGE_ONLY).size)

        val copy = Rees46Config::class.java.getMethod("copy", *oldParameters.toTypedArray())
        assertTrue(copy.isSynthetic)
        val copied = copy.invoke(Rees46Config("shop"), "other", "api.rees46.ru", "android", true, false, true, "SDK", true, LogLevel.DEBUG, true, "key", null, false)
        assertEquals(Rees46Config("other", logLevel = LogLevel.DEBUG, storageKey = "key"), copied)
        assertEquals(2, logs(LogCodes.INVALID_CONFIG, LogReasons.BRIDGE_ONLY).size)
    }

    private companion object {
        const val SHOP = "bridge-shop"
        const val OTHER = "bridge-other"
    }
}
