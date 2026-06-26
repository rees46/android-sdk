package com.personalization.features.profile.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.profile.GetProfileResponse
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
class ProfileManagerImplTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var impl: ProfileManagerImpl

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        impl = ProfileManagerImpl(sendNetworkMethodUseCase)
    }

    @Test
    fun getProfile_getsFromProfileEndpoint_withoutExplicitParams() {
        val paramsSlot = slot<JSONObject>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), capture(paramsSlot), any())
        } just Runs

        impl.getProfile(onSuccess = {})

        verify(exactly = 1) {
            sendNetworkMethodUseCase.getAsync(
                ProfileManagerImpl.GET_PROFILE_REQUEST,
                any(),
                any()
            )
        }
        // shop_id and did are appended later by the network layer, not here.
        assertEquals(0, paramsSlot.captured.length())
    }

    @Test
    fun getProfile_parsesResponse_andInvokesOnSuccess() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var result: GetProfileResponse? = null
        impl.getProfile(onSuccess = { result = it })

        listenerSlot.captured.onSuccess(
            JSONObject(
                """{"id":"8001909","has_email":false,"computed_gender":null,"gender":null,"bought_something":false,"custom_properties":{"vip":"1"}}"""
            )
        )

        assertEquals("8001909", result?.id)
        assertEquals(false, result?.hasEmail)
        assertEquals(false, result?.boughtSomething)
        assertEquals("1", result?.customProperties?.get("vip")?.asString)
    }

    @Test
    fun getProfile_forwardsError() {
        val listenerSlot = slot<OnApiCallbackListener>()
        every {
            sendNetworkMethodUseCase.getAsync(any(), any(), capture(listenerSlot))
        } just Runs

        var code = 0
        var msg: String? = null
        impl.getProfile(onSuccess = {}, onError = { c, m -> code = c; msg = m })

        listenerSlot.captured.onError(400, "bad request")

        assertEquals(400, code)
        assertTrue(msg!!.contains("bad request"))
    }
}
