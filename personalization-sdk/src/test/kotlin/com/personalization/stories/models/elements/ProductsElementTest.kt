package com.personalization.stories.models.elements

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers parsing of the slide's `products` element, whose captions drive the carousel toggle
 * button. The backend nests them inside a `labels` object; reading them from the element itself
 * fails silently — the button still renders, just with no text on it.
 *
 * The payload below is the shape returned by `GET /stories/{code}`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProductsElementTest {

    private fun element(json: String) = ProductsElement(JSONObject(json))

    @Test
    fun `reads both carousel captions out of the nested labels object`() {
        val parsed = element(
            """
            {
              "type": "products",
              "labels": {
                "hide_carousel": "Скрыть товары",
                "show_carousel": "Товары для геймеров"
              },
              "products": []
            }
            """.trimIndent()
        )

        assertEquals("Товары для геймеров", parsed.labelShow)
        assertEquals("Скрыть товары", parsed.labelHide)
    }

    @Test
    fun `falls back to empty captions when the labels object is absent`() {
        val parsed = element("""{"type":"products","products":[]}""")

        assertEquals("", parsed.labelShow)
        assertEquals("", parsed.labelHide)
    }

    @Test
    fun `captions at the element root are ignored, they are not where the backend puts them`() {
        val parsed = element(
            """
            {
              "type": "products",
              "show_carousel": "Wrong place",
              "hide_carousel": "Wrong place",
              "labels": { "show_carousel": "Right place", "hide_carousel": "Also right" },
              "products": []
            }
            """.trimIndent()
        )

        assertEquals("Right place", parsed.labelShow)
        assertEquals("Also right", parsed.labelHide)
    }

    @Test
    fun `parses the products carried alongside the labels`() {
        val parsed = element(
            """
            {
              "type": "products",
              "labels": { "show_carousel": "Show", "hide_carousel": "Hide" },
              "products": [
                { "id": "300201", "name": "Игровая мышь Bloody V8M Max, Black",
                  "url": "https://www.technodom.kz/p/igrovaya-mysh-bloody-v8m-max-black-300201" },
                { "id": "300202", "name": "Второй товар", "url": "https://example.com/2" }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, parsed.getProducts().size)
        assertEquals("Игровая мышь Bloody V8M Max, Black", parsed.getProducts()[0].name)
    }
}
