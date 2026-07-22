package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.push.PushTokenManager
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushTokenManagerTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var getPreferencesValueUseCase: GetPreferencesValueUseCase
    private lateinit var savePreferencesValueUseCase: SavePreferencesValueUseCase
    private lateinit var pushTokenManager: PushTokenManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sendNetworkMethodUseCase = mockk(relaxed = true)
        getPreferencesValueUseCase = mockk(relaxed = true)
        savePreferencesValueUseCase = mockk(relaxed = true)

        // No stored token by default — incoming tokens are treated as new.
        every { getPreferencesValueUseCase.getPushToken(any()) } returns ""
        every { getPreferencesValueUseCase.getLastPushTokenDate(any()) } returns 0L

        pushTokenManager = PushTokenManager(
            sendNetworkMethodUseCase,
            getPreferencesValueUseCase,
            savePreferencesValueUseCase
        )
    }

    // sendToken is the manual API — bypasses dedup, no initialize() required.
    @Test
    fun `sendToken sends push_provider firebase for FCM`() {
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        pushTokenManager.sendToken("fcm-token-123", PushProvider.FCM, null)

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
        val body = paramsSlot.captured
        assertEquals("fcm-token-123", body.getString("token"))
        assertEquals("android", body.getString("platform"))
        assertEquals("firebase", body.getString("push_provider"))
    }

    @Test
    fun `sendToken sends push_provider huawei for HMS`() {
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        pushTokenManager.sendToken("hms-token-1", PushProvider.HMS, null)

        val body = paramsSlot.captured
        assertEquals("hms-token-1", body.getString("token"))
        assertEquals("huawei", body.getString("push_provider"))
    }

    @Test
    fun `sendToken forwards backend success to caller listener and persists token`() {
        val listener = mockk<OnApiCallbackListener>(relaxed = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            thirdArg<OnApiCallbackListener>().onSuccess(null as JSONObject?)
        }

        pushTokenManager.sendToken("fcm-token", PushProvider.FCM, listener)

        verify(exactly = 1) { listener.onSuccess(any<JSONObject>()) }
        verify(exactly = 1) { savePreferencesValueUseCase.savePushToken(PushProvider.FCM, "fcm-token") }
    }

    // onTokenReceived goes through dedup, so autoSendPushToken = true (set via initialize()) is required.
    @Test
    fun `onTokenReceived sends push_provider huawei for new HMS token`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        pushTokenManager.onTokenReceived("hms-token-456", PushProvider.HMS)

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
        val body = paramsSlot.captured
        assertEquals("hms-token-456", body.getString("token"))
        assertEquals("huawei", body.getString("push_provider"))
    }

    @Test
    fun `onTokenReceived skips send when same token already stored`() {
        every { getPreferencesValueUseCase.getPushToken(PushProvider.HMS) } returns "hms-token-same"
        every { getPreferencesValueUseCase.getLastPushTokenDate(PushProvider.HMS) } returns System.currentTimeMillis()
        pushTokenManager.initialize(context, autoSendPushToken = true)

        pushTokenManager.onTokenReceived("hms-token-same", PushProvider.HMS)

        verify(exactly = 0) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `onTokenReceived resends token after 7 days`() {
        val eightDaysAgo = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L
        every { getPreferencesValueUseCase.getPushToken(PushProvider.HMS) } returns "hms-token-old"
        every { getPreferencesValueUseCase.getLastPushTokenDate(PushProvider.HMS) } returns eightDaysAgo
        pushTokenManager.initialize(context, autoSendPushToken = true)
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        pushTokenManager.onTokenReceived("hms-token-old", PushProvider.HMS)

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
        assertEquals("hms-token-old", paramsSlot.captured.getString("token"))
    }

    @Test
    fun `onTokenReceived does not send when autoSendPushToken is false`() {
        pushTokenManager.initialize(context, autoSendPushToken = false)

        pushTokenManager.onTokenReceived("hms-token", PushProvider.HMS)

        verify(exactly = 0) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `onTokenReceived notifies the host listener`() {
        var notified: Pair<String, PushProvider>? = null
        pushTokenManager.setOnPushTokenListener { token, provider -> notified = token to provider }
        pushTokenManager.initialize(context, autoSendPushToken = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } just Runs

        pushTokenManager.onTokenReceived("hms-token-789", PushProvider.HMS)

        assertEquals("hms-token-789" to PushProvider.HMS, notified)
    }

    // On a fresh install the same token reaches onTokenReceived twice: from the proactive fetch in
    // initialize() and from the messaging service's onNewToken. The stored token is only written in
    // the response callback, so without an in-flight claim both deliveries see an empty cache.
    @Test
    fun `onTokenReceived sends once when the same token arrives twice before the response`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        // post() never invokes its callback: the first request is still on the wire.
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } just Runs

        pushTokenManager.onTokenReceived("fcm-token-race", PushProvider.FCM)
        pushTokenManager.onTokenReceived("fcm-token-race", PushProvider.FCM)

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `onTokenReceived still notifies the host for a delivery it deduplicates`() {
        val notified = mutableListOf<String>()
        pushTokenManager.setOnPushTokenListener { token, _ -> notified.add(token) }
        pushTokenManager.initialize(context, autoSendPushToken = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } just Runs

        pushTokenManager.onTokenReceived("fcm-token-race", PushProvider.FCM)
        pushTokenManager.onTokenReceived("fcm-token-race", PushProvider.FCM)

        assertEquals(listOf("fcm-token-race", "fcm-token-race"), notified)
    }

    @Test
    fun `the claim is per provider so the same token value is sent for FCM and HMS`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } just Runs

        pushTokenManager.onTokenReceived("shared-token", PushProvider.FCM)
        pushTokenManager.onTokenReceived("shared-token", PushProvider.HMS)

        verify(exactly = 2) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `a failed send releases the claim so the next delivery retries`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            thirdArg<OnApiCallbackListener>().onError(500, "server error")
        }

        pushTokenManager.onTokenReceived("fcm-token-retry", PushProvider.FCM)
        pushTokenManager.onTokenReceived("fcm-token-retry", PushProvider.FCM)

        verify(exactly = 2) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `a new token is sent after the previous one completed`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            thirdArg<OnApiCallbackListener>().onSuccess(null as JSONObject?)
        }

        pushTokenManager.onTokenReceived("fcm-token-first", PushProvider.FCM)
        pushTokenManager.onTokenReceived("fcm-token-second", PushProvider.FCM)

        verify(exactly = 2) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `fcm and hms tokens carry different push_provider values`() {
        pushTokenManager.initialize(context, autoSendPushToken = true)
        val capturedParams = mutableListOf<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            capturedParams.add(secondArg())
        }

        pushTokenManager.onTokenReceived("fcm-token", PushProvider.FCM)
        pushTokenManager.onTokenReceived("hms-token", PushProvider.HMS)

        assertEquals(2, capturedParams.size)
        assertEquals("firebase", capturedParams[0].getString("push_provider"))
        assertEquals("huawei", capturedParams[1].getString("push_provider"))
    }
}
