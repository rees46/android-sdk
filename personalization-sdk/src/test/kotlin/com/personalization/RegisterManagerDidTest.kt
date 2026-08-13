package com.personalization

import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.push.PushTokenManager
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the device-id policy in [RegisterManager]. The SDK must NOT seed the did with a hardware id
 * (Settings.Secure.ANDROID_ID): like the iOS, React Native and web SDKs, the first `/init` goes out
 * with an empty did and the server-assigned did is persisted. An existing install (non-empty did) is
 * left untouched — no re-init, so nobody is silently re-identified on upgrade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegisterManagerDidTest {

    private lateinit var update: UpdateUserSettingsValueUseCase
    private lateinit var get: GetUserSettingsValueUseCase
    private lateinit var network: SendNetworkMethodUseCase
    private lateinit var queue: ExecuteQueueTasksUseCase
    private lateinit var inApp: InAppNotificationManager
    private lateinit var pushTokens: PushTokenManager
    private lateinit var manager: RegisterManager

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        update = mockk(relaxed = true)
        get = mockk(relaxed = true)
        network = mockk(relaxed = true)
        queue = mockk(relaxed = true)
        inApp = mockk(relaxed = true)
        pushTokens = mockk(relaxed = true)
        manager = RegisterManager(update, get, network, queue, inApp, pushTokens)
        context = ApplicationProvider.getApplicationContext()
        contentResolver = context.contentResolver
    }

    @Test
    fun `first init never seeds a hardware did and persists the server-assigned one`() {
        every { get.getDid() } returns "" // fresh install
        val listener = slot<OnApiCallbackListener>()
        every { network.get(eq("init"), any(), capture(listener)) } just Runs

        manager.initialize(context, contentResolver, autoSendPushToken = false)

        // Before the server responds, the did must not be written at all — no ANDROID_ID seeding.
        verify(exactly = 0) { update.updateDid(any()) }

        // The server assigns the did; a successful /init persists it verbatim.
        listener.captured.onSuccess(JSONObject("""{"did":"SERVER_DID","seance":"SRV_SEANCE"}"""))
        verify(exactly = 1) { update.updateDid("SERVER_DID") }
    }

    @Test
    fun `an existing install with a did is not re-initialized`() {
        every { get.getDid() } returns "EXISTING_DID"

        manager.initialize(context, contentResolver, autoSendPushToken = false)

        // No /init round-trip and no did write — the persisted did is reused as-is.
        verify(exactly = 0) { network.get(eq("init"), any(), any()) }
        verify(exactly = 0) { update.updateDid(any()) }
    }

    @Test
    fun `needReInitialization forces a fresh init even when a did exists`() {
        every { get.getDid() } returns "EXISTING_DID"
        val listener = slot<OnApiCallbackListener>()
        every { network.get(eq("init"), any(), capture(listener)) } just Runs

        manager.initialize(
            context,
            contentResolver,
            autoSendPushToken = false,
            needReInitialization = true
        )

        // Still no hardware seeding; the refreshed did comes from the server response.
        verify(exactly = 0) { update.updateDid(any()) }
        listener.captured.onSuccess(JSONObject("""{"did":"REFRESHED_DID","seance":"S"}"""))
        verify(exactly = 1) { update.updateDid("REFRESHED_DID") }
    }
}
