package com.personalization.features.products.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.products.counters.ProductCountersResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
