package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
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
class RegisterManagerPushProviderTest {

    private lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase
    private lateinit var getPreferencesValueUseCase: GetPreferencesValueUseCase
    private lateinit var savePreferencesValueUseCase: SavePreferencesValueUseCase
    private lateinit var updateUserSettingsValueUseCase: UpdateUserSettingsValueUseCase
    private lateinit var getUserSettingsValueUseCase: GetUserSettingsValueUseCase
    private lateinit var executeQueueTasksUseCase: ExecuteQueueTasksUseCase
    private lateinit var inAppNotificationManager: InAppNotificationManager
    private lateinit var registerManager: RegisterManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sendNetworkMethodUseCase = mockk(relaxed = true)
        getPreferencesValueUseCase = mockk(relaxed = true)
        savePreferencesValueUseCase = mockk(relaxed = true)
        updateUserSettingsValueUseCase = mockk(relaxed = true)
        getUserSettingsValueUseCase = mockk(relaxed = true)
        executeQueueTasksUseCase = mockk(relaxed = true)
        inAppNotificationManager = mockk(relaxed = true)

        // Return non-empty DID so initialize() skips new-device flow
        every { getUserSettingsValueUseCase.getDid() } returns "test-did"
        every { getUserSettingsValueUseCase.getSid() } returns "test-sid"
        every { getUserSettingsValueUseCase.getSidLastActTime() } returns System.currentTimeMillis()
        // No stored HMS token — token will be treated as new
        every { getPreferencesValueUseCase.getHmsToken() } returns ""
        every { getPreferencesValueUseCase.getLastHmsPushTokenDate() } returns 0L

        registerManager = RegisterManager(
            getPreferencesValueUseCase,
            savePreferencesValueUseCase,
            updateUserSettingsValueUseCase,
            getUserSettingsValueUseCase,
            sendNetworkMethodUseCase,
            executeQueueTasksUseCase,
            inAppNotificationManager
        )
    }

    // setPushTokenNotification is a direct public API — bypasses dedup logic,
    // no need for autoSendPushToken or initialize()
    @Test
    fun `setPushTokenNotification sends push_provider fcm`() {
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        registerManager.setPushTokenNotification("fcm-token-123", null)

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), null) }
        val body = paramsSlot.captured
        assertEquals("fcm-token-123", body.getString("token"))
        assertEquals("android", body.getString("platform"))
        assertEquals("fcm", body.getString("push_provider"))
    }

    @Test
    fun `setPushTokenNotification passes listener to network call`() {
        val listener = mockk<OnApiCallbackListener>(relaxed = true)

        registerManager.setPushTokenNotification("fcm-token", listener)

        verify(exactly = 1) { sendNetworkMethodUseCase.post(any(), any(), eq(listener)) }
    }

    // onHmsNewToken goes through processHmsToken → shouldSendToken,
    // so autoSendPushToken = true is required (set via initialize())
    @Test
    fun `onHmsNewToken sends push_provider hms for new token`() {
        registerManager.initialize(context, context.contentResolver, autoSendPushToken = true)
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        registerManager.onHmsNewToken("hms-token-456")

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
        val body = paramsSlot.captured
        assertEquals("hms-token-456", body.getString("token"))
        assertEquals("android", body.getString("platform"))
        assertEquals("hms", body.getString("push_provider"))
    }

    @Test
    fun `onHmsNewToken skips send when same token already stored`() {
        every { getPreferencesValueUseCase.getHmsToken() } returns "hms-token-same"
        every { getPreferencesValueUseCase.getLastHmsPushTokenDate() } returns System.currentTimeMillis()
        registerManager.initialize(context, context.contentResolver, autoSendPushToken = true)

        registerManager.onHmsNewToken("hms-token-same")

        verify(exactly = 0) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
    }

    @Test
    fun `onHmsNewToken resends token after 7 days`() {
        val sevenDaysAgo = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L
        every { getPreferencesValueUseCase.getHmsToken() } returns "hms-token-old"
        every { getPreferencesValueUseCase.getLastHmsPushTokenDate() } returns sevenDaysAgo
        registerManager.initialize(context, context.contentResolver, autoSendPushToken = true)
        val paramsSlot = slot<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), capture(paramsSlot), any()) } just Runs

        registerManager.onHmsNewToken("hms-token-old")

        verify(exactly = 1) { sendNetworkMethodUseCase.post("mobile_push_tokens", any(), any()) }
        assertEquals("hms-token-old", paramsSlot.captured.getString("token"))
    }

    @Test
    fun `fcm and hms tokens carry different push_provider values`() {
        registerManager.initialize(context, context.contentResolver, autoSendPushToken = true)
        val capturedParams = mutableListOf<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            capturedParams.add(secondArg())
        }

        registerManager.setPushTokenNotification("fcm-token", null)
        registerManager.onHmsNewToken("hms-token")

        assertEquals(2, capturedParams.size)
        assertEquals("fcm", capturedParams[0].getString("push_provider"))
        assertEquals("hms", capturedParams[1].getString("push_provider"))
    }

    @Test
    fun `all token requests use platform android`() {
        registerManager.initialize(context, context.contentResolver, autoSendPushToken = true)
        val capturedParams = mutableListOf<JSONObject>()
        every { sendNetworkMethodUseCase.post(any(), any(), any()) } answers {
            capturedParams.add(secondArg())
        }

        registerManager.setPushTokenNotification("fcm-token", null)
        registerManager.onHmsNewToken("hms-token")

        capturedParams.forEach { params ->
            assertEquals("android", params.getString("platform"))
        }
    }
}
