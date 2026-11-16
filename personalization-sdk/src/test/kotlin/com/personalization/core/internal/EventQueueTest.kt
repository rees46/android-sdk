package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.models.NetworkMethod
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The new core's event queue (`core/queue.md`, decision 0002) over its real files and a real `/init`:
 * every row of the answers' table, the order, a restart, the limits, `flush()`, the triggers, the
 * clearing, the popup rule, `release()`, two instances of one shop and what goes on the wire. Time is the
 * fake clock's for the events' age and the session, the test scheduler's for the pauses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventQueueTest {

    private lateinit var context: Context
    private lateinit var storage: ShopStorage
    private lateinit var settings: UserSettingsRepository
    private val layout = StorageLayout(SHOP, null)
    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = testCoreDispatchers(scheduler)
    private val clock = FakeClock()
    private val connectivity = FakeConnectivity(connected = true)
    private val lifecycle = FakeAppLifecycle(foreground = true)
    private lateinit var state: InstanceState
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** `/init`: answered at once with [initAnswer] unless [holdInit]; then the test answers [initCalls]. */
    private var initAnswer: () -> HttpResult = { ok() }
    private var holdInit = false
    private val initCalls = mutableListOf<CompletableDeferred<HttpResult>>()
    private var initsSent = 0

    /** Events: answered from [eventAnswers], else a `2xx` with `{}`; or held while [holdEvents]. */
    private val eventAnswers = ArrayDeque<HttpResult>()
    private var holdEvents = false
    private val eventCalls = mutableListOf<CompletableDeferred<HttpResult>>()

    /** What went: the path, the body with its own fields and the sid of that moment. */
    private val sent = mutableListOf<Sent>()

    private class Sent(val path: String, val body: JSONObject, val sid: String)

    private var source: JSONObject? = null

    /** How many times the source throws before it answers: an unexpected failure while sending. */
    private var sourceFailures = 0
    private val inits = mutableListOf<CoreInit>()
    private val queues = mutableListOf<EventQueue>()
    private val direct = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        QueueStore.reset()
        storage = ShopStorage(context, layout)
        settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) })
        state = InstanceState(shopId = { SHOP }, dispatchers = dispatchers)
        logged.clear()
        sent.clear()
        eventAnswers.clear()
        eventCalls.clear()
        initCalls.clear()
        direct.clear()
        initsSent = 0
        holdInit = false
        holdEvents = false
        initAnswer = { ok() }
        source = null
        sourceFailures = 0
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        queues.forEach { it.release() }
        inits.forEach { it.release() }
        queues.clear()
        inits.clear()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
        QueueStore.reset()
    }

    private fun ok(did: String = "D1", sid: String = "S1") = HttpResult.Success(JSONObject().put("did", did).put("seance", sid))
    private fun http(code: Int, retryAfterMs: Long? = null) = HttpResult.Failure(SdkError.Http(code), """{"status":"error"}""", retryAfterMs)

    /**
     * An instance as an app starts it: its `/init`, then its queue over the shop's files. [pushOnly]: as
     * `Rees46.handlePush` brings up a shop registered but not created.
     */
    private fun start(cap: Int = EventQueue.CAP, pushOnly: Boolean = false): EventQueue {
        val init = CoreInit(
            setup = InitSetup(SHOP, pushOnly = pushOnly),
            send = {
                initsSent++
                if (holdInit) CompletableDeferred<HttpResult>().also { initCalls += it }.await() else initAnswer()
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
            session = SessionWindow(settings, storage, clock),
        ).also { inits += it }
        val queue = EventQueue(
            shopId = SHOP,
            store = QueueStore.of(storage.queuePath, SHOP),
            init = init,
            state = state,
            send = { method, body ->
                sent += Sent(method.method, JSONObject(body.toString()), settings.getSid())
                if (holdEvents) CompletableDeferred<HttpResult>().also { eventCalls += it }.await()
                else eventAnswers.removeFirstOrNull() ?: HttpResult.Success(JSONObject())
            },
            connectivity = connectivity,
            lifecycle = lifecycle,
            source = {
                if (sourceFailures > 0) {
                    sourceFailures--
                    throw IllegalStateException("The source failed")
                }
                source
            },
            stamp = { body -> CommonFields.addTo(body, settings) },
            sendDirectly = { path, _, _ -> direct += path },
            dispatchers = dispatchers,
            clock = clock,
            backoff = Backoff(random = { 0.5 }),
            cap = cap,
            pushEventsOnly = pushOnly,
        ).also { queues += it }
        queue.start()
        init.start()
        run()
        return queue
    }

    private fun run() = scheduler.runCurrent()

    private fun after(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun event(queue: EventQueue, id: String, path: String = "push", listener: OnApiCallbackListener? = null) {
        queue.accept(path, JSONObject().put("event", "view").put("items", org.json.JSONArray().put(JSONObject().put("id", id))), listener)
    }

    private fun ids(): List<String> = sent.map { it.body.optJSONArray("items")?.optJSONObject(0)?.optString("id").orEmpty() }

    /** The events' files and leftovers, not the queue's lock. */
    private fun files(): List<String> = storage.queuePath.list()?.filter { it != QueueStore.LOCK_FILE }?.sorted().orEmpty()

    private fun codes(code: String) = logged.filter { it.code == code }

    private fun flush(queue: EventQueue): Deferred<Throwable?> = CoroutineScope(StandardTestDispatcher(scheduler)).async {
        try {
            queue.flush()
            null
        } catch (e: SdkException) {
            e
        }
    }.also { run() }

    /** Records what an old-style listener hears. */
    private class Heard : EventAnswerListener() {
        val calls = CopyOnWriteArrayList<String>()
        var heldBack: String? = "unset"

        /** The whole error behind each `onError`, as a bridge reads it. */
        val errors = CopyOnWriteArrayList<SdkError?>()

        override fun onSuccess(response: JSONObject?) {
            heldBack = popupHeldBack
            calls += "success:$response"
        }

        override fun onError(code: Int, msg: String?) {
            errors += CurrentError.get()
            calls += "error:$code"
        }
    }

    // Order, readiness, the restart.

    @Test
    fun `events wait for ready and go in order, each with the sid of its send`() {
        holdInit = true
        val queue = start()
        listOf("1", "2", "3").forEach { event(queue, it) }
        run()
        assertTrue("nothing goes before ready", sent.isEmpty())
        assertEquals(3, files().size)

        initCalls.single().complete(ok(sid = "S1"))
        run()

        assertEquals(listOf("1", "2", "3"), ids())
        assertTrue(sent.all { it.sid == "S1" })
        assertTrue(files().isEmpty())
    }

    @Test
    fun `an event is accepted once its file is on disk, written whole`() {
        val queue = start()
        connectivity.set(false)
        event(queue, "1")
        assertTrue("written off the caller's thread", files().isEmpty())
        run()

        val names = files()
        assertEquals(1, names.size)
        assertTrue(names.single().endsWith(".json"))
        val stored = JSONObject(File(storage.queuePath, names.single()).readText())
        assertEquals("push", stored.getString("path"))
        assertEquals(clock.now, stored.getLong("created_at"))
        assertFalse("no common fields on disk", stored.getJSONObject("body").has("did"))
    }

    @Test
    fun `the queue outlives a restart, a new instance over the same files sends them`() {
        val first = start()
        connectivity.set(false)
        listOf("1", "2").forEach { event(first, it) }
        run()
        first.release()
        inits.forEach { it.release() }
        queues.clear()
        inits.clear()
        // A new process: no store in memory, a temporary file a crash left, an unreadable event, and a
        // temporary file another process is writing right now.
        QueueStore.reset()
        File(storage.queuePath, "0000000000000000099.tmp").apply { writeText("{") }.setLastModified(System.currentTimeMillis() - QueueStore.STALE_TEMPORARY_MS - 1_000)
        File(storage.queuePath, "0000000000000000-1-00000000.json").writeText("not json")
        File(storage.queuePath, "9999999999999999-2-00000000.tmp").writeText("{")
        state = InstanceState(shopId = { SHOP }, dispatchers = dispatchers)

        connectivity.set(true)
        start()

        assertEquals(listOf("1", "2"), ids())
        assertEquals("another process's write stays", listOf("9999999999999999-2-00000000.tmp"), files())
        assertEquals("unreadable", codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    // The answers (decision 0002).

    @Test
    fun `a 2xx removes the event and its listener hears the server's answer`() {
        val queue = start()
        eventAnswers += HttpResult.Success(JSONObject().put("ok", 1))
        eventAnswers += HttpResult.Failure(SdkError.Decoding)
        val first = Heard()
        val second = Heard()
        event(queue, "1", listener = first)
        event(queue, "2", listener = second)
        run()

        assertEquals(listOf("""success:{"ok":1}"""), first.calls)
        assertEquals("a 2xx that is not JSON went all the same", listOf("success:{}"), second.calls)
        assertTrue(files().isEmpty())
    }

    @Test
    fun `without a network events wait without an attempt and go when it comes back`() {
        val queue = start()
        connectivity.set(false)
        val heard = Heard()
        event(queue, "1", listener = heard)
        after(EventQueue.NETWORK_RECHECK_MS * 3)

        assertTrue(sent.isEmpty())
        assertTrue(heard.calls.isEmpty())
        connectivity.set(true)
        run()
        assertEquals(listOf("1"), ids())
        assertEquals(1, heard.calls.size)
    }

    @Test
    fun `a timeout or a lost connection repeats after a pause and does not count`() {
        val queue = start()
        repeat(12) { eventAnswers += HttpResult.Failure(SdkError.Timeout) }
        event(queue, "1")
        run()
        assertEquals(1, sent.size)
        after(1_000)
        assertEquals(2, sent.size)
        after(2_000)
        assertEquals(3, sent.size)
        // Never dropped for timeouts: the attempts stay at 0.
        val stored = JSONObject(File(storage.queuePath, files().single()).readText())
        assertEquals(0, stored.getInt("attempts"))
    }

    @Test
    fun `5xx, 408, 410, 417, 426 and 429 repeat with growing pauses and the tenth drops the event, not the next`() {
        listOf(500, 502, 503, 408, 410, 417, 426, 429).forEach { code ->
            tearDown()
            setUp()
            val queue = start()
            repeat(10) { eventAnswers += http(code) }
            val heard = Heard()
            event(queue, "1", listener = heard)
            event(queue, "2")
            run()
            // 1, 2, 4… seconds, up to 256: nine pauses after the first nine answers.
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 256_000L).forEachIndexed { index, pause ->
                after(pause - 1)
                assertEquals("$code", index + 1, sent.size)
                after(1)
            }
            assertEquals("$code", listOf("1", "1", "1", "1", "1", "1", "1", "1", "1", "1", "2"), ids())
            assertEquals("$code", listOf("error:$code"), heard.calls)
            val drop = codes(LogCodes.QUEUE_DROPPED).single()
            assertEquals(LogLevel.WARNING, drop.level)
            assertEquals(LogReasons.ATTEMPTS, drop.context["reason"])
            assertEquals(10, drop.context["attempts"])
        }
    }

    @Test
    fun `the attempts are kept across a restart`() {
        val queue = start()
        repeat(3) { eventAnswers += http(500) }
        event(queue, "1")
        run()
        after(1_000)
        after(2_000)
        assertEquals(3, sent.size)
        val stored = JSONObject(File(storage.queuePath, files().single()).readText())
        assertEquals(3, stored.getInt("attempts"))
    }

    @Test
    fun `Retry-After counts on 429 and 503 only, from a second to five minutes, and no trigger ends it`() {
        val queue = start()
        eventAnswers += http(503, retryAfterMs = 30_000)
        eventAnswers += http(500, retryAfterMs = 600_000)
        eventAnswers += http(429, retryAfterMs = 3_600_000)
        event(queue, "1")
        run()

        // The server's 30 s: a network flap or the foreground does not end them.
        connectivity.set(false)
        connectivity.set(true)
        lifecycle.set(false)
        after(29_999)
        assertEquals(1, sent.size)
        after(1)
        assertEquals(2, sent.size)

        // A 500's Retry-After is not read: the backoff's 2 s, which a trigger ends.
        after(1_000)
        lifecycle.set(true)
        run()
        assertEquals(3, sent.size)

        // An hour asked: five minutes.
        after(299_999)
        assertEquals(3, sent.size)
        after(1)
        assertEquals(4, sent.size)
    }

    @Test
    fun `402, 403 or 404 pause the queue for a check of the shop, which then sends the event once more`() {
        listOf(402, 403, 404).forEach { code ->
            tearDown()
            setUp()
            val queue = start()
            eventAnswers += http(code)
            val heard = Heard()
            event(queue, "1", listener = heard)
            run()

            assertEquals("$code: /init checked the shop", 2, initsSent)
            assertEquals("$code", listOf("1", "1"), ids())
            assertEquals("$code", 1, heard.calls.size)
            assertTrue(heard.calls.single().startsWith("success"))
        }
    }

    @Test
    fun `a second 402, 403 or 404 after the shop was found fine drops the event as a refused service`() {
        val queue = start()
        eventAnswers += http(402)
        eventAnswers += http(402)
        val heard = Heard()
        event(queue, "1", path = "push/custom", listener = heard)
        event(queue, "2")
        run()

        assertEquals(listOf("push/custom", "push/custom", "push"), sent.map { it.path })
        assertEquals(listOf("error:402"), heard.calls)
        assertEquals(LogReasons.SERVICE_REFUSED, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
        assertEquals(SdkState.READY, state.current)
    }

    @Test
    fun `a check of the shop that refuses it clears the queue and takes no more events`() {
        val queue = start()
        eventAnswers += http(403)
        initAnswer = { http(403) }
        val first = Heard()
        val second = Heard()
        connectivity.set(false)
        event(queue, "1", listener = first)
        event(queue, "2", listener = second)
        run()
        connectivity.set(true)
        run()

        assertEquals(SdkState.REFUSED, state.current)
        // Dropped with the queue, not refused: cancelled (core/queue.md, «Пределы»).
        assertEquals(listOf("error:-1"), first.calls)
        assertEquals(listOf("error:-1"), second.calls)
        assertEquals(listOf<SdkError?>(SdkError.Cancelled), first.errors)
        assertEquals(listOf<SdkError?>(SdkError.Cancelled), second.errors)
        assertTrue(files().isEmpty())
        val later = Heard()
        event(queue, "3", listener = later)
        run()
        assertEquals("refused at once", listOf("error:402"), later.calls)
        assertEquals(listOf<SdkError?>(SdkError.ShopBlocked), later.errors)
        assertEquals(1, sent.size)
        assertEquals(LogReasons.REFUSED, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    @Test
    fun `a refusal less than ten minutes after a check that found the shop fine drops the event as the service's`() {
        val queue = start()
        eventAnswers += http(404)
        eventAnswers += http(404)
        eventAnswers += http(404)
        val heard = Heard()
        event(queue, "1")
        event(queue, "2", listener = heard)
        run()

        // The first event: a 404, the check, a 404 again and it is dropped. The second: a 404 while the
        // shop was found fine less than ten minutes ago: dropped at once, without another /init.
        assertEquals(2, initsSent)
        assertEquals(listOf("1", "1", "2"), ids())
        assertEquals(listOf("error:404"), heard.calls)
        assertEquals(listOf(LogReasons.SERVICE_REFUSED, LogReasons.SERVICE_REFUSED), codes(LogCodes.QUEUE_DROPPED).map { it.context["reason"] })
    }

    @Test
    fun `a check of the shop whose answer does not come in time does not count as the shop found fine`() {
        val queue = start()
        holdInit = true
        eventAnswers += http(402)
        eventAnswers += http(402)
        val heard = Heard()
        event(queue, "1", listener = heard)
        run()

        // The 402 asks the shop; /init does not answer within the queue's wait: a pause, and the event goes
        // again unchecked — so its next 402 waits for the check again instead of dropping it.
        after(10_000)
        after(1_000)
        assertEquals(listOf("1", "1"), ids())
        assertTrue(heard.calls.isEmpty())

        // The check's answer comes: the shop is fine, the event goes once more.
        initCalls.single().complete(ok())
        run()
        assertEquals(listOf("1", "1", "1"), ids())
        assertTrue(heard.calls.single().startsWith("success"))
        assertTrue(codes(LogCodes.QUEUE_DROPPED).isEmpty())
    }

    @Test
    fun `400, 401, 405, 422 and other 4xx drop the event and the next one goes`() {
        listOf(400, 401, 405, 422, 451).forEach { code ->
            tearDown()
            setUp()
            val queue = start()
            eventAnswers += http(code)
            val heard = Heard()
            event(queue, "1", listener = heard)
            event(queue, "2")
            run()

            assertEquals("$code", listOf("1", "2"), ids())
            assertEquals("$code", listOf("error:$code"), heard.calls)
            assertEquals(LogReasons.REJECTED, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
        }
    }

    // The limits.

    @Test
    fun `an event older than seven days is dropped when its turn comes`() {
        val queue = start()
        connectivity.set(false)
        val heard = Heard()
        event(queue, "old", listener = heard)
        run()
        clock.advance(EventQueue.TTL_MS + 1)
        event(queue, "new")
        connectivity.set(true)
        run()

        assertEquals(listOf("new"), ids())
        assertEquals(listOf("error:-1"), heard.calls)
        assertEquals(LogReasons.EXPIRED, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    @Test
    fun `at the cap the oldest event makes room`() {
        assertEquals(1_000, EventQueue.CAP)
        val queue = start(cap = 3)
        connectivity.set(false)
        val oldest = Heard()
        event(queue, "1", listener = oldest)
        listOf("2", "3", "4").forEach { event(queue, it) }
        run()
        assertEquals(3, files().size)

        connectivity.set(true)
        run()
        assertEquals(listOf("2", "3", "4"), ids())
        assertEquals(listOf("error:-1"), oldest.calls)
        assertEquals(LogReasons.OVERFLOW, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    // flush().

    @Test
    fun `flush sends now and returns once the queue was tried`() {
        val queue = start()
        repeat(2) { eventAnswers += http(500) }
        event(queue, "1")
        run()
        assertEquals(1, sent.size)

        // In the backoff's pause: flush sends at once.
        val flushed = flush(queue)
        assertTrue(flushed.isCompleted)
        assertNull(flushed.getCompleted())
        assertEquals(2, sent.size)
    }

    @Test
    fun `flush returns without sending what cannot go now`() {
        val queue = start()
        connectivity.set(false)
        event(queue, "1")
        assertNull(flush(queue).getCompleted())
        assertTrue(sent.isEmpty())

        // A server's Retry-After: flush does not override it.
        connectivity.set(true)
        run()
        eventAnswers += http(503, retryAfterMs = 60_000)
        event(queue, "2")
        run()
        val count = sent.size
        val flushed = flush(queue)
        assertTrue(flushed.isCompleted)
        assertEquals(count, sent.size)
    }

    @Test
    fun `flush covers the events accepted right before it`() {
        val queue = start()
        connectivity.set(false)
        run()
        connectivity.set(true)
        event(queue, "1")
        val flushed = flush(queue)
        assertTrue(flushed.isCompleted)
        assertEquals(listOf("1"), ids())
    }

    @Test
    fun `flush of a refused shop ends without an error, its queue being empty, and fails with notInitialized after release`() {
        initAnswer = { http(402) }
        val queue = start()
        assertEquals(SdkState.REFUSED, state.current)
        val refused = flush(queue)
        assertTrue(refused.isCompleted)
        assertNull("nothing to send: no error (core/init.md)", refused.getCompleted())
        queue.release()
        assertEquals(SdkError.NotInitialized, (flush(queue).getCompleted() as SdkException).error)
    }

    // The triggers.

    @Test
    fun `sending starts on ready, on the foreground and the background, on the network and on the timer`() {
        holdInit = true
        val queue = start()
        event(queue, "ready")
        run()
        initCalls.single().complete(ok())
        run()
        assertEquals(listOf("ready"), ids())

        // Held by a pause the backoff set: each trigger ends it.
        listOf<() -> Unit>(
            { lifecycle.set(false) },
            { lifecycle.set(true) },
            { connectivity.set(false); connectivity.set(true) },
        ).forEachIndexed { index, trigger ->
            eventAnswers += http(500)
            event(queue, "t$index")
            run()
            val before = sent.size
            trigger()
            run()
            assertEquals("trigger $index", before + 1, sent.size)
        }

        // Waiting for a session whose /init does not answer: the pass gives up after its wait, and a
        // session that comes without a word is found by the timer.
        clock.advance(SessionWindow.WINDOW_MS + 1)
        event(queue, "timer")
        run()
        val waiting = sent.size
        storage.preferences.edit().putString(StorageKeys.SID, "S3").putLong(StorageKeys.SID_LAST_ACT, clock.now).commit()
        after(10_000)
        assertEquals(waiting, sent.size)
        after(EventQueue.TIMER_MS)
        assertEquals(waiting + 1, sent.size)
        assertEquals("S3", sent.last().sid)
    }

    @Test
    fun `a network that comes back without a word is found by the queue looking again`() {
        val queue = start()
        connectivity.set(false)
        event(queue, "1")
        run()
        connectivity.setQuietly(true)
        after(EventQueue.NETWORK_RECHECK_MS - 1)
        assertTrue(sent.isEmpty())
        after(1)
        assertEquals(listOf("1"), ids())
    }

    @Test
    fun `a new session sends what waited for it`() {
        val queue = start()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        initAnswer = { HttpResult.Failure(SdkError.Http(500)) }
        event(queue, "1")
        run()
        assertTrue("events need a sid", sent.isEmpty())

        initAnswer = { ok(sid = "S2") }
        after(1_000)
        assertEquals(listOf("S2"), sent.map { it.sid })
    }

    // Clearing, release, the popup rule.

    @Test
    fun `clearing empties the queue, its listeners hear it, and an event accepted after it stays`() {
        val queue = start()
        connectivity.set(false)
        val before = Heard()
        event(queue, "before", listener = before)
        val cleared = CoroutineScope(StandardTestDispatcher(scheduler)).async {
            queue.clear(LogReasons.DELETE_USER_CREDENTIALS, LegacyError(-1, "gone")) { true }
        }
        run()
        event(queue, "after")
        run()

        assertTrue(cleared.getCompleted())
        assertEquals(listOf("error:-1"), before.calls)
        assertEquals(1, files().size)
        connectivity.set(true)
        run()
        assertEquals(listOf("after"), ids())
    }

    @Test
    fun `release stops sending and keeps the events on disk, its listeners hear -1`() {
        val queue = start()
        connectivity.set(false)
        val waiting = Heard()
        event(queue, "1", listener = waiting)
        run()

        queue.release()
        connectivity.set(true)
        after(EventQueue.TIMER_MS)

        assertTrue(sent.isEmpty())
        assertEquals(1, files().size)
        assertEquals(listOf("error:-1"), waiting.calls)
        val later = Heard()
        event(queue, "2", listener = later)
        run()
        assertEquals(listOf("error:-1"), later.calls)
    }

    @Test
    fun `an answer that comes after release still takes the event off the disk`() {
        val queue = start()
        holdEvents = true
        event(queue, "1")
        run()
        assertEquals(1, eventCalls.size)

        queue.release()
        run()
        assertEquals(1, files().size)
        eventCalls.single().complete(HttpResult.Success(JSONObject()))
        run()

        assertTrue("it went: not sent again at the next launch", files().isEmpty())
    }

    @Test
    fun `a popup is shown only for an event sent within ten seconds and in the foreground`() {
        val queue = start()
        val fresh = Heard()
        event(queue, "1", listener = fresh)
        run()
        assertNull(fresh.heldBack)

        connectivity.set(false)
        val late = Heard()
        event(queue, "2", listener = late)
        run()
        clock.advance(EventQueue.POPUP_WINDOW_MS + 1)
        connectivity.set(true)
        run()
        assertEquals(LogReasons.LATE_EVENT, late.heldBack)

        lifecycle.set(false)
        val background = Heard()
        event(queue, "3", listener = background)
        run()
        assertEquals(LogReasons.APP_IN_BACKGROUND, background.heldBack)
    }

    @Test
    fun `two instances of one shop never send an event twice`() {
        val first = start()
        connectivity.set(false)
        event(first, "1")
        run()
        val second = EventQueue(
            shopId = SHOP,
            store = QueueStore.of(storage.queuePath, SHOP),
            init = inits.single(),
            state = state,
            send = { method, body ->
                sent += Sent(method.method, JSONObject(body.toString()), settings.getSid())
                HttpResult.Success(JSONObject())
            },
            connectivity = connectivity,
            lifecycle = lifecycle,
            dispatchers = dispatchers,
            clock = clock,
        ).also { queues += it }
        second.start()
        event(second, "2")
        run()

        connectivity.set(true)
        run()
        assertEquals(listOf("1", "2"), ids())

        // The one made last sends; when it goes, the first takes over.
        second.release()
        event(first, "3")
        run()
        assertEquals(listOf("1", "2", "3"), ids())
    }

    @Test
    fun `an event the disk does not take goes once as a request`() {
        val queue = start()
        storage.directory.mkdirs()
        // The queue's directory is a file: nothing can be written there.
        storage.queuePath.deleteRecursively()
        storage.queuePath.writeText("in the way")
        event(queue, "1")
        run()

        assertEquals(listOf("push"), direct)
        assertTrue(codes(LogCodes.STORAGE_FAILED).isNotEmpty())
        storage.queuePath.delete()
    }

    // What goes on the wire.

    @Test
    fun `the source goes on push only and the time of the event on push custom only, the call's own time kept`() {
        val queue = start()
        connectivity.set(false)
        source = JSONObject().put("from", "chain").put("code", "c1")
        val made = clock.now
        listOf("push", "push/custom", "track/stories", "track/slider", "popup/showed").forEach { path ->
            queue.accept(path, JSONObject().put("event", "e"), null)
        }
        queue.accept("push/custom", JSONObject().put("event", "own").put("time", 42), null)
        run()
        clock.advance(60_000)
        connectivity.set(true)
        run()

        assertEquals(listOf("push", "push/custom", "track/stories", "track/slider", "popup/showed", "push/custom"), sent.map { it.path })
        assertEquals(listOf(true, false, false, false, false, false), sent.map { it.body.has("source") })
        assertEquals("c1", sent[0].body.getJSONObject("source").getString("code"))
        assertEquals(made / 1_000, sent[1].body.getLong("time"))
        assertEquals(listOf(false, true, false, false, false, true), sent.map { it.body.has("time") })
        assertEquals(42, sent[5].body.getInt("time"))
    }

    @Test
    fun `the source of the moment of sending, not of the event`() {
        val queue = start()
        connectivity.set(false)
        source = JSONObject().put("from", "chain").put("code", "old")
        event(queue, "1")
        run()
        source = JSONObject().put("from", "bulk").put("code", "new")
        connectivity.set(true)
        run()

        assertEquals("new", sent.single().body.getJSONObject("source").getString("code"))
    }

    @Test
    fun `numbers the older builders put in as text go as numbers, and text that is not a plain number stays text`() {
        val queue = start()
        connectivity.set(false)
        val items = org.json.JSONArray()
            .put(JSONObject().put("id", "37").put("amount", "2").put("price", "999.50"))
            .put(JSONObject().put("id", "38").put("amount", "0123").put("price", "1e3"))
            .put(JSONObject().put("id", "39").put("amount", 3).put("price", "12345678.9"))
        queue.accept("push", JSONObject().put("event", "cart").put("order_price", "2599.0").put("items", items), null)
        queue.accept("push/custom", JSONObject().put("event", "e").put("value", "7").put("label", "8"), null)
        queue.accept("popup/showed", JSONObject().put("popup", "42"), null)
        queue.accept("track/stories", JSONObject().put("event", "view").put("story_id", 12).put("slide_id", "34"), null)
        run()
        connectivity.set(true)
        run()

        val push = sent[0].body
        val lines = push.getJSONArray("items")
        assertEquals(2, lines.getJSONObject(0).get("amount"))
        assertEquals(999.5, (lines.getJSONObject(0).get("price") as Number).toDouble(), 0.0)
        assertEquals("0123", lines.getJSONObject(1).get("amount"))
        assertEquals("1e3", lines.getJSONObject(1).get("price"))
        assertEquals(3, lines.getJSONObject(2).get("amount"))
        assertEquals(12345678.9, (lines.getJSONObject(2).get("price") as Number).toDouble(), 0.0)
        assertEquals(2599.0, (push.get("order_price") as Number).toDouble(), 0.0)
        assertEquals(7, sent[1].body.get("value"))
        assertEquals("8", sent[1].body.get("label"))
        assertEquals(42, sent[2].body.get("popup"))
        // The slide's id is text by the contract.
        assertEquals("34", sent[3].body.get("slide_id"))
    }

    @Test
    fun `an event extends a live session, and one that waited past the window goes with a new sid after init`() {
        val queue = start()
        val firstActivity = settings.getSidLastActTime()
        clock.advance(60_000)
        event(queue, "1")
        assertEquals(firstActivity + 60_000, settings.getSidLastActTime())
        run()

        connectivity.set(false)
        event(queue, "2")
        run()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        initAnswer = { ok(sid = "S2") }
        connectivity.set(true)
        run()

        assertEquals(listOf("S1", "S2"), sent.map { it.sid })
        assertEquals(2, initsSent)
    }

    // After the review of stages 6 and 7.

    @Test
    fun `a push's event after the session's window ended goes with the did and starts no init`() {
        val queue = start()
        assertEquals(1, initsSent)
        clock.advance(SessionWindow.WINDOW_MS + 1)
        assertFalse(inits.single().session.isAlive())

        queue.accept("track/delivered", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()

        assertEquals(listOf("track/delivered"), sent.map { it.path })
        assertEquals("no /init for a push's event", 1, initsSent)
        assertFalse(inits.single().session.isAlive())

        // An event of the session asks for a new one first.
        event(queue, "1")
        run()
        assertEquals(2, initsSent)
        assertEquals("push", sent.last().path)
    }

    @Test
    fun `a tracking event that waits for its new session does not hold the push events behind it`() {
        val queue = start()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        holdInit = true
        event(queue, "1")
        queue.accept("track/clicked", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()

        assertEquals(listOf("track/clicked"), sent.map { it.path })
        assertEquals("the new session's /init is on its way", 2, initsSent)
        initCalls.single().complete(ok(sid = "S2"))
        run()
        assertEquals(listOf("track/clicked", "push"), sent.map { it.path })
        assertEquals("S2", sent.last().sid)
    }

    @Test
    fun `a push in the background after the window ended goes past the tracking events, and no init goes until the foreground`() {
        val queue = start()
        connectivity.set(false)
        event(queue, "1")
        event(queue, "2")
        run()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        lifecycle.set(false)
        connectivity.set(true)
        run()

        queue.accept("track/delivered", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()
        assertEquals(listOf("track/delivered"), sent.map { it.path })
        // The timer looks again: still in the background, still no new session.
        after(EventQueue.TIMER_MS)
        assertEquals(listOf("track/delivered"), sent.map { it.path })
        assertEquals(1, initsSent)

        // The app comes back: a new session, then the tracking lane in its order.
        initAnswer = { ok(sid = "S2") }
        lifecycle.set(true)
        run()
        assertEquals(2, initsSent)
        assertEquals(listOf("track/delivered", "push", "push"), sent.map { it.path })
        assertEquals(listOf("1", "2"), sent.drop(1).map { it.body.getJSONArray("items").getJSONObject(0).getString("id") })
        assertTrue(sent.drop(1).all { it.sid == "S2" })
    }

    @Test
    fun `the queue of an instance brought up for a push sends its push events alone, ready on the stored did, and asks the server nothing`() {
        settings.updateDid("STORED")
        val queue = start(pushOnly = true)
        assertEquals(SdkState.READY, state.current)
        event(queue, "1")
        queue.accept("track/delivered", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()

        assertEquals(listOf("track/delivered"), sent.map { it.path })
        assertEquals("STORED", sent.single().body.getString("did"))
        assertEquals("the app's instance sends the other events", 1, files().size)

        // A refusal to a push's event: no check of the shop, the event is dropped.
        eventAnswers += http(404)
        queue.accept("track/clicked", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        lifecycle.set(false)
        lifecycle.set(true)
        after(CoreInit.CHECK_SHOP_INTERVAL_MS)
        assertEquals(0, initsSent)
        assertEquals(listOf("track/delivered", "track/clicked"), sent.map { it.path })
        assertEquals(LogReasons.REJECTED, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    // After the final review: the lanes.

    private fun pushEvent(queue: EventQueue, code: String, listener: OnApiCallbackListener? = null) =
        queue.accept("track/delivered", JSONObject().put("code", code).put("type", "bulk"), listener)

    private fun codesSent(): List<String> = sent.map { it.body.optString("code").ifEmpty { "t:" + it.body.optJSONArray("items")?.optJSONObject(0)?.optString("id") } }

    @Test
    fun `a push event answered 402 or 403 is dropped as the service's refusal and 404 as rejected, and asks init nothing`() {
        listOf(402 to LogReasons.SERVICE_REFUSED, 403 to LogReasons.SERVICE_REFUSED, 404 to LogReasons.REJECTED).forEach { (code, reason) ->
            tearDown()
            setUp()
            val queue = start()
            assertEquals(1, initsSent)
            eventAnswers += http(code)
            val heard = Heard()
            pushEvent(queue, "m-1", heard)
            pushEvent(queue, "m-2")
            run()

            assertEquals("$code", listOf("m-1", "m-2"), codesSent())
            assertEquals("$code", listOf("error:$code"), heard.calls)
            assertEquals("$code", reason, codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
            assertEquals("$code: no check of the shop", 1, initsSent)
            assertEquals(SdkState.READY, state.current)
            // Later, still no check: the push lane never asks /init.
            after(CoreInit.CHECK_SHOP_INTERVAL_MS)
            assertEquals(1, initsSent)
        }
    }

    @Test
    fun `a tracking event answered 402 in the background waits for the foreground to check the shop, and the push lane goes meanwhile`() {
        val queue = start()
        assertEquals(1, initsSent)
        lifecycle.set(false)
        eventAnswers += http(402)
        val heard = Heard()
        event(queue, "1", listener = heard)
        run()
        assertEquals(listOf("t:1"), codesSent())

        // In the background no /init goes, whatever wakes the queue; the push lane is not held.
        pushEvent(queue, "m-1")
        run()
        after(EventQueue.TIMER_MS)
        connectivity.set(false)
        connectivity.set(true)
        run()
        assertEquals(listOf("t:1", "m-1"), codesSent())
        assertEquals("no check of the shop from the background", 1, initsSent)
        assertTrue(heard.calls.isEmpty())

        // The foreground: the check goes, finds the shop fine, and the event goes once more.
        lifecycle.set(true)
        run()
        assertEquals(2, initsSent)
        assertEquals(listOf("t:1", "m-1", "t:1"), codesSent())
        assertTrue(heard.calls.single().startsWith("success"))
    }

    @Test
    fun `a tracking event's pause never holds the push events, nor a push event's pause the tracking ones`() {
        val queue = start()
        eventAnswers += http(500)
        event(queue, "1")
        run()
        assertEquals(listOf("t:1"), codesSent())

        // The tracking lane waits out its second: a push event goes at once.
        pushEvent(queue, "m-1")
        run()
        assertEquals(listOf("t:1", "m-1"), codesSent())

        // Half a second on a push event fails: its lane waits, and the tracking event goes when its own pause ends.
        after(500)
        eventAnswers += http(503)
        pushEvent(queue, "m-2")
        run()
        assertEquals(listOf("t:1", "m-1", "m-2"), codesSent())
        after(500)
        assertEquals(listOf("t:1", "m-1", "m-2", "t:1"), codesSent())
        event(queue, "2")
        run()
        assertEquals("the tracking lane goes on while the push lane waits", listOf("t:1", "m-1", "m-2", "t:1", "t:2"), codesSent())
        after(500)
        assertEquals(listOf("t:1", "m-1", "m-2", "t:1", "t:2", "m-2"), codesSent())
    }

    @Test
    fun `each lane counts its own failures and starts over after its own success`() {
        val queue = start()
        eventAnswers += http(500)
        eventAnswers += HttpResult.Success(JSONObject())
        eventAnswers += http(500)
        event(queue, "1")
        run()
        // A push event goes in the tracking lane's pause: the tracking lane's count stays.
        pushEvent(queue, "m-1")
        run()
        assertEquals(listOf("t:1", "m-1"), codesSent())
        after(1_000)
        assertEquals(3, sent.size)
        // Its second failure in a row: two seconds.
        after(1_999)
        assertEquals(3, sent.size)
        after(1)
        assertEquals("the event went at last", 4, sent.size)

        // The tracking lane succeeded: a new failure pauses it for a second again.
        eventAnswers += http(500)
        event(queue, "2")
        run()
        assertEquals(5, sent.size)
        after(999)
        assertEquals(5, sent.size)
        after(1)
        assertEquals(6, sent.size)
    }

    @Test
    fun `the head's scan reads the file of an event its lane skips once, not on every pass`() {
        settings.updateDid("STORED")
        connectivity.set(false)
        val first = start()
        event(first, "1")
        run()
        first.release()

        // A new process, whose queue sends the push events alone: its first pass reads the tracking event.
        QueueStore.reset()
        connectivity.set(true)
        val queue = start(pushOnly = true)
        pushEvent(queue, "m-0")
        run()
        assertEquals(listOf("m-0"), codesSent())

        // Its file changes now: a pass that read it again would find no event and drop it.
        val file = File(storage.queuePath, files().single())
        file.writeText("not json")
        pushEvent(queue, "m-1")
        run()
        after(EventQueue.TIMER_MS)
        pushEvent(queue, "m-2")
        run()

        assertEquals(listOf("m-0", "m-1", "m-2"), codesSent())
        assertTrue("its file was not read again", file.exists())
        assertTrue(codes(LogCodes.QUEUE_DROPPED).isEmpty())
    }

    @Test
    fun `flush ends the pauses of both lanes but not a server's Retry-After`() {
        val queue = start()
        eventAnswers += http(500)
        eventAnswers += http(500)
        event(queue, "1")
        pushEvent(queue, "m-1")
        run()
        assertEquals(listOf("t:1", "m-1"), codesSent())

        // Both lanes wait out their backoff: flush sends both now.
        assertNull(flush(queue).getCompleted())
        assertEquals(listOf("t:1", "m-1", "t:1", "m-1"), codesSent())

        // A server's Retry-After on the push lane holds through a flush, which still ends the tracking lane's.
        eventAnswers += http(429, retryAfterMs = 60_000)
        eventAnswers += http(500)
        pushEvent(queue, "m-2")
        run()
        event(queue, "2")
        run()
        assertEquals(listOf("t:1", "m-1", "t:1", "m-1", "m-2", "t:2"), codesSent())
        assertNull(flush(queue).getCompleted())
        assertEquals(listOf("t:1", "m-1", "t:1", "m-1", "m-2", "t:2", "t:2"), codesSent())
        after(59_999)
        assertEquals(7, sent.size)
        after(1)
        assertEquals("m-2", codesSent().last())
    }

    @Test
    fun `an init answer without a seance wakes the queue that waits for the session at once`() {
        val queue = start()
        clock.advance(SessionWindow.WINDOW_MS + 1)
        holdInit = true
        event(queue, "1")
        run()
        assertEquals("the new session's /init is on its way", 1, initCalls.size)
        // The wait for the instance runs out: the queue waits for a trigger or its timer.
        after(10_000)
        assertTrue(sent.isEmpty())

        initCalls.single().complete(HttpResult.Success(JSONObject().put("did", "D1")))
        run()

        assertEquals("at once, not on the timer", listOf("1"), ids())
    }

    @Test
    fun `a flush ends however the pass it waits for ends, a release that cuts it off or an exception in it`() {
        val queue = start()
        holdEvents = true
        event(queue, "1")
        // Taken by the pass that sends 1 and waits for its answer.
        val cutOff = flush(queue)
        assertEquals(1, eventCalls.size)
        assertFalse(cutOff.isCompleted)
        queue.release()
        run()
        assertTrue("ended by the release", cutOff.isCompleted)
        assertNull(cutOff.getCompleted())
        eventCalls.single().complete(HttpResult.Success(JSONObject()))
        run()

        // A pass that throws: the flush ends the pause of the first failure, and the pass it is taken by
        // throws too.
        val next = start()
        holdEvents = false
        sourceFailures = 2
        event(next, "2")
        val failed = flush(next)
        assertEquals(0, sourceFailures)
        assertTrue("ended by the failed pass", failed.isCompleted)
        assertEquals(listOf("1"), ids())
        after(2_000)
        assertEquals(listOf("1", "2"), ids())
    }

    @Test
    fun `a request cut off by release keeps its event until it ends, and the queue made next does not send it again meanwhile`() {
        val queue = start()
        holdEvents = true
        event(queue, "1")
        run()
        assertEquals(1, eventCalls.size)

        queue.release()
        run()
        // The app initializes the shop again: a new queue over the same files.
        holdEvents = false
        val next = start()
        event(next, "2")
        run()
        flush(next)
        assertEquals("1 is still on its way", listOf("1"), ids())

        // It failed: the new queue sends it, then the next one.
        eventCalls.single().complete(HttpResult.Failure(SdkError.Timeout))
        run()
        assertEquals(listOf("1", "1", "2"), ids())
        assertTrue(files().isEmpty())

        // Cut off again, and it went: nobody sends it twice.
        holdEvents = true
        event(next, "3")
        run()
        next.release()
        holdEvents = false
        val third = start()
        run()
        eventCalls.last().complete(HttpResult.Success(JSONObject()))
        run()
        flush(third)
        assertEquals(listOf("1", "1", "2", "3"), ids())
        assertTrue(files().isEmpty())
    }

    // After the review of stages 4 and 5.

    @Test
    fun `an event read before the customer was deleted does not go under the next customer's did`() {
        val queue = start()
        val init = inits.single()
        // A deletion holds the instance; the event read meanwhile waits for it.
        init.holdForRestart()
        event(queue, "old")
        run()
        val cleared = CoroutineScope(StandardTestDispatcher(scheduler)).async {
            queue.clear(LogReasons.DELETE_USER_CREDENTIALS, LegacyError(-1, "deleted")) { storage.deleteUserCredentials(includeQueue = true) }
        }
        run()
        assertTrue(cleared.getCompleted())
        initAnswer = { ok(did = "D2", sid = "S2") }
        init.restart()
        run()

        assertTrue("the deleted customer's event does not go", sent.isEmpty())
        event(queue, "new")
        run()
        assertEquals(listOf("new"), ids())
        assertEquals("D2", sent.single().body.getString("did"))
    }

    @Test
    fun `a disk that keeps the shop's check or a sent event pauses or skips instead of sending again at once`() {
        val queue = start()
        eventAnswers += http(402)
        connectivity.set(false)
        event(queue, "1")
        run()
        assertTrue(storage.queuePath.setWritable(false))
        try {
            connectivity.set(true)
            run()
            // The shop is fine, but its check could not be written: a pause, not a send at once.
            assertEquals(1, sent.size)
            assertTrue(codes(LogCodes.STORAGE_FAILED).isNotEmpty())
            after(1_000)
            assertEquals(2, sent.size)
            // It went, but its file stays: it is not sent again.
            lifecycle.set(false)
            lifecycle.set(true)
            after(EventQueue.TIMER_MS)
            assertEquals(2, sent.size)
            assertEquals(1, files().size)
        } finally {
            storage.queuePath.setWritable(true)
        }
        // Once the disk takes it, the file goes.
        lifecycle.set(false)
        run()
        assertTrue(files().isEmpty())
        assertEquals(2, sent.size)
    }

    @Test
    fun `fields JSON cannot hold are refused at once, and nothing stops the writer or the sender`() {
        val queue = start()
        val refused = Heard()
        queue.accept("push/custom", JSONObject(mapOf("event" to "e", "value" to Double.NaN)), refused)
        run()
        assertEquals(listOf("error:-1"), refused.calls)
        assertTrue(files().isEmpty())
        assertEquals("invalid_json", codes(LogCodes.INVALID_ARGUMENT).single().context["reason"])

        // A clear whose deletion throws ends, and the writer takes the next event.
        val cleared = CoroutineScope(StandardTestDispatcher(scheduler)).async {
            queue.clear(LogReasons.DELETE_USER_CREDENTIALS, LegacyError(-1, "deleted")) { throw IllegalStateException("disk") }
        }
        run()
        assertFalse(cleared.getCompleted())

        // A send that throws: a pause, then it goes.
        sourceFailures = 1
        event(queue, "1")
        run()
        assertTrue(sent.isEmpty())
        after(1_000)
        assertEquals(listOf("1"), ids())
        assertNull(flush(queue).getCompleted())
    }

    @Test
    fun `only the process that holds the queue's lock sends, and two processes never name two events alike`() {
        val queue = start()
        storage.queuePath.mkdirs()
        val channel = java.io.RandomAccessFile(File(storage.queuePath, QueueStore.LOCK_FILE), "rw").channel
        val other = channel.lock()
        try {
            event(queue, "1")
            run()
            assertTrue("another process sends", sent.isEmpty())
            assertEquals(1, files().size)
        } finally {
            other.release()
            channel.close()
        }
        lifecycle.set(false)
        run()
        assertEquals(listOf("1"), ids())

        // The same moment in two processes: names apart all the same.
        connectivity.set(false)
        val mine = QueueStore.of(storage.queuePath, SHOP)
        val theirs = QueueStore.otherProcess(storage.queuePath, SHOP)
        repeat(20) {
            listOf(mine, theirs).forEach { store -> store.add(queue, "push", JSONObject(), clock.now, null, EventQueue.CAP, mutableListOf()) }
        }
        assertEquals(40, files().size)
    }

    @Test
    fun `a flush asked before release and handled after it ends with notInitialized`() {
        val queue = start()
        val flushed = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).async {
            try {
                queue.flush()
                null
            } catch (e: SdkException) {
                e
            }
        }
        queue.release()
        run()

        assertEquals(SdkError.NotInitialized, (flushed.getCompleted() as SdkException).error)
    }

    @Test
    fun `an event accepted by the queue made first wakes the one that sends`() {
        val first = start()
        start()
        event(first, "1")
        run()

        assertEquals(listOf("1"), ids())
    }

    @Test
    fun `nothing stamped reaches the disk, and a repeat after the source expired goes without it`() {
        val queue = start()
        source = JSONObject().put("from", "chain").put("code", "c1")
        eventAnswers += http(500)
        event(queue, "1")
        run()
        val stored = JSONObject(File(storage.queuePath, files().single()).readText()).getJSONObject("body")
        listOf("source", "did", "sid", "seance", "shop_id", "segment", "stream").forEach { assertFalse(it, stored.has(it)) }

        source = null
        after(1_000)

        assertEquals(listOf(true, false), sent.map { it.body.has("source") })
        assertTrue(sent.all { it.body.getString("did") == "D1" && it.body.getString("sid") == "S1" })
    }

    @Test
    fun `an event the disk cannot read now stays for later, and one that is not an event tells its listener`() {
        val queue = start()
        connectivity.set(false)
        val later = Heard()
        event(queue, "1", listener = later)
        run()
        val file = File(storage.queuePath, files().single())
        assertTrue(file.setReadable(false))
        try {
            connectivity.set(true)
            run()
            assertTrue(sent.isEmpty())
            assertTrue(file.exists())
            assertTrue(later.calls.isEmpty())
            assertTrue(codes(LogCodes.QUEUE_DROPPED).isEmpty())
        } finally {
            file.setReadable(true)
        }
        after(1_000)
        assertEquals(listOf("1"), ids())

        connectivity.set(false)
        val broken = Heard()
        event(queue, "2", listener = broken)
        run()
        File(storage.queuePath, files().single()).writeText("not json")
        connectivity.set(true)
        run()
        assertEquals(listOf("error:-1"), broken.calls)
        assertEquals("unreadable", codes(LogCodes.QUEUE_DROPPED).single().context["reason"])
    }

    @Test
    fun `an event the disk does not take goes with its source or its time, and accepted hears after the write`() {
        val queue = start()
        source = JSONObject().put("from", "chain").put("code", "c1")
        val accepted = mutableListOf<SdkError?>()
        val bodies = mutableListOf<JSONObject>()
        queue.accept("push", JSONObject().put("event", "view"), null) { accepted += it }
        assertTrue("not before the write", accepted.isEmpty())
        run()
        assertEquals(listOf<SdkError?>(null), accepted)

        storage.queuePath.deleteRecursively()
        storage.queuePath.writeText("in the way")
        try {
            val direct = EventQueue(
                shopId = SHOP,
                store = QueueStore.otherProcess(storage.queuePath, SHOP),
                init = inits.single(),
                state = state,
                send = { _, _ -> HttpResult.Success(JSONObject()) },
                connectivity = connectivity,
                lifecycle = lifecycle,
                source = { source },
                sendDirectly = { _, body, _ -> bodies += body },
                dispatchers = dispatchers,
                clock = clock,
            ).also { queues += it; it.start() }
            direct.accept("push", JSONObject().put("event", "view"), null)
            direct.accept("push/custom", JSONObject().put("event", "e"), null)
            run()
        } finally {
            storage.queuePath.delete()
        }
        assertEquals("c1", bodies[0].getJSONObject("source").getString("code"))
        assertEquals(clock.now / 1_000, bodies[1].getLong("time"))
        assertFalse(bodies[1].has("source"))
    }

    // After the decisions of the contract's review.

    @Test
    fun `a push's delivery, notification shown or dismissed does not extend the session, a tap and the tracking events do`() {
        val queue = start()
        val firstActivity = settings.getSidLastActTime()
        listOf("track/delivered", "track/opened", "track/closed").forEach { path ->
            clock.advance(60_000)
            queue.accept(path, JSONObject().put("code", "m-$path").put("type", "bulk"), null)
            assertEquals(path, firstActivity, settings.getSidLastActTime())
        }
        clock.advance(60_000)
        queue.accept("track/clicked", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        assertEquals("a tap, which the server counts too", clock.now, settings.getSidLastActTime())
        clock.advance(60_000)
        event(queue, "1")
        assertEquals(clock.now, settings.getSidLastActTime())
        run()
        assertEquals(5, sent.size)
    }

    @Test
    fun `a push delivered in the background to an instance brought up for it leaves the session to end when it would`() {
        settings.updateDid("STORED")
        settings.updateSid("S0")
        val lastActivity = clock.now - SessionWindow.WINDOW_MS + 60_000
        storage.preferences.edit().putLong(StorageKeys.SID_LAST_ACT, lastActivity).commit()
        lifecycle.set(false)
        val queue = start(pushOnly = true)

        queue.accept("track/delivered", JSONObject().put("code", "m-1").put("type", "bulk"), null)
        run()

        assertEquals(listOf("track/delivered"), sent.map { it.path })
        assertEquals(lastActivity, settings.getSidLastActTime())
        clock.advance(60_001)
        assertFalse("the window ended as it would have without the push", inits.single().session.isAlive())
    }

    @Test
    fun `each dropped event's listener hears the error of its reason`() {
        val queue = start(cap = 2)
        fun dropOne(path: String = "push", answers: List<HttpResult> = emptyList(), before: () -> Unit = {}): Heard {
            val heard = Heard()
            eventAnswers.clear()
            eventAnswers += answers
            before()
            queue.accept(path, JSONObject().put("event", "view"), heard)
            run()
            return heard
        }
        // rejected: the answer's own code.
        assertEquals(listOf<SdkError?>(SdkError.Http(422)), dropOne(answers = listOf(http(422))).errors)
        // attempts: the last answer's code.
        val attempts = dropOne(answers = List(EventQueue.MAX_ATTEMPTS) { http(503) })
        // Each pause of the backoff (at most five minutes) runs out on the test scheduler.
        repeat(EventQueue.MAX_ATTEMPTS) { after(300_000) }
        assertEquals(listOf<SdkError?>(SdkError.Http(503)), attempts.errors)
        // service_refused: a push event's 403.
        assertEquals(listOf<SdkError?>(SdkError.Http(403)), dropOne(path = "track/delivered", answers = listOf(http(403))).errors)
        // expired: timeout.
        connectivity.set(false)
        val expired = dropOne()
        clock.advance(EventQueue.TTL_MS + 1)
        connectivity.set(true)
        run()
        assertEquals(listOf<SdkError?>(SdkError.Timeout), expired.errors)
        // overflow: cancelled.
        connectivity.set(false)
        val oldest = dropOne()
        dropOne()
        dropOne()
        assertEquals(listOf<SdkError?>(SdkError.Cancelled), oldest.errors)
        // delete_user_credentials: cancelled.
        val waiting = dropOne()
        val cleared = CoroutineScope(StandardTestDispatcher(scheduler)).async {
            queue.clear(LogReasons.DELETE_USER_CREDENTIALS, LegacyError(-1, null, SdkError.Cancelled)) { true }
        }
        run()
        assertTrue(cleared.isCompleted)
        assertEquals(listOf<SdkError?>(SdkError.Cancelled), waiting.errors)
        assertEquals(
            listOf(LogReasons.REJECTED, LogReasons.ATTEMPTS, LogReasons.SERVICE_REFUSED, LogReasons.EXPIRED, LogReasons.OVERFLOW),
            codes(LogCodes.QUEUE_DROPPED).map { it.context["reason"] }.take(5),
        )
    }

    private companion object {
        const val SHOP = "queue-shop"
    }
}
