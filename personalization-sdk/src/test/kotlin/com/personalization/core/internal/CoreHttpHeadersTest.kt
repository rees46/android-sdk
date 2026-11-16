package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * What the new core's client puts on the wire beside the fields, against a local server: the SDK's
 * `User-Agent` on every request, under a Turkish locale too; no cookie ever, whatever `CookieHandler` the
 * app installed (`core/network.md`), while the legacy core follows it as it always did; a `2xx` with JSON
 * cut short is `decoding`; and the log codes of a request answered, of one that cannot be built and of one
 * whose instance was not ready in time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreHttpHeadersTest {

    private val server = MockWebServer()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val defaultLocale = Locale.getDefault()
    private val defaultCookies: CookieHandler? = CookieHandler.getDefault()
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns "shop"
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }

    @Before
    fun setUp() {
        server.start()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        CookieHandler.setDefault(defaultCookies)
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        server.shutdown()
    }

    private fun client() = CoreHttpClient({ server.url("/").toString() }, settings, FakeConnectivity(connected = true), "shop")

    private fun send(method: NetworkMethod, client: CoreHttpClient = client()): HttpResult =
        runBlocking { client.send(method, JSONObject().put("k", "v")) }

    @Test
    fun `every request of the new core carries the SDK's User-Agent, under a Turkish locale too`() {
        listOf(Locale.ROOT, Locale("tr", "TR")).forEach { locale ->
            Locale.setDefault(locale)
            repeat(3) { server.enqueue(MockResponse().setBody("{}")) }

            val results = listOf(
                send(NetworkMethod.GET("products/get")),
                send(NetworkMethod.POST("push")),
                send(NetworkMethod.GET(CoreHttpClient.INIT_PATH)),
            )

            assertTrue("$locale: $results", results.all { it is HttpResult.Success })
            repeat(3) { assertEquals("$locale", SDK.userAgent(), server.takeRequest().getHeader("User-Agent")) }
        }
        assertTrue(SDK.userAgent().all { it.code < 128 })
    }

    @Test
    fun `the new core never sends a cookie the server set, even with the app's CookieHandler installed`() {
        listOf<CookieHandler?>(null, CookieManager(null, CookiePolicy.ACCEPT_ALL)).forEach { handler ->
            CookieHandler.setDefault(handler)
            server.enqueue(MockResponse().setBody("""{"did":"D1"}""").addHeader("Set-Cookie", "did=COOKIE; Max-Age=31536000; Path=/"))
            server.enqueue(MockResponse().setBody("{}"))
            val client = client()

            send(NetworkMethod.GET(CoreHttpClient.INIT_PATH), client)
            send(NetworkMethod.GET("products/get"), client)
            send(NetworkMethod.GET(CoreHttpClient.INIT_PATH), client())

            server.takeRequest()
            repeat(2) { assertNull("handler $handler", server.takeRequest().getHeader("Cookie")) }
        }
    }

    @Test
    fun `the legacy core follows the app's CookieHandler, and sends none without one`() {
        val threads = TrackingThreads()
        LegacyCore.threads = threads
        val legacy = LegacyCore(settings) { server.url("/").toString() }
        fun get() {
            legacy.send(NetworkMethod.GET("products/get"), JSONObject(), object : OnApiCallbackListener() {})
            threads.awaitIdle()
        }

        CookieHandler.setDefault(null)
        server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "did=COOKIE; Max-Age=31536000; Path=/"))
        server.enqueue(MockResponse().setBody("{}"))
        get()
        get()
        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))

        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "did=COOKIE; Max-Age=31536000; Path=/"))
        server.enqueue(MockResponse().setBody("{}"))
        get()
        get()
        server.takeRequest()
        // The JDK's own cookie format: the value is there, whatever the attributes beside it.
        val cookie = server.takeRequest().getHeader("Cookie").orEmpty()
        assertTrue(cookie, cookie.contains("did=") && cookie.contains("COOKIE"))
    }

    @Test
    fun `a 2xx whose JSON is cut short is decoding on the new core, logged without the answer`() {
        server.enqueue(MockResponse().setBody("""{"did":"D"""))
        server.enqueue(MockResponse().setBody("""[1,"""))

        assertEquals(SdkError.Decoding, (send(NetworkMethod.GET(CoreHttpClient.INIT_PATH)) as HttpResult.Failure).error)
        assertEquals(SdkError.Decoding, (send(NetworkMethod.GET("products/get")) as HttpResult.Failure).error)

        val decoding = logged.filter { it.code == LogCodes.DECODING_FAILED }
        assertEquals(2, decoding.size)
        assertTrue(decoding.all { it.level == LogLevel.WARNING && "\"D" !in it.message && "\"D" !in it.context.toString() })
    }

    @Test
    fun `an answered request is logged as request at debug, with its method, path and code`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        send(NetworkMethod.POST("push"))

        val line = logged.single { it.code == LogCodes.REQUEST }
        assertEquals(LogLevel.DEBUG, line.level)
        assertEquals(mapOf("method" to "POST", "path" to "push", "status" to 201), line.context)
    }

    @Test
    fun `a request that cannot be built is logged as request_build_failed and goes nowhere`() {
        val client = CoreHttpClient({ "not a url" }, settings, FakeConnectivity(connected = true), "shop")

        val result = send(NetworkMethod.GET("products/get"), client)

        assertTrue((result as HttpResult.Failure).error is SdkError.InvalidArgument)
        val line = logged.single { it.code == LogCodes.REQUEST_BUILD_FAILED }
        assertEquals(LogLevel.ERROR, line.level)
        assertEquals("GET", line.context["method"])
        assertEquals("products/get", line.context["path"])
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a request whose instance is not ready within its read timeout fails, logged as not ready`() {
        val scheduler = TestCoroutineScheduler()
        val timeouts = CoreTimeouts(readMs = 3_000)
        val core = CoreV2(
            CoreHttpClient({ server.url("/").toString() }, settings, FakeConnectivity(connected = true), "shop", timeouts = timeouts),
            "shop",
            testCoreDispatchers(scheduler),
        )
        val answers = CopyOnWriteArrayList<String>()

        // No /init is ever set: the instance never becomes ready.
        core.send(NetworkMethod.GET("products/get"), JSONObject(), object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                answers += "error:$code"
            }
        })
        scheduler.advanceTimeBy(timeouts.readMs - 1)
        scheduler.runCurrent()
        assertTrue("not before its read timeout", answers.isEmpty())
        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()

        assertEquals(listOf("error:-1"), answers)
        val line = logged.single { it.code == LogCodes.REQUEST_FAILED }
        assertEquals(LogLevel.WARNING, line.level)
        assertEquals(LogReasons.NOT_READY, line.context["reason"])
        assertEquals("products/get", line.context["path"])
        assertEquals(0, server.requestCount)
        core.release()
    }
}
