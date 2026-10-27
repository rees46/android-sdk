package com.personalization.features.category.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.category.CategoryResponse
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
class CategoryManagerImplTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var impl: CategoryManagerImpl

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        impl = CategoryManagerImpl(sendNetworkMethodUseCase)
    }

    @Test
    fun getCategory_getsFromCategoryEndpoint_withSlugAndParams() {
        val methodSlot = slot<String>()
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(capture(methodSlot), capture(paramsSlot), any())
        } just Runs

        impl.getCategory(
            category = "smartfony-i-gadzhety",
            limit = 10,
            page = 2,
            brands = "Apple",
            onSuccess = {}
        )

        verify(exactly = 1) { sendNetworkMethodUseCase.getAsync(any(), any(), any()) }
        assertEquals("category/smartfony-i-gadzhety", methodSlot.captured)
        val body = paramsSlot.captured
        assertEquals(10, body.getInt("limit"))
        assertEquals(2, body.getInt("page"))
        assertEquals("Apple", body.getString("brands"))
    }

    @Test
    fun getCategory_sendsPriceRangeSortingAndFacets() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getCategory(
            category = "tehnika-dlja-kuhni",
            page = 3,
            brands = "Ardesto,AENO",
            filters = mapOf("Цвет" to listOf("Белый", "Чёрный")),
            priceMin = 1000.0,
            priceMax = 2000.5,
            sortBy = "price",
            sortDir = "asc",
            onSuccess = {}
        )

        val body = paramsSlot.captured
        assertEquals(3, body.getInt("page"))
        assertEquals("Ardesto,AENO", body.getString("brands"))
        assertEquals("1000", body.getString("price_min"))
        assertEquals("2000.5", body.getString("price_max"))
        assertEquals("price", body.getString("sort_by"))
        assertEquals("asc", body.getString("sort_dir"))
        val facets = JSONObject(body.getString("filters")).getJSONArray("Цвет")
        assertEquals(listOf("Белый", "Чёрный"), List(facets.length()) { facets.getString(it) })
    }

    @Test
    fun getCategory_withoutPriceAndSorting_sendsTheSameAsBefore() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getCategory(category = "tehnika-dlja-kuhni", limit = 20, sortBy = null, onSuccess = {})

        val body = paramsSlot.captured
        assertEquals(1, body.length())
        assertEquals(20, body.getInt("limit"))
    }

    @Test
    fun getCategory_omitsOptionalParams_whenNull() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getCategory(category = "smartfony-i-gadzhety", onSuccess = {})

        val body = paramsSlot.captured
        assertEquals(0, body.length())
    }

    @Test
    fun getCategory_parsesProductsAndTotal_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: CategoryResponse? = null
        impl.getCategory(category = "smartfony-i-gadzhety", onSuccess = { result = it })

        listenerSlot.captured.onSuccess(
            JSONObject(
                """{"products_total":2208,"products":[{"id":"300275","name":"Phone"}],"brands":[{"name":"Apple","count":12}]}"""
            )
        )

        assertEquals(2208, result?.productsTotal)
        assertEquals(1, result?.products?.size)
        assertEquals("300275", result?.products?.first()?.id)
        assertEquals("Apple", result?.brands?.first()?.name)
    }

    @Test
    fun getCategory_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        var msg: String? = null
        impl.getCategory(
            category = "unknown",
            onSuccess = {},
            onError = { c, m -> code = c; msg = m }
        )

        listenerSlot.captured.onError(404, "not found")

        assertEquals(404, code)
        assertTrue(msg!!.contains("not found"))
    }
}
