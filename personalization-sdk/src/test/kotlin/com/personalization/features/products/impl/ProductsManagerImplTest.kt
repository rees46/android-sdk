package com.personalization.features.products.impl

import com.personalization.Fixtures
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.products.ProductsListResponse
import com.personalization.api.responses.products.counters.ProductCountersResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProductsManagerImplTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var impl: ProductsManagerImpl

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        impl = ProductsManagerImpl(sendNetworkMethodUseCase)
    }

    @Test
    fun getProductCounters_getsFromCountersEndpoint_withItem() {
        val methodSlot = slot<String>()
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(capture(methodSlot), capture(paramsSlot), any())
        } just Runs

        impl.getProductCounters(item = "300275", onSuccess = {})

        verify(exactly = 1) { sendNetworkMethodUseCase.getAsync(any(), any(), any()) }
        assertEquals(ProductsManagerImpl.GET_PRODUCT_COUNTERS_REQUEST, methodSlot.captured)
        assertEquals("300275", paramsSlot.captured.getString("item"))
    }

    @Test
    fun getProductCounters_parsesCounters_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: ProductCountersResponse? = null
        impl.getProductCounters(item = "300275", onSuccess = { result = it })

        listenerSlot.captured.onSuccess(
            JSONObject(
                """{"daily":{"view":3,"cart":1,"purchase":0},"now":{"view":5,"cart":0,"purchase":0},"triggers":{"back_in_stock":0,"price_drop":10}}"""
            )
        )

        assertEquals(3, result?.daily?.view)
        assertEquals(1, result?.daily?.cart)
        assertEquals(5, result?.now?.view)
        assertEquals(10, result?.triggers?.priceDrop)
        assertEquals(0, result?.triggers?.backInStock)
    }

    @Test
    fun getProductCounters_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        var msg: String? = null
        impl.getProductCounters(
            item = "300275",
            onSuccess = {},
            onError = { c, m -> code = c; msg = m }
        )

        listenerSlot.captured.onError(404, "not found")

        assertEquals(404, code)
        assertTrue(msg!!.contains("not found"))
    }

    @Test
    fun getProductsList_sendsFiltersAsJsonUnderFiltersKey() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getProductsList(
            brands = null,
            merchants = null,
            categories = null,
            locations = null,
            limit = null,
            page = null,
            filters = mapOf("color" to listOf("red"))
        )

        val body = paramsSlot.captured
        assertFalse(body.has(""))
        assertEquals("""{"color":["red"]}""", body.getString("filters"))
    }

    @Test
    fun getProductsList_typed_parsesListingWithFacets() {
        val methodSlot = slot<String>()
        val paramsSlot = slot<JSONObject>()
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(capture(methodSlot), capture(paramsSlot), capture(listenerSlot))
        } just Runs

        var result: ProductsListResponse? = null
        impl.getProductsList(brands = "Beko", limit = 2, page = 1, onSuccess = { result = it })
        listenerSlot.captured.onSuccess(JSONObject(Fixtures.read("products.json")))

        assertEquals(ProductsManagerImpl.GET_PRODUCT_LIST_REQUEST, methodSlot.captured)
        assertEquals("Beko", paramsSlot.captured.getString("brands"))
        assertEquals(2, paramsSlot.captured.getInt("limit"))
        val listing = result!!
        assertTrue(listing.productsTotal > 0)
        assertEquals(2, listing.products.size)
        assertTrue(listing.brands.isNotEmpty())
        assertEquals(2, listing.filters!!.getValue("\"Сухой\" ТЭН").values.getValue("Да"))
        assertEquals(10.0, listing.priceRange!!.min, 0.0)
        val discounted = listing.products[1]
        assertEquals("174 990 ₽", discounted.oldPriceFormatted)
        assertEquals(5, discounted.discountPercent)
        assertNotNull(discounted.deeplinkAndroid)
    }

    @Test
    fun getProductInfo_typed_parsesProduct() {
        val methodSlot = slot<String>()
        val paramsSlot = slot<JSONObject>()
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(capture(methodSlot), capture(paramsSlot), capture(listenerSlot))
        } just Runs

        var result: Product? = null
        impl.getProductInfo(itemId = "868", onSuccess = { result = it })
        listenerSlot.captured.onSuccess(JSONObject(Fixtures.read("product_info.json")))

        assertEquals(ProductsManagerImpl.GET_PRODUCT_INFO_REQUEST, methodSlot.captured)
        assertEquals("868", paramsSlot.captured.getString("item_id"))
        assertEquals("868", result?.id)
        assertEquals("14 990 ₽", result?.priceFormatted)
        assertNull(result?.discount)
        assertEquals("https://www.technodom.kz/p/garnitura-nakladnaa-logitech-h-white-868", result?.deeplinkAndroid)
    }

    @Test
    fun getProductInfo_typed_reportsAnswerWithoutProductAsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var success = false
        var code = 0
        impl.getProductInfo(itemId = "868", onSuccess = { success = true }, onError = { c, _ -> code = c })
        listenerSlot.captured.onSuccess(JSONObject("{}"))

        assertFalse(success)
        assertEquals(ProductsManagerImpl.PARSE_ERROR_CODE, code)
    }

    @Test
    fun getProductInfo_typed_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        impl.getProductInfo(itemId = "868", onSuccess = {}, onError = { c, _ -> code = c })
        listenerSlot.captured.onError(400, "bad request")

        assertEquals(400, code)
    }

    @Test
    fun getProductsList_typed_reportsAnswerWithoutProductsAsError() {
        val result = typedListAnswer { it.onSuccess(JSONObject()) }

        assertEquals(null, result.first)
        assertEquals(ProductsManagerImpl.PARSE_ERROR_CODE, result.second)
    }

    @Test
    fun getProductsList_typed_reportsArrayAnswerAsError() {
        val result = typedListAnswer { it.onSuccess(JSONArray()) }

        assertEquals(null, result.first)
        assertEquals(ProductsManagerImpl.PARSE_ERROR_CODE, result.second)
    }

    @Test
    fun getProductsList_typed_reportsMalformedListingAsError() {
        val result = typedListAnswer { it.onSuccess(JSONObject("""{"products":"not a list"}""")) }

        assertEquals(null, result.first)
        assertEquals(ProductsManagerImpl.PARSE_ERROR_CODE, result.second)
    }

    @Test
    fun getProductInfo_typed_reportsMalformedProductAsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var success = false
        var code = 0
        impl.getProductInfo(itemId = "868", onSuccess = { success = true }, onError = { c, _ -> code = c })
        listenerSlot.captured.onSuccess(JSONObject("""{"id":"868","categories":"not a list"}"""))

        assertFalse(success)
        assertEquals(ProductsManagerImpl.PARSE_ERROR_CODE, code)
    }

    /** Runs a typed listing request, feeds [answer] to its listener; the parsed listing and error code. */
    private fun typedListAnswer(answer: (OnApiCallbackListener) -> Unit): Pair<ProductsListResponse?, Int> {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var listing: ProductsListResponse? = null
        var code = 0
        impl.getProductsList(onSuccess = { listing = it }, onError = { c, _ -> code = c })
        answer(listenerSlot.captured)
        return listing to code
    }
}
