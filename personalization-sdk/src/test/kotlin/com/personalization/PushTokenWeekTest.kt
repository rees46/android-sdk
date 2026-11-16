package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
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
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import com.personalization.push.PushTokenSource
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The push token's week belongs to the `did` that sent it (`core/push.md`, «Токен»), on a local server: a new
 * `did` — `needReInitialization` on either core, `deleteUserCredentials()` while a send is on its way — gets
 * the token at once, not a week later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushTokenWeekTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val threads = TrackingThreads()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultSources = PushTokenManager.sourceFactory

    /** The dids the server hands out, one per /init; the last one stays. */
    private val dids = ConcurrentLinkedQueue<String>()

    @Volatile
    private var lastDid = "D1"

    /** Holds the answers to mobile_push_tokens while it is not null. */
    @Volatile
    private var holdTokens: CountDownLatch? = null

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
        PushTokenManager.sourceFactory = { emptyList() }
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
        requests.clear()
        seen.clear()
        dids.clear()
        lastDid = "D1"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val body = if (request.method == "POST") JSONObject(request.body.readUtf8())
                else JSONObject().apply { url.queryParameterNames.forEach { put(it, url.queryParameter(it)) } }
                requests += path to body
                seen += path to body
                return when (path) {
                    "init" -> {
                        val did = dids.poll() ?: lastDid
                        lastDid = did
                        MockResponse().setBody("""{"did":"$did","seance":"S-$did"}""")
                    }
                    "mobile_push_tokens" -> {
                        holdTokens?.await(5, TimeUnit.SECONDS)
                        MockResponse().setBody("{}")
                    }
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        holdTokens?.countDown()
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

    private fun config(coreV2: Boolean, needReInitialization: Boolean = false) = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = true,
        needReInitialization = needReInitialization,
        coreV2 = coreV2,
        logLevel = LogLevel.DEBUG,
    )

    private fun partition() = context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE)

    /** The next request to [path], skipping others; null when none came in [timeoutMs]. */
    private fun next(path: String, timeoutMs: Long = 5_000): JSONObject? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return null
            val request = requests.poll(left, TimeUnit.NANOSECONDS) ?: return null
            if (request.first == path) return request.second
        }
    }

    /** A device whose did [did] sent the token "tok" a day ago, as a launch before left it. */
    private fun sentYesterday(did: String) {
        val yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        partition().edit()
            .putString(StorageKeys.DID, did)
            .putString(StorageKeys.pushToken("fcm"), "tok")
            .putLong(StorageKeys.pushTokenDate("fcm"), yesterday)
            .commit()
        DeviceStorage(context).savePushToken("fcm", "tok")
    }

    /** FCM on the device, whose token is "tok". */
    private class Fcm : PushTokenSource {
        override val provider: PushProvider = PushProvider.FCM
        override fun isAvailable(context: Context): Boolean = true
        override fun fetchToken(context: Context, callback: (Result<String?>) -> Unit) = callback(Result.success("tok"))
    }

    @Test
    fun `on the new core needReInitialization sends the token sent this week to the new did at once`() {
        sentYesterday("D0")

        val sdk = Rees46.initialize(context, config(coreV2 = true, needReInitialization = true))

        assertFalse("/init asks for a new did", next("init")?.has("did") ?: true)
        val token = next("mobile_push_tokens") ?: throw AssertionError("the token waited for a week")
        assertEquals("D1", token.getString("did"))
        assertEquals("tok", token.getString("token"))
        runBlocking { withTimeout(5_000) { sdk.pushTokenManager.core?.awaitIdle() } }
        assertEquals(1, seen.count { it.first == "mobile_push_tokens" })
    }

    @Test
    fun `on the legacy core a did another init hands out for needReInitialization gets the token sent this week at once`() {
        sentYesterday("D0")
        PushTokenManager.sourceFactory = { listOf(Fcm()) }

        val sdk = Rees46.initialize(context, config(coreV2 = false, needReInitialization = true))

        val token = next("mobile_push_tokens") ?: throw AssertionError("the token waited for a week")
        assertEquals("D1", token.getString("did"))
        assertEquals("D1", sdk.deviceId)
        threads.awaitIdle()
        assertEquals(1, seen.count { it.first == "mobile_push_tokens" })
    }

    @Test
    fun `on the legacy core an init that keeps the did keeps the token's week`() {
        sentYesterday("D1")
        PushTokenManager.sourceFactory = { listOf(Fcm()) }

        val sdk = Rees46.initialize(context, config(coreV2 = false, needReInitialization = true))
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        next("init")
        threads.awaitIdle()

        // Asked for its token once before /init and once after: neither goes.
        assertNull(next("mobile_push_tokens", timeoutMs = 1_000))
        assertEquals("D1", sdk.deviceId)
    }

    @Test
    fun `a token send on its way when the customer is deleted goes again for the new did at once`() {
        dids += listOf("D1", "D2")
        holdTokens = CountDownLatch(1)
        val sdk = Rees46.initialize(context, config(coreV2 = true))
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        sdk.setPushToken("tok", PushProvider.FCM)
        // The server has the send and has not answered yet.
        assertEquals("D1", next("mobile_push_tokens")?.getString("did"))

        runBlocking { withTimeout(5_000) { sdk.deleteUserCredentials() } }

        val again = next("mobile_push_tokens") ?: throw AssertionError("the token waited for the forgotten customer's send")
        assertEquals("D2", again.getString("did"))
        holdTokens?.countDown()
        runBlocking { withTimeout(5_000) { sdk.pushTokenManager.core?.awaitIdle() } }
        assertEquals("the week is the new did's", "tok", partition().getString(StorageKeys.pushToken("fcm"), null))
        assertEquals(2, seen.count { it.first == "mobile_push_tokens" })
    }

    private companion object {
        const val SHOP = "token-week-shop"
    }
}
