package com.personalization

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests check the SDK's contract with the API on a mock server, never the work of a real one:
 * the test task routes every connection to a host other than this machine through a SOCKS proxy that
 * does not exist (build.gradle, `testOptions`), so a request that would reach a server fails at once.
 * These tests pin that guard: if it goes, they fail.
 */
class NoLiveServerGuardTest {

    @Test
    fun `a real host goes through the dead proxy, this machine and the exempt hosts do not`() {
        val remote = ProxySelector.getDefault().select(URI("https://api.rees46.ru/init")).single()
        assertEquals(Proxy.Type.SOCKS, remote.type())
        val address = remote.address() as InetSocketAddress
        assertEquals("127.0.0.1" to 9, address.hostString to address.port)

        listOf(
            "http://localhost:8080/init",
            "http://127.0.0.1:8080/init",
            // Tests map hosts of the reserved .test domain to this machine with their own DNS.
            "https://sdk.test/init",
            // Robolectric fetches its Android jars from Maven Central on a fresh machine, and Maven
            // Central sends a jar to a mirror of its own.
            "https://repo1.maven.org/maven2/",
            "https://gcp.mirrors.maven.org/maven2/",
            "socket://gcp.mirrors.maven.org:443",
        ).forEach { url ->
            assertEquals(url, listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI(url)))
        }
    }

    @Test
    fun `OkHttp cannot reach a real host`() {
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
        try {
            client.newCall(Request.Builder().url("https://api.rees46.ru/init").build()).execute().close()
            fail("a unit test reached a real server")
        } catch (expected: java.io.IOException) {
            // The dead proxy refuses the connection.
        }
    }

    @Test
    fun `HttpURLConnection cannot reach a real host`() {
        val connection = URL("https://api.rees46.ru/init").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        try {
            connection.responseCode
            fail("a unit test reached a real server")
        } catch (expected: java.io.IOException) {
            // The dead proxy refuses the connection.
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `a mock server on this machine still answers`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.start()
            val response = OkHttpClient().newCall(Request.Builder().url(server.url("/init")).build()).execute()
            response.use { assertTrue(it.isSuccessful) }
        }
    }
}
