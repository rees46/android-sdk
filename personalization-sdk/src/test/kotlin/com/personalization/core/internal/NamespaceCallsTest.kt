package com.personalization.core.internal

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The path of the namespaces' methods on the new core: one request with a result, its answer read into a
 * model or an [SdkError] of the contract, the legacy core refused without a request, and the form with a
 * [CompletionListener] ending once on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NamespaceCallsTest {

    private val server = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getShopId() } returns SHOP
        every { getDid() } returns "did-1"
        every { getSid() } returns "sid-1"
        every { getStream() } returns "android"
        every { getSegmentForABTesting() } returns "A"
    }
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val main = HeldMain()
    private var core: Core? = null
    private val calls = NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers(main = { main }) }, shopId = { SHOP })

    @Before
    fun setUp() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        server.start()
        core = newCore()
    }

    @After
    fun tearDown() {
        core?.release()
        server.close()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    /** Puts [next] in the place of the current core, which is released. */
    private fun replaceCore(next: Core?) {
        core?.release()
        core = next
    }

    private fun newCore(timeouts: CoreTimeouts = CoreTimeouts(), init: CoreInit? = null): CoreV2 {
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, SHOP, timeouts = timeouts)
        return CoreV2(client, SHOP).also {
            it.init = init ?: startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity, SHOP)
        }
    }

    /** Reads the answer as a list of `id`s under `items`; null when there is no such list. */
    private fun ids(body: Any?): List<String>? =
        (body as? JSONObject)?.arrayOrNull("items")?.mapObjects { it.nonBlankStringOrNull("id") }

    private fun get(read: (Any?) -> List<String>? = ::ids): List<String> = runBlocking {
        withTimeout(10_000) { calls.request(CALL, NetworkMethod.GET(PATH), JSONObject().put("limit", 2), read = read) }
    }

    private fun failure(read: (Any?) -> List<String>? = ::ids): SdkException = try {
        get(read)
        fail("expected SdkException")
        throw AssertionError()
    } catch (e: SdkException) {
        e
    }

    // The request and its answer.

    @Test
    fun `a 2xx answer is read into the method's value, and the request carries the method's and the common fields`() {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"37"},{"id":38},{"name":"no id"}],"unknown":true}"""))

        assertEquals(listOf("37", "38"), get())

        val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("/$PATH", url.encodedPath)
        assertEquals(
            mapOf("limit" to "2", "shop_id" to SHOP, "did" to "did-1", "sid" to "sid-1", "seance" to "sid-1", "segment" to "A", "stream" to "android"),
            url.queryParameterNames.associateWith { url.queryParameter(it) },
        )
    }

    @Test
    fun `an answer without a body reaches the reader as null, an empty object as itself`() {
        val bodies = LinkedBlockingQueue<Any>()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody("{}"))

        get { body -> bodies += body ?: "none"; emptyList() }
        get { body -> bodies += body ?: "none"; emptyList() }

        assertEquals("none", bodies.poll())
        assertEquals("{}", bodies.poll()?.toString())
    }

    @Test
    fun `an answer outside 2xx is http with its code and no server code, and nothing repeats it`() {
        listOf(400, 402, 403, 404, 422, 500, 503).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"status":"error","message":"Client not found"}"""))

            assertEquals(SdkError.Http(code), failure().error)
        }
        assertEquals(7, server.requestCount)
    }

    @Test
    fun `a 2xx answer that does not read into the model is decoding, logged without the server's text`() {
        server.enqueue(MockResponse().setBody("""{"status":"error","message":"secret@example.com is not allowed"}"""))
        server.enqueue(MockResponse().setBody("<html>maintenance</html>"))
        server.enqueue(MockResponse().setBody("""[1,2]"""))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        repeat(3) { assertEquals(SdkError.Decoding, failure().error) }
        assertEquals(SdkError.Decoding, failure { throw IllegalStateException("a bug of the reader") }.error)

        val decoding = logged.filter { it.code == LogCodes.DECODING_FAILED }
        assertTrue(decoding.isNotEmpty())
        assertTrue(decoding.all { it.level == LogLevel.WARNING })
        assertTrue(decoding.any { it.context["call"] == CALL && it.context["path"] == PATH })
        assertTrue(logged.none { "secret@example.com" in it.message || "secret@example.com" in it.context.toString() })
    }

    @Test
    fun `without a network the call fails at once with network and sends nothing`() {
        connectivity.set(false)

        assertEquals(SdkError.Network, failure().error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an answer that does not come within the read timeout is timeout`() {
        replaceCore(newCore(CoreTimeouts(readMs = 300)))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val started = System.nanoTime()
        assertEquals(SdkError.Timeout, failure().error)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a read timeout of the method's own holds for its request`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val started = System.nanoTime()
        val error = try {
            runBlocking { calls.request(CALL, NetworkMethod.GET(PATH), readTimeoutMs = 300, read = ::ids) }
            null
        } catch (e: SdkException) {
            e.error
        }

        assertEquals(SdkError.Timeout, error)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
    }

    @Test
    fun `an instance not ready within the read timeout fails with timeout and sends nothing`() {
        // No /init is ever set: the instance never gets ready.
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, SHOP, timeouts = CoreTimeouts(readMs = 300))
        replaceCore(CoreV2(client, SHOP))

        assertEquals(SdkError.Timeout, failure().error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refused shop fails with shopBlocked and sends nothing`() {
        val noDid = mockk<UserSettingsRepository>(relaxed = true) {
            every { getShopId() } returns REFUSED_SHOP
            every { getDid() } returns ""
        }
        val init = startedCoreInit(ApplicationProvider.getApplicationContext(), noDid, connectivity, REFUSED_SHOP) {
            HttpResult.Failure(SdkError.Http(402))
        }
        replaceCore(newCore(init = init))

        assertEquals(SdkError.ShopBlocked, failure().error)
        assertEquals(0, server.requestCount)
    }

    // The core.

    @Test
    fun `on the legacy core the call fails with notInitialized, says why and sends nothing`() {
        replaceCore(LegacyCore(settings) { server.url("/").toString() })

        val error = failure()

        assertEquals(SdkError.NotInitialized, error.error)
        assertEquals("$CALL runs on the new core only: start the shop with Rees46.initializeBridge", error.message)
        val line = logged.single { it.code == LogCodes.CORE_FLAG_MISMATCH }
        assertEquals(LogLevel.WARNING, line.level)
        assertEquals(mapOf("call" to CALL), line.context)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an instance not initialized or released fails with notInitialized and sends nothing`() {
        val released = core
        core = null
        assertEquals(SdkError.NotInitialized, failure().error)

        core = released
        released?.release()
        assertEquals(SdkError.NotInitialized, failure().error)

        assertEquals(0, server.requestCount)
        assertTrue(logged.none { it.code == LogCodes.CORE_FLAG_MISMATCH })
    }

    @Test
    fun `release ends a request in flight with cancelled`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val outcome = runBlocking(Dispatchers.IO) {
            val call = async { runCatching { calls.request(CALL, NetworkMethod.GET(PATH), read = ::ids) } }
            server.takeRequest(5, TimeUnit.SECONDS)
            core?.release()
            withTimeout(5_000) { call.await() }
        }

        assertEquals(SdkError.Cancelled, (outcome.exceptionOrNull() as SdkException).error)
        assertTrue(logged.any { it.code == LogCodes.REQUEST_CANCELLED && it.context["path"] == PATH })
    }

    @Test
    fun `cancelling the caller cancels the request and the caller gets its own cancellation`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val thrown = LinkedBlockingQueue<Throwable>()
        runBlocking(Dispatchers.IO) {
            val call = async {
                try {
                    calls.request(CALL, NetworkMethod.GET(PATH), read = ::ids)
                } catch (e: Throwable) {
                    thrown += e
                    throw e
                }
            }
            server.takeRequest(5, TimeUnit.SECONDS)
            call.cancel()
            withTimeout(5_000) { call.join() }
        }

        val error = thrown.poll(5, TimeUnit.SECONDS)
        // The caller's own cancellation, not an SdkException.
        assertTrue("$error", error is CancellationException)
        assertTrue(logged.none { it.code == LogCodes.REQUEST_CANCELLED })
        // The instance goes on: the next call is answered.
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"1"}]}"""))
        assertEquals(listOf("1"), get())
    }

    // The form with a listener.

    private fun complete(): List<Pair<SdkResult<List<String>>, Boolean>> {
        val ends = CopyOnWriteArrayList<Pair<SdkResult<List<String>>, Boolean>>()
        calls.complete(CompletionListener { result -> ends += result to (Looper.myLooper() == Looper.getMainLooper()) }) {
            calls.request(CALL, NetworkMethod.GET(PATH), read = ::ids)
        }
        // Handed to the main thread: run it here, on the test's main thread, then wait for any second end.
        while (ends.isEmpty() && main.runNext()) Unit
        main.runNext(timeoutMs = 200)
        return ends
    }

    @Test
    fun `the listener hears the value or the error once, on the main thread`() {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"37"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(listOf(SdkResult.Success(listOf("37")) to true), complete())
        assertEquals(listOf(SdkResult.Failure(SdkError.Http(404)) to true), complete())

        replaceCore(LegacyCore(settings) { server.url("/").toString() })
        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), complete())
    }

    @Test
    fun `release ends the listener's request with cancelled`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val ends = CopyOnWriteArrayList<SdkResult<List<String>>>()
        calls.complete(CompletionListener { ends += it }) { calls.request(CALL, NetworkMethod.GET(PATH), read = ::ids) }
        server.takeRequest(5, TimeUnit.SECONDS)

        core?.release()
        while (ends.isEmpty() && main.runNext()) Unit

        assertEquals(listOf<SdkResult<List<String>>>(SdkResult.Failure(SdkError.Cancelled)), ends)
    }

    @Test
    fun `a listener that throws is logged once and not called again`() {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        var heard = 0
        calls.complete(CompletionListener<List<String>> { heard++; throw IllegalStateException("the app's bug") }) {
            calls.request(CALL, NetworkMethod.GET(PATH), read = ::ids)
        }

        while (heard == 0 && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(1, heard)
        assertEquals(1, logged.count { it.code == LogCodes.CALLBACK_FAILED && it.level == LogLevel.ERROR })
    }

    @Test
    fun `a failure nobody expected still ends the listener's call`() {
        val ends = CopyOnWriteArrayList<SdkResult<String>>()
        calls.complete(CompletionListener { ends += it }) { throw IllegalStateException("a bug") }

        while (ends.isEmpty() && main.runNext()) Unit

        assertEquals(1, ends.size)
        assertTrue(ends.single() is SdkResult.Failure)
        assertTrue(logged.any { it.code == LogCodes.SDK_LOG && it.level == LogLevel.ERROR })
    }

    private companion object {
        const val SHOP = "namespace-shop"
        const val REFUSED_SHOP = "namespace-refused-shop"
        const val CALL = "test.get"
        const val PATH = "products/test"
    }
}
