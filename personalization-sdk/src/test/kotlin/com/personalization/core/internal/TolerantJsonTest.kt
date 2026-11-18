package com.personalization.core.internal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The answers are read as the contract asks: missing and `null` are null, numbers may come as strings and
 * strings as numbers, a value of the wrong kind is null, and an unknown value of a closed set is null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TolerantJsonTest {

    private val answer = JSONObject(
        """
        {
          "text": "37", "number": 37, "whole": 37.0, "fraction": 999.5, "big": 9007199254740993,
          "textNumber": " 12 ", "textFraction": "2.5", "textWhole": "3.0", "blank": "  ", "empty": "",
          "nothing": null, "yes": true, "no": false, "one": 1, "zero": 0, "textYes": "TRUE", "textNo": "0",
          "object": { "a": 1 }, "array": [ 1, "2", null, { "id": "x" }, 3.5, [ ] ], "huge": 1e300
        }
        """.trimIndent()
    )

    @Test
    fun `missing fields and null are null whatever the type asked`() {
        listOf("absent", "nothing").forEach { key ->
            assertNull(answer.valueOrNull(key))
            assertNull(answer.stringOrNull(key))
            assertNull(answer.intOrNull(key))
            assertNull(answer.longOrNull(key))
            assertNull(answer.doubleOrNull(key))
            assertNull(answer.booleanOrNull(key))
            assertNull(answer.objectOrNull(key))
            assertNull(answer.arrayOrNull(key))
        }
    }

    @Test
    fun `a string may come as a number, a whole one without a fraction`() {
        assertEquals("37", answer.stringOrNull("text"))
        assertEquals("37", answer.stringOrNull("number"))
        assertEquals("37", answer.stringOrNull("whole"))
        assertEquals("999.5", answer.stringOrNull("fraction"))
        assertEquals("true", answer.stringOrNull("yes"))
        assertNull(answer.stringOrNull("object"))
        assertNull(answer.stringOrNull("array"))
        assertEquals("", answer.stringOrNull("empty"))
        assertNull(answer.nonBlankStringOrNull("empty"))
        assertNull(answer.nonBlankStringOrNull("blank"))
        assertEquals("37", answer.nonBlankStringOrNull("number"))
    }

    @Test
    fun `a number may come as a string, and a whole number is only a whole one`() {
        assertEquals(37, answer.intOrNull("number"))
        assertEquals(37, answer.intOrNull("whole"))
        assertEquals(37, answer.intOrNull("text"))
        assertEquals(12, answer.intOrNull("textNumber"))
        assertEquals(3, answer.intOrNull("textWhole"))
        assertNull(answer.intOrNull("fraction"))
        assertNull(answer.intOrNull("textFraction"))
        assertNull(answer.intOrNull("big"))
        assertEquals(9007199254740993L, answer.longOrNull("big"))
        assertNull(answer.intOrNull("yes"))
        assertNull(answer.intOrNull("blank"))

        assertEquals(999.5, answer.doubleOrNull("fraction")!!, 0.0)
        assertEquals(2.5, answer.doubleOrNull("textFraction")!!, 0.0)
        assertEquals(37.0, answer.doubleOrNull("number")!!, 0.0)
        assertEquals(1e300, answer.doubleOrNull("huge")!!, 0.0)
        assertNull(answer.longOrNull("huge"))
        assertNull(answer.doubleOrNull("object"))
    }

    @Test
    fun `a boolean may come as a string or as 1 and 0`() {
        assertEquals(true, answer.booleanOrNull("yes"))
        assertEquals(false, answer.booleanOrNull("no"))
        assertEquals(true, answer.booleanOrNull("one"))
        assertEquals(false, answer.booleanOrNull("zero"))
        assertEquals(true, answer.booleanOrNull("textYes"))
        assertEquals(false, answer.booleanOrNull("textNo"))
        assertNull(answer.booleanOrNull("number"))
        assertNull(answer.booleanOrNull("text"))
    }

    @Test
    fun `objects and arrays are read only as what they are`() {
        assertEquals(1, answer.objectOrNull("object")?.intOrNull("a"))
        assertNull(answer.objectOrNull("array"))
        assertNull(answer.arrayOrNull("object"))
        assertEquals(6, answer.arrayOrNull("array")?.length())
    }

    @Test
    fun `an array keeps the elements it can read and leaves out the others`() {
        val array = answer.getJSONArray("array")

        assertEquals(listOf("x"), array.mapObjects { it.stringOrNull("id") })
        assertEquals(listOf("1", "2", "3.5"), array.strings())
        assertEquals(emptyList<String>(), JSONArray().strings())
        assertEquals(emptyList<String>(), JSONArray("[{}, {}]").mapObjects { it.stringOrNull("id") })
    }

    @Test
    fun `an unknown value of a closed set is null, not an error`() {
        val values = listOf(Kind.INSTANT_SEARCH, Kind.DYNAMIC)

        assertEquals(Kind.INSTANT_SEARCH, valueOfWire("instant_search", values) { it.wire })
        assertNull(valueOfWire("agent_search", values) { it.wire })
        assertNull(valueOfWire(null, values) { it.wire })
    }

    private enum class Kind(val wire: String) { INSTANT_SEARCH("instant_search"), DYNAMIC("dynamic") }
}
