package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.core.SdkError
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** The new core's client against a local server: what each outcome becomes, and what goes on the wire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreHttpClientTest {

    private val server = MockWebServer()
    private val connectivity = FakeConnectivity(connected = true)
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
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        server.close()
        SdkLogs.reset()
    }

    private fun client(timeouts: CoreTimeouts = CoreTimeouts(), baseUrl: String = server.url("/").toString()) =
        CoreHttpClient({ baseUrl }, settings, connectivity, "shop", timeouts = timeouts)

    private fun send(
        method: NetworkMethod = NetworkMethod.GET("products/get"),
        params: JSONObject = JSONObject(),
        client: CoreHttpClient = client(),
    ): HttpResult = runBlocking { client.send(method, params) }

    private fun reply(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun HttpResult.error(): SdkError = (this as HttpResult.Failure).error

    private fun HttpResult.body(): Any = (this as HttpResult.Success).body

    @Test
    fun `any 2xx is a success and an empty one an empty object`() {
        reply(200, """{"id":1}""")
        reply(201, "[1,2]")
        reply(204)
        reply(202, "  ")

        assertEquals("""{"id":1}""", send().body().toString())
        assertEquals(JSONArray("[1,2]").toString(), (send().body() as JSONArray).toString())
        assertEquals("{}", send().body().toString())
        assertEquals("{}", send().body().toString())
    }

    @Test
    fun `a 2xx that is not a JSON object or array is decoding`() {
        reply(200, "<html>maintenance</html>")
        reply(200, "\"ok\"")
        reply(200, "42")

        repeat(3) { assertEquals(SdkError.Decoding, send().error()) }
    }

    @Test
    fun `an answer outside 2xx is http with its code, the body kept for the old callbacks`() {
        listOf(304, 400, 402, 404, 422, 500, 503).forEach { code ->
            reply(code, if (code == 304) "" else """{"message":"failed $code"}""")
            val result = send() as HttpResult.Failure
            assertEquals(SdkError.Http(code), result.error)
            if (code != 304) assertEquals("""{"message":"failed $code"}""", result.errorBody)
        }
    }

    @Test
    fun `a body that breaks off midway is a timeout, not a success with part of it`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"did":"abc","popup":{"id":7,"components":"${"x".repeat(4_000)}"}}""")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        assertEquals(SdkError.Timeout, send().error())
    }

    @Test
    fun `an answer that does not come within the read timeout is a timeout`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val started = System.nanoTime()
        val result = send(client = client(CoreTimeouts(readMs = 300)))

        assertEquals(SdkError.Timeout, result.error())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
    }

    @Test
    fun `init reads with its own timeout, other requests keep theirs`() {
        val client = client(CoreTimeouts(readMs = 60_000, initReadMs = 300))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val started = System.nanoTime()
        assertEquals(SdkError.Timeout, send(NetworkMethod.GET("init"), client = client).error())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
        assertEquals(60_000, client.clientFor(NetworkMethod.GET("products/get")).readTimeoutMillis)
    }

    @Test
    fun `the timeouts are 5 seconds to connect, 10 to read and 5 to read init`() {
        val client = client()

        assertEquals(5_000, client.client.connectTimeoutMillis)
        assertEquals(10_000, client.client.readTimeoutMillis)
        assertEquals(10_000, client.client.writeTimeoutMillis)
        assertEquals(5_000, client.clientFor(NetworkMethod.GET("init")).readTimeoutMillis)
        assertEquals(5_000, client.clientFor(NetworkMethod.GET("init")).connectTimeoutMillis)
    }

    @Test
    fun `the instances' clients share one connection pool`() {
        val first = client()
        val second = client()

        assertTrue(first.client.connectionPool === second.client.connectionPool)
        assertTrue(first.client.dispatcher === second.client.dispatcher)
        assertTrue(first.client.connectionPool === CoreHttp.shared.connectionPool)
    }

    @Test
    fun `a server that refuses the connection is network`() {
        val closedPort = ServerSocket(0).use { it.localPort }

        val result = send(client = client(baseUrl = "http://127.0.0.1:$closedPort/"))

        assertEquals(SdkError.Network, result.error())
    }

    @Test
    fun `without a network a request fails at once and is never sent`() {
        SdkLogs.register("shop", LogLevel.DEBUG, "CoreTag")
        connectivity.set(false)
        reply(200, "{}")

        assertEquals(SdkError.Network, send().error())
        assertEquals(0, server.requestCount)
        val log = ShadowLog.getLogsForTag("CoreTag").joinToString("\n") { it.msg }
        assertTrue(log, log.contains("[request_failed] GET products/get: no network {method=GET, path=products/get, reason=offline}"))
    }

    @Test
    fun `the client does not retry`() {
        reply(500, "{}")
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"a\":\"${"x".repeat(4_000)}\"}").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        reply(200, "{}")

        assertEquals(SdkError.Http(500), send().error())
        assertEquals(SdkError.Timeout, send(NetworkMethod.POST("push")).error())

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `OkHttp never sends a request again by itself when its kept-alive connection drops after the request went`() {
        reply(200, "{}")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        reply(200, "{}")
        val client = client()

        assertTrue(send(NetworkMethod.POST("push"), JSONObject().put("event", "view"), client) is HttpResult.Success)
        // The second goes over the connection the first left in the pool; the server drops it once the
        // request is in. OkHttp's own retry would send it again on a new connection: the event twice.
        val second = send(NetworkMethod.POST("push"), JSONObject().put("event", "view"), client)

        assertEquals("the server got the event once", 2, server.requestCount)
        assertEquals("sent and not answered: the queue repeats it after a pause", SdkError.Timeout, second.error())
    }

    @Test
    fun `OkHttp never sends a request without a body again by itself either`() {
        reply(200, "{}")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        reply(200, "{}")
        val client = client()

        assertTrue(send(NetworkMethod.GET("products/get"), client = client) is HttpResult.Success)
        val second = send(NetworkMethod.GET("products/get"), client = client)

        assertEquals("the server got the request once", 2, server.requestCount)
        assertEquals(SdkError.Timeout, second.error())
    }

    @Test
    fun `a request goes to the host's next address when the first cannot be connected, once`() {
        val live = MockWebServer()
        live.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            // Nothing listens on the first address's port, or there is no IPv6 at all: it fails at once.
            val dead = InetAddress.getByName("::1")
            val asked = mutableListOf<String>()
            val shared = OkHttpClient.Builder().dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(dead, InetAddress.getByName("127.0.0.1")).also { asked += hostname }
            }).build()
            val client = CoreHttpClient({ "http://sdk.test:${live.port}/" }, settings, connectivity, "shop", shared = shared)
            live.enqueue(MockResponse().setBody("{\"ok\":true}"))
            live.enqueue(MockResponse().setBody("{\"ok\":true}"))

            // A POST, whose body goes only once, and a GET.
            val posted = send(NetworkMethod.POST("push"), JSONObject().put("event", "view"), client)
            val got = send(NetworkMethod.GET("products/get"), client = client)

            assertEquals("{\"ok\":true}", posted.body().toString())
            assertEquals("{\"ok\":true}", got.body().toString())
            assertEquals("each went once", 2, live.requestCount)
            assertEquals("/push", live.takeRequest().path)
            assertTrue(asked.isNotEmpty())
        } finally {
            live.close()
        }
    }

    @Test
    fun `a GET carries the common fields in its query`() {
        reply(200, "{}")

        send(NetworkMethod.GET("products/get"), JSONObject().put("item_id", "42"))

        val url = checkNotNull(server.takeRequest().requestUrl)
        assertEquals("/products/get", url.encodedPath)
        assertEquals("shop", url.queryParameter("shop_id"))
        assertEquals("did-1", url.queryParameter("did"))
        assertEquals("sid-1", url.queryParameter("sid"))
        assertEquals("sid-1", url.queryParameter("seance"))
        assertEquals("A", url.queryParameter("segment"))
        assertEquals("android", url.queryParameter("stream"))
        assertEquals("42", url.queryParameter("item_id"))
    }

    @Test
    fun `init leaves out a did and a session it does not have and the segment, and carries the stream of the config`() {
        every { settings.getDid() } returns ""
        every { settings.getSid() } returns ""
        every { settings.getStream() } returns "flutter"
        reply(200, "{}")

        send(NetworkMethod.GET("init"), JSONObject().put("tz", "3"))

        val url = checkNotNull(server.takeRequest().requestUrl)
        assertEquals(setOf("tz", "shop_id", "stream"), url.queryParameterNames)
        assertEquals("flutter", url.queryParameter("stream"))
    }

    @Test
    fun `init carries a did and a session it has, still without the segment`() {
        reply(200, "{}")

        send(NetworkMethod.GET("init"), JSONObject())

        val url = checkNotNull(server.takeRequest().requestUrl)
        assertEquals(setOf("shop_id", "did", "sid", "seance", "stream"), url.queryParameterNames)
    }

    @Test
    fun `the Retry-After of an answer comes back with it, in seconds or as a date`() {
        val values = listOf(
            503 to "30",
            429 to "Wed, 21 Oct 2015 07:28:00 GMT",
            503 to "soon",
            500 to null,
            503 to "0",
            503 to "Wed, 21 Oct 2015 07:26:00 GMT",
            503 to "9223372036854776",
            503 to "99999999999999999999999",
            503 to "-5",
            503 to "NaN",
            503 to "Infinity",
            503 to "1.5",
        )
        values.forEach { (code, header) ->
            server.enqueue(MockResponse().setResponseCode(code).apply { if (header != null) setHeader("Retry-After", header) })
        }
        val clock = FakeClock(now = 1_445_412_420_000L) // a minute before the first date above
        val client = CoreHttpClient({ server.url("/").toString() }, settings, connectivity, "shop", clock = clock)

        val results = values.map { send(NetworkMethod.GET("init"), client = client) as HttpResult.Failure }

        // A date already past, a negative number and NaN are now; a number too large for milliseconds
        // does not wrap around. Nothing throws.
        assertEquals(
            listOf(30_000L, 60_000L, null, null, 0L, 0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L, 0L, Long.MAX_VALUE, 1_500L),
            results.map { it.retryAfterMs },
        )
        assertEquals(values.size, server.requestCount)
        val backoff = Backoff(random = { 0.5 })
        assertEquals(
            listOf(30_000L, 60_000L, 1_000L, 1_000L, 300_000L, 300_000L, 1_000L, 1_000L, 300_000L, 1_500L),
            listOf(0, 1, 4, 5, 6, 7, 8, 9, 10, 11).map { backoff.delayMs(0, results[it].retryAfterMs) },
        )
    }

    @Test
    fun `OkHttp does not repeat a 408 or a 503 that says to come back at once`() {
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "99999999999"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(SdkError.Http(408), send(NetworkMethod.POST("push")).error())
        assertEquals(SdkError.Http(503), send(NetworkMethod.POST("push")).error())
        assertEquals(SdkError.Http(503), send(NetworkMethod.POST("push")).error())

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a Retry-After outside ASCII on a 408 or a 503 neither throws nor repeats`() {
        server.enqueue(MockResponse().setResponseCode(503).addHeaderLenient("Retry-After", "5 сек"))
        server.enqueue(MockResponse().setResponseCode(408).addHeaderLenient("Retry-After", "ünf"))

        val first = send(NetworkMethod.POST("push"))
        val second = send(NetworkMethod.POST("push"))

        assertEquals(SdkError.Http(503), first.error())
        assertEquals(SdkError.Http(408), second.error())
        // Not a number of seconds nor a date: no pause of the server's.
        assertNull((first as HttpResult.Failure).retryAfterMs)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a POST carries the common fields in its JSON body`() {
        reply(200, "{}")

        send(NetworkMethod.POST("push"), JSONObject().put("event", "view").put("segment", "B"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertNull(request.requestUrl?.query)
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(
            setOf("event", "shop_id", "did", "sid", "seance", "segment", "stream"),
            body.keys().asSequence().toSet()
        )
        assertEquals("sid-1", body.getString("seance"))
        // A strict call's own segment wins over the device's.
        assertEquals("B", body.getString("segment"))
    }

    @Test
    fun `the log has the method, the path and the code, never the query, a body or the answer`() {
        SdkLogs.register("shop", LogLevel.DEBUG, "CoreTag")
        reply(200, """{"email":"answer@example.com"}""")
        reply(422, """{"error":"invalid phone +79990004455"}""")

        send(NetworkMethod.GET("profile"), JSONObject().put("email", "get@example.com"))
        send(NetworkMethod.POST("profile/set"), JSONObject().put("phone", "+79990004455"))

        val log = ShadowLog.getLogsForTag("CoreTag").joinToString("\n") { it.msg }
        assertTrue(log, log.contains("[request] 200: GET profile"))
        assertTrue(log, log.contains("[request_failed] 422: POST profile/set {method=POST, path=profile/set, status=422}"))
        listOf("get@example.com", "answer@example.com", "79990004455", "did-1", "sid-1").forEach {
            assertFalse(log, log.contains(it))
        }
    }

    @Test
    fun `a request that cannot be built fails without being sent`() {
        val result = send(client = client(baseUrl = "not a url"))

        assertTrue(result.error() is SdkError.InvalidArgument)
        assertEquals(0, server.requestCount)
    }
}
