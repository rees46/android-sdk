package com.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [PreferencesPartition.keyFor], the per-shop SharedPreferences naming introduced in
 * Release 2. The derived name is the file name on disk, so it must be stable and filename-safe.
 */
class PreferencesPartitionTest {

    @Test
    fun `each shop gets a distinct partition key`() {
        assertNotEquals(
            PreferencesPartition.keyFor("shop-a"),
            PreferencesPartition.keyFor("shop-b")
        )
    }

    @Test
    fun `the key is stable for a given shop`() {
        assertEquals(
            PreferencesPartition.keyFor("c1140c8254976de297c3caf971701a"),
            PreferencesPartition.keyFor("c1140c8254976de297c3caf971701a")
        )
    }

    @Test
    fun `a hex shop id is carried through verbatim under the prefix`() {
        assertEquals(
            "personalization_sdk_c1140c8254976de297c3caf971701a",
            PreferencesPartition.keyFor("c1140c8254976de297c3caf971701a")
        )
    }

    @Test
    fun `unsafe filename characters are replaced`() {
        val key = PreferencesPartition.keyFor("shop/../weird id")

        assertTrue("key must not contain path or space chars: $key", key.none { it == '/' || it == ' ' })
        // "/../" is four unsafe chars and the space is a fifth, each replaced with '_'.
        assertEquals("personalization_sdk_shop____weird_id", key)
    }

    @Test
    fun `the partition key is never the legacy key`() {
        assertNotEquals(PreferencesPartition.LEGACY_KEY, PreferencesPartition.keyFor("any-shop"))
    }
}
