package com.personalization.core.internal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The test harness of the core: the contract fixtures load and are well formed, and the fakes behave.
 * No behaviour of the SDK is checked here yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreHarnessTest {

    @Test
    fun `init fixtures pair a server answer with an expected state`() {
        val cases = ContractFixtures.cases("init")
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            assertTrue(name, case.has("response"))
            assertTrue(name, case.getJSONObject("expect").has("state"))
            val status = case.getJSONObject("response").opt("status")
            assertTrue(name, status == null || status == org.json.JSONObject.NULL || status is Int)
        }
    }

    @Test
    fun `tracking fixtures pair a call with a request or an error`() {
        val cases = ContractFixtures.cases("tracking")
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            val name = case.getString("name")
            val call = case.getJSONObject("call")
            assertEquals(name, "tracking", call.getString("namespace"))
            assertTrue(name, call.getString("method").isNotEmpty())
            assertTrue(name, case.has("request") xor case.has("error"))
            if (case.has("request")) {
                val request = case.getJSONObject("request")
                assertTrue(name, request.getString("method") in setOf("GET", "POST"))
                assertFalse(name, request.getString("path").startsWith("/"))
            }
        }
    }

    @Test
    fun `the fake clock moves only when told`() {
        val clock = FakeClock(now = 1_000)
        clock.advance(2 * 60 * 60 * 1000L)
        assertEquals(1_000 + 2 * 60 * 60 * 1000L, clock.nowMillis())
    }

    @Test
    fun `fake connectivity and lifecycle tell listeners about changes until cancelled`() {
        val network = FakeConnectivity(connected = false)
        val heard = mutableListOf<Boolean>()
        val handle = network.addListener { heard += it }
        network.set(true)
        network.set(true)
        handle.cancel()
        network.set(false)
        assertEquals(listOf(true), heard)

        val lifecycle = FakeAppLifecycle(foreground = true)
        val states = mutableListOf<Boolean>()
        lifecycle.addListener { states += it }
        lifecycle.set(false)
        assertEquals(listOf(false), states)
        assertFalse(lifecycle.isForeground)
    }

    @Test
    fun `the main dispatcher is looked up on first use, not when the dispatchers are made`() {
        var lookedUp = 0
        val dispatchers = CoreDispatchers(main = { lookedUp++; kotlinx.coroutines.Dispatchers.Unconfined })
        assertEquals(0, lookedUp)

        dispatchers.main
        dispatchers.main
        assertEquals(1, lookedUp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test dispatchers run the core's work when the test lets them`() = runTest {
        val dispatchers = testCoreDispatchers(testScheduler)
        var ran = false
        launch { withContext(dispatchers.io) { ran = true } }
        assertFalse(ran)
        advanceUntilIdle()
        assertTrue(ran)
    }
}
