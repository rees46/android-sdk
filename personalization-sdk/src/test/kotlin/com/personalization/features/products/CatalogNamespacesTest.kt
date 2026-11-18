package com.personalization.features.products

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
import com.personalization.api.models.products.Product
import com.personalization.api.models.products.ProductsListRequest
import com.personalization.api.models.recommendations.RecommendationRequest
import com.personalization.api.models.search.SearchFullRequest
import com.personalization.api.models.search.SearchInstantResponse
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
import com.personalization.features.collection.CollectionsApiImpl
import com.personalization.features.recommendation.RecommendationsApiImpl
import com.personalization.features.search.SearchApiImpl
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
 * The catalog's namespaces — `sdk.products`, `sdk.collections`, `sdk.recommendations`, `sdk.search` — as a
 * bridge calls them: on the new core they ask the server and answer in both forms; on the legacy core,
 * where apps run, every method fails with `notInitialized`, whatever its arguments, and sends nothing.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CatalogNamespacesTest {

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
                    ProductsApiImpl.INFO_PATH -> MockResponse().setBody(PRODUCT)
                    CollectionsApiImpl.PATH + "12" -> MockResponse().setBody("""{"products":[$PRODUCT]}""")
                    RecommendationsApiImpl.PATH + "block-1" -> MockResponse().setBody("""{"id":1,"recommends":[$PRODUCT]}""")
                    SearchApiImpl.SEARCH_PATH -> MockResponse().setBody("""{"search_query":"shoes","products":[$PRODUCT]}""")
                    else -> MockResponse().setBody("{}")
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

        assertEquals(SdkResult.Success(SHOES), outcome { sdk.products.getInfo("37") })
        assertEquals(SdkResult.Success(listOf(SHOES)), outcome { sdk.collections.get("12").products })
        assertEquals(SdkResult.Success(listOf("37")), outcome { sdk.recommendations.get("block-1").itemIds })
        assertEquals(SdkResult.Success(listOf(SHOES)), outcome { sdk.search.full("shoes").products })
        val sent = sent()
        assertTrue(sent.toString(), sent.containsAll(listOf("products/get", "collection/12", "recommend/block-1", "search")))
    }

    @Test
    fun `the form with a listener ends once, on the main thread`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        awaitState(sdk, SdkState.READY)
        val ends = CopyOnWriteArrayList<Pair<SdkResult<SearchInstantResponse>, Boolean>>()

        sdk.search.instant("shoes", null, null, null, CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        while (ends.isEmpty() && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(1, ends.size)
        assertEquals(SearchInstantResponse(searchQuery = "shoes", products = listOf(SHOES)), (ends[0].first as SdkResult.Success).value)
        assertTrue(ends[0].second)
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized from every method, whatever its arguments, and sends nothing`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = false)
        awaitState(sdk, SdkState.READY)
        sent()
        val calls: Map<String, suspend () -> Any?> = mapOf(
            ProductsApiImpl.GET_INFO to { sdk.products.getInfo("") },
            ProductsApiImpl.GET_LIST to { sdk.products.getList(ProductsListRequest(limit = 0)) },
            ProductsApiImpl.GET_COUNTERS to { sdk.products.getCounters("37") },
            ProductsApiImpl.GET_CATEGORY to { sdk.products.getCategory("5") },
            CollectionsApiImpl.GET to { sdk.collections.get("") },
            RecommendationsApiImpl.GET to { sdk.recommendations.get("block-1", RecommendationRequest(limit = 0)) },
            SearchApiImpl.BLANK to { sdk.search.blank() },
            SearchApiImpl.INSTANT to { sdk.search.instant("") },
            SearchApiImpl.FULL to { sdk.search.full("shoes", SearchFullRequest(page = 0)) },
        )

        calls.forEach { (call, block) -> assertEquals(call, SdkResult.Failure(SdkError.NotInitialized), outcome { block() }) }
        val ends = CopyOnWriteArrayList<SdkResult<Product?>>()
        sdk.products.getInfo("37", CompletionListener { ends += it })
        while (ends.isEmpty() && main.runNext()) Unit

        assertEquals(listOf<SdkResult<Product?>>(SdkResult.Failure(SdkError.NotInitialized)), ends)
        assertTrue(sent().none { it != "init" })
        calls.keys.forEach { call ->
            assertTrue(call, logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == call })
        }
    }

    @Test
    fun `an instance never initialized gets notInitialized`() {
        val sdk = SDK()

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.products.getInfo("37") })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.search.blank() })
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `a bridge's instance checks the arguments before any request`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)
        sent()

        val outcome = outcome { sdk.products.getList(ProductsListRequest(priceMin = 10.0, priceMax = 1.0)) }

        assertTrue("$outcome", outcome is SdkResult.Failure && outcome.error is SdkError.InvalidArgument)
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `Java does not see the namespaces`() {
        listOf("getProducts", "getCollections", "getRecommendations", "getSearch").forEach { name ->
            val getters = SDK::class.java.methods.filter { it.name == name }
            assertTrue(name, getters.isNotEmpty() && getters.all { it.isSynthetic })
        }
    }

    private companion object {
        const val SHOP = "catalog-namespace-shop"
        const val PRODUCT = """{"id":"37","name":"Shoes","url":"https://shop.example/p/37","price":4990,"currency":"USD"}"""
        val SHOES = Product(id = "37", name = "Shoes", url = "https://shop.example/p/37", price = 4990.0, currency = "USD")
    }
}
