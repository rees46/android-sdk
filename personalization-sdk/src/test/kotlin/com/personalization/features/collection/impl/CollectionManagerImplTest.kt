package com.personalization.features.collection.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.collection.CollectionResponse
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
class CollectionManagerImplTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var impl: CollectionManagerImpl

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        impl = CollectionManagerImpl(sendNetworkMethodUseCase)
    }

    @Test
    fun getCollection_getsFromCollectionEndpoint_withIdAndOptionalParams() {
        val methodSlot = slot<String>()
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(capture(methodSlot), capture(paramsSlot), any())
        } just Runs

        impl.getCollection(
            collectionId = "1",
            email = "demo@rees46.ru",
            loyaltyId = "loyal-7",
            onSuccess = {}
        )

        verify(exactly = 1) { sendNetworkMethodUseCase.getAsync(any(), any(), any()) }
        assertEquals("collection/1", methodSlot.captured)
        val body = paramsSlot.captured
        assertEquals("demo@rees46.ru", body.getString("email"))
        assertEquals("loyal-7", body.getString("loyalty_id"))
    }

    @Test
    fun getCollection_omitsOptionalParams_whenNull() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getCollection(collectionId = "1", onSuccess = {})

        assertEquals(0, paramsSlot.captured.length())
    }

    @Test
    fun getCollection_parsesProducts_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: CollectionResponse? = null
        impl.getCollection(collectionId = "1", onSuccess = { result = it })

        listenerSlot.captured.onSuccess(
            JSONObject(
                """{"products":[{"id":"868","_id":"131887","name":"Logitech H150","brand":"Logitech","price":14990}]}"""
            )
        )

        assertEquals(1, result?.products?.size)
        assertEquals("868", result?.products?.first()?.id)
        assertEquals("Logitech", result?.products?.first()?.brand)
    }

    @Test
    fun getCollection_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        var msg: String? = null
        impl.getCollection(
            collectionId = "999",
            onSuccess = {},
            onError = { c, m -> code = c; msg = m }
        )

        listenerSlot.captured.onError(404, "not found")

        assertEquals(404, code)
        assertTrue(msg!!.contains("not found"))
    }
}
