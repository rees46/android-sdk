package com.personalization.features.profile

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.models.profile.Gender
import com.personalization.api.models.profile.Profile
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.api.models.segments.Segment
import com.personalization.api.models.subscriptions.SubscriptionStatus
import com.personalization.api.models.subscriptions.SubscriptionsUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.HeldMain
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.features.segments.SegmentsApiImpl
import com.personalization.features.subscriptions.SubscriptionsApiImpl
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * The customer's namespaces — `sdk.profile`, `sdk.subscriptions`, `sdk.segments` — as a bridge calls them:
 * on the new core they ask the server and answer in both forms; on the legacy core, where apps run, every
 * method that asks the server fails with `notInitialized`, whatever its arguments, and sends nothing.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClientNamespacesTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val paths = LinkedBlockingQueue<String>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

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
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        LegacyCore.threads = threads
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath?.removePrefix("/") ?: return MockResponse().setResponseCode(400)
                paths += path
                return when (path) {
                    "init" -> MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                    ProfileApiImpl.GET_PATH -> MockResponse().setBody("""{"id":"7","gender":"m"}""")
                    SubscriptionsApiImpl.CHECK_PATH -> MockResponse().setBody("""{"exists":true,"email_bulk":true}""")
                    SegmentsApiImpl.GET_PATH -> MockResponse().setBody("""[{"id":5,"type":"static"}]""")
                    else -> MockResponse().setBody("""{"status":"success"}""")
                }
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

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    private fun <T> outcome(block: suspend () -> T): SdkResult<T> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { block() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The paths the server got, after the calls so far had time to arrive. */
    private fun sent(): List<String> {
        threads.awaitIdle()
        Thread.sleep(200)
        return generateSequence { paths.poll() }.toList()
    }

    @Test
    fun `a bridge's instance gets each namespace's answer from the server`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)

        assertEquals(SdkResult.Success(Profile(id = "7", gender = Gender.MALE)), outcome { sdk.profile.get() })
        assertEquals(SdkResult.Success(Unit), outcome { sdk.profile.set(ProfileUpdateRequest(location = "msk")) })
        assertEquals(SdkResult.Success(SubscriptionStatus(exists = true, emailBulk = true)), outcome { sdk.subscriptions.check() })
        assertEquals(SdkResult.Success(listOf(Segment(5, "static"))), outcome { sdk.segments.get() })
        assertEquals(sdk.getSegment(), sdk.segments.getAbGroup())
        assertTrue(sdk.segments.getAbGroup() in setOf("A", "B"))
        val sent = sent()
        assertTrue(sent.toString(), sent.containsAll(listOf("profile", "profile/set", "subscriptions/check", "segments/get")))
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized from every request, whatever its arguments, and sends nothing`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = false)
        awaitState(sdk, SdkState.READY)
        sent()
        val calls: Map<String, suspend () -> Any?> = mapOf(
            ProfileApiImpl.GET to { sdk.profile.get() },
            ProfileApiImpl.SET to { sdk.profile.set(ProfileUpdateRequest()) },
            ProfileApiImpl.GET_LAST_ORDER to { sdk.profile.getLastOrder() },
            SubscriptionsApiImpl.MANAGE to { sdk.subscriptions.manage(SubscriptionsUpdateRequest()) },
            SubscriptionsApiImpl.CHECK to { sdk.subscriptions.check() },
            SubscriptionsApiImpl.SUBSCRIBE_PRICE to { sdk.subscriptions.subscribeForPriceDrop("37", 0.0) },
            SubscriptionsApiImpl.UNSUBSCRIBE_PRICE to { sdk.subscriptions.unsubscribeFromPriceDrop(listOf("")) },
            SubscriptionsApiImpl.SUBSCRIBE_STOCK to { sdk.subscriptions.subscribeForBackInStock("") },
            SubscriptionsApiImpl.UNSUBSCRIBE_STOCK to { sdk.subscriptions.unsubscribeFromBackInStock(listOf("37")) },
            SegmentsApiImpl.ADD to { sdk.segments.add(0) },
            SegmentsApiImpl.REMOVE to { sdk.segments.remove(12) },
            SegmentsApiImpl.GET to { sdk.segments.get() },
        )

        calls.forEach { (call, block) -> assertEquals(call, SdkResult.Failure(SdkError.NotInitialized), outcome { block() }) }
        val ends = CopyOnWriteArrayList<SdkResult<Unit>>()
        sdk.segments.add(12, null, null, CompletionListener { ends += it })
        while (ends.isEmpty() && main.runNext()) Unit

        assertEquals(listOf<SdkResult<Unit>>(SdkResult.Failure(SdkError.NotInitialized)), ends)
        assertTrue(sent().none { it != "init" })
        calls.keys.forEach { call ->
            assertTrue(call, logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == call })
        }
        // The A/B group asks nothing of the server: it is there on either core.
        assertEquals(sdk.getSegment(), sdk.segments.getAbGroup())
    }

    @Test
    fun `an instance never initialized gets notInitialized, and an empty A-B group`() {
        val sdk = SDK()

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.profile.get() })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.subscriptions.check() })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.segments.get() })
        assertEquals("", sdk.segments.getAbGroup())
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `Java does not see the namespaces`() {
        listOf("getProfile", "getSubscriptions", "getSegments").forEach { name ->
            val getters = SDK::class.java.methods.filter { it.name == name }
            assertTrue(name, getters.isNotEmpty() && getters.all { it.isSynthetic })
        }
    }

    private companion object {
        const val SHOP = "client-namespace-shop"
    }
}
