package com.personalization.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loading state of a data widget follows its latest request, and an error can be retried. */
class LoadStateTest {

    private val states = mutableListOf<LoadState<List<String>>>()
    private val calls = mutableListOf<Pair<(List<String>) -> Unit, (Int, String?) -> Unit>>()
    private val controller = LoadController<List<String>>(
        isEmpty = { it.isEmpty() },
        post = { it() },
        onChange = { states += it }
    )
    private val request: LoadRequest<List<String>> = { onSuccess, onError -> calls += onSuccess to onError }

    @Test
    fun `it starts idle`() {
        assertEquals(LoadState.Idle, controller.state)
    }

    @Test
    fun `an answer with data is content`() {
        controller.load(request)
        calls.last().first(listOf("a"))

        assertEquals(listOf(LoadState.Loading, LoadState.Content(listOf("a"))), states)
    }

    @Test
    fun `an empty answer is empty`() {
        controller.load(request)
        calls.last().first(emptyList())

        assertEquals(LoadState.Empty, controller.state)
    }

    @Test
    fun `a failed request is an error, and retry repeats it`() {
        controller.load(request)
        calls.last().second(500, "server error")
        assertEquals(LoadState.Error(LoadError(500, "server error")), controller.state)

        controller.retry()
        calls.last().first(listOf("a"))

        assertEquals(2, calls.size)
        assertEquals(LoadState.Content(listOf("a")), controller.state)
    }

    @Test
    fun `a late answer of a replaced request is dropped`() {
        controller.load(request)
        controller.load(request)
        calls[1].first(listOf("new"))
        calls[0].first(listOf("old"))

        assertEquals(LoadState.Content(listOf("new")), controller.state)
    }

    @Test
    fun `reset goes back to idle and drops the answer in flight`() {
        controller.load(request)
        controller.reset()
        calls.last().first(listOf("late"))

        assertEquals(LoadState.Idle, controller.state)
        controller.retry()
        assertEquals(1, calls.size)
    }

    @Test
    fun `loading too is set through post, not on the calling thread`() {
        val queue = mutableListOf<() -> Unit>()
        val queued = LoadController<List<String>>(post = { queue += it }, onChange = { states += it })

        queued.load(request)
        assertEquals(LoadState.Idle, queued.state)
        assertTrue(states.isEmpty())

        queue.toList().forEach { it() }
        assertEquals(LoadState.Loading, queued.state)
    }

    @Test
    fun `retry while loading sends no second request`() {
        controller.load(request)

        controller.retry()

        assertEquals(1, calls.size)
        assertEquals(LoadState.Loading, controller.state)
    }
}
