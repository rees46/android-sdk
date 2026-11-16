package com.personalization.core.internal

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The answers of the legacy core (`coreV2 = false`) as 2.49.0 gave them to the old callbacks, which the
 * new core gives differently (commit 92e214e): a `2xx` that is not JSON, or JSON cut short, succeeds with
 * an empty object, and so does one whose connection closes midway through the body; a JSON array reaches
 * `onSuccess(JSONArray)`; no connection is `onError(504)`. Each request runs on [TrackingThreads], so a test waits
 * for its answer, not for the clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyCoreParityTest {

    private val server = MockWebServer()
    private val threads = TrackingThreads()
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }
    private lateinit var repository: NetworkRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        LegacyCore.threads = threads
        repository = NetworkRepositoryImpl(settings)
        repository.initialize(server.url("/").toString())
    }

    @After
    fun tearDown() {
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        server.shutdown()
    }

    /** Every callback of one request, once its answer was delivered. */
    private fun answer(send: (OnApiCallbackListener) -> Unit): List<String> {
        val calls = CopyOnWriteArrayList<String>()
        send(object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                calls += "success:$response"
            }

            override fun onSuccess(response: JSONArray) {
                calls += "array:$response"
            }

            override fun onError(code: Int, msg: String?) {
                calls += "error:$code"
            }
        })
        threads.awaitIdle()
        return calls.toList()
    }

    private fun get(listener: OnApiCallbackListener) = repository.get("products/get", JSONObject(), listener)

    @Test
    fun `a 2xx that is not JSON, or JSON cut short, succeeds with an empty object`() {
        server.enqueue(MockResponse().setBody("OK"))
        server.enqueue(MockResponse().setBody("""{"a":"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("\"quoted\""))

        assertEquals(listOf("success:{}"), answer(::get))
        assertEquals(listOf("success:{}"), answer(::get))
        assertEquals(listOf("success:{}"), answer(::get))
    }

    @Test
    fun `a JSON array reaches onSuccess with the array`() {
        server.enqueue(MockResponse().setBody("[1,2]"))

        assertEquals(listOf("array:[1,2]"), answer(::get))
    }

    @Test
    fun `no connection to the server is 504`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        repository.initialize("http://127.0.0.1:$closedPort/")

        assertEquals(listOf("error:504"), answer(::get))
    }

    /**
     * 2.49.0 read the body to its end however it ended: a connection the server closes midway ends the
     * stream early without an error, and the part that came does not parse. Only a read timeout fails the
     * request there. The new core gives `timeout` here.
     */
    @Test
    fun `a 2xx whose connection closes midway through the body succeeds empty, as in 2_49_0`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"items":"${"x".repeat(8_000)}"}""")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        assertEquals(listOf("success:{}"), answer(::get))
    }

    @Test
    fun `an answer outside 2xx keeps its code and hands its body to the old callback`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"bad phone"}"""))
        val messages = CopyOnWriteArrayList<String?>()

        repository.post("profile/set", JSONObject(), object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                messages += "$code:$msg"
            }
        })
        threads.awaitIdle()

        assertEquals(listOf("""422:{"message":"bad phone"}"""), messages)
    }
}
