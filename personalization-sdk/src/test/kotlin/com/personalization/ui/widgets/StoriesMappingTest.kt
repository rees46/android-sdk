package com.personalization.ui.widgets

import android.graphics.Color
import com.personalization.Product
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryPrice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stories block data → kit components, on the shapes the stories endpoint returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesMappingTest {

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
    fun `a carousel product gets a signed discount only with a real markdown`() {
        val card = StoriesMapping.cardProduct(
            product(
                "id" to "1", "name" to "Mouse", "price" to 100, "price_formatted" to "100 ₽",
                "oldprice" to 150, "oldprice_formatted" to "150 ₽", "discount" to 33
            )
        )
        assertEquals("-33%", card.discount)
        assertEquals("150 ₽", card.oldPrice)
    }

    @Test
    fun `the cover state follows pinned and viewed`() {
        assertEquals(PersonalizationStoryCover.State.PINNED, StoriesMapping.item(story(pinned = true, viewed = true)).state)
        assertEquals(PersonalizationStoryCover.State.VIEWED, StoriesMapping.item(story(viewed = true)).state)
        val fresh = StoriesMapping.item(story(startPosition = 2, slides = 5))
        assertEquals(PersonalizationStoryCover.State.DEFAULT, fresh.state)
        assertEquals(5, fresh.segments)
        assertEquals(2, fresh.seenSegments)
    }

    @Test
    fun `an unfinished story resumes where it stopped, a viewed one from the start`() {
        assertEquals(2, StoriesMapping.startSlide(story(startPosition = 2, slides = 5)))
        assertEquals(0, StoriesMapping.startSlide(story(viewed = true, startPosition = 2, slides = 5)))
        assertEquals(4, StoriesMapping.startSlide(story(startPosition = 9, slides = 5)))
    }

    @Test
    fun `a text block background takes its opacity, zero means no background`() {
        val half = StoriesMapping.textBlockColor(textBlock("#0087E8", "50%"))!!
        assertEquals(127, Color.alpha(half))
        assertEquals(0x87, Color.green(half))
        assertNull(StoriesMapping.textBlockColor(textBlock("#0087E8", "0%")))
        assertNull(StoriesMapping.textBlockColor(textBlock("not a colour", "100%")))
    }

    private fun product(vararg fields: Pair<String, Any>): Product =
        Product(JSONObject().apply { fields.forEach { (key, value) -> put(key, value) } })

    private fun story(
        pinned: Boolean = false,
        viewed: Boolean = false,
        startPosition: Int = 0,
        slides: Int = 3
    ): Story = Story(
        JSONObject()
            .put("id", 1)
            .put("name", "Story")
            .put("pinned", pinned)
            .put("viewed", viewed)
            .put("start_position", startPosition)
            .put("slides", JSONArray().apply {
                repeat(slides) { put(JSONObject().put("id", "s$it").put("elements", JSONArray())) }
            })
    )

    private fun textBlock(color: String, opacity: String) = TextBlockElement(
        JSONObject()
            .put("text_input", "Text")
            .put("text_background_color", color)
            .put("text_background_color_opacity", opacity)
    )
}
