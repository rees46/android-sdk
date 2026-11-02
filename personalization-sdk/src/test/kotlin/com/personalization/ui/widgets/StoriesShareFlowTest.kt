package com.personalization.ui.widgets

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.stories.StoriesManager
import com.personalization.ui.components.PersonalizationStoryNavigation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** handleLink, the API v3 feed and events, the "Share" button of a slide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesShareFlowTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val manager = mockk<StoriesManager>(relaxed = true)
    private val sdk = mockk<SDK>(relaxed = true)
    private val requests = mutableListOf<Triple<String, String, String?>>()
    private var answer: (String) -> StoriesApiV3.Response = { StoriesApiV3.Response(200, CAMPAIGN) }

    @Before
    fun setUp() {
        every { sdk.storiesManager } returns manager
        every { sdk.getDid() } returns "did1"
        every { sdk.getUserSettingsValueUseCase.getShopId() } returns "shop1"
        StoriesApiV3.runner = { it.run() }
        StoriesApiV3.http = StoriesApiV3.Http { method, url, body ->
            requests += Triple(method, url, body)
            if (method == "GET") answer(url) else StoriesApiV3.Response(200, """{"status":"success"}""")
        }
        PersonalizationStories.apiV3Domain = "app.rees46.ru"
    }

    @After
    fun tearDown() {
        PersonalizationStories.apiV3Domain = null
        StoriesApiV3.runner = { Thread(it).start() }
        ShadowDialog.getShownDialogs().forEach { it.dismiss() }
        idle()
    }

    @Test
    fun `a foreign link or no api v3 host is not handled`() {
        assertFalse(handle("https://shop.test/?utm_source=tg"))
        assertFalse(handle("https://shop.test/?r46_stories=main&r46_story=abc"))
        PersonalizationStories.apiV3Domain = null
        assertFalse(handle(LINK))
        PersonalizationStories.apiV3Domain = " "
        assertFalse(handle(LINK))
        assertTrue(requests.isEmpty())
        assertEquals(0, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `the link opens its campaign at the slide of the link and reports shared_open once`() {
        assertTrue(handle(LINK))

        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/main/story/40?shop_id=shop1&did=did1&slide_id=37", requests[0].second)
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        assertEquals(1, navigation(viewer).progress.current)
        assertEquals(listOf("shared_open:37", "view:37"), events().sorted())
        verify { manager.setTrackingSourceUseCase(StoriesManager.STORIES_SOURCE_TYPE, "main") }
        verify(exactly = 0) { manager.trackStory(any(), any(), any<Int>(), any()) }
    }

    @Test
    fun `the same link while its viewer is open is ignored, another link replaces the viewer`() {
        handle(LINK)
        val first = ShadowDialog.getLatestDialog()
        assertTrue(handle(LINK))
        assertEquals(1, requests.count { it.first == "GET" })
        assertTrue(first.isShowing)

        assertTrue(handle("https://shop.test/?r46_stories=main&r46_story=40"))
        assertFalse(first.isShowing)
        assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing })

        ShadowDialog.getLatestDialog().dismiss()
        idle()
        assertTrue(handle("https://shop.test/?r46_stories=main&r46_story=40"))
        assertEquals(3, requests.count { it.first == "GET" })
    }

    @Test
    fun `a missing campaign reports the error, shows nothing and sends no events`() {
        answer = { StoriesApiV3.Response(404, """{"status":"fail","message":"Resource not found"}""") }
        var error = 0
        assertTrue(handle(LINK, onError = { code -> error = code }))

        assertEquals(404, error)
        assertEquals(0, ShadowDialog.getShownDialogs().size)
        assertTrue(events().isEmpty())
    }

    @Test
    fun `the feed goes to api v3 and its product slides are tracked too`() {
        answer = { StoriesApiV3.Response(200, PRODUCTS) }
        PersonalizationStories.show(context = activity, code = "main", sdk = sdk, imageLoader = { _, _ -> })
        idle()

        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/main?shop_id=shop1&did=did1", requests[0].second)
        verify(exactly = 0) { manager.requestStories(any(), any<OnApiCallbackListener>()) }
        assertEquals(listOf("view:39_0"), events())
    }

    @Test
    fun `share sends the event at once and opens the system sheet with the link`() {
        handle(LINK)
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        val share = navigation(viewer).shareButton
        assertEquals(View.VISIBLE, share.visibility)
        assertEquals("Share", share.contentDescription)

        share.performClick()
        idle()

        assertTrue(events().contains("share:37"))
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://shop.test/?r46_slide=37", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `the value of the widget overrides the global one`() {
        PersonalizationStories.apiV3Domain = "global.test"
        widget(apiV3Domain = "own.test")

        assertEquals("https://own.test/api/v3/storefront/stories/main?shop_id=shop1&did=did1", requests.single().second)
        verify(exactly = 0) { manager.requestStories(any(), any<OnApiCallbackListener>()) }
    }

    @Test
    fun `a widget without its value stays on the old api next to a v3 widget`() {
        PersonalizationStories.apiV3Domain = null
        every { manager.requestStories(any(), any()) } answers {
            secondArg<OnApiCallbackListener>().onSuccess(JSONObject(CAMPAIGN).getJSONObject("payload"))
        }
        widget(apiV3Domain = "app.test")
        val old = widget(apiV3Domain = null)

        assertEquals(1, requests.count { it.first == "GET" })
        verify(exactly = 1) { manager.requestStories("main", any()) }

        old.open(0)
        idle()
        assertEquals(View.GONE, navigation(ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).shareButton.visibility)
        verify { manager.trackStory("view", "main", 40, "37") }
        assertTrue(events().isEmpty())
    }

    @Test
    fun `show and handleLink take their own host without the global one`() {
        PersonalizationStories.apiV3Domain = null
        assertFalse(handle(LINK))

        assertTrue(PersonalizationStories.handleLink(activity, LINK, sdk = sdk, imageLoader = { _, _ -> }, apiV3Domain = "own.test"))
        idle()
        assertTrue(requests.first().second.startsWith("https://own.test/api/v3/storefront/stories/main/story/40?"))
        assertTrue(events().contains("shared_open:37"))
        ShadowDialog.getLatestDialog().dismiss()
        idle()

        requests.clear()
        PersonalizationStories.show(context = activity, code = "main", sdk = sdk, imageLoader = { _, _ -> }, apiV3Domain = "own.test")
        idle()
        assertTrue(requests.first().second.startsWith("https://own.test/api/v3/storefront/stories/main?"))
    }

    @Test
    fun `the share sheet gets the transformed link, the share event stays the same`() {
        val transform: (String) -> String = { it.replaceFirst("https://", "demo://") }
        assertTrue(PersonalizationStories.handleLink(activity, LINK, sdk = sdk, imageLoader = { _, _ -> }, shareUrlTransform = transform))
        idle()
        navigation(ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).shareButton.performClick()
        idle()

        assertTrue(events().contains("share:37"))
        @Suppress("DEPRECATION")
        val send = shadowOf(activity).nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("demo://shop.test/?r46_slide=37", send.getStringExtra(Intent.EXTRA_TEXT))
        ShadowDialog.getLatestDialog().dismiss()
        idle()

        // The widget hands its transform to the viewer it opens.
        val widget = widget(apiV3Domain = null).apply { shareUrlTransform = transform }
        widget.open(0)
        idle()
        assertTrue((ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).shareUrlTransform === transform)
    }

    private fun widget(apiV3Domain: String?) = PersonalizationStories(activity).apply {
        code = "main"
        this.apiV3Domain = apiV3Domain
        imageLoader = { _, _ -> }
        attach(sdk)
        idle()
    }

    private fun handle(url: String, onError: (Int) -> Unit = {}): Boolean =
        PersonalizationStories.handleLink(activity, url, sdk = sdk, onError = { code, _ -> onError(code) }, imageLoader = { _, _ -> })
            .also { idle() }

    private fun events(): List<String> = requests.filter { it.first == "POST" }.map {
        val body = JSONObject(it.third!!)
        assertEquals("shop1", body.getString("shop_id"))
        assertEquals("did1", body.getString("did"))
        assertTrue(it.second.endsWith("/api/v3/storefront/stories/main/events"))
        body.getString("event") + ":" + body.getString("slide_id")
    }

    private fun navigation(viewer: PersonalizationStoriesViewer): PersonalizationStoryNavigation {
        val pager = find(viewer.window!!.decorView, ViewPager2::class.java)!!
        val page = (pager.getChildAt(0) as ViewGroup).getChildAt(0)
        return find(page, PersonalizationStoryNavigation::class.java)!!
    }

    private fun <T : View> find(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) find(view.getChildAt(index), type)?.let { return it }
        return null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val LINK = "https://demo.test/?r46_stories=main&r46_story=40&r46_slide=37"

        const val CAMPAIGN = """{"status":"success","payload":{"id":1,"settings":{},"stories":[{"id":40,"name":"Media",
            "share_url":"https://shop.test/?r46_story=40","shareable":true,"start_position":1,"slides":[
            {"id":36,"type":"image","background":"https://cdn.test/a.jpg","share_url":"https://shop.test/?r46_slide=36","elements":[]},
            {"id":37,"type":"image","background":"https://cdn.test/b.jpg","share_url":"https://shop.test/?r46_slide=37","elements":[]}]}]}}"""

        const val PRODUCTS = """{"status":"success","payload":{"id":1,"stories":[{"id":39,"name":"Products",
            "share_url":"https://shop.test/?r46_story=39","slides":[{"id":"39_0","type":"","background":null,"preview":null,
            "elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]}]}]}}"""
    }
}
