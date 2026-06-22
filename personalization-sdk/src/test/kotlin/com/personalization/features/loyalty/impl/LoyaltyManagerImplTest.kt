package com.personalization.features.loyalty.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.loyalty.LoyaltyJoinResponse
import com.personalization.api.responses.loyalty.LoyaltyStatusResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoyaltyManagerImplTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var impl: LoyaltyManagerImpl

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        impl = LoyaltyManagerImpl(sendNetworkMethodUseCase)
    }

    @Test
    fun join_postsToJoinEndpoint_withAllProvidedFields() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.postAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.join(
            phone = "79991234567",
            email = "en@rees46.ru",
            firstName = "Ivan",
            lastName = "Petrov",
            onSuccess = {},
        )

        verify(exactly = 1) {
            sendNetworkMethodUseCase.postAsync(
                LoyaltyManagerImpl.JOIN_REQUEST,
                any(),
                any()
            )
        }
        val body = paramsSlot.captured
        assertEquals("79991234567", body.getString("phone"))
        assertEquals("en@rees46.ru", body.getString("email"))
        assertEquals("Ivan", body.getString("first_name"))
        assertEquals("Petrov", body.getString("last_name"))
    }

    @Test
    fun join_omitsOptionalFields_whenNull() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.postAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.join(phone = "79991234567", onSuccess = {})

        val body = paramsSlot.captured
        assertEquals("79991234567", body.getString("phone"))
        assertFalse(body.has("email"))
        assertFalse(body.has("first_name"))
        assertFalse(body.has("last_name"))
        // shop_id is appended later by the network layer, not here.
        assertFalse(body.has("shop_id"))
    }

    @Test
    fun join_parsesEnvelopeResponse_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.postAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: LoyaltyJoinResponse? = null
        impl.join(phone = "79991234567", onSuccess = { result = it })

        listenerSlot.captured.onSuccess(JSONObject("""{"status":"success","payload":{"member_id":42}}"""))

        assertEquals("success", result?.status)
        assertEquals(42, result?.payload?.get("member_id")?.asInt)
    }

    @Test
    fun getStatus_getsFromStatusEndpoint_withIdentifier() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getStatus(identifier = "79991234567", onSuccess = {})

        verify(exactly = 1) {
            sendNetworkMethodUseCase.getAsync(
                LoyaltyManagerImpl.STATUS_REQUEST,
                any(),
                any()
            )
        }
        assertEquals("79991234567", paramsSlot.captured.getString("identifier"))
    }

    @Test
    fun getStatus_parsesPayloadWithLevel_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: LoyaltyStatusResponse? = null
        impl.getStatus(identifier = "79991234567", onSuccess = { result = it })

        listenerSlot.captured.onSuccess(
            JSONObject(
                """{"status":"success","payload":{"member":true,"level":{"name":"Gold","code":"gold","expiration_date":null}}}"""
            )
        )

        assertEquals("success", result?.status)
        assertEquals(true, result?.payload?.member)
        assertEquals("Gold", result?.payload?.level?.name)
        assertEquals("gold", result?.payload?.level?.code)
        assertNull(result?.payload?.level?.expirationDate)
    }

    @Test
    fun getStatus_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        var msg: String? = null
        impl.getStatus(
            identifier = "79991234567",
            onSuccess = {},
            onError = { c, m -> code = c; msg = m }
        )

        listenerSlot.captured.onError(404, "not found")

        assertEquals(404, code)
        assertTrue(msg!!.contains("not found"))
    }
}
