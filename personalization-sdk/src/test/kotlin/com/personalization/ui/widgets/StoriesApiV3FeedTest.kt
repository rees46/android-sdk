package com.personalization.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The stories v3 feed keeps every slide and start_position as sent; the slides the web SDK skips (`show()`
 * in stories.js) are only marked, the viewer passes them.
 */
@RunWith(RobolectricTestRunner::class)
class StoriesApiV3FeedTest {

    private fun feed(stories: String) = StoriesApiV3.parse("""{"status":"success","payload":{"id":1,"stories":[$stories]}}""")

    private fun ids(story: com.personalization.stories.models.Story) = List(story.slidesCount) { story.getSlide(it).id }

    @Test
    fun `a slide with no media type and no product element is kept and marked skipped, as the web SDK skips it`() {
        val story = feed(
            """{"id":1,"name":"S","start_position":1,"slides":[
            {"id":"10","type":"image","background":"https://cdn.test/a.jpg","elements":[]},
            {"id":"11","type":"","elements":[{"type":"text_block","text_input":"Text"},{"type":"poll","poll_type":"csat","poll":{"code":"c"}}]},
            {"id":"12","type":"video","background":"https://cdn.test/b.mp4","elements":[]},
            {"id":"13","elements":[{"type":"header","title":"Shop"}]},
            {"id":"14","type":"","elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]},
            {"id":"15","type":"gif","elements":[{"type":"products","products":[]}]}]}"""
        ).single()
        assertEquals(listOf("10", "11", "12", "13", "14", "15"), ids(story))
        assertEquals(1, story.startPosition)
        assertEquals(
            listOf(true, false, true, false, true, false),
            List(story.slidesCount) { StoriesMapping.isShownV3(story.getSlide(it)) }
        )
    }

    @Test
    fun `a story whose slides are all skipped stays in the feed`() {
        val stories = feed(
            """{"id":1,"name":"Skipped","slides":[{"id":"1","type":"","elements":[{"type":"button","title":"Go"}]}]},
            {"id":2,"name":"Shown","slides":[{"id":"2","type":"image","background":"a"}]},
            {"id":3,"name":"Nothing","slides":[]}"""
        )
        assertEquals("only a story without any slide is left out", listOf(1, 2), stories.map { it.id })
        assertFalse(StoriesMapping.isShownV3(stories[0].getSlide(0)))
        assertTrue(StoriesMapping.isShownV3(stories[1].getSlide(0)))
    }
}
