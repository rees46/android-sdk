package com.personalization.ui.widgets

import com.personalization.SDK
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The API v3 client of stories, polls and popups v3 sends and keeps no cookie, as the new core, whatever
 * `CookieHandler` the app installed; its requests are as before otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesApiV3CookiesTest {

    private val server = MockWebServer()
    private val defaultCookies: CookieHandler? = CookieHandler.getDefault()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        CookieHandler.setDefault(defaultCookies)
        server.shutdown()
    }

    @Test
    fun `no cookie goes or stays, with the app's CookieHandler installed and holding one`() {
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookies)
        val url = server.url("/api/v3/storefront/stories/main").toString()
        cookies.put(URI(url), mapOf("Set-Cookie" to listOf("did=APP; Path=/")))
        server.enqueue(MockResponse().setBody("""{"status":"success"}""").addHeader("Set-Cookie", "did=SERVER; Max-Age=31536000; Path=/"))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"status":"error","message":"bad"}"""))

        val first = StoriesApiV3.get(url)
        val second = StoriesApiV3.post(url, JSONObject().put("event", "view"))

        val sent = List(2) { server.takeRequest(5, TimeUnit.SECONDS)!! }
        sent.forEach { assertNull(it.getHeader("Cookie")) }
        // The server's cookie was not kept: the store holds the app's own only.
        assertEquals(listOf("did=APP"), cookies.cookieStore.cookies.map { "${it.name}=${it.value}" })
        assertEquals(200 to """{"status":"success"}""", first.code to first.body)
        assertEquals(422, second.code)
        assertEquals("""{"status":"error","message":"bad"}""", second.body)
        assertEquals("GET", sent[0].method)
        assertEquals("POST", sent[1].method)
        assertEquals("""{"event":"view"}""", sent[1].body.readUtf8())
        assertEquals(SDK.userAgent(), sent[1].getHeader("User-Agent"))
        assertEquals("application/json", sent[0].getHeader("Accept"))
    }
}
