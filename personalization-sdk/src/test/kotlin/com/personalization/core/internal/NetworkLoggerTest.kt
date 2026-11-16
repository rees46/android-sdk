package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.NetworkLogger
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The app's [SDK.networkLogger] (public since 2.4x) on both cores: one call per request with its method,
 * full URL, request and answer bodies, code and duration, the error only for a request without an answer;
 * and a logger that throws is logged and changes nothing for the request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkLoggerTest {

    private class Call(
        val method: String,
        val url: String,
        val requestBody: String?,
        val responseCode: Int,
        val responseBody: String?,
        val durationMs: Long,
        val error: String?,
    )

    private val server = MockWebServer()
    private val threads = TrackingThreads()
    private val calls = CopyOnWriteArrayList<Call>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }
    private val recorder = NetworkLogger { method, url, requestBody, responseCode, responseBody, durationMs, error ->
        calls += Call(method, url, requestBody, responseCode, responseBody, durationMs, error)
    }

    @Before
    fun setUp() {
        server.start()
        LegacyCore.threads = threads
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        SDK.networkLogger = recorder
    }

    @After
    fun tearDown() {
        SDK.networkLogger = null
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        server.shutdown()
    }

    private val closedUrl: String get() = "http://127.0.0.1:${ServerSocket(0).use { it.localPort }}/"

    /** The legacy core over [baseUrl]; each request's answer is delivered before this returns. */
    private fun legacy(baseUrl: String, method: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener? = null) {
        LegacyCore(settings) { baseUrl }.send(method, params, listener)
        threads.awaitIdle()
    }

    private fun core(baseUrl: String) = CoreHttpClient({ baseUrl }, settings, FakeConnectivity(connected = true), "shop")

    @Test
    fun `the legacy core tells the logger every call, the error only for one without an answer`() {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"down"}"""))
        val base = server.url("/").toString()

        legacy(base, NetworkMethod.GET("products/get"), JSONObject().put("item_id", "42"))
        legacy(base, NetworkMethod.POST("push"), JSONObject().put("event", "view"))
        legacy(closedUrl, NetworkMethod.GET("products/get"), JSONObject())

        assertEquals(3, calls.size)
        val (got, failed, refused) = calls
        assertEquals("GET", got.method)
        assertTrue(got.url, got.url.startsWith(base + "products/get?") && got.url.contains("item_id=42") && got.url.contains("shop_id=shop"))
        assertNull(got.requestBody)
        assertEquals(200, got.responseCode)
        assertEquals("""{"items":[]}""", got.responseBody)
        assertNull(got.error)

        assertEquals("POST", failed.method)
        assertEquals(base + "push", failed.url)
        assertEquals("view", JSONObject(failed.requestBody.orEmpty()).getString("event"))
        assertEquals(500, failed.responseCode)
        assertEquals("""{"message":"down"}""", failed.responseBody)
        assertNull(failed.error)

        assertEquals("no connection is 504 on the legacy core", 504, refused.responseCode)
        assertNull(refused.responseBody)
        assertNotNull(refused.error)
        assertTrue(calls.all { it.durationMs >= 0 })
    }

    @Test
    fun `the new core tells the logger every call, no answer being -1 with its error`() {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"down"}"""))
        val base = server.url("/").toString()

        runBlocking {
            core(base).send(NetworkMethod.GET("products/get"), JSONObject().put("item_id", "42"))
            core(base).send(NetworkMethod.POST("push"), JSONObject().put("event", "view"))
            core(closedUrl).send(NetworkMethod.GET("products/get"), JSONObject())
        }

        assertEquals(3, calls.size)
        val (got, failed, refused) = calls
        assertEquals("GET", got.method)
        assertTrue(got.url, got.url.startsWith(base + "products/get?") && got.url.contains("item_id=42") && got.url.contains("did=did-1"))
        assertNull(got.requestBody)
        assertEquals(200, got.responseCode)
        assertEquals("""{"items":[]}""", got.responseBody)
        assertNull(got.error)

        assertEquals("POST", failed.method)
        assertEquals("view", JSONObject(failed.requestBody.orEmpty()).getString("event"))
        assertEquals(500, failed.responseCode)
        assertEquals("""{"message":"down"}""", failed.responseBody)
        assertNull(failed.error)

        assertEquals(-1, refused.responseCode)
        assertNotNull(refused.error)
        assertTrue(calls.all { it.durationMs >= 0 })
    }

    @Test
    fun `a logger that throws is logged and the request still gets its one answer, on both cores`() {
        SDK.networkLogger = NetworkLogger { _, _, _, _, _, _, _ -> throw IllegalStateException("the app's logger") }
        server.enqueue(MockResponse().setBody("""{"id":1}"""))
        server.enqueue(MockResponse().setBody("""{"id":2}"""))
        val base = server.url("/").toString()
        val answers = CopyOnWriteArrayList<String>()

        legacy(base, NetworkMethod.GET("products/get"), JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                answers += "success:$response"
            }

            override fun onError(code: Int, msg: String?) {
                answers += "error:$code"
            }
        })
        val result = runBlocking { core(base).send(NetworkMethod.GET("products/get"), JSONObject()) }

        assertEquals(listOf("""success:{"id":1}"""), answers)
        assertEquals("""{"id":2}""", (result as HttpResult.Success).body.toString())
        assertEquals(2, logged.count { it.code == LogCodes.CALLBACK_FAILED && it.level == LogLevel.ERROR })
    }

    @Test
    fun `without a logger nothing is told and the request goes as before`() {
        SDK.networkLogger = null
        server.enqueue(MockResponse().setBody("{}"))

        val result = runBlocking { core(server.url("/").toString()).send(NetworkMethod.GET("products/get"), JSONObject()) }

        assertTrue(result is HttpResult.Success)
        assertTrue(calls.isEmpty())
        assertTrue(logged.none { it.code == LogCodes.CALLBACK_FAILED })
        // The error of a failed send is the client's, not the logger's.
        assertEquals(SdkError.Network, runBlocking { core(closedUrl).send(NetworkMethod.GET("x"), JSONObject()) }.let { (it as HttpResult.Failure).error })
    }
}
