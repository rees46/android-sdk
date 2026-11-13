package com.personalization.core.internal

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The new core behind the old callbacks: one answer per request, the old codes, and release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreV2Test {

    private val server = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
    }
    private lateinit var core: CoreV2

    @Before
    fun setUp() {
        server.start()
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, "shop")
        core = CoreV2(client, "shop")
    }

    @After
    fun tearDown() {
        core.release()
        server.close()
    }

    @Test
    fun `answers come back as the old callbacks get them`() {
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":1}""").build())
        server.enqueue(MockResponse.Builder().code(200).body("[1]").build())
        server.enqueue(MockResponse.Builder().code(404).body("""{"message":"no such product"}""").build())
        server.enqueue(MockResponse.Builder().code(200).body("<html/>").build())

        assertEquals(listOf("""success:{"id":1}"""), call())
        assertEquals(listOf("success:[1]"), call())
        assertEquals(listOf("""error:404:{"message":"no such product"}"""), call())
        assertEquals(listOf("error:-1"), call().map { it.substringBefore(":The") })
    }

    @Test
    fun `without a network the old callback gets -1 at once`() {
        connectivity.set(false)

        assertEquals(listOf("error:-1"), call().map { it.substringBefore(":No") })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a failing onSuccess ends in one onError and nothing escapes`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        val recorder = Recorder(throwOnSuccess = true)
        core.send(NetworkMethod.GET("products/get"), JSONObject(), recorder)

        assertEquals(listOf("success:{}", "error:-1"), recorder.await(expected = 2).map { it.substringBefore(":host") })
    }

    @Test
    fun `release cancels the requests in flight and fails the ones after it`() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
        val inFlight = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject(), inFlight)
        server.takeRequest(5, TimeUnit.SECONDS)

        val started = System.nanoTime()
        core.release()

        assertEquals(listOf("error:-1"), inFlight.await().map { it.substringBefore(":The") })
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)

        val after = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject(), after)
        assertEquals(listOf("error:-1"), after.await().map { it.substringBefore(":The") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a request without a listener still goes`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        core.send(NetworkMethod.POST("push"), JSONObject().put("event", "view"), null)

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotEquals(null, request)
    }

    private fun call(): List<String> {
        val recorder = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject(), recorder)
        return recorder.await()
    }

    private class Recorder(private val throwOnSuccess: Boolean = false) : OnApiCallbackListener() {
        private val calls = CopyOnWriteArrayList<String>()
        private val latch = CountDownLatch(2)

        override fun onSuccess(response: JSONObject?) = record("success:$response", throwOnSuccess)

        override fun onSuccess(response: JSONArray) = record("success:$response", throwOnSuccess)

        override fun onError(code: Int, msg: String?) = record("error:$code" + (msg?.let { ":$it" } ?: ""), false)

        private fun record(call: String, fail: Boolean) {
            calls += call
            latch.countDown()
            if (fail) throw IllegalStateException("host callback failed")
        }

        /** Waits for [expected] calls, then a little longer for any extra one. */
        fun await(expected: Int = 1): List<String> {
            val deadline = System.currentTimeMillis() + 5_000
            while (calls.size < expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
            Thread.sleep(200)
            return calls.toList()
        }
    }
}
