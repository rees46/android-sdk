package com.personalization.ui.widgets

import com.personalization.ui.widgets.StoriesViewerState.Move
import com.personalization.ui.widgets.StoriesViewerState.Pause
import com.personalization.ui.widgets.StoriesViewerState.SlideShape
import com.personalization.ui.widgets.StoriesViewerState.StoryShape
import com.personalization.ui.widgets.StoriesViewerState.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The viewer's navigation, pause and seen logic, without any view. */
class StoriesViewerStateTest {

    private val seen = MemorySeen()

    @Test
    fun `a fresh story opens at the server start position and is not seen until it settles`() {
        val state = state(story(1, slides = 3, startPosition = 1))
        assertEquals(1, state.slideIndex)
        assertTrue(seen.seen(1).isEmpty())

        assertTrue(state.enter())
        assertEquals(setOf("1_1"), seen.seen(1))
        // Entering twice (adapter bind + page settle) starts the slide once.
        assertFalse(state.enter())
    }

    @Test
    fun `a partly seen story opens at its first unseen slide, a viewed one at the start`() {
        seen.markSeen(1, "1_0")
        seen.markSeen(1, "1_2")
        assertEquals(1, state(story(1, slides = 4, startPosition = 3)).slideIndex)

        listOf("1_1", "1_3").forEach { seen.markSeen(1, it) }
        assertEquals(0, state(story(1, slides = 4, startPosition = 3)).slideIndex)
        assertEquals(0, state(story(2, slides = 4, startPosition = 3, viewed = true)).slideIndex)
    }

    @Test
    fun `the start position is clamped to the slides`() {
        assertEquals(2, state(story(1, slides = 3, startPosition = 9)).slideIndex)
    }

    @Test
    fun `tap zones are thirds, the centre does nothing and RTL mirrors the edges`() {
        assertEquals(Zone.PREVIOUS, StoriesViewerState.zone(10f, 300, rtl = false))
        assertEquals(Zone.CENTER, StoriesViewerState.zone(150f, 300, rtl = false))
        assertEquals(Zone.NEXT, StoriesViewerState.zone(290f, 300, rtl = false))
        assertEquals(Zone.NEXT, StoriesViewerState.zone(10f, 300, rtl = true))
        assertEquals(Zone.PREVIOUS, StoriesViewerState.zone(290f, 300, rtl = true))

        val state = entered(story(1, slides = 3))
        assertEquals(Move.None, state.tap(Zone.CENTER))
        assertEquals(0, state.slideIndex)
    }

    @Test
    fun `next walks the slides, then the stories, then closes`() {
        val state = entered(story(1, slides = 2), story(2, slides = 1))
        assertEquals(Move.Slide, state.tap(Zone.NEXT))
        assertEquals(1, state.slideIndex)
        assertEquals(Move.Story(1), state.tap(Zone.NEXT))
        // Leaving the story stops its clock until the pager settles on the next one.
        assertFalse(state.isRunning)

        state.selectStory(1)
        state.enter()
        assertEquals(Move.Close, state.next())
    }

    @Test
    fun `previous on the first slide of the first story restarts it`() {
        val state = entered(story(1, slides = 2))
        state.mediaReady()
        state.tick(1500)
        assertEquals(Move.Restart, state.previous())
        assertEquals(0, state.slideIndex)
        assertEquals(0L, state.elapsedMs)
        assertTrue(state.isRunning)
    }

    @Test
    fun `previous on the first slide of a later story goes back a story`() {
        val state = entered(story(1, slides = 2), story(2, slides = 2), start = 1)
        assertEquals(Move.Story(0), state.previous())
    }

    @Test
    fun `swiping back into a story applies the start rule`() {
        val state = entered(story(1, slides = 3), story(2, slides = 2))
        state.next()
        state.selectStory(1)
        state.enter()
        state.selectStory(0)
        // Slides 0 and 1 of the first story were seen: it reopens at slide 2.
        assertEquals(2, state.slideIndex)
        assertFalse(state.entered)
    }

    @Test
    fun `the clock runs only when the media is ready and nothing pauses it`() {
        val state = entered(story(1, slides = 2, durationMs = 5000))
        assertTrue(state.isPaused(Pause.LOADING))
        assertEquals(Move.None, state.tick(6000))
        assertEquals(0L, state.elapsedMs)

        state.mediaReady()
        state.tick(2000)
        assertEquals(0.4f, state.progress, 0.001f)

        for (reason in listOf(Pause.HOLD, Pause.DRAG, Pause.SHEET, Pause.BACKGROUND, Pause.HOST)) {
            assertTrue(state.setPaused(reason, true))
            assertEquals(Move.None, state.tick(10_000))
            assertTrue(state.setPaused(reason, false))
        }
        assertEquals(2000L, state.elapsedMs)

        assertEquals(Move.Slide, state.tick(3000))
        assertEquals(1, state.slideIndex)
    }

