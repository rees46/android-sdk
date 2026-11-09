package com.personalization.demo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The popups of triggers are off while a game screen is started and come back as they were. */
class GamePopupsTest {

    private var auto: Boolean? = true
    private val writes = mutableListOf<Boolean>()
    private val popups = GamePopups(read = { auto }, write = { auto = it; writes += it })

    @Test
    fun `a game screen turns the popups off until it stops`() {
        popups.started()
        assertEquals(false, auto)
        popups.stopped()
        assertEquals(true, auto)
    }

    @Test
    fun `a game screen that takes the place of another keeps them off`() {
        popups.started()
        popups.started()
        popups.stopped()
        assertEquals(false, auto)
        popups.stopped()
        assertEquals(true, auto)
        assertEquals(listOf(false, true), writes)
    }

    @Test
    fun `popups the app turned off stay off`() {
        auto = false
        popups.started()
        popups.stopped()
        assertEquals(false, auto)
    }

    @Test
    fun `without an SDK nothing is written`() {
        auto = null
        popups.started()
        popups.stopped()
        popups.stopped()
        assertTrue(writes.isEmpty())
    }
}
