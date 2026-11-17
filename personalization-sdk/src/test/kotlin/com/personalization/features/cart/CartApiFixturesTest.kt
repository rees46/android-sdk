package com.personalization.features.cart

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.responses.cart.CartItem
import com.personalization.core.BridgeApi
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.FakeConnectivity
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.startedCoreInit
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** `cart.get` against the contract's fixtures (`contract/0/cart.json`): the request it makes and how it reads each answer. */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CartApiFixturesTest {

    private val server = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns SHOP
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getStream() } returns "android"
        every { getSegmentForABTesting() } returns "B"
    }
    private lateinit var core: CoreV2
    private lateinit var cart: CartApiImpl

    @Before
    fun setUp() {
        SdkLogs.reset()
        server.start()
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, SHOP)
        core = CoreV2(client, SHOP).also {
            it.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity, SHOP)
        }
        cart = CartApiImpl(NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { SHOP }))
    }

    @After
    fun tearDown() {
        core.release()
        server.close()
        SdkLogs.reset()
    }

    private fun get(): SdkResult<List<CartItem>> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { cart.get() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    @Test
    fun `get makes the contract's request, with no field of its own beside the common ones`() {
        val cases = ContractFixtures.requests("cart")
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            val expected = case.getJSONObject("request")
            server.enqueue(MockResponse().setBody("""{"status":"success","data":{"items":[]}}"""))

            get()

            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.let { recorded ->
                assertEquals(name, expected.getString("method"), recorded.method)
                recorded.requestUrl!!
            }
            assertEquals(name, "/" + expected.getString("path"), url.encodedPath)
            val query = expected.getJSONObject("query")
            assertEquals(name, query.keys().asSequence().toSet(), url.queryParameterNames - COMMON_FIELDS)
            query.keys().forEach { key -> assertEquals(name, query.getString(key), url.queryParameter(key)) }
            // The core adds the common fields; the core's own tests check their values.
            assertEquals(name, COMMON_FIELDS, url.queryParameterNames intersect COMMON_FIELDS)
        }
    }

    @Test
    fun `get reads every answer of the contract into its items or its error`() {
        val cases = ContractFixtures.answers("cart")
        assertTrue(cases.size >= 5)
        cases.forEach { case ->
            val name = case.getString("name")
            val response = ContractFixtures.mockResponse(case)
            // No answer: the request finds no network.
            connectivity.set(response != null)
            response?.let(server::enqueue)
            val expect = case.getJSONObject("expect")

            val outcome = get()

            if (expect.has("result")) {
                assertEquals(name, SdkResult.Success(items(expect.getJSONArray("result"))), outcome)
            } else {
                assertEquals(name, SdkResult.Failure(ContractFixtures.errorOf(expect.getJSONObject("error"))), outcome)
            }
        }
    }

    @Test
    fun `an answer without a body is an empty cart`() {
        server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(emptyList<CartItem>()), get())
    }

    private fun items(array: JSONArray): List<CartItem> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        CartItem(id = item.getString("id"), quantity = item.getInt("quantity"))
    }

    private companion object {
        const val SHOP = "cart-shop"
        val COMMON_FIELDS = setOf("shop_id", "did", "sid", "seance", "segment", "stream")
    }
}
