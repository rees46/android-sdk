package com.personalization.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Code 128 against hand-computed symbols: the check symbol is where encoders usually go wrong, and
 * a wrong one makes every scan fail while the bars still look fine.
 */
class PersonalizationCode128Test {

    @Test
    fun `text goes to set B`() {
        // Start B, P J J 1 2 3 C, check (104 + 48 + 2*42 + 3*42 + 4*17 + 5*18 + 6*19 + 7*35) % 103.
        assertEquals(
            listOf(104, 48, 42, 42, 17, 18, 19, 35, 55),
            PersonalizationCode128.values("PJJ123C")
        )
        val modules = PersonalizationCode128.encode("PJJ123C")!!
        assertEquals(9 * 11 + 13, modules.size)
        assertEquals("11010010000", modules.bits(0, 11))
        assertEquals("1100011101011", modules.bits(modules.size - 13, 13))
    }

    @Test
    fun `digits go to set C two at a time`() {
        assertEquals(listOf(105, 12, 34, 82), PersonalizationCode128.values("1234"))
        val modules = PersonalizationCode128.encode("1234")!!
        assertEquals(4 * 11 + 13, modules.size)
        assertEquals("11010011100", modules.bits(0, 11))
    }

    @Test
    fun `an odd last digit switches to set B`() {
        // Start C, 12, CODE B, '3', check (105 + 12 + 2*100 + 3*19) % 103.
        assertEquals(listOf(105, 12, 100, 19, 65), PersonalizationCode128.values("123"))
        assertEquals(5 * 11 + 13, PersonalizationCode128.encode("123")!!.size)
    }

    @Test
    fun `what Code 128 cannot carry is not encoded`() {
        assertNull(PersonalizationCode128.encode(""))
        assertNull(PersonalizationCode128.encode("é"))
        assertNull(PersonalizationCode128.encode("tab\there"))
    }

    private fun BooleanArray.bits(from: Int, count: Int): String =
        (from until from + count).joinToString("") { if (this[it]) "1" else "0" }
}
