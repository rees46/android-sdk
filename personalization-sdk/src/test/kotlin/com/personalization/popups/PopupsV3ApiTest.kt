package com.personalization.popups

import android.os.Looper
import com.personalization.Fixtures
import com.personalization.RegisterManager
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.ui.widgets.StoriesApiV3
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.net.URI
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Запросы попапов (`requests.json`) и ответы с ошибками (`error-responses.json`) через HTTP-шов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupsV3ApiTest {

    private val requestsFixture = JSONObject(Fixtures.read("popups-v3/requests.json"))
    private val errors = JSONObject(Fixtures.read("popups-v3/error-responses.json"))
    private val context = requestsFixture.getJSONObject("_fixture").getJSONObject("context")
    private val sdk = mockk<SDK>(relaxed = true)
    private val requests = mutableListOf<Triple<String, String, String?>>()
    private val replies = ArrayDeque<StoriesApiV3.Response>()
    private val originalHttp = StoriesApiV3.http
    private lateinit var api: PopupsV3Api

    @Before
    fun setUp() {
        every { sdk.getDid() } returns context.getString("did")
        every { sdk.getSid() } returns context.getString("sid")
        every { sdk.getUserSettingsValueUseCase.getShopId() } returns context.getString("shop_id")
        StoriesApiV3.runner = { it.run() }
        StoriesApiV3.http = StoriesApiV3.Http { method, url, body ->
            requests += Triple(method, url, body)
            replies.removeFirstOrNull() ?: StoriesApiV3.Response(200, """{"status":"success"}""")
        }
        PopupsV3Api.retryDelayMs = 0
        api = PopupsV3Api(sdk) { context.getString("app_host") }
    }

    @After
    fun tearDown() {
        StoriesApiV3.http = originalHttp
        StoriesApiV3.runner = { Thread(it).start() }
        PopupsV3Api.retryDelayMs = 1_000
    }

    private fun request(name: String): JSONObject = requestsFixture.getJSONObject(name)

    /** URL без учёта порядка параметров. */
    private fun assertUrl(expected: String, actual: String) {
        val want = URI(expected)
        val got = URI(actual)
        assertEquals("${want.scheme}://${want.host}${want.path}", "${got.scheme}://${got.host}${got.path}")
        assertEquals(want.query.orEmpty().split("&").toSet(), got.query.orEmpty().split("&").toSet())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `list with and without test mode and debug`() {
        replies += StoriesApiV3.Response(200, Fixtures.read("popups-v3/list-response.json"))
        var result: PopupsV3ListResult? = null
        api.list(testMode = false, debug = false) { result = it }
        api.list(testMode = true, debug = true) { }
        idle()
        assertEquals("GET", requests[0].first)
        assertUrl(request("list").getString("url"), requests[0].second)
        assertUrl(request("list_testmode_debug").getString("url"), requests[1].second)
        val list = (result as PopupsV3ListResult.Success).list
        assertEquals(6, list.popups.size)
    }

    @Test
    fun `client errors of the list are cached as empty, server ones are failures`() {
        listOf("list_400" to 400, "list_402" to 402, "list_403" to 403, "list_404" to 404).forEach { (name, code) ->
            replies += StoriesApiV3.Response(code, errors.getJSONObject(name).toString())
            var result: PopupsV3ListResult? = null
            api.list(false, false) { result = it }
            idle()
            val error = result as PopupsV3ListResult.ClientError
            assertEquals(code, error.code)
            assertEquals(errors.getJSONObject(name).getString("message"), error.message)
        }
        replies += StoriesApiV3.Response(503, "<html>down</html>")
        var result: PopupsV3ListResult? = null
        api.list(false, false) { result = it }
        idle()
        assertTrue(result is PopupsV3ListResult.Failure)
    }

    @Test
    fun `one popup for a manual show, a 404 is no popup`() {
        replies += StoriesApiV3.Response(200, Fixtures.read("popups-v3/popup-response.json"))
        replies += StoriesApiV3.Response(404, errors.getJSONObject("one_404").toString())
        var popup: PopupV3? = null
        var missing: PopupV3? = PopupV3.parse(JSONObject("""{"id":1,"layout":"modal","trigger_type":"manual"}""")).let { (it as PopupV3.Parsed.Ok).popup }
        api.one(516, testMode = false) { popup = it }
        api.one(516, testMode = true) { missing = it }
        idle()
        assertUrl(request("one").getString("url"), requests[0].second)
        assertUrl(request("one_testmode").getString("url"), requests[1].second)
        assertEquals(516, popup!!.id)
        assertEquals(PopupV3.Layout.FULLSCREEN, popup!!.layout)
        assertNull(missing)
    }

    @Test
    fun `events go as json, channel only with subscribe`() {
        api.event(512, "show")
        api.event(512, "subscribe", "email")
        assertEquals("POST", requests[0].first)
        assertEquals(request("event_show").getString("url"), requests[0].second)
        assertEquals(request("event_show").getJSONObject("json").toString(), requests[0].third)
        assertEquals(request("event_subscribe").getJSONObject("json").toString(), requests[1].third)
    }

    @Test
    fun `an event is retried once after a network or server failure, never after a 4xx`() {
        replies += StoriesApiV3.Response(503, "")
        api.event(512, "click")
        assertEquals(2, requests.size)
        requests.clear()
        replies += StoriesApiV3.Response(400, errors.getJSONObject("events_400_channel").toString())
        api.event(512, "click")
        assertEquals(1, requests.size)
    }

    @Test
    fun `429 pauses events for a minute`() {
        replies += StoriesApiV3.Response(429, errors.getJSONObject("events_429").toString())
        api.event(512, "show")
        api.event(512, "close")
        assertEquals(1, requests.size)
    }

    @Test
    fun `the promo code goes through the sdk network to promo_codes fetch`() {
        val network = mockk<com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase>(relaxed = true)
        every { sdk.sendNetworkMethodUseCase } returns network
        val params = slot<JSONObject>()
        val listener = slot<OnApiCallbackListener>()
        every { network.getAsync(PopupsV3Api.PROMO_METHOD, capture(params), capture(listener)) } answers {
            listener.captured.onSuccess(errors.getJSONObject("promo_ok"))
        }
        var code: String? = null
        api.promoCode("7") { code = it }
        idle()
        assertEquals("7", params.captured.getString("id"))
        assertEquals("ABC123", code)
        val promo = URI(request("promo").getString("url"))
        assertEquals("/" + PopupsV3Api.PROMO_METHOD, promo.path)

        every { network.getAsync(any(), any(), capture(listener)) } answers {
            listener.captured.onError(404, errors.getJSONObject("promo_404").toString())
        }
        code = "stale"
        api.promoCode("7") { code = it }
        idle()
        assertNull(code)
    }

    @Test
    fun `init asks for test popups in test mode and reports the popups_v3 flag after the session is set`() {
        val update = mockk<com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase>(relaxed = true)
        val settings = mockk<com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase>(relaxed = true)
        val network = mockk<com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase>(relaxed = true)
        val manager = RegisterManager(update, settings, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        every { settings.getDid() } returns ""
        val params = slot<JSONObject>()
        val listener = slot<OnApiCallbackListener>()
        every { network.get(eq("init"), capture(params), capture(listener)) } answers { }
        val flags = mutableListOf<Boolean>()
        manager.extraInitParams = { mapOf("testmode" to "true") }
        manager.onInitFinished = { flag ->
            verify { update.updateSid("SRV_SEANCE") }
            flags += flag
        }
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        manager.initialize(app, app.contentResolver, autoSendPushToken = false)
        val init = URI(request("init_testmode").getString("url"))
        assertTrue(init.query.split("&").contains("testmode=true"))
        assertEquals("true", params.captured.getString("testmode"))

        listener.captured.onSuccess(JSONObject("""{"did":"D","seance":"SRV_SEANCE","popups_v3":true}"""))
        assertEquals(listOf(true), flags)
    }
}
