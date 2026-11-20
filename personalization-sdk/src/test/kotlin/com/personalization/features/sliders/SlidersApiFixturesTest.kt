package com.personalization.features.sliders

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.sliders.Slider
import com.personalization.api.models.sliders.SliderBanner
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argStrings
import com.personalization.core.internal.argString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * `sliders.get` against the contract's fixtures (`contract/0/sliders.json`). The banner events of the same
 * file are `tracking`'s: [com.personalization.features.tracking.impl.BannerEventsTest].
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SlidersApiFixturesTest {

    private val fixtures = NamespaceFixtures(SHOP)
    private lateinit var sliders: SlidersApiImpl

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        check(method == "get") { "unknown method $method" }
        sliders.get(args.getString("code"), args.argStrings("tags"), args.argString("locationId"))
    }

    @Before
    fun setUp() {
        fixtures.start(ApplicationProvider.getApplicationContext())
        sliders = SlidersApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    @Test
    fun `get makes the contract's request, tags joined by commas`() {
        assertEquals(2, fixtures.checkRequests(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `an empty code, an empty tag or one with a comma is invalidArgument without a request`() {
        assertEquals(3, fixtures.checkErrors(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `get reads every answer of the contract into its slider or its error`() {
        val json = NamespaceFixtures.Json { value ->
            (value as Slider).let { slider ->
                mapOf(
                    "code" to slider.code, "width" to slider.width, "height" to slider.height,
                    "banners" to slider.banners.map { banner ->
                        mapOf(
                            "id" to banner.id, "code" to banner.code, "imageUrl" to banner.imageUrl, "url" to banner.url,
                            "deeplinkIos" to banner.deeplinkIos, "deeplinkAndroid" to banner.deeplinkAndroid, "html" to banner.html,
                        )
                    },
                )
            }
        }

        assertTrue(fixtures.checkAnswers(FILE, invoke, NAMESPACE, json) >= 8)
    }

    @Test
    fun `an empty list of tags is not sent`() {
        fixtures.server.enqueue(MockResponse().setBody("{}"))

        runCatching { runBlocking { withTimeout(10_000) { sliders.get("homepage_mobile", tags = emptyList()) } } }

        val url = fixtures.server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals(setOf("code"), url.queryParameterNames - NamespaceFixtures.COMMON_FIELDS)
    }

    @Test
    fun `a slider without its own code and size takes the code it was asked for and 0, a banner without an id is left out`() {
        val body = JSONObject("""{"status":"success","payload":{"slides":[{"image_url":"a.jpg"},{"banner_id":5,"url":""}]}}""")

        val slider = SlidersApiImpl.readSlider(body, asked = "asked_code")

        assertEquals(
            Slider("asked_code", 0, 0, listOf(SliderBanner("5", "asked_code", null, null, null, null, null))),
            slider,
        )
    }

    @Test
    fun `a payload without slides is a slider without banners, and without properties code the asked code`() {
        listOf(
            """{"status":"success","payload":{"properties":{"width":1000,"height":160}}}""",
            """{"payload":{"properties":{"code":"","width":1000,"height":160},"slides":null}}""",
        ).forEach { body ->
            assertEquals(body, Slider("asked_code", 1000, 160, emptyList()), SlidersApiImpl.readSlider(JSONObject(body), asked = "asked_code"))
        }
        assertEquals(Slider("asked_code", 0, 0, emptyList()), SlidersApiImpl.readSlider(JSONObject("""{"payload":{}}"""), "asked_code"))
    }

    @Test
    fun `through get a payload without slides is a success with no banners`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"status":"success","payload":{"properties":{"code":"own"}}}"""))

        val slider = runBlocking { withTimeout(10_000) { sliders.get("homepage_mobile") } }

        assertEquals(Slider("own", 0, 0, emptyList()), slider)
    }

    @Test
    fun `an answer of status error or without payload stays decoding`() {
        assertEquals(null, SlidersApiImpl.readSlider(JSONObject("""{"status":"error","payload":{"message":"x"}}"""), "c"))
        assertEquals(null, SlidersApiImpl.readSlider(JSONObject("""{"status":"success"}"""), "c"))
    }

    private companion object {
        const val SHOP = "sliders-shop"
        const val FILE = "sliders"
        const val NAMESPACE = "sliders"
    }
}
