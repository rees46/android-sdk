package com.personalization.sdk.data.repositories.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives [NetworkRepositoryImpl] against a local HTTP server: which answers reach which
 * callback, how often, and what a host's failing callback does to the network thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkRepositoryImplTest {

    private data class Recorded(val method: String, val uri: String, val body: String)

    /**
     * An answer of the local server; `null` from [handler] keeps the connection open, silent.
     * [stall] sends the status, the headers and [body], then falls silent before the promised rest.
     */
    private data class Reply(val code: Int, val body: String?, val stall: Boolean = false)

    private lateinit var server: ServerSocket
    private val requests = CopyOnWriteArrayList<Recorded>()

    @Volatile
    private var handler: (Recorded) -> Reply? = { Reply(200, "{}") }

    private val release = CountDownLatch(1)
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var repository: NetworkRepositoryImpl

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: Exception) { break }
                Thread { serve(socket) }.start()
            }
        }.start()

        val settings = mockk<UserSettingsRepository>(relaxed = true) {
            every { getShopId() } returns "shop"
            every { getDid() } returns "did"
            every { getSid() } returns "sid"
            every { getSegmentForABTesting() } returns "A"
            every { getStream() } returns "android"
        }
        repository = NetworkRepositoryImpl(settings)
        repository.initialize("http://127.0.0.1:${server.localPort}/")

        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        release.countDown()
        server.close()
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
    }

    @Test
    fun `a 201 answer reaches onSuccess`() {
        handler = { Reply(201, """{"id":1}""") }

        val listener = Recorder()
        repository.post("track/custom", JSONObject(), listener)

        assertEquals(listOf("""success:{"id":1}"""), listener.awaitCalls())
    }

    @Test
    fun `an empty 202 answer reaches onSuccess with an empty object`() {
        handler = { Reply(202, null) }

        val listener = Recorder()
        repository.post("track/custom", JSONObject(), listener)

        assertEquals(listOf("success:{}"), listener.awaitCalls())
    }

    @Test
    fun `an answer outside 2xx below 400 reaches onError`() {
        handler = { Reply(304, null) }

        val listener = Recorder()
        repository.get("products/get", JSONObject(), listener)

        assertEquals(listOf("error:304"), listener.awaitCalls())
    }

    @Test
    fun `an error answer without a body keeps its status`() {
        handler = { Reply(404, null) }

        val listener = Recorder()
        repository.get("products/get", JSONObject(), listener)

        assertEquals(listOf("error:404"), listener.awaitCalls())
    }

    @Test
    fun `a failing onSuccess ends in one onError and does not crash the network thread`() {
        handler = { Reply(200, "{}") }

        // The SDK's own listeners parse the answer in onSuccess: a parse error must reach the app.
        val listener = Recorder(throwOnSuccess = true)
        repository.post("track/custom", JSONObject(), listener)

        assertEquals(listOf("success:{}", "error:-1"), listener.awaitCalls())
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
    }

    @Test
    fun `a failing onSuccess and onError end the request without crashing the network thread`() {
        handler = { Reply(200, "{}") }

        val listener = Recorder(throwOnSuccess = true, throwOnError = true)
        repository.post("track/custom", JSONObject(), listener)

        assertEquals(listOf("success:{}", "error:-1"), listener.awaitCalls())
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
    }

    @Test
    fun `a throwing onError is called once and does not crash the network thread`() {
        handler = { Reply(500, "oops") }

        val listener = Recorder(throwOnError = true)
        repository.post("track/custom", JSONObject(), listener)

        assertEquals(listOf("error:500"), listener.awaitCalls())
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
    }

    @Test
    fun `a server that stops answering ends in onError after the read timeout`() {
        handler = { release.await(10, TimeUnit.SECONDS); null }
        repository.readTimeoutMs = 300

        val listener = Recorder()
        repository.get("products/get", JSONObject(), listener)

        assertEquals(listOf("error:-1"), listener.awaitCalls(timeoutMs = 5_000))
        val log = ShadowLog.getLogs().joinToString("\n") { it.msg.orEmpty() }
        assertTrue("the failed request is named: $log", log.contains("GET products/get:"))
    }

    @Test
    fun `a success answer cut short by the read timeout ends in onError`() {
        handler = { Reply(200, """{"did":"abc",""", stall = true) }
        repository.readTimeoutMs = 300

        val listener = Recorder()
        repository.get("init", JSONObject(), listener)

        assertEquals(listOf("error:-1"), listener.awaitCalls(timeoutMs = 5_000))
    }

    @Test
    fun `a request keeps its own code and gets no from`() {
        val listener = Recorder()
        repository.post("track/stories", JSONObject().put("code", "story-1"), listener)
        listener.awaitCalls()

        val body = JSONObject(requests.single().body)
        assertEquals("story-1", body.getString("code"))
        assertFalse(body.has("from"))
    }

    // `/init` with top-level `from` and `code` records a click of that mailing: they are not common
    // fields. A clicked push travels as the `source` of `/push` events instead.
    @Test
    fun `common fields carry no from or code`() {
        val listener = Recorder()
        repository.post("push", JSONObject().put("event", "view"), listener)
        listener.awaitCalls()

        val body = JSONObject(requests.single().body)
        assertEquals(setOf("event", "shop_id", "did", "sid", "seance", "segment", "stream"), body.keys().asSequence().toSet())
    }

    @Test
    fun `request and answer data stay out of the log`() {
        handler = { Reply(200, """{"email":"answer@example.com"}""") }

        val get = Recorder()
        repository.get("profile", JSONObject().put("email", "get@example.com"), get)
        get.awaitCalls()
        val post = Recorder()
        repository.post("profile/set", JSONObject().put("phone", "+79990001122"), post)
        post.awaitCalls()
        handler = { Reply(422, """{"error":"invalid phone +79990004455"}""") }
        val rejected = Recorder()
        repository.post("profile/set", JSONObject().put("phone", "+79990004455"), rejected)
        rejected.awaitCalls()

        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag}: ${it.msg}" }
        assertFalse(log, log.contains("get@example.com"))
        assertFalse(log, log.contains("79990001122"))
        assertFalse(log, log.contains("answer@example.com"))
        assertFalse(log, log.contains("79990004455"))
        assertTrue(log, log.contains("422: POST profile/set"))
    }

    /** Reads one HTTP/1.1 request from [socket], records it and writes the [handler]'s reply. */
    private fun serve(socket: Socket) = socket.use {
        val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
        val (method, uri) = reader.readLine()?.split(" ")?.let { line -> line[0] to line[1] } ?: return
        var length = 0
        while (true) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                length = header.substringAfter(':').trim().toInt()
            }
        }
        val body = CharArray(length).also { chars ->
            var read = 0
            while (read < length) {
                val n = reader.read(chars, read, length - read)
                if (n < 0) break
                read += n
            }
        }
        val recorded = Recorded(method, uri, String(body))
        requests += recorded
        val reply = handler(recorded) ?: return
        val bytes = reply.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val promised = if (reply.stall) bytes.size + 100 else bytes.size
        val out = it.getOutputStream()
        out.write(
            ("HTTP/1.1 ${reply.code} Status\r\n" +
                "Content-Length: $promised\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        )
        out.write(bytes)
        out.flush()
        if (reply.stall) release.await(10, TimeUnit.SECONDS)
    }

    /** Records every callback; waits for the first one, then a little longer for repeats. */
    private class Recorder(
        private val throwOnSuccess: Boolean = false,
        private val throwOnError: Boolean = false,
    ) : OnApiCallbackListener() {

        private val calls = CopyOnWriteArrayList<String>()
        private val first = CountDownLatch(1)

        override fun onSuccess(response: JSONObject?) = record("success:$response", throwOnSuccess)

        override fun onSuccess(response: JSONArray) = record("success:$response", throwOnSuccess)

        override fun onError(code: Int, msg: String?) = record("error:$code", throwOnError)

        private fun record(call: String, fail: Boolean) {
            calls += call
            first.countDown()
            if (fail) throw IllegalStateException("host callback failed")
        }

        fun awaitCalls(timeoutMs: Long = 3_000): List<String> {
            first.await(timeoutMs, TimeUnit.MILLISECONDS)
            Thread.sleep(300)
            return calls.toList()
        }
    }
}
