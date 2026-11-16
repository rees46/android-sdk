package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.SdkError
import com.personalization.core.SdkState
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The new core's session (`core/session.md`, decision 0001): the server's `sid`, the two-hour window
 * every request checks, what extends it and what does not, and "the session changed" for the bridges.
 * Time is the fake clock's for the window and the test scheduler's for the waits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreSessionTest {

    private lateinit var context: Context
    private lateinit var storage: ShopStorage
    private lateinit var settings: UserSettingsRepository
    private val layout = StorageLayout(SHOP, null)
    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = testCoreDispatchers(scheduler)
    private val clock = FakeClock()
    private val connectivity = FakeConnectivity(connected = true)
    private val lifecycle = FakeAppLifecycle(foreground = true)
    private val state = InstanceState(shopId = { SHOP }, dispatchers = dispatchers)

    private val calls = mutableListOf<CompletableDeferred<HttpResult>>()
    private val sidAtSend = mutableListOf<String>()
    private val inits = mutableListOf<CoreInit>()
    private val heard = mutableListOf<Pair<String, Boolean>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        storage = ShopStorage(context, layout)
        settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) })
        SdkLogs.reset()
    }

    @After
    fun tearDown() {
        inits.forEach { it.release() }
        SdkLogs.reset()
    }

    private fun started(): CoreInit {
        val session = SessionWindow(settings, storage, clock).also { it.addListener { sid, changed -> heard += sid to changed } }
        return CoreInit(
            setup = InitSetup(SHOP),
            send = {
                sidAtSend += settings.getSid()
                CompletableDeferred<HttpResult>().also { calls += it }.await()
            },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = lifecycle,
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
            dispatchers = dispatchers,
            clock = clock,
            backoff = Backoff(random = { 0.5 }),
            session = session,
        ).also {
            inits += it
            it.start()
            scheduler.runCurrent()
        }
    }

    private fun answer(result: HttpResult) {
        calls.first { it.isActive }.complete(result)
        scheduler.runCurrent()
    }

    private fun ok(sid: String, did: String = "D1") = HttpResult.Success(JSONObject().put("did", did).put("seance", sid))

    /** A request with a result asking to go, as [CoreV2] asks. */
    private fun request(init: CoreInit, timeoutMs: Long = 10_000): Deferred<SdkError?> =
        CoroutineScope(StandardTestDispatcher(scheduler)).async { init.usableWithin(timeoutMs) }.also { scheduler.runCurrent() }

    private fun knownDevice(sid: String, lastActivity: Long) {
        settings.updateDid("D1")
        storage.preferences.edit().putString(StorageKeys.SID, sid).putLong(StorageKeys.SID_LAST_ACT, lastActivity).commit()
    }

    private val lastActivity: Long get() = settings.getSidLastActTime()

    // The sid comes from the server.

    @Test
    fun `a new device has no sid until init brings the server's, and the core makes none`() {
        val init = started()
        assertEquals("", settings.getSid())
        assertEquals(listOf(""), sidAtSend)

        answer(ok("SERVER1"))

        assertEquals("SERVER1", settings.getSid())
        assertEquals(clock.now, lastActivity)
        assertTrue(init.session.isAlive())
    }

    @Test
    fun `an install's own sid goes out once with init while its window is alive, then the server's is used`() {
        knownDevice("CLIENT_MADE", lastActivity = clock.now - 60_000)
        started()

        assertEquals(listOf("CLIENT_MADE"), sidAtSend)
        answer(ok("SERVER1"))
        assertEquals("SERVER1", settings.getSid())
    }

    @Test
    fun `init leaves out a sid whose window ended`() {
        knownDevice("OLD", lastActivity = clock.now - SessionWindow.WINDOW_MS - 1)
        started()

        assertEquals(listOf(""), sidAtSend)
        answer(ok("NEW"))
        assertEquals("NEW", settings.getSid())
    }

    @Test
    fun `a restart inside the window goes on with the same session`() {
        val first = started()
        answer(ok("S1"))
        first.release()
        clock.advance(SessionWindow.WINDOW_MS - 1)

        val next = started()

        assertTrue(next.session.isAlive())
        assertEquals("S1", sidAtSend.last())
    }

    // The window every request checks.

    @Test
    fun `a request inside the window goes at once and does not extend it`() {
        val init = started()
        answer(ok("S1"))
        val startedAt = lastActivity
        clock.advance(SessionWindow.WINDOW_MS - 1)

        val waiting = request(init)

        assertTrue(waiting.isCompleted)
        assertNull(waiting.getCompleted())
        assertEquals(1, sidAtSend.size)
        assertEquals("a request with a result does not extend the session", startedAt, lastActivity)
    }

    @Test
    fun `a request after the window ended sends init first and goes with the new sid, the state staying ready`() {
        val init = started()
        answer(ok("S1"))
        val states = mutableListOf<SdkState>()
        state.addListener { states += it }
        scheduler.runCurrent()
        states.clear()
        clock.advance(SessionWindow.WINDOW_MS + 1)

        val waiting = request(init)

        assertFalse("waits for the new session", waiting.isCompleted)
        assertEquals("the ended sid does not go", listOf("", ""), sidAtSend)
        answer(ok("S2"))
        assertNull(waiting.getCompleted())
        assertEquals("S2", settings.getSid())
        assertEquals(clock.now, lastActivity)
        assertEquals(SdkState.READY, state.current)
        assertTrue("no state change for a new session", states.isEmpty())
    }

    @Test
    fun `requests that find the window ended together send one init`() {
        val init = started()
        answer(ok("S1"))
        clock.advance(SessionWindow.WINDOW_MS + 1)

        val first = request(init)
        val second = request(init)

        assertEquals(2, sidAtSend.size)
        answer(ok("S2"))
        assertNull(first.getCompleted())
        assertNull(second.getCompleted())
    }

    @Test
    fun `when the new session's init fails the request goes on what the instance has`() {
        val init = started()
        answer(ok("S1"))
        clock.advance(SessionWindow.WINDOW_MS + 1)

        val waiting = request(init)
        answer(HttpResult.Failure(SdkError.Http(503)))

        assertNull(waiting.getCompleted())
        assertEquals("", settings.getSid())
        // A new session's /init that fails leaves the state as the start's answer set it.
        assertEquals(SdkState.READY, state.current)
        // While init is repeated, a request does not wait for it.
        assertNull(request(init).getCompleted())
    }

    @Test
    fun `an init answer without a seance starts a session without a sid, and requests do not send init after init`() {
        val init = started()
        answer(HttpResult.Success(JSONObject().put("did", "D1")))

        assertTrue(init.session.isAlive())
        assertEquals("", settings.getSid())
        repeat(3) { assertNull(request(init).getCompleted()) }
        assertEquals("one init, no storm", 1, calls.size)

        // Its window ends like any session's: the next request asks for a new one.
        clock.advance(SessionWindow.WINDOW_MS + 1)
        assertFalse(init.session.isAlive())
        request(init)
        assertEquals(2, calls.size)
    }

    @Test
    fun `an init answer without a seance keeps the live sid`() {
        knownDevice("LIVE", lastActivity = clock.now - 60_000)
        val init = started()
        clock.advance(1_000)
        answer(HttpResult.Success(JSONObject().put("did", "D1")))

        assertEquals("LIVE", settings.getSid())
        assertEquals(clock.now, lastActivity)
        assertTrue(init.session.isAlive())
    }

    @Test
    fun `a failed init of a new session keeps the state and does not act on the saved answer again`() {
        val answers = mutableListOf<Boolean>()
        val init = CoreInit(
            setup = InitSetup(SHOP),
            send = { CompletableDeferred<HttpResult>().also { calls += it }.await() },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = lifecycle,
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) {
                    answers += fresh
                }

                override fun onUsable() = Unit
            },
            dispatchers = dispatchers,
            clock = clock,
            backoff = Backoff(random = { 0.5 }),
            session = SessionWindow(settings, storage, clock),
        ).also {
            inits += it
            it.start()
            scheduler.runCurrent()
        }
        answer(ok("S1"))
        clock.advance(SessionWindow.WINDOW_MS + 1)

        request(init)
        answer(HttpResult.Failure(SdkError.Http(503)))
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        answer(HttpResult.Failure(SdkError.Network))

        assertEquals(SdkState.READY, state.current)
        assertEquals("only the start's answer, and it was fresh", listOf(true), answers)
    }

    @Test
    fun `a relaunch more than two hours after the last event is ready at once, and offline requests fail at once`() {
        knownDevice("OLD", lastActivity = clock.now - SessionWindow.WINDOW_MS - 1)
        connectivity.set(false)
        val init = started()

        assertEquals(SdkState.READY, state.current)
        // A request waits for the launch's /init, which finds no network at once: the request goes on at
        // once too (and fails at once in the client), without waiting for a repeat.
        val waiting = request(init)
        assertFalse(waiting.isCompleted)
        answer(HttpResult.Failure(SdkError.Network))
        assertNull(waiting.getCompleted())
        assertNull(request(init).getCompleted())
    }

    @Test
    fun `a wait for the new session runs out with the request's timeout`() {
        val init = started()
        answer(ok("S1"))
        clock.advance(SessionWindow.WINDOW_MS + 1)

        val waiting = request(init, timeoutMs = 10_000)
        scheduler.advanceTimeBy(10_001)
        scheduler.runCurrent()

        assertEquals(SdkError.Timeout, waiting.getCompleted())
    }

    @Test
    fun `a request at the start of a launch after the window ended waits for the start's init`() {
        knownDevice("OLD", lastActivity = clock.now - SessionWindow.WINDOW_MS - 1)
        val init = CoreInit(
            setup = InitSetup(SHOP),
            send = { sidAtSend += settings.getSid(); CompletableDeferred<HttpResult>().also { calls += it }.await() },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = lifecycle,
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
            dispatchers = dispatchers,
            clock = clock,
        ).also { inits += it }
        init.start()
        // Ready at once on its stored did (decision 1), before the storage is even prepared.
        assertEquals(SdkState.READY, state.current)

        val waiting = request(init)
        assertFalse(waiting.isCompleted)
        assertEquals("one init, the start's", 1, sidAtSend.size)
        answer(ok("S2"))
        assertNull(waiting.getCompleted())
        assertEquals("S2", settings.getSid())
    }

    // What extends the window.

    @Test
    fun `an event extends a live session, not an ended one`() {
        val init = started()
        answer(ok("S1"))

        clock.advance(SessionWindow.WINDOW_MS - 1)
        init.session.extend(clock.now)
        assertEquals(clock.now, lastActivity)
        clock.advance(SessionWindow.WINDOW_MS - 1)
        assertTrue(init.session.isAlive())

        clock.advance(SessionWindow.WINDOW_MS + 1)
        val ended = lastActivity
        init.session.extend(clock.now)
        assertEquals(ended, lastActivity)
        assertFalse(init.session.isAlive())
    }

    @Test
    fun `coming back to the foreground inside the window sends nothing, after it a new session`() {
        val init = started()
        answer(ok("S1"))
        lifecycle.set(false)
        clock.advance(SessionWindow.WINDOW_MS)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertEquals(1, sidAtSend.size)

        lifecycle.set(false)
        clock.advance(1)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertEquals(2, sidAtSend.size)
        assertEquals("the ended sid does not go", "", sidAtSend.last())
        answer(ok("S2"))
        assertTrue(init.session.isAlive())
    }

    // "The session changed".

    @Test
    fun `the listeners hear every sid init brings, and whether it changed`() {
        val init = started()
        answer(ok("S1"))
        init.checkShop()
        scheduler.runCurrent()
        answer(ok("S1"))
        clock.advance(SessionWindow.WINDOW_MS + 1)
        init.renewSession()
        scheduler.runCurrent()
        answer(ok("S2"))

        assertEquals(listOf("S1" to true, "S1" to false, "S2" to true), heard)
    }

    @Test
    fun `a refused or released instance renews nothing`() {
        val init = started()
        answer(HttpResult.Failure(SdkError.Http(403)))
        clock.advance(SessionWindow.WINDOW_MS + 1)

        assertFalse(init.renewSession())
        assertEquals(SdkError.ShopBlocked, request(init).getCompleted())
        init.release()
        assertFalse(init.renewSession())
        assertEquals(1, sidAtSend.size)
    }

    private companion object {
        const val SHOP = "session-shop"
    }
}
