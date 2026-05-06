package com.personalization.features.search

import com.google.gson.Gson
import com.personalization.api.responses.search.SearchBlankResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchBlankResponseParsingTest {

    private val gson = Gson()

    private val json = """
        {
            "html": "",
            "products": [],
            "suggests": [],
            "popular_categories": [{"name": "Электроника", "url": "/electronics"}],
            "popular_brands": [{"name": "Samsung", "url": "/samsung"}],
            "popular_links": [{"name": "Блог", "url": "/blog"}, {"name": "Кейсы", "url": "/cases"}]
        }
    """.trimIndent()

    @Test
    fun `popular fields are parsed from JSON`() {
        val response = gson.fromJson(json, SearchBlankResponse::class.java)

        assertEquals(1, response.popularCategories!!.size)
        assertEquals("Электроника", response.popularCategories!![0].name)
        assertEquals("/electronics", response.popularCategories!![0].url)

        assertEquals(1, response.popularBrands!!.size)
        assertEquals("Samsung", response.popularBrands!![0].name)

        assertEquals(2, response.popularLinks!!.size)
        assertEquals("Блог", response.popularLinks!![0].name)
        assertEquals("Кейсы", response.popularLinks!![1].name)
    }

    @Test
    fun `missing popular fields default to empty list`() {
        val minimalJson = """{"html": "", "products": [], "suggests": []}"""
        val response = gson.fromJson(minimalJson, SearchBlankResponse::class.java)

        assertNull(response.popularCategories)
        assertNull(response.popularBrands)
        assertNull(response.popularLinks)
    }
}
