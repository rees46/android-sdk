package com.personalization.ui.widgets

import com.google.gson.JsonParser
import com.personalization.api.responses.search.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which facets the filters screen shows, the category's raw `filters` read as facets, value titles. */
class ListingSupportTest {

    private fun facet(count: Int, values: Int = 2) =
        SearchFilter(count = count, values = (1..values).associate { "v$it" to it })

    // In answer order: counts not sorted, some facets with one value only.
    private val available = linkedMapOf(
        "is_preorder" to facet(2428, values = 1),
        "Тип" to facet(1992),
        "Бренд" to facet(2408),
        "Цвет" to facet(1405),
        "Высота" to facet(778),
        "merchant" to facet(5000, values = 1),
        "Ширина" to facet(762)
    )

    @Test
    fun `facets with more than one value go by product count, the first max of them`() {
        assertEquals(listOf("Бренд", "Тип", "Цвет"), visibleFacets(available, emptyMap(), null, 3))
    }

    @Test
    fun `null or zero max shows every facet with more than one value`() {
        val all = listOf("Бренд", "Тип", "Цвет", "Высота", "Ширина")
        assertEquals(all, visibleFacets(available, emptyMap(), null, null))
        assertEquals(all, visibleFacets(available, emptyMap(), null, 0))
    }

    @Test
    fun `equal counts keep the answer order`() {
        val tied = linkedMapOf("b" to facet(10), "a" to facet(10), "c" to facet(20))
        assertEquals(listOf("c", "b", "a"), visibleFacets(tied, emptyMap(), null, 10))
    }

    @Test
    fun `a facet with checked values stays visible past the limit, in its place`() {
        val checked = mapOf("Высота" to setOf("v1"), "merchant" to setOf("v1"), "Тип" to emptySet<String>())

        assertEquals(
            listOf("merchant", "Бренд", "Тип", "Высота"),
            visibleFacets(available, checked, null, 2)
        )
    }

    @Test
    fun `a checked facet missing from the answer goes last`() {
        assertEquals(
            listOf("Бренд", "gone"),
            visibleFacets(available, mapOf("gone" to setOf("x")), null, 1)
        )
    }

    @Test
    fun `the host list wins, in the host order and without the limit`() {
        val wanted = linkedSetOf("Ширина", "is_preorder", "absent", "Бренд")

        assertEquals(listOf("Ширина", "is_preorder", "Бренд"), visibleFacets(available, emptyMap(), wanted, 1))
        assertEquals(
            listOf("Ширина", "is_preorder", "Бренд", "Цвет"),
            visibleFacets(available, mapOf("Цвет" to setOf("v1")), wanted, 1)
        )
    }

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
    fun `a value that is a JSON object with a title reads as that title`() {
        assertEquals(
            "Суперцена",
            jsonTitle("""{"slug": "super_price", "title": "Суперцена", "text_color": "#ffffff", "priority": 1}""")
        )
        assertEquals("Суперцена", jsonTitle("""  {"title": " Суперцена "}  """))
    }

    @Test
    fun `anything else has no title`() {
        assertNull(jsonTitle("Суперцена"))
        assertNull(jsonTitle("""{"title": "Суперцена""""))
        assertNull(jsonTitle("""{"title": "A"} tail"""))
        assertNull(jsonTitle("""{"slug": "new"}"""))
        assertNull(jsonTitle("""{"title": 5}"""))
        assertNull(jsonTitle("""{"title": {"ru": "Суперцена"}}"""))
        assertNull(jsonTitle("""{"title": "   "}"""))
        assertNull(jsonTitle("""["title"]"""))
        assertNull(jsonTitle(""))
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
