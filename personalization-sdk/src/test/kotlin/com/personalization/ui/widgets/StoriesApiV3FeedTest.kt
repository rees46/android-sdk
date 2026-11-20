package com.personalization.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The stories v3 feed keeps only the slides the web SDK shows (`show()` in stories.js). */
@RunWith(RobolectricTestRunner::class)
class StoriesApiV3FeedTest {

    private fun feed(stories: String) = StoriesApiV3.parse("""{"status":"success","payload":{"id":1,"stories":[$stories]}}""")

    private fun ids(story: com.personalization.stories.models.Story) = List(story.slidesCount) { story.getSlide(it).id }

    @Test
    fun `a slide with no media type and no product element is skipped, as the web SDK skips it`() {
        val story = feed(
            """{"id":1,"name":"S","slides":[
            {"id":"10","type":"image","background":"https://cdn.test/a.jpg","elements":[]},
            {"id":"11","type":"","elements":[{"type":"text_block","text_input":"Text"},{"type":"poll","poll_type":"csat","poll":{"code":"c"}}]},
            {"id":"12","type":"video","background":"https://cdn.test/b.mp4","elements":[]},
            {"id":"13","elements":[{"type":"header","title":"Shop"}]},
            {"id":"14","type":"","elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]},
            {"id":"15","type":"gif","elements":[{"type":"products","products":[]}]}]}"""
        ).single()
        assertEquals(listOf("10", "12", "14"), ids(story))
    }

    @Test
    fun `start_position moves to the first shown slide from it on`() {
        fun start(position: Int) = feed(
            """{"id":1,"name":"S","start_position":$position,"slides":[
            {"id":"0","type":"image","background":"a"},
            {"id":"1","type":""},
            {"id":"2","type":"image","background":"b"},
            {"id":"3","type":""}]}"""
        ).single().startPosition
        assertEquals(0, start(0))
        assertEquals("the skipped slide 1 hands over to slide 2", 1, start(1))
        assertEquals(1, start(2))
        assertEquals("nothing shown after it — from the start, as the web SDK after its skip", 0, start(3))
    }

    @Test
    fun `a story with no shown slides is not in the feed`() {
        val stories = feed(
            """{"id":1,"name":"Empty","slides":[{"id":"1","type":"","elements":[{"type":"button","title":"Go"}]}]},
            {"id":2,"name":"Shown","slides":[{"id":"2","type":"image","background":"a"}]}"""
        )
        assertEquals(listOf(2), stories.map { it.id })
        assertTrue(stories.single().slidesCount == 1)
    }
}
