package com.personalization.ui.widgets

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stories block data → kit components, on the shapes the stories endpoint returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesMappingTest {

    // --- Price (C10) ----------------------------------------------------------------------------

    @Test
    fun `a promo code beats the discount and crosses out the regular price`() {
        val price = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "discount_percent" to 10,
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.PROMO_CODE, price.type)
        assertEquals("900 ₽", price.price)
        assertEquals("1 000 ₽", price.oldPrice)
        assertEquals("PROMO", price.promoCode)
    }

    @Test
    fun `a promo code crosses out the old price when there is one`() {
        val price = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 1500, "oldprice_formatted" to "1 500 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 500 ₽", price.oldPrice)
    }

    @Test
    fun `under a promo code an old price counts only above the regular price`() {
        // 950 is above the promo price but below the regular one: the regular price is crossed out.
        val belowRegular = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 950, "oldprice_formatted" to "950 ₽",
                "promocode" to "PROMO", "price_with_promocode" to 900, "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 000 ₽", belowRegular.oldPrice)
        // An old price equal to the regular one is no markdown either.
        val equalToRegular = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 1000, "oldprice_formatted" to "1 000.00 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 000 ₽", equalToRegular.oldPrice)
    }

    @Test
    fun `under a promo code the regular price is crossed out only when it differs from the promo price`() {
        val same = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "promocode" to "PROMO", "price_with_promocode" to "1000.00", "price_with_promocode_formatted" to "1 000 ₽"
            )
        )
        assertNull(same.oldPrice)
        val higherPromo = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "1 100 ₽"
            )
        )
        assertEquals("1 000 ₽", higherPromo.oldPrice)
    }

    @Test
    fun `a promo code without a promo price shows the regular price and crosses nothing`() {
        val price = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "promocode" to "PROMO")
        )
        assertEquals("1 000 ₽", price.price)
        assertNull(price.oldPrice)
    }

    @Test
    fun `a discount percent shows the discount block with the old price`() {
        val price = StoriesMapping.price(
            product(
                "price" to 19990, "price_formatted" to "19 990 ₽",
                "oldprice" to 27990, "oldprice_formatted" to "27 990 ₽",
                "discount_percent" to 28
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.DISCOUNT, price.type)
        assertEquals("28%", price.discount)
        assertEquals("27 990 ₽", price.oldPrice)
    }

    @Test
    fun `a fractional discount keeps its fraction`() {
        val price = StoriesMapping.price(product("price" to 100, "price_formatted" to "100 ₽", "discount_percent" to 12.5))
        assertEquals("12.5%", price.discount)
    }

    @Test
    fun `an old price equal to the price is not shown`() {
        // The endpoint sends oldprice == price for goods without a markdown.
        val price = StoriesMapping.price(
            product(
                "price" to 14990, "price_formatted" to "14 990 ₽",
                "oldprice" to 14990, "oldprice_formatted" to "14 990 ₽"
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.PRICE, price.type)
        assertNull(price.oldPrice)
    }

    @Test
    fun `an old price lower than the price is not shown, compared as decimals`() {
        val lower = StoriesMapping.price(
            product(
                "price" to 99.99, "price_formatted" to "99,99 €",
                "oldprice" to 99.5, "oldprice_formatted" to "99,50 €"
            )
        )
        assertNull(lower.oldPrice)
        // 99.99 > 99.5 only as a decimal: as Ints both are 99.
        val higher = StoriesMapping.price(
            product(
                "price" to 99.5, "price_formatted" to "99,50 €",
                "oldprice" to 99.99, "oldprice_formatted" to "99,99 €"
            )
        )
        assertEquals("99,99 €", higher.oldPrice)
    }

    @Test
    fun `formatted prices parse as decimals`() {
        assertEquals(BigDecimal("1190900"), Decimals.parse("1 190 900 ₽"))
        assertEquals(BigDecimal("19.99"), Decimals.parse("19,99 €"))
        assertEquals(BigDecimal("1190.50"), Decimals.parse("$1,190.50"))
        assertEquals(BigDecimal("1190"), Decimals.parse("1,190 ₸"))
        assertNull(Decimals.parse("₽"))
        assertNull(Decimals.parse(""))
    }

    @Test
    fun `a carousel product gets a signed discount only with a real markdown`() {
        val card = StoriesMapping.cardProduct(
            product(
                "id" to "1", "name" to "Mouse", "price" to 100, "price_formatted" to "100 ₽",
                "oldprice" to 150, "oldprice_formatted" to "150 ₽", "discount" to 33
            )
        )
        assertEquals("-33%", card.discount)
        assertEquals("150 ₽", card.oldPrice)

        val same = StoriesMapping.cardProduct(
            product("price" to 100, "price_formatted" to "100 ₽", "oldprice" to 100, "oldprice_formatted" to "100 ₽")
        )
        assertNull(same.oldPrice)
    }

    // --- Seen, start slide and ring (A3, A4, A5) ------------------------------------------------

    @Test
    fun `a story is viewed when the server says so or every slide is seen`() {
        val story = story(slides = 2)
        assertFalse(StoriesMapping.isViewed(story, setOf("s0")))
        assertTrue(StoriesMapping.isViewed(story, setOf("s0", "s1")))
        assertTrue(StoriesMapping.isViewed(story(viewed = true), emptySet()))
    }

    @Test
    fun `the start slide is the first unseen, the server position, or the start`() {
        assertEquals(2, StoriesMapping.startSlide(story(startPosition = 2, slides = 5), emptySet()))
        assertEquals(1, StoriesMapping.startSlide(story(startPosition = 3, slides = 5), setOf("s0", "s2")))
        assertEquals(0, StoriesMapping.startSlide(story(viewed = true, startPosition = 2, slides = 5), emptySet()))
        assertEquals(0, StoriesMapping.startSlide(story(slides = 2), setOf("s0", "s1")))
        assertEquals(4, StoriesMapping.startSlide(story(startPosition = 9, slides = 5), emptySet()))
    }

    @Test
    fun `the ring lights seen slides by index`() {
        val item = StoriesMapping.item(story(startPosition = 2, slides = 5), setOf("s1", "s3"))
        assertEquals(PersonalizationStoryCover.State.DEFAULT, item.state)
        assertEquals(5, item.segments)
        assertEquals(setOf(1, 3), item.seenSegments)
    }

    @Test
    fun `a viewed story has a plain ring, a pinned one stays pinned`() {
        val viewed = StoriesMapping.item(story(slides = 3), setOf("s0", "s1", "s2"))
        assertEquals(PersonalizationStoryCover.State.VIEWED, viewed.state)
        assertEquals(1, viewed.segments)
        assertTrue(viewed.seenSegments.isEmpty())

        val pinnedViewed = StoriesMapping.item(story(pinned = true, viewed = true, slides = 3), emptySet())
        assertEquals(PersonalizationStoryCover.State.PINNED, pinnedViewed.state)
        assertEquals(1, pinnedViewed.segments)
        assertTrue(pinnedViewed.seenSegments.isEmpty())

        val pinnedPartly = StoriesMapping.item(story(pinned = true, slides = 3), setOf("s2"))
        assertEquals(PersonalizationStoryCover.State.PINNED, pinnedPartly.state)
        assertEquals(setOf(2), pinnedPartly.seenSegments)
    }

    @Test
    fun `seen slides persist per shop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("stories_seen_test", Context.MODE_PRIVATE)
        StoriesSeenStore(preferences).markSeen(7, "70")
        assertEquals(setOf("70"), StoriesSeenStore(preferences).seen(7))
        assertTrue(StoriesSeenStore(preferences).seen(8).isEmpty())

        val shopA = StoriesSeenStore.forShop(context, "shop-a")
        shopA.markSeen(1, "10")
        assertTrue(StoriesSeenStore.forShop(context, "shop-b").seen(1).isEmpty())
        assertEquals(setOf("10"), StoriesSeenStore.forShop(context, "shop-a").seen(1))
    }

    // --- Tracking, duration (A6, A7) ------------------------------------------------------------

    @Test
    fun `only positive integer ids are tracked`() {
        assertTrue(StoriesMapping.isTrackable(40, "36"))
        assertFalse(StoriesMapping.isTrackable(39, "39_0"))
        assertFalse(StoriesMapping.isTrackable(39, "0"))
        assertFalse(StoriesMapping.isTrackable(39, "-3"))
        assertFalse(StoriesMapping.isTrackable(39, "036"))
        assertFalse(StoriesMapping.isTrackable(0, "36"))
    }

    @Test
    fun `a slide without a duration runs 10 seconds`() {
        assertEquals(10_000L, StoriesMapping.durationMs(slide()))
        assertEquals(10_000L, StoriesMapping.durationMs(slide("duration" to 0)))
        assertEquals(6_000L, StoriesMapping.durationMs(slide("duration" to 6)))
    }

    // --- Slide look (C1, C3) --------------------------------------------------------------------

    @Test
    fun `only a bare media slide gets the strong scrim`() {
        val photo = slide("type" to "image", "background" to "https://x/1.jpg")
        assertEquals(StoriesMapping.SCRIM_STRONG, StoriesMapping.scrimAlpha(photo))
        val withText = slide(
            "type" to "image", "background" to "https://x/1.jpg",
            "elements" to JSONArray().put(JSONObject().put("type", "text_block").put("text_input", "Hi"))
        )
        assertEquals(StoriesMapping.SCRIM_LIGHT, StoriesMapping.scrimAlpha(withText))
        val blankText = slide(
            "type" to "image", "background" to "https://x/1.jpg",
            "elements" to JSONArray().put(JSONObject().put("type", "text_block").put("text_input", "  "))
        )
        assertEquals(StoriesMapping.SCRIM_STRONG, StoriesMapping.scrimAlpha(blankText))
        assertEquals(StoriesMapping.SCRIM_LIGHT, StoriesMapping.scrimAlpha(slide()))
    }

    @Test
    fun `the letterbox takes the slide colour, black without it`() {
        assertEquals(Color.parseColor("#112233"), StoriesMapping.letterboxColor(slide("background_color" to "#112233")))
        assertEquals(Color.BLACK, StoriesMapping.letterboxColor(slide("background_color" to "")))
    }

    // --- Links and captions (C9, C11, C12) ------------------------------------------------------

    @Test
    fun `a button opens its own link, without one the product`() {
        val product = product("url" to "https://shop/p/1", "deeplink_android" to "app://p/1")
        val own = ButtonElement(JSONObject().put("title", "Buy").put("link", "https://web").put("link_android", "app://own"))
        assertEquals("app://own", StoriesMapping.buttonLink(own, product))
        val bare = ButtonElement(JSONObject().put("title", "Buy"))
        assertEquals("app://p/1", StoriesMapping.buttonLink(bare, product))
        assertEquals("https://shop/p/2", StoriesMapping.buttonLink(bare, product("url" to "https://shop/p/2")))
        assertEquals("", StoriesMapping.buttonLink(bare, null))
    }

    @Test
    fun `the promo caption comes from the admin title, else the host text`() {
        val titled = ProductElement(JSONObject().put("title", "Ваш код"))
        assertEquals("Ваш код", StoriesMapping.promoCaption(titled, "Promo code"))
        val blank = ProductElement(JSONObject().put("title", " "))
        assertEquals("Promo code", StoriesMapping.promoCaption(blank, "Promo code"))
    }

    @Test
    fun `a text block promocode is read, a null one is not`() {
        assertEquals("SALE", StoriesMapping.textPromocode(TextBlockElement(JSONObject().put("promocode", " SALE "))))
        assertNull(StoriesMapping.textPromocode(TextBlockElement(JSONObject())))
        assertNull(StoriesMapping.textPromocode(TextBlockElement(JSONObject().put("promocode", JSONObject.NULL))))
    }

    // --- Text blocks (C15) ----------------------------------------------------------------------

    @Test
    fun `a text block background takes its opacity, zero means no background`() {
        val half = StoriesMapping.textBlockColor(textBlock("#0087E8", "50%"))!!
        assertEquals(127, Color.alpha(half))
        assertEquals(0x87, Color.green(half))
        assertNull(StoriesMapping.textBlockColor(textBlock("#0087E8", "0%")))
        assertNull(StoriesMapping.textBlockColor(textBlock("not a colour", "100%")))
    }

    @Test
    fun `text alignment maps start, centre and end`() {
        fun align(value: String) = StoriesMapping.textAlign(TextBlockElement(JSONObject().put("text_align", value)))
        assertEquals(PersonalizationStoryTextBlock.Align.START, align("left"))
        assertEquals(PersonalizationStoryTextBlock.Align.START, align("start"))
        assertEquals(PersonalizationStoryTextBlock.Align.CENTER, align("center"))
        assertEquals(PersonalizationStoryTextBlock.Align.END, align("right"))
        assertEquals(PersonalizationStoryTextBlock.Align.END, align("end"))
    }

    @Test
    fun `a missing line spacing falls back to the preset of that size`() {
        assertNull(StoriesMapping.lineHeightSp(TextBlockElement(JSONObject().put("font_size", 20))))
        assertEquals(30f, StoriesMapping.lineHeightSp(TextBlockElement(JSONObject().put("font_size", 20).put("text_line_spacing", 1.5)))!!, 0.01f)
        assertEquals(48f, PersonalizationStoryTextBlock.Size.lineHeightFor(48f), 0.01f)
        assertEquals(36f, PersonalizationStoryTextBlock.Size.lineHeightFor(30f), 0.01f)
        assertEquals(24f, PersonalizationStoryTextBlock.Size.lineHeightFor(20f), 0.01f)
        assertEquals(16.8f, PersonalizationStoryTextBlock.Size.lineHeightFor(14f), 0.01f)
    }

    // --- Loading (C16) --------------------------------------------------------------------------

    @Test
    fun `stories without slides are dropped`() {
        val response = JSONObject().put(
            "stories",
            JSONArray()
                .put(storyJson(id = 1, slides = 2))
                .put(storyJson(id = 2, slides = 0))
        )
        assertEquals(listOf(1), StoriesLoader.parse(response).map { it.id })
        assertTrue(StoriesLoader.parse(null).isEmpty())
    }

    private fun product(vararg fields: Pair<String, Any>): Product =
        Product(JSONObject().apply { fields.forEach { (key, value) -> put(key, value) } })

    private fun slide(vararg fields: Pair<String, Any>): Slide =
        Slide(JSONObject().put("id", "1").apply { fields.forEach { (key, value) -> put(key, value) } })

    private fun story(
        pinned: Boolean = false,
        viewed: Boolean = false,
        startPosition: Int = 0,
        slides: Int = 3
    ): Story = Story(storyJson(1, slides, pinned, viewed, startPosition))

    private fun storyJson(
        id: Int,
        slides: Int,
        pinned: Boolean = false,
        viewed: Boolean = false,
        startPosition: Int = 0
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "Story")
        .put("pinned", pinned)
        .put("viewed", viewed)
        .put("start_position", startPosition)
        .put("slides", JSONArray().apply {
            repeat(slides) { put(JSONObject().put("id", "s$it").put("elements", JSONArray())) }
        })

    private fun textBlock(color: String, opacity: String) = TextBlockElement(
        JSONObject()
            .put("text_input", "Text")
            .put("text_background_color", color)
            .put("text_background_color_opacity", opacity)
    )
}
