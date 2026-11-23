package com.personalization.ui.widgets

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.personalization.SDK
import com.personalization.core.BridgeApi
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
@OptIn(BridgeApi::class)
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
        every { sdk.sessionId } returns "sid1"
        every { sdk.segments.getAbGroup() } returns "A"
        every { sdk.requestStream } returns "android"
        every { sdk.popupsV3 } returns null
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
        StoriesSound.chosenMuted = true
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

        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/main/story/40?$IDENTITY&slide_id=37", requests[0].second)
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        assertEquals(1, navigation(viewer).progress.current)
        assertEquals(listOf("shared_open:37", "view:37"), events().sorted())
        verify { manager.setTrackingSourceUseCase(StoriesManager.STORIES_SOURCE_TYPE, "main") }
        verify(exactly = 0) { manager.trackStory(any(), any(), any<Int>(), any()) }
    }

    @Test
    fun `the slide of the link wins over start_position, product slides too, an unknown one falls back`() {
        fun opened(url: String): Pair<Int, List<String>> {
            requests.clear()
            assertTrue(handle(url))
            val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
            val result = navigation(viewer).progress.current to events().filter { it.startsWith("shared_open:") }
            viewer.dismiss()
            idle()
            return result
        }

        assertEquals(0 to listOf("shared_open:36"), opened("https://demo.test/?r46_stories=main&r46_story=40&r46_slide=36"))
        assertEquals(1 to listOf("shared_open:37"), opened("https://demo.test/?r46_stories=main&r46_story=40&r46_slide=99"))
        assertEquals(1 to listOf("shared_open:37"), opened("https://demo.test/?r46_stories=main&r46_story=40"))

        // Slides of a product campaign are unknown to the server: start_position stays 0. A ready
        // product slide runs its clock here, so the viewer is read right after it opens.
        answer = { StoriesApiV3.Response(200, PRODUCT_CAMPAIGN) }
        assertEquals(1 to listOf("shared_open:39_1"), opened("https://demo.test/?r46_stories=main&r46_story=39&r46_slide=39_1"))
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

        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/main?$IDENTITY", requests[0].second)
        verify(exactly = 0) { manager.requestStories(any(), any<OnApiCallbackListener>()) }
        assertEquals(listOf("view:39_0"), events())
    }

    @Test
    fun `on api v3 back on the first slide of the first story closes the viewer, as the web SDK`() {
        val viewer = PersonalizationStoriesViewer(activity, StoriesApiV3.parse(PRODUCTS)).apply {
            code = "main"
            sdk = this@StoriesShareFlowTest.sdk
            apiV3Domain = "app.rees46.ru"
            imageLoader = { _, _ -> }
            // The slide must not run out while the test taps it.
            pause()
        }
        viewer.show()
        idle()
        tap(viewer, right = false)

        assertFalse(viewer.isShowing)
        assertEquals(listOf("view:39_0"), events())
    }

    @Test
    fun `on the legacy feed back on the first slide starts it again and sends no view again`() {
        val viewer = PersonalizationStoriesViewer(activity, listOf(com.personalization.stories.models.Story(JSONObject(
            """{"id":5,"name":"Legacy","slides":[{"id":"51","type":"image","background":"https://cdn.test/a.jpg","elements":[]},
            {"id":"52","type":"image","background":"https://cdn.test/b.jpg","elements":[]}]}"""
        )))).apply {
            code = "main"
            sdk = this@StoriesShareFlowTest.sdk
            apiV3Domain = null
            imageLoader = { _, _ -> }
            pause()
        }
        viewer.show()
        idle()
        tap(viewer, right = false)

        assertTrue(viewer.isShowing)
        assertEquals(0, navigation(viewer).progress.current)
        verify(exactly = 1) { manager.trackStory("view", "main", 5, "51") }
        assertTrue(events().isEmpty())
    }

    @Test
    fun `on api v3 a story opens at start_position and played to its end is viewed and opens from the start`() {
        val stories = StoriesApiV3.parse(CAMPAIGN)
        val viewer = v3Viewer(stories)
        // start_position 1 of the feed, nothing of the device's seen slides.
        assertEquals(1, navigation(viewer).progress.current)
        tap(viewer, right = true)

        assertFalse(viewer.isShowing)
        assertTrue(stories[0].isViewed)
        assertEquals(0, stories[0].startPosition)
        assertEquals(listOf("view:37"), events())
    }

    @Test
    fun `on api v3 a closed story keeps its position for the session and is not viewed`() {
        val stories = StoriesApiV3.parse(CAMPAIGN).also { it[0].startPosition = 0 }
        val viewer = v3Viewer(stories)
        tap(viewer, right = true)
        viewer.dismiss()
        idle()
        assertFalse(stories[0].isViewed)
        assertEquals(1, stories[0].startPosition)
        assertEquals(1, navigation(v3Viewer(stories)).progress.current)
    }

    @Test
    fun `on api v3 a story with nothing to show stays in the bar, opening it shows the next one and views it`() {
        answer = { StoriesApiV3.Response(200, SKIPPING) }
        val widget = widget(apiV3Domain = "app.rees46.ru")
        assertEquals(listOf(60, 61), widget.stories.map { it.id })
        assertEquals(2, widget.bar.items.size)

        widget.open(0)
        idle()
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        assertTrue(viewer.isShowing)
        // Story 61 opens at its first shown slide; the skipped slide keeps its segment, filled.
        val shown = all(viewer.window!!.decorView).filterIsInstance<PersonalizationStoryNavigation>().single { it.progress.count == 2 }
        assertEquals(1, shown.progress.current)
        assertEquals(listOf("view:611"), events())
        viewer.dismiss()
        idle()
        assertTrue(widget.stories[0].isViewed)
        assertEquals(com.personalization.ui.components.PersonalizationStoryCover.State.VIEWED, widget.bar.items[0].state)
    }

    @Test
    fun `on api v3 a viewer with nothing to show closes at once, viewed, and never paints the screen black`() {
        val stories = StoriesApiV3.parse(
            """{"status":"success","payload":{"id":1,"stories":[{"id":60,"name":"Text","slides":[
            {"id":600,"type":"","elements":[{"type":"text_block","text_input":"Hi"}]}]}]}}"""
        )
        var closed = 0
        val viewer = PersonalizationStoriesViewer(activity, stories).apply {
            code = "main"
            sdk = this@StoriesShareFlowTest.sdk
            apiV3Domain = "app.rees46.ru"
            imageLoader = { _, _ -> }
            setOnDismissListener { closed++ }
        }
        viewer.show()
        assertFalse((viewer.window!!.decorView.background as? android.graphics.drawable.ColorDrawable)?.color == android.graphics.Color.BLACK)
        idle()
        assertFalse(viewer.isShowing)
        assertEquals(1, closed)
        assertTrue(stories[0].isViewed)
        assertTrue(events().isEmpty())
    }

    @Test
    fun `on api v3 a button without a link sends click and opens nothing, a promo code copy sends nothing`() {
        val clicked = mutableListOf<String>()
        val stories = StoriesApiV3.parse(
            """{"status":"success","payload":{"id":1,"stories":[{"id":39,"name":"P","slides":[{"id":"39_0","type":"",
            "elements":[{"type":"button","title":"Go","link":""},{"type":"product","item":{"id":"p1","name":"Sneakers","url":"https://shop.test/p1","price_formatted":"100 ₽"}}]}]}]}}"""
        )
        val viewer = v3Viewer(stories, object : com.personalization.OnClickListener {
            override fun onClick(url: String): Boolean = true.also { clicked += url }
            override fun onClick(product: com.personalization.Product): Boolean = true.also { clicked += product.id }
        })
        val page = (find(viewer.window!!.decorView, ViewPager2::class.java)!!.getChildAt(0) as ViewGroup).getChildAt(0)
        all(page).filterIsInstance<com.personalization.ui.components.PersonalizationButton>().single { it.text == "Go" }.performClick()
        idle()
        assertTrue(viewer.isShowing)
        assertTrue(clicked.isEmpty())
        assertEquals(listOf("view:39_0", "click:39_0"), events())

        val host = PersonalizationStoriesViewer::class.java.getDeclaredField("slideHost").apply { isAccessible = true }.get(viewer) as StorySlideHost
        host.onPromoCopied()
        idle()
        assertEquals(listOf("view:39_0", "click:39_0"), events())
    }

    @Test
    fun `on api v3 the header sends click and opens its link`() {
        val opened = mutableListOf<String>()
        val stories = StoriesApiV3.parse(
            """{"status":"success","payload":{"id":1,"stories":[{"id":40,"name":"M","slides":[{"id":36,"type":"image","background":"https://cdn.test/a.jpg",
            "elements":[{"type":"header","title":"Shop","link":"https://shop.test/new"}]}]}]}}"""
        )
        val viewer = v3Viewer(stories, object : com.personalization.OnClickListener {
            override fun onClick(url: String): Boolean = false.also { opened += url }
            override fun onClick(product: com.personalization.Product): Boolean = false
        })
        val host = PersonalizationStoriesViewer::class.java.getDeclaredField("slideHost").apply { isAccessible = true }.get(viewer) as StorySlideHost
        host.onHeader(stories[0].getSlide(0).getElements().filterIsInstance<com.personalization.stories.models.elements.HeaderElement>().single())
        idle()
        assertEquals(listOf("https://shop.test/new"), opened)
        assertFalse(viewer.isShowing)
        assertEquals(listOf("view:36", "click:36"), events())
    }

    @Test
    fun `on api v3 the mute choice is kept on the device per shop, as localStorage of a site`() {
        StoriesSound.chosenMuted = true
        StoriesSound.save(activity, "shop-a", false)
        StoriesSound.restore(activity, "shop-a")
        assertFalse(StoriesSound.chosenMuted)
        // Another shop has not chosen: it opens without sound, whatever shop A chose.
        StoriesSound.restore(activity, "shop-b")
        assertTrue(StoriesSound.chosenMuted)
        StoriesSound.restore(activity, "shop-a")
        assertFalse(StoriesSound.chosenMuted)
        StoriesSound.save(activity, "shop-a", true)
        StoriesSound.chosenMuted = false
        StoriesSound.restore(activity, "shop-a")
        assertTrue(StoriesSound.chosenMuted)
        // No shop — the choice of the process stays and nothing is kept.
        StoriesSound.chosenMuted = false
        StoriesSound.save(activity, null, true)
        StoriesSound.restore(activity, null)
        assertFalse(StoriesSound.chosenMuted)
    }

    private fun v3Viewer(stories: List<com.personalization.stories.models.Story>, clicks: com.personalization.OnClickListener? = null) =
        PersonalizationStoriesViewer(activity, stories).apply {
            code = "main"
            sdk = this@StoriesShareFlowTest.sdk
            apiV3Domain = "app.rees46.ru"
            imageLoader = { _, _ -> }
            onClickListener = clicks
            // The slide must not run out while the test taps it.
            pause()
            show()
            idle()
        }

    private fun tap(viewer: PersonalizationStoriesViewer, right: Boolean) {
        val pager = find(viewer.window!!.decorView, ViewPager2::class.java)!!
        val touch = find((pager.getChildAt(0) as ViewGroup).getChildAt(0), StoryTouchLayer::class.java)!!
        val x = if (right) touch.width - 5f else 5f
        val now = SystemClock.uptimeMillis()
        touch.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, 5f, 0))
        touch.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, 5f, 0))
        idle()
    }

    @Test
    fun `in the test mode of popups v3 the feed and the events send testmode`() {
        every { sdk.popupsV3 } returns mockk(relaxed = true) { every { testMode } returns true }
        answer = { StoriesApiV3.Response(200, PRODUCTS) }
        PersonalizationStories.show(context = activity, code = "main", sdk = sdk, imageLoader = { _, _ -> })
        idle()

        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/main?$IDENTITY&testmode=true", requests[0].second)
        assertEquals(listOf("view:39_0"), events(testMode = true))
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

        assertEquals("https://own.test/api/v3/storefront/stories/main?$IDENTITY", requests.single().second)
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

    private fun events(testMode: Boolean = false): List<String> = requests.filter { it.first == "POST" }.map {
        val body = JSONObject(it.third!!)
        assertEquals("shop1", body.getString("shop_id"))
        assertEquals("did1", body.getString("did"))
        assertEquals("sid1", body.getString("sid"))
        assertEquals("A", body.getString("segment"))
        assertEquals("android", body.getString("stream"))
        assertEquals(testMode, body.optBoolean("testmode"))
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

    private fun all(view: View): List<View> =
        if (view !is ViewGroup) listOf(view) else listOf(view) + (0 until view.childCount).flatMap { all(view.getChildAt(it)) }

    private companion object {
        const val IDENTITY = "shop_id=shop1&did=did1&sid=sid1&segment=A&stream=android"
        const val LINK = "https://demo.test/?r46_stories=main&r46_story=40&r46_slide=37"

        const val CAMPAIGN = """{"status":"success","payload":{"id":1,"settings":{},"stories":[{"id":40,"name":"Media",
            "share_url":"https://shop.test/?r46_story=40","shareable":true,"start_position":1,"slides":[
            {"id":36,"type":"image","background":"https://cdn.test/a.jpg","share_url":"https://shop.test/?r46_slide=36","elements":[]},
            {"id":37,"type":"image","background":"https://cdn.test/b.jpg","share_url":"https://shop.test/?r46_slide=37","elements":[]}]}]}}"""

        const val PRODUCT_CAMPAIGN = """{"status":"success","payload":{"id":1,"stories":[{"id":39,"name":"Products",
            "share_url":"https://shop.test/?r46_story=39","shareable":true,"start_position":0,"slides":[
            {"id":"39_0","type":"","background":null,"preview":null,
             "elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]},
            {"id":"39_1","type":"","background":null,"preview":null,
             "elements":[{"type":"product","item":{"id":"p2","name":"Boots","price_formatted":"200 ₽"}}]}]}]}}"""

        /** Story 60 has nothing to show; story 61 starts on a skipped slide. */
        const val SKIPPING = """{"status":"success","payload":{"id":1,"stories":[
            {"id":60,"name":"Text","slides":[{"id":600,"type":"","elements":[{"type":"text_block","text_input":"Hi"}]}]},
            {"id":61,"name":"Media","start_position":0,"slides":[
            {"id":610,"type":"","background_color":"#123456","elements":[]},
            {"id":611,"type":"image","background":"https://cdn.test/a.jpg","elements":[]}]}]}}"""

        const val PRODUCTS = """{"status":"success","payload":{"id":1,"stories":[{"id":39,"name":"Products",
            "share_url":"https://shop.test/?r46_story=39","slides":[{"id":"39_0","type":"","background":null,"preview":null,
            "elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]}]}]}}"""
    }
}
