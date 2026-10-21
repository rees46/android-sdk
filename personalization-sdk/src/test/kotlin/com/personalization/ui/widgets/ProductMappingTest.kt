package com.personalization.ui.widgets

import com.google.gson.Gson
import com.personalization.Fixtures
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One mapping from the API product to the kit card, shared by every data widget. */
class ProductMappingTest {

    private val gson = Gson()

    private val recommended: List<Product> by lazy {
        gson.fromJson(Fixtures.read("recommend_extended.json"), GetExtendedRecommendationResponse::class.java).products
    }

    @Test
    fun `a discounted product shows the old price, the discount and its links`() {
        val card = recommended[0].toPersonalizationProduct(actionText = "Add to cart")

        assertEquals("164 990 ₽", card.price)
        assertEquals("174 990 ₽", card.oldPrice)
        assertEquals("-6%", card.discount)
        assertEquals("Beko", card.brand)
        assertEquals(recommended[0].picture, card.imageUrl)
        assertEquals(recommended[0].url, card.url)
        assertEquals("https://www.technodom.kz/p/holodilnik-beko-rdsk-mw-52173", card.deeplink)
        assertEquals("Add to cart", card.actionText)
        assertNull(card.ratingValue)
    }

    @Test
    fun `a product without a discount shows the price only`() {
        val card = recommended[1].toPersonalizationProduct()

        assertEquals("15 990 ₽", card.price)
        assertNull(card.oldPrice)
        assertNull(card.discount)
        assertNull(card.actionText)
    }

    @Test
    fun `an old price not above the price is not shown, and neither is the discount`() {
        val card = product("""{"id":"1","name":"A","price":100,"oldprice":100,"oldprice_formatted":"100 ₽","discount":5}""")
            .toPersonalizationProduct()

        assertNull(card.oldPrice)
        assertNull(card.discount)
    }

    @Test
    fun `without a formatted discount the percent number is used`() {
        val card = product("""{"id":"1","name":"A","price":90,"oldprice":100,"oldprice_formatted":"100 ₽","discount":10}""")
            .toPersonalizationProduct()

        assertEquals("100 ₽", card.oldPrice)
        assertEquals("-10%", card.discount)
    }

    @Test
    fun `a zero discount shows no badge, whatever its formatted text says`() {
        val card = discounted(""""discount":0,"discount_formatted":"0%"""").toPersonalizationProduct()

        assertEquals("100 ₽", card.oldPrice)
        assertNull(card.discount)
    }

    @Test
    fun `a boolean discount shows no badge`() {
        val card = discounted(""""discount":true,"discount_formatted":"10%"""").toPersonalizationProduct()

        assertNull(card.discount)
    }

    @Test
    fun `without a discount the percent is used`() {
        val card = discounted(""""discount_percent":"7"""").toPersonalizationProduct()

        assertEquals("-7%", card.discount)
    }

    @Test
    fun `a zero discount with a percent never reads minus zero`() {
        val card = discounted(""""discount":0,"discount_formatted":"0%","discount_percent":5""").toPersonalizationProduct()

        assertEquals("-5%", card.discount)
    }

    @Test
    fun `a formatted discount that already has a minus keeps exactly one`() {
        val card = discounted(""""discount":10,"discount_formatted":"-10%"""").toPersonalizationProduct()

        assertEquals("-10%", card.discount)
    }

    @Test
    fun `without formatted strings the price is the number with the currency`() {
        val card = product("""{"id":"1","name":"A","price":90.5,"currency":"₽"}""").toPersonalizationProduct()

        assertEquals("90.5 ₽", card.price)
    }

    @Test
    fun `the fallback price has no exponent and no trailing zero`() {
        val large = product("""{"id":"1","name":"A","price":16499000,"currency":"₽"}""").toPersonalizationProduct()
        val whole = product("""{"id":"1","name":"A","price":"90.0","currency":"₽"}""").toPersonalizationProduct()

        assertEquals("16499000 ₽", large.price)
        assertEquals("90 ₽", whole.price)
    }

    @Test
    fun `without a picture the original image is used, and empty links are none`() {
        val card = product("""{"id":"1","name":"A","price":1,"picture":"","image_url":"https://img/1.jpg","url":"","deeplink_android":""}""")
            .toPersonalizationProduct()

        assertEquals("https://img/1.jpg", card.imageUrl)
        assertNull(card.url)
        assertNull(card.deeplink)
        assertNull(card.brand)
    }

    private fun product(json: String): Product = gson.fromJson(json, Product::class.java)

    /** A product with an old price above the price, plus the given discount fields. */
    private fun discounted(fields: String): Product =
        product("""{"id":"1","name":"A","price":90,"oldprice":100,"oldprice_formatted":"100 ₽",$fields}""")
}
