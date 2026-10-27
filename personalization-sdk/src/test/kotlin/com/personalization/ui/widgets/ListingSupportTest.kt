package com.personalization.ui.widgets

import com.google.gson.JsonParser
import com.personalization.api.responses.search.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The category's raw `filters` read as facets, and the typed price read as a number. */
class ListingSupportTest {

    @Test
    fun `the raw category filters read as facets, the malformed ones skipped`() {
        val json = JsonParser.parseString(
            """
            {
              "Цвет": {"count": 1405, "values": {"Белый": 172, "Чёрный": 99}},
              "broken": "text",
              "no_values": {"count": 3},
              "odd": {"count": "many", "values": {"a": "x", "b": 2}}
            }
            """
        ).asJsonObject

        val facets = parseFacets(json)

        assertEquals(listOf("Цвет", "odd"), facets.keys.toList())
        assertEquals(1405, facets.getValue("Цвет").count)
        assertEquals(mapOf("Белый" to 172, "Чёрный" to 99), facets.getValue("Цвет").values)
        assertEquals(0, facets.getValue("odd").count)
        assertEquals(mapOf("a" to 0, "b" to 2), facets.getValue("odd").values)
        assertEquals(emptyMap<String, SearchFilter>(), parseFacets(null))
    }

    @Test
    fun `a typed price reads as a number`() {
        assertEquals(1000.0, priceNumber(" 1 000 ")!!, 0.0)
        assertEquals(99.5, priceNumber("99,5")!!, 0.0)
        assertNull(priceNumber("abc"))
        assertNull(priceNumber(""))
        assertNull(priceNumber(null))
    }
}
