package com.personalization.ui.widgets

import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Share" of Stories V2: the link format (the web SDK table), the slide link, the API v3 shapes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesShareTest {

    // --- parseLink: the table of the web SDK (stories-share.test.ts) ---------------------------

    @Test
    fun `reads block, campaign and slide`() {
        assertEquals(
            StoriesShare.Link("main", 5, "10"),
            StoriesShare.parseLink("https://shop.test/?utm_source=tg&r46_stories=main&r46_story=5&r46_slide=10")
        )
    }

    @Test
    fun `accepts links without a slide and virtual slides of product campaigns`() {
        assertEquals(StoriesShare.Link("main", 5, null), StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=5"))
        assertEquals("5_2", StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=5&r46_slide=5_2")?.slideId)
    }

    @Test
    fun `ignores other pages and broken links`() {
        assertNull(StoriesShare.parseLink(""))
        assertNull(StoriesShare.parseLink("https://shop.test/"))
        assertNull(StoriesShare.parseLink("https://shop.test/?r46_story=5"))
        assertNull(StoriesShare.parseLink("https://shop.test/?r46_stories=&r46_story=5"))
        assertNull(StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=abc"))
        assertNull(StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=12345678901"))
        assertEquals(StoriesShare.Link("main", 5, null), StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=5&r46_slide=1;DROP"))
        assertNull(StoriesShare.parseLink("https://shop.test/?r46_stories=main&r46_story=5&r46_slide=5_12345")?.slideId)
    }

    @Test
    fun `the path, the fragment and encoded values do not get in the way`() {
        assertEquals(
            StoriesShare.Link("115bd0b67cf5989a1b60c841790b2af6", 40, "37"),
            StoriesShare.parseLink("https://demo.rees46.ru/promo/page?r46_stories=115bd0b67cf5989a1b60c841790b2af6&r46_story=40&r46_slide=37#top")
        )
        assertEquals("a b", StoriesShare.parseLink("https://shop.test/?r46_stories=a%20b&r46_story=1")?.code)
        // The first value wins, as URLSearchParams.get.
        assertEquals(1L, StoriesShare.parseLink("https://shop.test/?r46_stories=x&r46_story=1&r46_story=2")?.storyId)
    }

    // --- slideShareUrl --------------------------------------------------------------------------

    @Test
    fun `prefers the slide link and falls back to the campaign link`() {
        val story = story("""{"id":5,"shareable":true,"share_url":"https://shop.test/?r46_story=5","slides":[]}""")
        assertEquals("https://shop.test/?r46_slide=1", StoriesShare.slideShareUrl(story, slide("""{"id":1,"share_url":"https://shop.test/?r46_slide=1"}""")))
        assertEquals("https://shop.test/?r46_story=5", StoriesShare.slideShareUrl(story, slide("""{"id":"5_0"}""")))
        assertEquals("https://shop.test/?r46_story=5", StoriesShare.slideShareUrl(story, slide("""{"id":"5_0","share_url":null}""")))
    }

    @Test
    fun `returns nothing when sharing is off or the link is not http(s)`() {
        val off = story("""{"id":5,"shareable":false,"share_url":"https://shop.test/","slides":[]}""")
        assertNull(StoriesShare.slideShareUrl(off, slide("""{"id":1,"share_url":"https://shop.test/"}""")))
        assertNull(StoriesShare.slideShareUrl(story("""{"id":5}"""), slide("""{"id":1}""")))
        assertNull(StoriesShare.slideShareUrl(story("""{"id":5,"share_url":"javascript:alert(1)"}"""), slide("""{"id":1}""")))
        assertNull(StoriesShare.slideShareUrl(story("""{"id":5,"share_url":null}"""), slide("""{"id":1}""")))
        assertEquals("HTTP://shop.test/", StoriesShare.slideShareUrl(story("""{"id":5,"share_url":"HTTP://shop.test/"}"""), slide("""{"id":1}""")))
    }

    // --- API v3 ---------------------------------------------------------------------------------

    @Test
    fun `the feed is read from the payload, json null of product slides is an empty string`() {
        val stories = StoriesApiV3.parse(
            """{"status":"success","payload":{"id":35,"settings":{"color":"#212529"},"stories":[
              {"id":39,"name":"Products","shareable":true,"share_url":"https://shop.test/?r46_story=39","start_position":0,
               "slides":[{"id":"39_0","background":null,"preview":null,"type":"","share_url":null,"elements":[null,{"type":"button","title":"Buy","link":null}]}]},
              {"id":40,"name":"Media","share_url":"https://shop.test/?r46_story=40","start_position":1,
               "slides":[{"id":36,"type":"video","background":"https://cdn.test/v.mp4","preview":"https://cdn.test/p.jpg","share_url":"https://shop.test/?r46_slide=36"},
                         {"id":37,"type":"image","background":"https://cdn.test/a.jpg","share_url":"https://shop.test/?r46_slide=37"}]},
              {"id":41,"name":"Empty","slides":[]}]}}"""
        )

        assertEquals(listOf(39, 40), stories.map { it.id })
        val product = stories[0].getSlide(0)
        assertEquals("", product.background)
        assertEquals("", product.preview)
        assertEquals("", product.shareUrl)
        assertEquals(1, product.getElements().size)
        assertEquals("https://shop.test/?r46_story=39", StoriesShare.slideShareUrl(stories[0], product))
        assertTrue(stories[0].isShareable)
        assertEquals("36", stories[1].getSlide(0).id)
        assertEquals("https://shop.test/?r46_slide=37", stories[1].getSlide(1).shareUrl)
        assertEquals(1, stories[1].startPosition)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a failed status is an error`() {
        StoriesApiV3.parse("""{"status":"fail","message":"Resource not found"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a success without a payload is an error`() {
        StoriesApiV3.parse("""{"status":"success"}""")
    }

    @Test
    fun `the event body sends the campaign id as a number and the slide id as a string`() {
        val body = StoriesApiV3.eventBody("shop1", "did1", "share", 39, "39_0")
        assertEquals("""{"shop_id":"shop1","did":"did1","event":"share","story_id":39,"slide_id":"39_0"}""", body.toString())
        assertEquals("36", StoriesApiV3.eventBody("shop1", "did1", "view", 40, "36").get("slide_id"))
    }

    @Test
    fun `the requests go to the api v3 host with shop_id and did only`() {
        val link = StoriesShare.Link("main", 40, "37")
        assertEquals(
            "https://app.rees46.ru/api/v3/storefront/stories/main?shop_id=shop1&did=did1",
            StoriesApiV3.feedUrl("app.rees46.ru", "main", "shop1", "did1")
        )
        assertEquals(
            "https://app.rees46.ru/api/v3/storefront/stories/main/story/40?shop_id=shop1&did=did1&slide_id=37",
            StoriesApiV3.storyUrl("https://app.rees46.ru/", link, "shop1", "did1")
        )
        assertEquals(
            "https://app.rees46.ru/api/v3/storefront/stories/main/story/40?shop_id=shop1&did=did1",
            StoriesApiV3.storyUrl(" app.rees46.ru ", link.copy(slideId = null), "shop1", "did1")
        )
        assertEquals("https://app.rees46.ru/api/v3/storefront/stories/a%20b/events", StoriesApiV3.eventsUrl("app.rees46.ru", "a b"))
    }

    private fun story(json: String) = Story(JSONObject(json))

    private fun slide(json: String) = Slide(JSONObject(json))
}
