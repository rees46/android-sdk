package com.personalization

import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManageSubscriptionTest {

    private lateinit var sdk: SDK
    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase

    @Before
    fun setUp() {
        sendNetworkMethodUseCase = mockk(relaxed = true)
        sdk = SDK()
        sdk.sendNetworkMethodUseCase = sendNetworkMethodUseCase
    }

    @Test
    fun telegramWhatsappMaxAndWalletFlags_goUnderTheirKeys() {
        val flags = hashMapOf(
            "telegram_bulk" to true,
            "telegram_chain" to false,
            "telegram_transactional" to true,
            "whatsapp_bulk" to false,
            "whatsapp_chain" to true,
            "whatsapp_transactional" to false,
            "max_bulk" to true,
            "max_chain" to false,
            "max_transactional" to true,
            "wallet_bulk" to false
        )

        sdk.manageSubscription(email = null, phone = "+79990000000", subscriptions = flags)

        val body = capturedBody()
        flags.forEach { (key, value) -> assertEquals(key, value, body.getBoolean(key)) }
        assertEquals("+79990000000", body.getString("phone"))
    }

    @Test
    fun flagsLeftOut_areNotSent() {
        sdk.manageSubscription(email = "user@example.com", phone = null, subscriptions = hashMapOf("email_bulk" to true))

        val body = capturedBody()
        assertEquals(true, body.getBoolean("email_bulk"))
        listOf("telegram_bulk", "whatsapp_bulk", "max_bulk", "wallet_bulk").forEach {
            assertFalse(it, body.has(it))
        }
    }

    private fun capturedBody(): JSONObject {
        val path = slot<String>()
        val body = slot<JSONObject>()
        verify { sendNetworkMethodUseCase.postAsync(capture(path), capture(body), any()) }
        assertEquals("subscriptions/manage", path.captured)
        return body.captured
    }
}
