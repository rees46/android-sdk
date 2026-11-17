package com.personalization.features.cart

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.responses.cart.CartItem
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * `sdk.cart` as a bridge calls it: on the new core it asks the server and answers in both forms; on the
 * legacy core, where apps run, it fails with `notInitialized` and sends nothing.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CartNamespaceTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val paths = LinkedBlockingQueue<String>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

    /** The answer to `products/cart`; `/init` answers with a `did` unless [initAnswer] says otherwise. */
    @Volatile
    private var cartAnswer: MockResponse = MockResponse().setBody(CART)

    @Volatile
    private var initAnswer: MockResponse = MockResponse().setBody("""{"did":"D1","seance":"S1"}""")

    /** Holds the answer to `products/cart` while it is not null. */
    @Volatile
    private var holdCart: CountDownLatch? = null

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
                    "init" -> initAnswer
                    CartApiImpl.PATH -> {
                        holdCart?.await(5, TimeUnit.SECONDS)
                        cartAnswer
                    }
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        holdCart?.countDown()
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

    private fun get(sdk: SDK): SdkResult<List<CartItem>> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { sdk.cart.get() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The paths the server got, after the calls so far had time to arrive. */
    private fun sent(): List<String> {
        threads.awaitIdle()
        Thread.sleep(200)
        return generateSequence { paths.poll() }.toList()
    }

    /** A bridge's instance whose listener forms end on [main], which the test runs. */
    private fun bridgeSdk(main: HeldMain): SDK {
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        awaitState(sdk, SdkState.READY)
        return sdk
    }

    @Test
    fun `a bridge's instance gets the cart from the server`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)

        assertEquals(SdkResult.Success(listOf(CartItem("37", 2), CartItem("38", 1))), get(sdk))
        assertTrue(CartApiImpl.PATH in sent())
    }

    @Test
    fun `the form with a listener ends once, on the main thread`() {
        val main = HeldMain()
        val sdk = bridgeSdk(main)
        val ends = CopyOnWriteArrayList<Pair<SdkResult<List<CartItem>>, Boolean>>()

        sdk.cart.get(CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        while (ends.isEmpty() && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(listOf(SdkResult.Success(listOf(CartItem("37", 2), CartItem("38", 1))) to true), ends)
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized in both forms and sends nothing`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = false)
        awaitState(sdk, SdkState.READY)
        sent()

        val thrown = try {
            runBlocking { sdk.cart.get() }
            null
        } catch (e: SdkException) {
            e
        }
        val ends = CopyOnWriteArrayList<SdkResult<List<CartItem>>>()
        sdk.cart.get(CompletionListener { ends += it })
        while (ends.isEmpty() && main.runNext()) Unit

        assertEquals(SdkError.NotInitialized, thrown?.error)
        assertTrue("${thrown?.message}", thrown?.message.orEmpty().contains("Rees46.initializeBridge"))
        assertEquals(listOf<SdkResult<List<CartItem>>>(SdkResult.Failure(SdkError.NotInitialized)), ends)
        assertTrue(CartApiImpl.PATH !in sent())
        assertEquals(2, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == CartApiImpl.GET })
    }

    @Test
    fun `an instance never initialized gets notInitialized`() {
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), get(SDK()))
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `a 403 to the cart is http and leaves the instance ready`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)
        cartAnswer = MockResponse().setResponseCode(403)

        assertEquals(SdkResult.Failure(SdkError.Http(403)), get(sdk))
        assertEquals(SdkState.READY, sdk.state)
    }

    @Test
    fun `a refused shop gets shopBlocked without asking for the cart`() {
        initAnswer = MockResponse().setResponseCode(402).setBody("""{"status":"error","message":"Restricted Mode"}""")
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.REFUSED)

        assertEquals(SdkResult.Failure(SdkError.ShopBlocked), get(sdk))
        assertTrue(CartApiImpl.PATH !in sent())
    }

    @Test
    fun `release ends a call in flight with cancelled, and the calls after it fail with notInitialized`() {
        val main = HeldMain()
        val sdk = bridgeSdk(main)
        holdCart = CountDownLatch(1)
        val ends = CopyOnWriteArrayList<SdkResult<List<CartItem>>>()

        sdk.cart.get(CompletionListener { ends += it })
        // The request reached the server, which holds its answer.
        assertTrue(generateSequence { paths.poll(5, TimeUnit.SECONDS) }.any { it == CartApiImpl.PATH })
        sdk.release()
        while (ends.isEmpty() && main.runNext()) Unit
        holdCart?.countDown()

        assertEquals(listOf<SdkResult<List<CartItem>>>(SdkResult.Failure(SdkError.Cancelled)), ends)
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), get(sdk))
    }

    @Test
    fun `Java does not see the namespace`() {
        val getters = SDK::class.java.methods.filter { it.name == "getCart" }

        assertTrue(getters.isNotEmpty() && getters.all { it.isSynthetic })
    }

    private companion object {
        const val SHOP = "cart-namespace-shop"
        const val CART = """{"status":"success","data":{"items":[{"uniqid":"37","quantity":2},{"uniqid":"38","quantity":1}]}}"""
    }
}
