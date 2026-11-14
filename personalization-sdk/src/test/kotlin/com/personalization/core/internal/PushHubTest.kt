package com.personalization.core.internal

import com.personalization.core.PushEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** One event of one message goes once: reserved while it is handed to a queue, sent once a queue accepted it. */
class PushHubTest {

    @Before
    fun setUp() = PushHub.reset()

    @After
    fun tearDown() = PushHub.reset()

    private val retried = mutableListOf<String>()

    private fun reserve(name: String, event: PushEvent = PushEvent.CLICKED, code: String = "m-1") =
        PushHub.reserve(SHOP, event, code) { retried += name }

    @Test
    fun `an event a queue accepted is a repeat from then on`() {
        assertEquals(PushHub.Reservation.TAKEN, reserve("first"))
        PushHub.confirm(SHOP, PushEvent.CLICKED, "m-1")

        assertEquals(PushHub.Reservation.REPEAT, reserve("again"))
        assertEquals("another event of the message is its own", PushHub.Reservation.TAKEN, reserve("closed", PushEvent.CLOSED))
        assertEquals("another shop's is its own", PushHub.Reservation.TAKEN, PushHub.reserve("other", PushEvent.CLICKED, "m-1") {})
    }

    @Test
    fun `a repeat that comes while the first is handed over goes if the first is not accepted`() {
        assertEquals(PushHub.Reservation.TAKEN, reserve("first"))
        assertEquals(PushHub.Reservation.PARKED, reserve("second"))
        assertEquals(PushHub.Reservation.PARKED, reserve("third"))
        assertEquals(emptyList<String>(), retried)

        // The queue refused the first: the repeats try again, and the event is free for them.
        PushHub.release(SHOP, PushEvent.CLICKED, "m-1")
        assertEquals(listOf("second", "third"), retried)
        assertEquals(PushHub.Reservation.TAKEN, reserve("fourth"))
    }

    @Test
    fun `the repeats that waited for an event a queue accepted send nothing`() {
        reserve("first")
        reserve("second")

        PushHub.confirm(SHOP, PushEvent.CLICKED, "m-1")
        PushHub.release(SHOP, PushEvent.CLICKED, "m-1")

        assertEquals(emptyList<String>(), retried)
        assertEquals(PushHub.Reservation.REPEAT, reserve("third"))
    }

    @Test
    fun `a repeat that tries again takes the event, and the next ones wait for it`() {
        reserve("first")
        PushHub.reserve(SHOP, PushEvent.CLICKED, "m-1") {
            // Tried again on the release: it takes the event for itself.
            retried += "second:" + PushHub.reserve(SHOP, PushEvent.CLICKED, "m-1") {}.name
        }
        PushHub.reserve(SHOP, PushEvent.CLICKED, "m-1") {
            retried += "third:" + PushHub.reserve(SHOP, PushEvent.CLICKED, "m-1") {}.name
        }

        PushHub.release(SHOP, PushEvent.CLICKED, "m-1")

        assertEquals(listOf("second:TAKEN", "third:PARKED"), retried)
    }

    private companion object {
        const val SHOP = "hub-shop"
    }
}
