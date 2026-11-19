package com.personalization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.SearchParams
import com.personalization.api.responses.cart.CartContent
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse
import com.personalization.api.responses.recommendation.GetRecommendationResponse
import com.personalization.api.responses.search.SearchBlankResponse
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.api.responses.search.SearchInstantResponse
import com.personalization.core.BridgeApi
import com.personalization.core.BridgeErrors
import com.personalization.core.SdkError
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The bug fixes of the old methods, on both cores: a `2xx` without a body still calls the callback, with an
 * empty result; a request that cannot be built tells its listener; `getCategory` sends its id as one segment of
 * the path; the old `subscribeFor…` send the product as `item_id`.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class LegacyMethodFixesTest {

    private lateinit var context: Context
    private var server = MockWebServer()
    private val threads = TrackingThreads()

    /** Every request but `/init`: its URL and its body (empty for a GET). */
    private val requests = LinkedBlockingQueue<Pair<HttpUrl, JSONObject>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
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
                val body = request.body.readUtf8()
                requests += url to (if (body.isBlank()) JSONObject() else JSONObject(body))
                // A 2xx without a body, to every request.
                return MockResponse().setResponseCode(200)
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
    }

    private fun sdk(coreV2: Boolean): SDK {
        val sdk = Rees46.initialize(
            context,
            Rees46Config(shopId = SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false),
            coreV2,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private fun <T> LinkedBlockingQueue<T>.next(name: String): T = poll(5, TimeUnit.SECONDS) ?: throw AssertionError("$name: nothing heard")

    /** Both cores, one after the other. */
    private fun bothCores(block: (sdk: SDK, name: String) -> Unit) = listOf(true, false).forEach { coreV2 ->
        if (!coreV2) tearDown().also { setUp() }
        block(sdk(coreV2), "core v2 $coreV2")
    }

    @Test
    fun `a 2xx without a body calls the old callbacks of search, recommendations and cart with an empty result`() = bothCores { sdk, name ->
        val heard = LinkedBlockingQueue<Any>()
        val onError: (Int, String?) -> Unit = { code, _ -> heard += "error:$code" }

        sdk.searchManager.searchFull("boots", SearchParams(), { heard += it }, onError)
        val full = heard.next("$name full") as SearchFullResponse
        assertEquals(name, "boots", full.searchQuery)
        assertEquals(name, emptyList<Any>(), full.products)
        assertEquals(name, 0, full.productsTotal)

        sdk.searchManager.searchInstant("boots", null, null, null, { heard += it }, onError)
        val instant = heard.next("$name instant") as SearchInstantResponse
        assertEquals(name, emptyList<Any>(), instant.products)
        assertEquals(name, emptyList<Any>(), instant.queries)

        sdk.searchManager.searchBlank({ heard += it }, onError)
        val blank = heard.next("$name blank") as SearchBlankResponse
        assertEquals(name, emptyList<Any>(), blank.products)
        assertEquals(name, emptyList<Any>(), blank.suggests)

        sdk.recommendationManager.getRecommendation("block", Params(), { heard += it }, onError)
        assertEquals(name, GetRecommendationResponse("", 0, emptyList(), ""), heard.next("$name recommendation"))

        sdk.recommendationManager.getExtendedRecommendation("block", Params(), { heard += it }, onError)
        assertEquals(name, GetExtendedRecommendationResponse("", 0, emptyList(), ""), heard.next("$name extended recommendation"))

        sdk.cartManager.getClientShoppingCartContent({ heard += it }, onError)
        assertEquals(name, CartContent(emptyList()), heard.next("$name cart"))

        sdk.cartManager.clearClientShoppingCartContent("secret", com.personalization.api.params.CartParams(), { heard += it }, onError)
        assertEquals(name, true, heard.next("$name clear"))
        assertTrue(name, heard.isEmpty())
    }

    @Test
    fun `a request that cannot be built tells its listener, behind invalidArgument`() = bothCores { sdk, name ->
        val heard = LinkedBlockingQueue<Pair<Int, SdkError?>>()
        val listener = object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                heard += code to BridgeErrors.currentError()
            }
        }
        // A key JSON cannot take: a map from Java may hold a null key.
        @Suppress("UNCHECKED_CAST")
        val subscriptions = (HashMap<String?, Boolean>().apply { put(null, true) }) as HashMap<String, Boolean>

        sdk.manageSubscription(email = "a@b.c", phone = null, subscriptions = subscriptions, listener = listener)

        val (code, error) = heard.next(name)
        assertEquals(name, -1, code)
        assertTrue("$name: $error", error is SdkError.InvalidArgument)
        threads.awaitIdle()
        assertFalse(name, requests.any { it.first.encodedPath.contains("subscriptions") })
    }

    @Test
    fun `getCategory sends its id as one segment of the path`() = bothCores { sdk, name ->
        val done = LinkedBlockingQueue<Any>()

        sdk.categoryManager.getCategory("shoes/men 1?x", null, null, null, null, null, { done += it }, { code, _ -> done += code })
        done.next(name)

        val url = requests.next(name).first
        assertEquals(name, "/category/shoes%2Fmen%201%3Fx", url.encodedPath)
        assertEquals(name, null, url.queryParameter("x"))
    }

    @Test
    fun `the old subscribeFor methods send the product as item_id`() = bothCores { sdk, name ->
        sdk.subscribeForPriceDrop("37", 10.0, email = "a@b.c")
        val price = requests.next("$name price").second
        sdk.subscribeForBackInStock("38", email = "a@b.c")
        val stock = requests.next("$name stock").second

        assertEquals(name, "37", price.getString("item_id"))
        assertFalse(name, price.has("item"))
        assertEquals(name, "38", stock.getString("item_id"))
        assertFalse(name, stock.has("item"))
    }

    private companion object {
        const val SHOP = "legacy-fixes-shop"
    }
}
