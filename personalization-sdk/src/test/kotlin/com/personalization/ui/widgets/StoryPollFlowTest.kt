package com.personalization.ui.widgets

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.personalization.Fixtures
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.stories.StoriesManager
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Polls in the viewer: API v3 only, the transport of the widget, the POLL pause of the slide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoryPollFlowTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val manager = mockk<StoriesManager>(relaxed = true)
    private val sdk = mockk<SDK>(relaxed = true)
    private val requests = mutableListOf<Triple<String, String, String?>>()
    private val originalHttp = StoriesApiV3.http
    private val feed = Fixtures.read("stories_polls_feed.json")
    private val transport = FakePollsTransport()

    @Before
    fun setUp() {
        StoryPollStore.clear()
        every { sdk.storiesManager } returns manager
        every { sdk.getDid() } returns "did1"
        every { sdk.getSid() } returns ""
        every { sdk.getUserSettingsValueUseCase.getShopId() } returns "shop1"
        StoriesApiV3.runner = { it.run() }
        StoriesApiV3.http = StoriesApiV3.Http { method, url, body ->
            requests += Triple(method, url, body)
            if (method == "GET") StoriesApiV3.Response(200, feed) else StoriesApiV3.Response(200, """{"status":"success"}""")
        }
    }

    @After
    fun tearDown() {
        StoriesApiV3.http = originalHttp
        StoriesApiV3.runner = { Thread(it).start() }
        ShadowDialog.getShownDialogs().forEach { it.dismiss() }
        idle()
        StoryPollStore.clear()
    }

    @Test
    fun `the v3 feed draws polls and answers go to the polls api of its host`() {
        // Own shop: the seen slides of other tests do not move the start.
        PersonalizationStories.show(context = activity, code = "main", sdk = sdk, shopId = "polls-v3", imageLoader = { _, _ -> }, apiV3Domain = "app.test")
        idle()
        val card = card(viewer())!!
        card.all().filterIsInstance<PollOptionRow>()[0].performClick()
        idle()

        val post = requests.single { it.second.contains("/polls/") }
        assertEquals("https://app.test/api/v3/polls/mini_polls/demo_single/answers", post.second)
        assertEquals(
            """{"shop_id":"shop1","did":"did1","sid":"","option_ids":[1],"story_id":9001,"slide_id":"900"}""",
            post.third
        )
        assertEquals(listOf(1), StoryPollStore.get("shop1", "mini_poll", "demo_single")!!.answer!!.optionIds)
    }

    @Test
    fun `the old api draws no polls, with a transport neither`() {
        every { manager.requestStories(any(), any()) } answers {
            secondArg<OnApiCallbackListener>().onSuccess(StoriesApiV3.withoutNulls(JSONObject(feed).getJSONObject("payload")) as JSONObject)
        }
        val widget = PersonalizationStories(activity).apply {
            code = "main"
            shopId = "polls-old"
            imageLoader = { _, _ -> }
            attach(sdk)
        }
        idle()
        widget.open(0)
        idle()
        assertNull(card(viewer()))
        viewer().dismiss()
        idle()

        widget.pollsTransport = transport
        widget.open(0)
        idle()
        assertNull(viewer().pollsTransport)
        assertNull(card(viewer()))
    }

    @Test
    fun `show and handleLink hand the transport and poll texts to the viewer`() {
        PersonalizationStories.show(
            context = activity, code = "main", sdk = sdk, imageLoader = { _, _ -> }, apiV3Domain = "app.test",
            pollReanswerText = "Again", pollErrorText = "Oops", pollSubmitText = "Go", pollsTransport = transport
        )
        idle()
        val shown = viewer()
        assertTrue(shown.pollsTransport === transport)
        assertEquals(listOf("Again", "Oops", "Go"), listOf(shown.pollReanswerText, shown.pollErrorText, shown.pollSubmitText).map { it.toString() })
        shown.dismiss()
        idle()

        assertTrue(
            PersonalizationStories.handleLink(
                activity, "https://shop.test/?r46_stories=main&r46_story=9001", sdk = sdk, imageLoader = { _, _ -> },
                apiV3Domain = "app.test", pollsTransport = transport
            )
        )
        idle()
        assertTrue(viewer().pollsTransport === transport)
        assertEquals("Answer again", viewer().pollReanswerText.toString())
    }

    @Test
    fun `an empty poll text of the host falls back to the default, a blank one does not`() {
        val viewer = PersonalizationStoriesViewer(activity, StoriesApiV3.parse(feed)).apply {
            imageLoader = { _, _ -> }
            pollsTransport = transport
            startSlide = 1
            pollErrorText = ""
            pollSubmitText = " "
        }
        viewer.show()
        idle()
        val texts = card(viewer)!!.controller.texts
        assertEquals("Failed to send the answer. Please try again", texts.error.toString())
        assertEquals(" ", texts.submit.toString())
        assertEquals("Answer again", texts.reanswer.toString())
    }

    @Test
    fun `a card that sends holds the slide until the slide changes, a late answer does not hold it again`() {
        val stories = StoriesApiV3.parse(feed)
        val viewer = PersonalizationStoriesViewer(activity, stories).apply {
            imageLoader = { _, _ -> }
            pollsTransport = transport
            startSlide = 0
        }
        viewer.show()
        idle()
        val state = state(viewer)
        val first = card(viewer)!!
        first.all().filterIsInstance<PollOptionRow>()[1].performClick()
        assertTrue(state.isPaused(StoriesViewerState.Pause.POLL))

        // A tap on the right third of the slide pages forward.
        val slide = first.ancestor<StorySlideView>()
        val now = SystemClock.uptimeMillis()
        val x = slide.width - 5f
        val y = slide.height * 0.15f
        slide.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        slide.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()
        assertEquals(1, state.slideIndex)
        assertFalse(state.isPaused(StoriesViewerState.Pause.POLL))

        transport.succeed(StoriesPollAnswer(optionIds = listOf(2)), null)
        idle()
        assertFalse(state.isPaused(StoriesViewerState.Pause.POLL))
        assertEquals(listOf(2), StoryPollStore.get("", "mini_poll", "demo_single")!!.answer!!.optionIds)
    }

    @Test
    fun `the poll pause of the state goes away with the slide, not with a restart`() {
        val seen = object : SeenSlides {
            override fun seen(storyId: Int): Set<String> = emptySet()
            override fun markSeen(storyId: Int, slideId: String) = Unit
        }
        val slides = listOf(StoriesViewerState.SlideShape("1", 1000), StoriesViewerState.SlideShape("2", 1000))
        val state = StoriesViewerState(listOf(StoriesViewerState.StoryShape(1, slides), StoriesViewerState.StoryShape(2, slides)), seen)
        state.enter()
        state.mediaReady()
        assertTrue(state.isRunning)

        assertTrue(state.setPaused(StoriesViewerState.Pause.POLL, true))
        assertFalse(state.isRunning)
        assertFalse(state.playerShouldPlay)
        state.previous()
        assertTrue("restart keeps the card", state.isPaused(StoriesViewerState.Pause.POLL))
        state.next()
        assertFalse(state.isPaused(StoriesViewerState.Pause.POLL))

        state.setPaused(StoriesViewerState.Pause.POLL, true)
        state.selectStory(1)
        assertFalse(state.isPaused(StoriesViewerState.Pause.POLL))
    }

    private fun viewer(): PersonalizationStoriesViewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer

    private fun card(viewer: PersonalizationStoriesViewer): StoryPollView? =
        viewer.window!!.decorView.all().filterIsInstance<StoryPollView>().firstOrNull { it.isShown }

    private fun state(viewer: PersonalizationStoriesViewer): StoriesViewerState =
        PersonalizationStoriesViewer::class.java.getDeclaredField("state").apply { isAccessible = true }.get(viewer) as StoriesViewerState

    private inline fun <reified T : View> View.ancestor(): T {
        var current = parent
        while (current !is T) current = (current as View).parent
        return current
    }

    private fun View.all(): List<View> =
        if (this !is ViewGroup) listOf(this) else listOf(this) + (0 until childCount).flatMap { getChildAt(it).all() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
