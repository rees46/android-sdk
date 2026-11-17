package com.personalization.core.internal

import com.personalization.api.models.products.Product
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fixtures' comparison of a model with `expect.result` catches what differs, and only that. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContractJsonTest {

    private val product = Product(id = "37", name = "Shoes", url = "https://shop.example/p/37", price = 4990.0, currency = "USD", rating = 4)

    private fun expected(json: String) = JSONObject(json)

    @Test
    fun `a model equals its fixture, numbers by value, null fields left out`() {
        ContractJson.assertEquals(
            "product",
            expected("""{"id":"37","name":"Shoes","url":"https://shop.example/p/37","price":4990,"currency":"USD","rating":4,"isNew":false,"brand":null}"""),
            ContractJson.of(product),
        )
    }

    @Test
    fun `a different value, a missing field and an extra field each fail`() {
        val base = """"id":"37","name":"Shoes","url":"https://shop.example/p/37","currency":"USD","isNew":false"""
        listOf(
            """{$base,"price":4990.5,"rating":4}""",
            """{$base,"price":4990}""",
            """{$base,"price":4990,"rating":4,"brand":"Nike"}""",
            """{$base,"price":4990,"rating":4,"categories":[]}""",
        ).forEach { json ->
            assertThrows(json, AssertionError::class.java) { ContractJson.assertEquals("product", expected(json), ContractJson.of(product)) }
        }
    }
}
