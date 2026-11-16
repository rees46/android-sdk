package com.personalization.core.internal

import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
        core = CoreV2(client, "shop").also { it.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity) }
    }

    @After
    fun tearDown() {
        core.release()
        server.close()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private val defaultExecutor = SdkLogs.handlerExecutor

    @Test
    fun `answers come back as the old callbacks get them`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[1]"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"no such product"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html/>"))

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
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val recorder = Recorder(throwOnSuccess = true)
        core.send(NetworkMethod.GET("products/get"), JSONObject(), recorder)

        assertEquals(listOf("success:{}", "error:-1"), recorder.await(expected = 2).map { it.substringBefore(":host") })
    }

    @Test
    fun `release cancels the requests in flight and fails the ones after it`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
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
    fun `a request release cancels is logged at debug by method and path`() {
        val logged = CopyOnWriteArrayList<SdkLogMessage>()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val inFlight = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject().put("email", "a@example.com"), inFlight)
        server.takeRequest(5, TimeUnit.SECONDS)

        core.release()
        inFlight.await()

        val cancelled = logged.filter { it.code == LogCodes.REQUEST_CANCELLED }
        assertEquals(1, cancelled.size)
        assertEquals(LogLevel.DEBUG, cancelled.single().level)
        assertEquals(mapOf("method" to "GET", "path" to "products/get"), cancelled.single().context)
        assertTrue(logged.none { "a@example.com" in it.message || "a@example.com" in it.context.toString() })
    }

    @Test
    fun `a request waits for the core's init to be set, within its read timeout`() {
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, "shop", timeouts = CoreTimeouts(readMs = 300))
        val bare = CoreV2(client, "shop")
        val timedOut = Recorder()
        bare.send(NetworkMethod.GET("products/get"), JSONObject(), timedOut)
        assertEquals(listOf("error:-1"), timedOut.await().map { it.substringBefore(":The") })
        assertEquals(0, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val waiting = Recorder()
        bare.send(NetworkMethod.GET("products/get"), JSONObject(), waiting)
        bare.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity)
        assertEquals(listOf("success:{}"), waiting.await())
        bare.release()
    }

    @Test
    fun `a release that lets requests finish gives the one in flight its answer`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setHeadersDelay(300, TimeUnit.MILLISECONDS))
        val inFlight = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject(), inFlight)
        server.takeRequest(5, TimeUnit.SECONDS)

        core.release(cancelRequests = false)

        assertEquals(listOf("success:{}"), inFlight.await())
        val after = Recorder()
        core.send(NetworkMethod.GET("products/get"), JSONObject(), after)
        assertEquals(listOf("error:-1"), after.await().map { it.substringBefore(":The") })
    }

    @Test
    fun `an event the queue's disk refused goes by the queue's rules - a push event neither waits for a session nor asks init`() {
        val inits = AtomicInteger()
        // The mock's last activity is never: every session's window has ended.
        val init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity) {
            inits.incrementAndGet()
            HttpResult.Success(JSONObject().put("did", "did-1").put("seance", "S"))
        }
        runBlocking {
            init.awaitPrepared()
            init.awaitIdle()
        }
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, "shop")
        val direct = CoreV2(client, "shop").also { it.init = init }
        assertEquals("the start's own /init", 1, inits.get())

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val pushed = Recorder()
        direct.sendDirectly("track/delivered", JSONObject().put("code", "m-1").put("type", "bulk"), pushed)
        assertEquals(listOf("success:{}"), pushed.await())
        assertEquals("/track/delivered", server.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertEquals("no /init for a push event", 1, inits.get())

        // A tracking event goes as a request does: the session's window ended, so /init comes first.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val tracked = Recorder()
        direct.sendDirectly("push", JSONObject().put("event", "view"), tracked)
        assertEquals(listOf("success:{}"), tracked.await())
        assertEquals(2, inits.get())
        direct.release()
    }

    @Test
    fun `a request without a listener still goes`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

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
