package com.personalization.core.internal

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkRegistry
import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The request funnel asks the instance's core: the legacy one by default, the new client once the
 * instance runs on [CoreV2]. The did-gated queue stays the legacy one for both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreFunnelTest {

    private val server = MockWebServer()
    private val did = AtomicReference("did-1")
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } answers { did.get() }
        every { getSid() } returns "sid-1"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }
    private val repository = NetworkRepositoryImpl(settings)

    @Before
    fun setUp() {
        server.start()
        repository.initialize(server.url("/").toString())
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
    }

    @After
    fun tearDown() {
        repository.core.release()
        server.close()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
    }

    @Test
    fun `the funnel sends through the legacy core until the instance takes the new one`() {
        assertFalse(repository.core.isV2)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        await { repository.get("products/get", JSONObject(), it) }
        // HttpURLConnection on the JVM asks for no compression; OkHttp asks for gzip.
        assertEquals(null, server.takeRequest().headers["Accept-Encoding"])

        repository.useCoreV2("shop", FakeConnectivity(connected = true))
        assertTrue(repository.core.isV2)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        await { repository.get("products/get", JSONObject(), it) }
        assertEquals("gzip", server.takeRequest().headers["Accept-Encoding"])
    }

    @Test
    fun `on the new core a request without a did waits for the instance, not for the did queue`() {
        repository.useCoreV2("shop", FakeConnectivity(connected = true))
        did.set("")
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val answered = CountDownLatch(1)

        // Without an init to wait on the request goes at once: the did queue no longer holds it.
        repository.getAsync("products/get", JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) = answered.countDown()
        })

        assertTrue(answered.await(5, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an instance made with coreV2 runs on the new core, its requests on the new client`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"did":"server-did","seance":"S"}"""))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Robolectric's default network has no internet capability: give it one, as a device's has.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)

        val sdk = Rees46.initialize(
            context,
            Rees46Config(
                shopId = "core-shop",
                apiDomain = server.url("/").toString().trimEnd('/'),
                autoSendPushToken = false,
                coreV2 = true,
            )
        )

        assertTrue(sdk.core is CoreV2)
        val init = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(init)
        assertEquals("/init", init?.requestUrl?.encodedPath)
        assertEquals("core-shop", init?.requestUrl?.queryParameter("shop_id"))
        assertEquals("gzip", init?.headers?.get("Accept-Encoding"))
        sdk.release()
    }

    @Test
    fun `an instance made without the flag keeps the legacy core`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"did":"server-did","seance":"S"}"""))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val sdk: SDK = Rees46.initialize(
            context,
            Rees46Config(shopId = "legacy-shop", apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false)
        )

        assertFalse(sdk.core?.isV2 ?: true)
        val init = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/init", init?.requestUrl?.encodedPath)
        sdk.release()
    }

    private fun await(send: (OnApiCallbackListener) -> Unit) {
        val answered = CountDownLatch(1)
        send(object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) = answered.countDown()
            override fun onError(code: Int, msg: String?) = answered.countDown()
        })
        assertTrue(answered.await(5, TimeUnit.SECONDS))
    }
}
