package com.personalization

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Storage through the SDK as an app starts it: the partition [Rees46Config.storageKey] picks, the old
 * `preferencesKey` file read into it, and [SDK.deleteUserCredentials] with the `/init` that follows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkStorageTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val inits = CopyOnWriteArrayList<String>()
    private val initCount = AtomicInteger()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** The answer to the n-th `/init` (from 1), given the `did` it carried. */
    @Volatile
    private var initAnswer: (n: Int, did: String) -> MockResponse = { _, did ->
        MockResponse().setBody(JSONObject().put("did", did.ifEmpty { "new-did" }).put("seance", "S").toString())
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        listOf(StorageLayout(SHOP, null), StorageLayout(SHOP, HOST_KEY)).forEach {
            context.getSharedPreferences(it.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        }
        listOf(HOST_KEY, PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.encodedPath != "/init") return MockResponse().setBody("{}")
                val did = url.queryParameter("did").orEmpty()
                inits += did
                return initAnswer(initCount.incrementAndGet(), did)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config(storageKey: String? = null, coreV2: Boolean = false) = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
        coreV2 = coreV2,
        storageKey = storageKey,
    )

    private fun awaitInits(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (inits.size < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("expected $count /init, got $inits", inits.size >= count)
    }

    private fun awaitDid(sdk: SDK, expected: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (sdk.getDid() != expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(expected, sdk.getDid())
    }

    @Test
    fun `a storageKey prefixes the shop's partition and reads the file of that name once`() {
        // The whole store of SDK.initialize(preferencesKey = "host_prefs") in an earlier release.
        context.getSharedPreferences(HOST_KEY, Context.MODE_PRIVATE).edit()
            .putString("did", "host-did").putString("sid", "host-sid").putString("shop_id", SHOP).commit()

        val sdk = Rees46.initialize(context, config(storageKey = HOST_KEY))

        assertEquals("host_prefs_personalization_sdk_shop-s", sdk.storage?.layout?.partitionName)
        assertEquals("host-did", sdk.getDid())
        awaitInits(1)
        assertEquals("host-did", inits.single())
        assertEquals("host-did", context.getSharedPreferences(HOST_KEY, Context.MODE_PRIVATE).getString("did", null))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the deprecated preferencesKey opens the same partition as the storageKey`() {
        context.getSharedPreferences(HOST_KEY, Context.MODE_PRIVATE).edit().putString("did", "host-did").commit()

        val sdk = SDK()
        sdk.initialize(context, SHOP, apiDomain = server.url("/").toString().trimEnd('/'), preferencesKey = HOST_KEY, autoSendPushToken = false)

        assertEquals(StorageLayout(SHOP, HOST_KEY).partitionName, sdk.storage?.layout?.partitionName)
        assertEquals("host-did", sdk.getDid())
    }

    @Test
    fun `deleting the customer forgets the did, logs it and asks init for a new one`() {
        val partition = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)
        partition.edit().putString("did", "old-did").putString("sid", "old-sid").putLong("sid_last_act", System.currentTimeMillis()).commit()
        val sdk = Rees46.initialize(context, config())
        awaitInits(1)
        sdk.storage?.saveInitAnswer(JSONObject().put("did", "old-did"))

        runBlocking { sdk.deleteUserCredentials() }

        awaitInits(2)
        assertEquals(listOf("old-did", ""), inits)
        awaitDid(sdk, "new-did")
        assertTrue(sdk.getSid() != "old-sid")
        assertEquals(null, sdk.storage?.initAnswer())
        val reset = logged.single { it.code == LogCodes.DEVICE_ID_RESET }
        assertEquals(LogLevel.INFO, reset.level)
        assertEquals(SHOP, reset.shopId)
        assertEquals("delete_user_credentials", reset.context["reason"])
        // The instance stays registered.
        assertTrue(Rees46.getInstance(SHOP) === sdk)
    }

    @Test
    fun `an init answer for the customer before the deletion is not taken`() {
        // A new device: its first /init is slow, and the deletion starts over before it answers.
        initAnswer = { n, _ ->
            val did = if (n == 1) "stale-did" else "fresh-did"
            MockResponse().setBody("""{"did":"$did","seance":"S$n"}""").apply { if (n == 1) setHeadersDelay(1_000, TimeUnit.MILLISECONDS) }
        }
        val sdk = Rees46.initialize(context, config())
        awaitInits(1)

        runBlocking { sdk.deleteUserCredentials() }

        awaitDid(sdk, "fresh-did")
        Thread.sleep(1_500)
        assertEquals("fresh-did", sdk.getDid())
    }

    @Test
    fun `the listener form ends once on the main thread`() {
        val sdk = Rees46.initialize(context, config())
        val ends = CopyOnWriteArrayList<Pair<SdkError?, Boolean>>()

        sdk.deleteUserCredentials(CompletionListener { error -> ends += error to (Looper.myLooper() == Looper.getMainLooper()) })

        val deadline = System.currentTimeMillis() + 5_000
        while (ends.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf<Pair<SdkError?, Boolean>>(null to true), ends)
    }

    @Test
    fun `an instance never initialized has nothing to delete`() {
        try {
            runBlocking { SDK().deleteUserCredentials() }
            fail("expected SdkException")
        } catch (e: SdkException) {
            assertEquals(SdkError.NotInitialized, e.error)
        }

        val ends = CopyOnWriteArrayList<SdkError?>()
        SDK().deleteUserCredentials(CompletionListener { ends += it })
        val deadline = System.currentTimeMillis() + 5_000
        while (ends.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(listOf<SdkError?>(SdkError.NotInitialized), ends)
    }

    @Test
    fun `on the new core the deletion takes the queue too`() {
        val sdk = Rees46.initialize(context, config(coreV2 = true))
        val queue = sdk.storage?.queueDirectory()
        File(queue, "event").writeText("{}")

        runBlocking { sdk.deleteUserCredentials() }

        assertTrue(queue?.exists() == false)
    }

    private companion object {
        const val SHOP = "shop-s"
        const val HOST_KEY = "host_prefs"
    }
}
