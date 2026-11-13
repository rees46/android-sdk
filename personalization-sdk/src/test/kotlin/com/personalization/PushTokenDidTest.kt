package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A push token never reaches the server without a `did`: it waits with the requests that wait for one
 * and goes once the `did` is stored, whichever way it came (the app's `setPushToken`, a provider's
 * token).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushTokenDidTest {

    private val server = MockWebServer()
    private val did = AtomicReference("")
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } answers { did.get() }
        every { getSid() } returns "sid"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }
    private val repository = NetworkRepositoryImpl(settings)
    private val preferences = mockk<GetPreferencesValueUseCase>(relaxed = true) {
        every { getPushToken(any()) } returns ""
        every { getLastPushTokenDate(any()) } returns 0L
    }
    private val manager = PushTokenManager(SendNetworkMethodUseCase(repository), preferences, mockk<SavePreferencesValueUseCase>(relaxed = true))

    @Before
    fun setUp() {
        server.start()
        repository.initialize(server.url("/").toString())
        server.enqueue(MockResponse().setBody("{}"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `the app's token waits for the did and goes with it`() {
        manager.sendToken("app-token", PushProvider.FCM, null)

        // Held with the requests that wait for a did: never handed to a thread.
        assertEquals(1, repository.waitingForDid)
        assertEquals(0, server.requestCount)

        did.set("new-did")
        repository.executeQueueTasks()

        val body = JSONObject(checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals("app-token", body.getString("token"))
        assertEquals("new-did", body.getString("did"))
    }

    @Test
    fun `a provider's token waits for the did as well`() {
        manager.initialize(ApplicationProvider.getApplicationContext<Context>(), autoSendPushToken = true)

        manager.onTokenReceived("provider-token", PushProvider.HMS)

        assertEquals(1, repository.waitingForDid)
        assertEquals(0, server.requestCount)
        did.set("new-did")
        repository.executeQueueTasks()
        val body = JSONObject(checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals("provider-token", body.getString("token"))
        assertEquals("new-did", body.getString("did"))
    }

    @Test
    fun `with a did the token goes at once`() {
        did.set("known-did")

        manager.sendToken("app-token", PushProvider.FCM, null)

        val body = JSONObject(checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals("known-did", body.getString("did"))
    }
}
