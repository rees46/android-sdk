package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.StorageMigrations
import com.personalization.core.internal.TrackingThreads
import com.personalization.core.internal.testCoreDispatchers
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.ui.widgets.RecentSearches
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Storage through the SDK as an app starts it: the partition [Rees46Config.storageKey] picks, the old
 * `preferencesKey` file read into it, and [SDK.deleteUserCredentials] with the `/init` that follows.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkStorageTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val inits = CopyOnWriteArrayList<String>()

    /** The legacy path's requests run here, so a test waits until they are answered instead of sleeping. */
    private val threads = TrackingThreads()
    private val initCount = AtomicInteger()
    private val tokens = LinkedBlockingQueue<JSONObject>()
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
        // Robolectric's default network has no internet capability: give it one, as a device's has.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        LegacyCore.threads = threads
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.encodedPath == "/mobile_push_tokens") tokens += JSONObject(request.body.readUtf8())
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
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
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

    /** Every `/init` sent so far has been answered and its answer handled. */
    private fun awaitInits(count: Int) {
        threads.awaitIdle()
        assertEquals("/init sent: $inits", count, inits.size)
    }

    private fun awaitDid(sdk: SDK, expected: String) {
        threads.awaitIdle()
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
        // A new device: its first /init is held, and the deletion starts over before it answers.
        val firstArrived = CountDownLatch(1)
        val answerFirst = CountDownLatch(1)
        initAnswer = { n, _ ->
            if (n == 1) {
                firstArrived.countDown()
                answerFirst.await(5, TimeUnit.SECONDS)
            }
            val did = if (n == 1) "stale-did" else "fresh-did"
            MockResponse().setBody("""{"did":"$did","seance":"S$n"}""")
        }
        val sdk = Rees46.initialize(context, config())
        assertTrue(firstArrived.await(5, TimeUnit.SECONDS))

        runBlocking { sdk.deleteUserCredentials() }
        answerFirst.countDown()

        // Both answers handled, the late one last.
        awaitDid(sdk, "fresh-did")
        assertEquals(listOf("", ""), inits)
    }

    @Test
    fun `the listener form ends once on the main thread`() {
        val sdk = Rees46.initialize(context, config())
        val scheduler = TestCoroutineScheduler()
        sdk.dispatchers = testCoreDispatchers(scheduler)
        val ends = mutableListOf<Pair<SdkResult<Unit>, Boolean>>()

        sdk.deleteUserCredentials(CompletionListener { result -> ends += result to (Looper.myLooper() == Looper.getMainLooper()) })
        scheduler.advanceUntilIdle()

        assertEquals(listOf<Pair<SdkResult<Unit>, Boolean>>(SdkResult.Success(Unit) to true), ends)
    }

    @Test
    fun `a listener that throws is logged and called once`() {
        val sdk = Rees46.initialize(context, config())
        val scheduler = TestCoroutineScheduler()
        sdk.dispatchers = testCoreDispatchers(scheduler)
        var calls = 0

        sdk.deleteUserCredentials(CompletionListener { calls++; throw IllegalStateException("app") })
        scheduler.advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(LogLevel.ERROR, logged.single { it.code == LogCodes.CALLBACK_FAILED }.level)
    }

    @Test
    fun `a deletion whose caller is cancelled midway still wipes and restarts, on both cores`() {
        initAnswer = { n, _ -> MockResponse().setBody("""{"did":"did-$n","seance":"S$n"}""") }
        listOf(false, true).forEach { coreV2 ->
            SdkRegistry.all().forEach { it.release() }
            CoreSelection.reset()
            initCount.set(0)
            inits.clear()
            context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
            val sdk = Rees46.initialize(context, config(coreV2 = coreV2))
            awaitReady(sdk, "did-1")
            val scheduler = TestCoroutineScheduler()
            sdk.dispatchers = testCoreDispatchers(scheduler)

            // The caller goes as soon as the wipe is handed to the io dispatcher.
            val caller = CoroutineScope(Dispatchers.Unconfined).launch { sdk.deleteUserCredentials() }
            assertEquals(SdkState.INITIALIZING, sdk.state)
            caller.cancel()
            scheduler.advanceUntilIdle()

            assertTrue(caller.isCancelled)
            awaitReady(sdk, "did-2")
            assertEquals("core v2: $coreV2", listOf("", ""), inits)
        }
    }

    /** Waits for the instance to be ready with [did]. */
    private fun awaitReady(sdk: SDK, did: String) {
        threads.awaitIdle()
        val reached = runBlocking {
            withTimeoutOrNull(5_000) { sdk.instanceState.changes.first { it == SdkState.READY && sdk.getDid() == did } }
        }
        assertTrue("state ${sdk.state}, did ${sdk.getDid()}, inits $inits, expected $did", reached != null)
    }

    @Test
    fun `an instance never initialized has nothing to delete`() {
        try {
            runBlocking { SDK().deleteUserCredentials() }
            fail("expected SdkException")
        } catch (e: SdkException) {
            assertEquals(SdkError.NotInitialized, e.error)
        }

        val scheduler = TestCoroutineScheduler()
        val ends = mutableListOf<SdkResult<Unit>>()
        SDK().apply { dispatchers = testCoreDispatchers(scheduler) }.deleteUserCredentials(CompletionListener { ends += it })
        scheduler.advanceUntilIdle()

        assertEquals(listOf<SdkResult<Unit>>(SdkResult.Failure(SdkError.NotInitialized)), ends)
    }

    @Test
    fun `the forgotten did is wiped from the store of older releases and does not come back`() {
        // A 2.4x install: the store all shops shared, with the app's segment beside the customer.
        context.getSharedPreferences(PreferencesPartition.LEGACY_KEY, Context.MODE_PRIVATE).edit()
            .putString("shop_id", SHOP).putString("did", "legacy-did").putString("sid", "legacy-sid")
            .putString("seance", "legacy-seance").putString("segment_ab_testing", "B").commit()
        val sdk = Rees46.initialize(context, config())
        assertEquals("legacy-did", sdk.getDid())

        runBlocking { sdk.deleteUserCredentials() }
        // The new customer's /init is done, and nothing writes to the partition any more.
        awaitDid(sdk, "new-did")
        sdk.release()

        val legacy = context.getSharedPreferences(PreferencesPartition.LEGACY_KEY, Context.MODE_PRIVATE)
        assertEquals(setOf("shop_id", "segment_ab_testing"), legacy.all.keys)
        // A restore that brought the old store back but not the partition: the start reads nothing old.
        val partition = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)
        partition.edit().clear().commit()
        StorageMigrations.run(context, StorageLayout(SHOP, null))
        assertEquals(null, partition.getString("did", null))
        assertEquals("B", partition.getString("segment_ab_testing", null))
    }

    @Test
    fun `the old preferencesKey file gives the SDK's keys only and loses only the customer`() {
        context.getSharedPreferences(HOST_KEY, Context.MODE_PRIVATE).edit()
            .putString("did", "host-did").putString("sid", "host-sid").putString("app_theme", "dark").commit()
        val sdk = Rees46.initialize(context, config(storageKey = HOST_KEY))
        assertEquals(null, sdk.storage?.preferences?.getString("app_theme", null))

        runBlocking { sdk.deleteUserCredentials() }

        assertEquals(mapOf("app_theme" to "dark"), context.getSharedPreferences(HOST_KEY, Context.MODE_PRIVATE).all)
    }

    @Test
    fun `deleting the customer forgets the shop's recent searches and keeps another shop's`() {
        RecentSearches(context, SHOP, 10).add("red shoes")
        RecentSearches(context, "other-shop", 10).add("blue hat")
        val sdk = Rees46.initialize(context, config())

        runBlocking { sdk.deleteUserCredentials() }

        assertEquals(emptyList<String>(), RecentSearches(context, SHOP, 10).load())
        assertEquals(listOf("blue hat"), RecentSearches(context, "other-shop", 10).load())
        RecentSearches(context, "other-shop", 10).clear()
    }

    @Test
    fun `a wipe that does not reach the disk is storage_failed, not device_id_reset, and still ends`() {
        val sdk = Rees46.initialize(context, config())
        val storage = checkNotNull(sdk.storage)
        storage.saveInitAnswer(JSONObject().put("did", "d"))
        // The answer's file cannot be deleted from a directory the SDK may not write.
        assertTrue(storage.directory.setWritable(false))
        try {
            val scheduler = TestCoroutineScheduler()
            sdk.dispatchers = testCoreDispatchers(scheduler)
            val ends = mutableListOf<SdkResult<Unit>>()

            sdk.deleteUserCredentials(CompletionListener { ends += it })
            scheduler.advanceUntilIdle()

            assertEquals(listOf<SdkResult<Unit>>(SdkResult.Success(Unit)), ends)
            assertTrue(logged.none { it.code == LogCodes.DEVICE_ID_RESET })
            assertTrue(logged.any { it.code == LogCodes.STORAGE_FAILED && it.level == LogLevel.ERROR })
        } finally {
            storage.directory.setWritable(true)
        }
    }

    @Test
    fun `after the deletion the push token waits for the new did`() {
        val secondInit = CountDownLatch(1)
        initAnswer = { n, _ ->
            if (n == 2) secondInit.await(5, TimeUnit.SECONDS)
            MockResponse().setBody("""{"did":"did-$n","seance":"S$n"}""")
        }
        val sdk = Rees46.initialize(context, config())
        awaitDid(sdk, "did-1")

        runBlocking { sdk.deleteUserCredentials() }
        sdk.setPushToken("app-token", PushProvider.FCM)

        // Held while the new /init has not answered: no token without a did.
        assertEquals(null, tokens.poll(300, TimeUnit.MILLISECONDS))
        secondInit.countDown()
        val token = checkNotNull(tokens.poll(5, TimeUnit.SECONDS))
        assertEquals("app-token", token.getString("token"))
        assertEquals("did-2", token.getString("did"))
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
