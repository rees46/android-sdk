package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.core.SdkError
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.json.JSONArray
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
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The new core's `/init` (`core/init.md`): every row of the answer table, the pauses, the refused
 * state across launches, decision 1, the saved answer, the wait for `ready`, the triggers and the
 * stale answers. Time is the test scheduler's; the server is a fake that answers each `/init` when told.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreInitTest {

    private lateinit var context: Context
    private lateinit var storage: ShopStorage
    private lateinit var settings: UserSettingsRepository
    private val layout = StorageLayout(SHOP, null)
    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = testCoreDispatchers(scheduler)
    private val clock = FakeClock(System.currentTimeMillis())
    private val connectivity = FakeConnectivity(connected = true)
    private val lifecycle = FakeAppLifecycle(foreground = true)
    private val state = InstanceState(shopId = { SHOP }, dispatchers = dispatchers)
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** What the fake server answers: queued answers first, otherwise a call that waits to be answered. */
    private val queued = ArrayDeque<HttpResult>()
    private val calls = mutableListOf<CompletableDeferred<HttpResult>>()
    private val sent = mutableListOf<JSONObject>()
    private val sidAtSend = mutableListOf<String>()

    private val answers = mutableListOf<Pair<JSONObject, Boolean>>()
    private var usable = 0
    private val inits = mutableListOf<CoreInit>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        storage = ShopStorage(context, layout)
        settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) })
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        inits.forEach { it.release() }
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun init(setup: InitSetup = InitSetup(SHOP), timeZone: TimeZone = TimeZone.getTimeZone("UTC")): CoreInit = CoreInit(
        setup = setup,
        send = { params ->
            sent += params
            sidAtSend += settings.getSid()
            queued.removeFirstOrNull() ?: CompletableDeferred<HttpResult>().also { calls += it }.await()
        },
        settings = settings,
        storage = storage,
        state = state,
        connectivity = connectivity,
        lifecycle = lifecycle,
        hooks = object : InitHooks {
            override fun onAnswer(answer: JSONObject, fresh: Boolean) {
                answers += answer to fresh
            }

            override fun onUsable() {
                usable++
            }
        },
        dispatchers = dispatchers,
        clock = clock,
        backoff = Backoff(random = { 0.5 }),
        timeZone = { timeZone },
    ).also { inits += it }

    /** Answers the oldest `/init` still waiting. */
    private fun answer(result: HttpResult) {
        val waiting = calls.firstOrNull { it.isActive }
        if (waiting != null) waiting.complete(result) else queued += result
        scheduler.runCurrent()
    }

    private fun started(setup: InitSetup = InitSetup(SHOP)): CoreInit = init(setup).also {
        it.start()
        scheduler.runCurrent()
    }

    private fun after(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun ok(did: String = "D1", seance: String = "S1") = HttpResult.Success(JSONObject().put("did", did).put("seance", seance))
    private fun http(code: Int, retryAfterMs: Long? = null) = HttpResult.Failure(SdkError.Http(code), null, retryAfterMs)

    private fun codes(code: String) = logged.filter { it.code == code }

    // The answer table.

    @Test
    fun `a 2xx with a did is ready, keeps did and sid, saves the answer and clears a refusal of an earlier launch`() {
        storage.isRefused = true
        started()

        answer(ok("D1", "S1"))

        assertEquals(SdkState.READY, state.current)
        assertEquals("D1", settings.getDid())
        assertEquals("S1", settings.getSid())
        assertEquals("D1", storage.initAnswer()?.getString("did"))
        assertFalse(storage.isRefused)
        assertEquals(listOf(true), answers.map { it.second })
        assertEquals(1, usable)
        assertEquals(LogLevel.INFO, codes(LogCodes.INIT_SUCCEEDED).single().level)
    }

    @Test
    fun `no answer, a 2xx without a did, 5xx, 408, 410, 417, 426 and 429 are repeated`() {
        val repeated = listOf(
            HttpResult.Failure(SdkError.Network),
            HttpResult.Failure(SdkError.Timeout),
            HttpResult.Failure(SdkError.Decoding),
            HttpResult.Success(JSONObject()),
            HttpResult.Success(JSONObject().put("seance", "S")),
            HttpResult.Success(JSONArray()),
            http(500), http(502), http(503), http(504),
            http(408), http(410), http(417), http(426), http(429),
        )
        repeated.forEach { result ->
            setUp()
            val init = started()
            answer(result)

            assertEquals("$result", SdkState.RETRYING, state.current)
            assertFalse(storage.isRefused)
            assertEquals(1, sent.size)
            after(1_000)
            assertEquals("$result is repeated after a second", 2, sent.size)
            init.release()
            sent.clear()
            calls.clear()
            queued.clear()
        }
    }

    @Test
    fun `a 2xx without a did is a decoding failure in the log`() {
        started()

        answer(HttpResult.Success(JSONObject().put("seance", "S")))

        assertEquals(LogLevel.WARNING, codes(LogCodes.DECODING_FAILED).single().level)
        assertEquals("", settings.getDid())
    }

    @Test
    fun `402, 403 and 404 refuse the shop, kept on disk`() {
        listOf(402, 403, 404).forEach { code ->
            setUp()
            logged.clear()
            val init = started()
            answer(http(code))

            assertEquals(SdkState.REFUSED, state.current)
            assertTrue(storage.isRefused)
            val refused = codes(LogCodes.INIT_REFUSED).single()
            assertEquals(LogLevel.ERROR, refused.level)
            assertEquals(code, refused.context["status"])
            assertTrue(codes(LogCodes.INIT_FAILED).isEmpty())
            // Not asked again during the run.
            after(60 * 60 * 1000L)
            assertEquals(1, sent.size)
            init.release()
            sent.clear()
            calls.clear()
        }
    }

    @Test
    fun `another 4xx refuses the shop too, and is an error of the SDK's request`() {
        listOf(400, 401, 405, 422, 451).forEach { code ->
            setUp()
            logged.clear()
            val init = started()
            answer(http(code))

            assertEquals(SdkState.REFUSED, state.current)
            assertTrue(storage.isRefused)
            assertEquals(1, codes(LogCodes.INIT_REFUSED).size)
            assertEquals(LogLevel.ERROR, codes(LogCodes.INIT_FAILED).single().level)
            init.release()
            sent.clear()
            calls.clear()
        }
    }

    // Pauses.

    @Test
    fun `repeats wait 1, 2, 4 and 8 seconds, logged as init_retry`() {
        started()
        answer(http(503))
        listOf(1_000L, 2_000L, 4_000L, 8_000L).forEachIndexed { index, pause ->
            after(pause - 1)
            assertEquals(index + 1, sent.size)
            after(1)
            assertEquals(index + 2, sent.size)
            answer(if (index < 3) http(503) else ok())
        }

        assertEquals(SdkState.READY, state.current)
        val retries = codes(LogCodes.INIT_RETRY)
        assertEquals(4, retries.size)
        assertTrue(retries.all { it.level == LogLevel.WARNING })
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), retries.map { it.context["delay_ms"] })
    }

    @Test
    fun `the Retry-After of a 429 or a 503 sets the pause, of other answers it does not`() {
        started()
        answer(http(503, retryAfterMs = 30_000))
        after(29_999)
        assertEquals(1, sent.size)
        after(1)
        assertEquals(2, sent.size)

        answer(http(429, retryAfterMs = 120_000))
        after(119_999)
        assertEquals(2, sent.size)
        after(1)
        assertEquals(3, sent.size)

        // The third repeat: 4 s, whatever a 500 says.
        answer(http(500, retryAfterMs = 600_000))
        after(4_000)
        assertEquals(4, sent.size)
    }

    @Test
    fun `an ordinary pause of init ends on the network's return, the foreground or a check of the shop`() {
        val init = started()
        answer(ok())
        // The session's window ended: the new session's /init fails and waits out its pause.
        clock.advance(SessionWindow.WINDOW_MS + 1)
        init.renewSession()
        scheduler.runCurrent()
        answer(http(500))
        after(500)
        assertEquals(2, sent.size)

        connectivity.set(false)
        connectivity.set(true)
        scheduler.runCurrent()
        assertEquals("the network back sends it at once", 3, sent.size)

        answer(http(500))
        lifecycle.set(false)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertEquals("so does the foreground", 4, sent.size)

        answer(http(500))
        init.checkShop()
        scheduler.runCurrent()
        assertEquals("and a check of the shop", 5, sent.size)
    }

    @Test
    fun `a pause the server named is waited in full whatever comes`() {
        val init = started()
        answer(http(503, retryAfterMs = 60_000))
        connectivity.set(false)
        connectivity.set(true)
        lifecycle.set(false)
        lifecycle.set(true)
        init.checkShop()
        after(59_999)
        assertEquals(1, sent.size)
        after(1)
        assertEquals(2, sent.size)
    }

    @Test
    fun `a Retry-After waits at least a second and at most five minutes`() {
        started()
        // 0, or a date already past: a second.
        answer(http(503, retryAfterMs = 0))
        after(999)
        assertEquals(1, sent.size)
        after(1)
        assertEquals(2, sent.size)

        // An hour, or a number too large for milliseconds: five minutes.
        answer(http(429, retryAfterMs = 3_600_000))
        after(299_999)
        assertEquals(2, sent.size)
        after(1)
        assertEquals(3, sent.size)

        answer(http(503, retryAfterMs = Long.MAX_VALUE))
        after(300_000)
        assertEquals(4, sent.size)
        assertEquals(listOf(1_000L, 300_000L, 300_000L), codes(LogCodes.INIT_RETRY).map { it.context["delay_ms"] })
    }

    // The refused state across launches.

    @Test
    fun `a shop refused at a launch starts refused at the next and is cleared by a successful init`() {
        started()
        answer(http(403))
        inits.single().release()

        // The next launch.
        val next = init()
        next.start()
        assertEquals(SdkState.REFUSED, state.current)
        assertEquals(SdkError.ShopBlocked, awaitReady(next, 10_000))
        scheduler.runCurrent()
        assertEquals(2, sent.size)

        // No answer keeps it refused while /init is repeated.
        answer(HttpResult.Failure(SdkError.Timeout))
        assertEquals(SdkState.REFUSED, state.current)
        after(1_000)
        answer(ok())

        assertEquals(SdkState.READY, state.current)
        assertFalse(storage.isRefused)
        assertEquals(null, awaitReady(next, 10_000))
    }

    @Test
    fun `a saved answer does not outweigh a refusal`() {
        storage.saveInitAnswer(JSONObject().put("did", "SAVED"))
        started()

        answer(http(402))

        assertEquals(SdkState.REFUSED, state.current)
        assertEquals(SdkError.ShopBlocked, awaitReady(inits.single(), 10_000))
    }

    // Decision 1 and the saved answer.

    @Test
    fun `a stored did without a saved answer is ready at once, and init still goes`() {
        settings.updateDid("OLD")
        val init = init()

        init.start()

        assertEquals(SdkState.READY, state.current)
        assertEquals(1, usable)
        scheduler.runCurrent()
        assertEquals(1, sent.size)
        answer(ok("OLD", "S2"))
        assertEquals(SdkState.READY, state.current)
        assertEquals("S2", settings.getSid())
        assertEquals(1, usable)
    }

    @Test
    fun `without an answer a device with a did retries and works as ready, told once`() {
        settings.updateDid("OLD")
        storage.saveInitAnswer(JSONObject().put("did", "OLD").put("popups_v3", true))
        val init = started()

        answer(HttpResult.Failure(SdkError.Network))
        after(1_000)
        answer(HttpResult.Failure(SdkError.Timeout))

        assertEquals(SdkState.RETRYING, state.current)
        assertEquals(null, awaitReady(init, 1))
        assertEquals(1, codes(LogCodes.INIT_SAVED_ANSWER).size)
        // The saved answer's flags, never its popup again.
        assertEquals(listOf(false), answers.map { it.second })
        assertTrue(answers.single().first.optBoolean("popups_v3"))
        assertEquals(1, usable)
    }

    @Test
    fun `the saved answer's did fills a shop without one and never replaces one`() {
        storage.saveInitAnswer(JSONObject().put("did", "SAVED"))
        started()
        assertEquals("SAVED", settings.getDid())
        assertEquals(SdkState.READY, state.current)
        inits.single().release()

        settings.updateDid("MINE")
        started()
        assertEquals("MINE", settings.getDid())
    }

    @Test
    fun `a new device without an answer retries and waits`() {
        val init = started()

        answer(HttpResult.Failure(SdkError.Timeout))

        assertEquals(SdkState.RETRYING, state.current)
        assertTrue(codes(LogCodes.INIT_SAVED_ANSWER).isEmpty())
        assertEquals(SdkError.Timeout, awaitReady(init, 10_000))
        assertEquals(0, usable)
    }

    // The config.

    @Test
    fun `the did of the config is taken only while the shop has none`() {
        started(InitSetup(SHOP, deviceId = "FROM_RN"))
        assertEquals("FROM_RN", settings.getDid())
        assertEquals(SdkState.READY, state.current)
        inits.single().release()

        started(InitSetup(SHOP, deviceId = "ANOTHER"))
        assertEquals("FROM_RN", settings.getDid())
    }

    @Test
    fun `the did of the config is taken once and after a deletion does not come back, even passed again`() {
        started(InitSetup(SHOP, deviceId = "FROM_RN"))
        assertEquals("FROM_RN", settings.getDid())
        inits.single().release()

        assertTrue(storage.deleteUserCredentials(includeQueue = true))
        started(InitSetup(SHOP, deviceId = "FROM_RN"))

        assertEquals("", settings.getDid())
        assertEquals(SdkState.INITIALIZING, state.current)
        assertTrue(codes(LogCodes.INVALID_CONFIG).isEmpty())
    }

    @Test
    fun `a did of the config that the shop does not take is logged once, as invalid_config with the reason device_id_kept`() {
        settings.updateDid("MINE")
        started(InitSetup(SHOP, deviceId = "FROM_RN"))
        inits.single().release()
        started(InitSetup(SHOP, deviceId = "FROM_RN"))

        assertEquals("MINE", settings.getDid())
        // The code and the reason of core/logging.md: device_id_kept is a reason, not a code.
        val kept = codes(LogCodes.INVALID_CONFIG).single()
        assertEquals(LogLevel.WARNING, kept.level)
        assertEquals(mapOf<String, Any?>("reason" to "device_id_kept"), kept.context)
        assertTrue("no did in the log", "MINE" !in kept.message && "FROM_RN" !in kept.message)
        assertTrue(logged.none { it.code == "device_id_kept" })
    }

    @Test
    fun `hasDid tells before the disk is read whether the start gives a did without init, and once it was read what it gave`() {
        // Nothing anywhere, read: no did, /init gives one.
        val none = started()
        assertFalse(none.hasDid())
        none.release()

        // The config brings one: the start will take it. Read, not taken.
        val configured = init(InitSetup(SHOP, deviceId = "FROM_RN")).also { it.start() }
        assertTrue(configured.hasDid())
        assertEquals("", settings.getDid())
        // Released before it read the disk: what the start would have taken still counts.
        configured.release()
        assertTrue(configured.hasDid())

        // needReInitialization forgets every did: none counts.
        val renewed = init(InitSetup(SHOP, deviceId = "FROM_RN", needReInitialization = true)).also { it.start() }
        assertFalse(renewed.hasDid())
        renewed.release()

        // The saved answer of an earlier /init brings one.
        storage.saveInitAnswer(JSONObject().put("did", "SAVED").put("seance", "S0"))
        val saved = init(InitSetup(SHOP)).also { it.start() }
        assertTrue(saved.hasDid())
        scheduler.runCurrent()
        assertEquals("SAVED", settings.getDid())
        assertTrue(saved.hasDid())
    }

    @Test
    fun `the pauses between repeats double up to five minutes and stay there`() {
        started()
        answer(http(503))
        repeat(11) { after(CoreInit.NETWORK_RECHECK_MS); answer(http(503)) }

        val pauses = codes(LogCodes.INIT_RETRY).map { it.context["delay_ms"] }
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 256_000L, 300_000L, 300_000L, 300_000L),
            pauses,
        )
    }

    @Test
    fun `a network that comes back without a word is found by init looking again within five minutes`() {
        connectivity.set(false)
        started()
        answer(HttpResult.Failure(SdkError.Network))
        assertEquals(1, sent.size)
        assertEquals(LogReasons.OFFLINE, codes(LogCodes.INIT_RETRY).single().context["reason"])

        // The system's callback never comes.
        connectivity.setQuietly(true)
        after(CoreInit.NETWORK_RECHECK_MS - 1)
        assertEquals(1, sent.size)
        after(1)
        assertEquals(2, sent.size)
        answer(ok())
        assertEquals(SdkState.READY, state.current)
    }

    @Test
    fun `a hook that throws is logged and changes nothing, the instance is ready and can send`() {
        var usableCalls = 0
        val failing = CoreInit(
            setup = InitSetup(SHOP),
            send = { queued.removeFirstOrNull() ?: CompletableDeferred<HttpResult>().also { calls += it }.await() },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = lifecycle,
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = throw IllegalStateException("the popup failed")
                override fun onUsable() {
                    usableCalls++
                    throw IllegalStateException("the token failed")
                }
            },
            dispatchers = dispatchers,
            clock = clock,
            backoff = Backoff(random = { 0.5 }),
        ).also { inits += it }
        failing.start()
        scheduler.runCurrent()

        answer(ok())

        assertEquals(SdkState.READY, state.current)
        assertEquals("D1", settings.getDid())
        assertEquals(1, usableCalls)
        val errors = codes(LogCodes.SDK_LOG).filter { it.level == LogLevel.ERROR }.map { it.message }
        assertEquals(listOf("Acting on /init failed: onAnswer", "Acting on /init failed: onUsable"), errors)
        assertEquals(null, runBlocking { failing.usableWithin(1) })
    }

    @Test
    fun `an init cancelled by something else than the SDK is repeated`() {
        started()

        answer(HttpResult.Failure(SdkError.Cancelled))

        assertEquals(SdkState.RETRYING, state.current)
        after(1_000)
        assertEquals(2, sent.size)
        answer(ok())
        assertEquals(SdkState.READY, state.current)
    }

    @Test
    fun `an answer whose start a deletion ended shows nothing and makes nothing usable`() {
        // Held right as the answer is taken: the hooks see the new start and do nothing.
        val init = started()
        val first = calls.single()
        init.holdForRestart()
        first.complete(ok("STALE"))
        scheduler.runCurrent()

        assertTrue(answers.isEmpty())
        assertEquals(0, usable)
    }

    @Test
    fun `needReInitialization forgets the did, the session and the saved answer before init`() {
        settings.updateDid("OLD")
        settings.updateSid("OLD_SID")
        storage.saveInitAnswer(JSONObject().put("did", "OLD"))

        started(InitSetup(SHOP, needReInitialization = true, deviceId = "IGNORED"))

        assertEquals(SdkState.INITIALIZING, state.current)
        assertEquals("", settings.getDid())
        assertEquals(listOf(""), sidAtSend)
        assertNull(storage.initAnswer())
        assertEquals(LogLevel.INFO, codes(LogCodes.DEVICE_ID_RESET).single().level)
        answer(ok("NEW", "S"))
        assertEquals("NEW", settings.getDid())
    }

    @Test
    fun `init carries the offset of now, summer time included, and the extras`() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        clock.now = 1_782_000_000_000L // July 2026
        init(InitSetup(SHOP, extraParams = { mapOf("testmode" to "true") }), berlin).start()
        scheduler.runCurrent()
        clock.now = 1_768_000_000_000L // January 2026
        answer(HttpResult.Failure(SdkError.Timeout))
        after(1_000)

        assertEquals(listOf("2", "1"), sent.map { it.getString("tz") })
        assertTrue(sent.all { it.getString("testmode") == "true" })
        assertTrue(sent.none { it.has("stream") || it.has("android_advertising_id") })
    }

    @Test
    fun `the advertising id goes only with sendAdvertisingId, read once`() {
        var reads = 0
        started(InitSetup(SHOP, advertisingId = { reads++; "gaid-1" }))
        answer(HttpResult.Failure(SdkError.Timeout))
        after(1_000)
        answer(ok())

        assertEquals(listOf("gaid-1", "gaid-1"), sent.map { it.getString("android_advertising_id") })
        assertEquals(1, reads)
        assertEquals(1, codes(LogCodes.ADVERTISING_ID_SENT).size)
    }

    @Test
    fun `on a pre-launch report device init is skipped`() {
        settings.updateDid("OLD")
        started(InitSetup(SHOP, skipInit = { true }))

        assertTrue(sent.isEmpty())
        assertEquals(SdkState.READY, state.current)
        assertEquals(LogLevel.INFO, codes(LogCodes.INIT_SKIPPED).single().level)
    }

    // Waiting for ready.

    @Test
    fun `a request waits for ready within its timeout`() {
        val init = started()
        val waiting = waitReady(init, 10_000)

        after(5_000)
        assertFalse(waiting.isCompleted)
        answer(ok())

        assertEquals(null, waiting.getCompleted())
    }

    @Test
    fun `a wait that runs out is a timeout, a refusal is shopBlocked, a release notInitialized`() {
        val init = started()
        val timedOut = waitReady(init, 10_000)
        after(10_000)
        assertEquals(SdkError.Timeout, timedOut.getCompleted())

        val blocked = waitReady(init, 10_000)
        answer(http(402))
        assertEquals(SdkError.ShopBlocked, blocked.getCompleted())

        setUp()
        val other = started()
        val released = waitReady(other, 10_000)
        other.release()
        scheduler.runCurrent()
        assertEquals(SdkError.NotInitialized, released.getCompleted())
    }

    // The network.

    @Test
    fun `without a network and a did init waits for the network, not for the pause`() {
        connectivity.set(false)
        started()
        answer(HttpResult.Failure(SdkError.Network))

        after(30 * 60 * 1000L)
        assertEquals(1, sent.size)
        assertEquals(SdkState.RETRYING, state.current)

        connectivity.set(true)
        scheduler.runCurrent()
        assertEquals(2, sent.size)
    }

    @Test
    fun `with a did and no network init repeats on the pause, or as soon as the network is back`() {
        settings.updateDid("OLD")
        connectivity.set(false)
        started()
        answer(HttpResult.Failure(SdkError.Network))
        after(1_000)
        assertEquals(2, sent.size)

        answer(HttpResult.Failure(SdkError.Network))
        after(500)
        connectivity.set(true)
        scheduler.runCurrent()
        assertEquals(3, sent.size)
    }

    // Later /init.

    @Test
    fun `coming back after more than two hours sends init, without the ended session`() {
        started()
        answer(ok("D1", "S1"))
        lifecycle.set(false)
        // The window runs from the last activity, which the session's start recorded.
        clock.now = settings.getSidLastActTime()

        clock.advance(CoreInit.SESSION_WINDOW_MS - 1)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertEquals(1, sent.size)

        lifecycle.set(false)
        clock.advance(2)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertEquals(2, sent.size)
        assertEquals("", sidAtSend.last())
        answer(ok("D1", "S2"))
        assertEquals("S2", settings.getSid())
    }

    @Test
    fun `a refused shop is not asked again on coming back`() {
        started()
        answer(http(403))
        lifecycle.set(false)
        clock.advance(CoreInit.SESSION_WINDOW_MS + 1)
        lifecycle.set(true)
        scheduler.runCurrent()

        assertEquals(1, sent.size)
    }

    @Test
    fun `checkShop sends init at most once in ten minutes`() {
        val init = started()
        assertFalse("init is on its way", init.checkShop())
        answer(ok())

        assertTrue(init.checkShop())
        scheduler.runCurrent()
        assertEquals(2, sent.size)
        answer(ok())
        clock.advance(CoreInit.CHECK_SHOP_INTERVAL_MS - 1)
        assertFalse(init.checkShop())
        clock.advance(1)
        assertTrue(init.checkShop())
        scheduler.runCurrent()
        answer(http(403))
        clock.advance(CoreInit.CHECK_SHOP_INTERVAL_MS)
        assertFalse("refused", init.checkShop())
    }

    // Restart and release.

    @Test
    fun `while a deletion holds the instance no init goes, not on coming back nor on checkShop`() {
        val init = started()
        answer(ok("D1", "S1"))
        lifecycle.set(false)

        init.holdForRestart()
        clock.advance(CoreInit.SESSION_WINDOW_MS + 1)
        lifecycle.set(true)
        scheduler.runCurrent()
        assertFalse(init.checkShop())
        after(CoreInit.CHECK_SHOP_INTERVAL_MS)
        assertEquals(1, sent.size)

        init.restart()
        scheduler.runCurrent()
        assertEquals(2, sent.size)
    }

    @Test
    fun `the storage is prepared off the caller's thread, a stored did is ready at once`() {
        storage.saveInitAnswer(JSONObject().put("did", "SAVED"))
        val init = init()

        init.start()
        // Nothing read from the file and nothing sent yet: that is the io dispatcher's work.
        assertEquals("", settings.getDid())
        assertEquals(SdkState.INITIALIZING, state.current)
        assertTrue(sent.isEmpty())

        scheduler.runCurrent()
        assertEquals("SAVED", settings.getDid())
        assertEquals(SdkState.READY, state.current)
        assertEquals(1, sent.size)
        assertEquals(1, usable)
    }

    @Test
    fun `coming back to the foreground is handled off the main thread`() {
        started()
        answer(ok("D1", "S1"))
        lifecycle.set(false)
        clock.now = settings.getSidLastActTime() + CoreInit.SESSION_WINDOW_MS + 1

        lifecycle.set(true)
        assertEquals(1, sent.size)
        scheduler.runCurrent()
        assertEquals(2, sent.size)
    }

    @Test
    fun `after a wipe the saved answer gives no did until an init succeeds`() {
        // The wipe committed its preferences, but the saved answer's file outlived it.
        storage.saveInitAnswer(JSONObject().put("did", "FORGOTTEN"))
        storage.isWiped = true
        started()

        assertEquals("", settings.getDid())
        assertEquals(SdkState.INITIALIZING, state.current)
        assertEquals("init goes without a did", 1, sent.size)
        answer(ok("NEW", "S"))
        assertEquals("NEW", settings.getDid())
        assertFalse(storage.isWiped)
        inits.single().release()

        // Later launches read the new answer again.
        settings.removeDid()
        started()
        assertEquals("NEW", settings.getDid())
    }

    @Test
    fun `a release that comes while start adds its lifecycle listener does not leave it behind`() {
        lateinit var racing: CoreInit
        var listening = 0
        val raceLifecycle = object : AppLifecycle {
            override val isForeground = true
            override fun addListener(listener: (foreground: Boolean) -> Unit): com.personalization.Cancellable {
                listening++
                racing.release()
                return com.personalization.Cancellable { listening-- }
            }
        }
        racing = CoreInit(
            setup = InitSetup(SHOP),
            send = { sent += it; ok() },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = raceLifecycle,
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
            dispatchers = dispatchers,
            clock = clock,
        )

        racing.start()
        scheduler.runCurrent()

        assertEquals(0, listening)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `an answer to an init sent before a restart is not taken`() {
        val init = started()
        val stale = calls.single()

        init.holdForRestart()
        assertEquals(SdkState.INITIALIZING, state.current)
        stale.complete(ok("STALE"))
        scheduler.runCurrent()
        assertEquals("", settings.getDid())

        init.restart()
        scheduler.runCurrent()
        answer(ok("FRESH"))
        assertEquals("FRESH", settings.getDid())
        assertEquals(SdkState.READY, state.current)
        assertEquals(1, usable)
    }

    @Test
    fun `after release nothing is sent and nothing is taken`() {
        val init = started()
        init.release()
        scheduler.runCurrent()

        calls.single().complete(ok())
        scheduler.runCurrent()
        lifecycle.set(false)
        clock.advance(CoreInit.SESSION_WINDOW_MS + 1)
        lifecycle.set(true)
        after(60 * 60 * 1000L)

        assertEquals(1, sent.size)
        assertEquals("", settings.getDid())
        assertFalse(init.checkShop())
        assertEquals(SdkError.NotInitialized, awaitReady(init, 10_000))
    }

    private fun waitReady(init: CoreInit, timeoutMs: Long) =
        CoroutineScope(StandardTestDispatcher(scheduler)).async { init.usableWithin(timeoutMs) }.also { scheduler.runCurrent() }

    private fun awaitReady(init: CoreInit, timeoutMs: Long): SdkError? {
        val waiting = waitReady(init, timeoutMs)
        scheduler.advanceTimeBy(timeoutMs)
        scheduler.runCurrent()
        return waiting.getCompleted()
    }

    private companion object {
        const val SHOP = "init-shop"
    }
}
