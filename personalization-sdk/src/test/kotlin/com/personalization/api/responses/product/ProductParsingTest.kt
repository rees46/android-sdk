package com.personalization.api.responses.product

import com.google.gson.Gson
import com.personalization.Fixtures
import com.personalization.api.responses.category.CategoryResponse
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse
import com.personalization.api.responses.search.SearchFullResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The product shape every listing shares, read from live answers of the API. */
class ProductParsingTest {

    private val gson = Gson()

    @Test
    fun `a discounted recommendation carries old price, discount and deeplinks`() {
        val response = gson.fromJson(Fixtures.read("recommend_extended.json"), GetExtendedRecommendationResponse::class.java)
        val product = response.products.first()

        assertEquals(164990.0, product.price, 0.0)
        assertEquals("164 990 ₽", product.priceFormatted)
        assertEquals(174990.0, product.oldPrice!!, 0.0)
        assertEquals("174 990 ₽", product.oldPriceFormatted)
        assertEquals(6, product.discount)
        assertEquals("6%", product.discountFormatted)
        assertEquals(5, product.discountPercent)
        assertEquals("https://www.technodom.kz/p/holodilnik-beko-rdsk-mw-52173", product.deeplinkAndroid)
        assertEquals("https://www.technodom.kz/p/holodilnik-beko-rdsk-mw-52173", product.deeplinkIos)
        assertTrue(product.picture.startsWith("https://pictures.rees46.ru/resize-images/180/"))
        assertTrue(product.url.startsWith("https://www.technodom.kz/p/holodilnik-beko-rdsk-mw-52173"))
    }

    @Test
    fun `a product without a discount leaves the discount fields empty`() {
        val response = gson.fromJson(Fixtures.read("recommend_extended.json"), GetExtendedRecommendationResponse::class.java)
        val product = response.products[1]

        assertNull(product.oldPrice)
        assertNull(product.oldPriceFormatted)
        assertNull(product.discount)
        assertNull(product.discountFormatted)
        assertNull(product.discountPercent)
        assertEquals("https://www.technodom.kz/p/naushniki-nakladnye-honor-choice-headphones-lite-gray-5504adgf-300252", product.deeplinkAndroid)
    }

    @Test
    fun `product info reads a boolean discount as none and a quoted price as a number`() {
        val product = gson.fromJson(Fixtures.read("product_info.json"), Product::class.java)

        assertEquals("868", product.id)
        assertEquals(14990.0, product.price, 0.0)
        assertNull(product.discount)
        assertNull(product.oldPrice)
        assertEquals("https://www.technodom.kz/p/garnitura-nakladnaa-logitech-h-white-868", product.deeplinkAndroid)
        assertEquals("https://www.technodom.kz/p/garnitura-nakladnaa-logitech-h-white-868", product.deeplinkIos)
    }

    @Test
    fun `category and full search products carry deeplinks`() {
        val category = gson.fromJson(Fixtures.read("category.json"), CategoryResponse::class.java)
        val search = gson.fromJson(Fixtures.read("search_full.json"), SearchFullResponse::class.java)

        assertTrue(category.products.isNotEmpty())
        assertTrue(category.products.all { !it.deeplinkAndroid.isNullOrEmpty() && !it.deeplinkIos.isNullOrEmpty() })
        assertTrue(search.products.isNotEmpty())
        assertTrue(search.products.all { !it.deeplinkAndroid.isNullOrEmpty() && !it.deeplinkIos.isNullOrEmpty() })
    }

    @Test
    fun `a numeric string discount is read as a number`() {
        val product = gson.fromJson("""{"id":"1","discount":"12","discount_percent":7.0}""", Product::class.java)

        assertEquals(12, product.discount)
        assertEquals(7, product.discountPercent)
    }

    @Test
    fun `an empty old price string is no old price, a numeric one is read`() {
        val empty = gson.fromJson("""{"id":"1","price":"100","oldprice":""}""", Product::class.java)
        val quoted = gson.fromJson("""{"id":"1","price":"100","oldprice":"174990.0"}""", Product::class.java)
        val junk = gson.fromJson("""{"id":"1","price":"100","oldprice":false}""", Product::class.java)

        assertNull(empty.oldPrice)
        assertEquals(174990.0, quoted.oldPrice!!, 0.0)
        assertNull(junk.oldPrice)
    }

    @Test
    fun `the old price survives a round trip through Gson`() {
        val product = gson.fromJson("""{"id":"1","price":90,"oldprice":"100.5"}""", Product::class.java)

        val again = gson.fromJson(gson.toJson(product), Product::class.java)

        assertEquals(100.5, again.oldPrice!!, 0.0)
        assertNull(gson.fromJson(gson.toJson(gson.fromJson("""{"id":"1"}""", Product::class.java)), Product::class.java).oldPrice)
    }
}