    @Test
    fun `a background pause survives a slide change`() {
        val state = entered(story(1, slides = 3))
        state.mediaReady()
        state.setPaused(Pause.BACKGROUND, true)
        state.next()
        state.mediaReady()
        assertFalse(state.isRunning)
        state.setPaused(Pause.BACKGROUND, false)
        assertTrue(state.isRunning)
    }

    @Test
    fun `the player keeps playing while loading but stops for a hold`() {
        val state = entered(story(1, slides = 1))
        assertTrue(state.playerShouldPlay)
        state.buffering(true)
        assertTrue(state.playerShouldPlay)
        assertFalse(state.isRunning)
        state.setPaused(Pause.HOLD, true)
        assertFalse(state.playerShouldPlay)
    }

    @Test
    fun `a video slide follows the player and ends with the video`() {
        val state = entered(story(1, slides = 2, durationMs = 10_000))
        state.mediaReady(videoDurationMs = 30_000)
        assertEquals(StoriesViewerState.Clock.PLAYER, state.clock)
        // Timer ticks do not move a video slide: only the playback position does.
        assertEquals(Move.None, state.tick(20_000))
        state.videoPosition(15_000)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(Move.Slide, state.mediaEnded())
    }

    @Test
    fun `a video of unknown length falls back to the admin duration`() {
        val state = entered(story(1, slides = 2, durationMs = 4000))
        state.mediaReady(videoDurationMs = null)
        assertEquals(StoriesViewerState.Clock.TIMER, state.clock)
        assertEquals(Move.Slide, state.tick(4000))
    }

    @Test
    fun `a failed media pauses the slide until retried`() {
        val state = entered(story(1, slides = 2))
        state.mediaFailed()
        assertTrue(state.hasError)
        assertFalse(state.isRunning)
        assertEquals(Move.None, state.tick(60_000))

        state.retry()
        assertFalse(state.hasError)
        assertTrue(state.isPaused(Pause.LOADING))
        state.mediaReady()
        assertTrue(state.isRunning)
    }

    @Test
    fun `an open sheet blocks taps, closes on a tap or back, and resets on a slide change`() {
        val state = entered(story(1, slides = 3))
        state.mediaReady()
        state.setSheet(true)
        assertFalse(state.isRunning)

        assertEquals(Move.CloseSheet, state.tap(Zone.NEXT))
        assertEquals(0, state.slideIndex)
        assertFalse(state.sheetOpen)
        assertTrue(state.isRunning)

        state.setSheet(true)
        assertEquals(Move.CloseSheet, state.back())
        assertEquals(Move.Close, state.back())

        state.setSheet(true)
        state.next()
        assertFalse(state.sheetOpen)
        assertFalse(state.isPaused(Pause.SHEET))
    }

    @Test
    fun `opening another story from the host pages there inside the open viewer`() {
        seen.markSeen(3, "3_0")
        val state = entered(story(1, slides = 2), story(2, slides = 2), story(3, slides = 3))
        state.mediaReady()
        state.setSheet(true)

        assertEquals(Move.Story(2), state.jumpTo(2))
        assertFalse(state.sheetOpen)
        // The clock stands until the pager settles on the new story.
        assertFalse(state.isRunning)
        assertEquals(Move.None, state.tick(StoriesMapping.DEFAULT_DURATION_MS))

        state.selectStory(2)
        assertTrue(state.enter())
        assertEquals(1, state.slideIndex)
    }

    @Test
    fun `opening the current or a missing story from the host changes nothing`() {
        val state = entered(story(1, slides = 2), story(2, slides = 2))
        assertEquals(Move.None, state.jumpTo(0))
        assertEquals(Move.None, state.jumpTo(5))
        assertTrue(state.entered)
    }

    @Test
    fun `the time is up only once per slide`() {
        val state = entered(story(1, slides = 1), story(2, slides = 1))
        state.mediaReady()
        assertEquals(Move.Story(1), state.tick(StoriesMapping.DEFAULT_DURATION_MS))
        assertEquals(Move.None, state.tick(16))
    }

    @Test
    fun `a story without slides does not crash the state`() {
        val state = StoriesViewerState(listOf(StoryShape(1, emptyList())), seen)
        assertFalse(state.enter())
        assertEquals(Move.Close, state.next())
        assertEquals(Move.None, state.tick(1000))
    }

    private fun state(vararg stories: StoryShape, start: Int = 0) = StoriesViewerState(stories.toList(), seen, start)

    private fun entered(vararg stories: StoryShape, start: Int = 0) =
        state(*stories, start = start).also { it.enter() }

    private fun story(
        id: Int,
        slides: Int,
        startPosition: Int = 0,
        viewed: Boolean = false,
        durationMs: Long = StoriesMapping.DEFAULT_DURATION_MS
    ) = StoryShape(id, List(slides) { SlideShape("${id}_$it", durationMs) }, viewed, startPosition)

    private class MemorySeen : SeenSlides {
        private val map = HashMap<Int, MutableSet<String>>()
        override fun seen(storyId: Int): Set<String> = map[storyId].orEmpty()
        override fun markSeen(storyId: Int, slideId: String) {
            map.getOrPut(storyId) { HashSet() }.add(slideId)
        }
    }
}
