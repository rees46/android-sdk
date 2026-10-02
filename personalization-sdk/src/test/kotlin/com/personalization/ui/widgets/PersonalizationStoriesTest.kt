package com.personalization.ui.widgets

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.stories.StoriesManager
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** The widget's programmatic control (open, open by id, pause, resume) and show() without the bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationStoriesTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private lateinit var sdk: SDK
    private var served = listOf(11, 12, 13)

    @Before
    fun setUp() {
        val manager = mockk<StoriesManager>(relaxed = true)
        every { manager.requestStories(any(), any()) } answers {
            secondArg<OnApiCallbackListener>().onSuccess(block(served))
        }
        sdk = mockk(relaxed = true)
        every { sdk.storiesManager } returns manager
    }

    @Test
    fun `open while the viewer is open pages inside that viewer`() {
        val widget = widget()
        widget.open(0)
        idle()
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        assertEquals(0, pager(viewer).currentItem)

        widget.open(2)
        idle()
        assertSame(viewer, ShadowDialog.getLatestDialog())
        assertTrue(viewer.isShowing)
        assertEquals(1, ShadowDialog.getShownDialogs().size)
        assertEquals(2, pager(viewer).currentItem)

        widget.openStory(12)
        idle()
        assertEquals(1, pager(viewer).currentItem)
        assertEquals(1, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `pause and resume without an open viewer do nothing`() {
        val widget = widget()
        widget.pause()
        widget.resume()
        idle()
        assertEquals(0, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `a story opens by its id as a string, a non-number opens nothing`() {
        val widget = widget()
        widget.openStory("x")
        idle()
        assertEquals(0, ShadowDialog.getShownDialogs().size)

        widget.openStory(" 13 ")
        idle()
        assertEquals(2, pager(ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).currentItem)
    }

    @Test
    fun `the compose controller keeps an open call until the widget shows`() {
        val controller = PersonalizationStoriesController()
        controller.openStory("12")
        controller.pause()

        controller.view = widget()
        idle()
        assertEquals(1, pager(ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).currentItem)
    }

    @Test
    fun `show reports the viewer closing`() {
        var closed = 0
        var errors = 0
        show(startStoryId = 12, onError = { errors++ }, onClose = { closed++ })
        val viewer = ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer
        assertEquals(1, pager(viewer).currentItem)
        assertEquals(0, closed)

        viewer.dismiss()
        idle()
        assertEquals(1, closed)
        assertEquals(0, errors)
    }

    @Test
    fun `show with an unknown start story opens the first one`() {
        show(startStoryId = 99)
        assertEquals(0, pager(ShadowDialog.getLatestDialog() as PersonalizationStoriesViewer).currentItem)
    }

    @Test
    fun `show of an empty block reports the error and opens nothing`() {
        served = emptyList()
        var closed = 0
        var errors = 0
        show(onError = { errors++ }, onClose = { closed++ })

        assertEquals(1, errors)
        assertEquals(0, closed)
        assertEquals(0, ShadowDialog.getShownDialogs().size)
    }

    private fun show(startStoryId: Int? = null, onError: () -> Unit = {}, onClose: () -> Unit = {}) {
        PersonalizationStories.show(
            context = activity,
            code = "BLOCK",
            sdk = sdk,
            startStoryId = startStoryId,
            onError = { _, _ -> onError() },
            onClose = onClose,
            imageLoader = { _, _ -> }
        )
        idle()
    }

    /** Photo slides whose loader never answers: the slide clock stands, nothing pages by itself. */
    private fun widget() = PersonalizationStories(activity).apply {
        code = "BLOCK"
        imageLoader = { _, _ -> }
        attach(sdk)
    }

    private fun pager(viewer: PersonalizationStoriesViewer): ViewPager2 =
        find(viewer.window!!.decorView, ViewPager2::class.java)!!

    private fun <T : View> find(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) find(view.getChildAt(index), type)?.let { return it }
        return null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun block(ids: List<Int>) = JSONObject().put(
        "stories",
        JSONArray().apply {
            ids.forEach { id ->
                put(
                    JSONObject()
                        .put("id", id)
                        .put("name", "Story $id")
                        .put(
                            "slides",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", "${id}1")
                                    .put("type", "image")
                                    .put("background", "https://example.com/$id.jpg")
                                    .put("elements", JSONArray())
                            )
                        )
                )
            }
        }
    )
}
