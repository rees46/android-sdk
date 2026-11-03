package com.personalization.popups

import com.personalization.Fixtures
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Движок на сценариях `scenarios.json` (попапы — из `list-response.json`): подменные часы,
 * таймеры, хранилище, сеть и показ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupsV3EngineTest {

    private val listFixture = JSONObject(Fixtures.read("popups-v3/list-response.json"))
    private val fixturePopups: Map<Int, JSONObject> = listFixture.getJSONObject("payload").getJSONArray("popups").let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getInt("id") }
    }

    /** Попап фикстуры с правками сценария (`{"from": 512, ...}`). */
    private fun popup(from: Int, vararg overrides: Pair<String, Any?>): JSONObject {
        val json = JSONObject(fixturePopups.getValue(from).toString())
        overrides.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        return json
    }

    private class Clock(var now: Long = 0L)

    /** Таймеры на виртуальном времени. */
    private class Scheduler(private val clock: Clock) : PopupsV3Scheduler {
        private class Task(val at: Long, val order: Int, val run: () -> Unit) {
            var cancelled = false
        }

        private val tasks = ArrayList<Task>()
        private var order = 0

        override fun schedule(delayMs: Long, task: () -> Unit): () -> Unit {
            val item = Task(clock.now + delayMs, order++, task)
            tasks += item
            return { item.cancelled = true }
        }

        fun advanceTo(time: Long) {
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= time }.minWithOrNull(compareBy({ it.at }, { it.order })) ?: break
                tasks -= next
                clock.now = next.at
                next.run()
            }
            clock.now = time
        }
    }

    private class Transport(var popups: List<JSONObject>) : PopupsV3Transport {
        var listCalls = 0
        var listResult: (() -> PopupsV3ListResult)? = null
        val oneCalls = ArrayList<Int>()
        val events = ArrayList<Triple<Int, String, String?>>()
        var promo: String? = null
        val promoCalls = ArrayList<String>()

        override fun list(testMode: Boolean, debug: Boolean, callback: (PopupsV3ListResult) -> Unit) {
            listCalls++
            val result = listResult?.invoke() ?: PopupsV3ListResult.Success(
                PopupsV3List.parse(JSONObject().put("status", "success").put("payload", JSONObject().put("ttl", "300").put("popups", JSONArray(popups))).toString())
            )
            callback(result)
        }

        override fun one(id: Int, testMode: Boolean, callback: (PopupV3?) -> Unit) {
            oneCalls += id
            val json = popups.firstOrNull { it.getInt("id") == id }
            callback(json?.let { (PopupV3.parse(it, manual = true) as? PopupV3.Parsed.Ok)?.popup })
        }

        override fun event(id: Int, event: String, channel: String?) {
            events += Triple(id, event, channel)
        }

        override fun promoCode(listId: String, callback: (String?) -> Unit) {
            promoCalls += listId
            callback(promo)
        }
    }

    /** Показ: модалка одна, баннер — по одному на край; попап «на экране» сразу. */
    private class Presenter : PopupsV3Presenter {
        val shown = ArrayList<Int>()
        val open = LinkedHashMap<Int, Pair<PopupV3, PopupsV3Presenter.Listener>>()
        val variables = HashMap<Int, Map<String, String>>()

        override fun canOpen(popup: PopupV3): String? = when {
            popup.isModal && open.values.any { it.first.isModal } -> "another modal is open"
            !popup.isModal && open.values.any { !it.first.isModal && it.first.placement == popup.placement } -> "banner busy"
            else -> null
        }

        override fun present(popup: PopupV3, variables: Map<String, String>, listener: PopupsV3Presenter.Listener) {
            shown += popup.id
            open[popup.id] = popup to listener
            this.variables[popup.id] = variables
            listener.onShown()
        }

        fun close(id: Int) {
            open.remove(id)?.second?.onClosed()
        }
    }

    private inner class Env(
        popups: List<JSONObject>,
        var sid: String = "SID1",
        var testMode: Boolean = false,
        var autoPresentation: Boolean = true,
        val storage: PopupsV3MemoryStorage = PopupsV3MemoryStorage(),
        val clock: Clock = Clock()
    ) {
        val scheduler = Scheduler(clock)
        val transport = Transport(popups)
        val presenter = Presenter()
        val logs = ArrayList<String>()
        val engine = PopupsV3Engine(
            transport = transport,
            presenter = presenter,
            frequency = PopupsV3Frequency(storage, { clock.now }, { sid }, { testMode }),
            scheduler = scheduler,
            clock = { clock.now },
            sid = { sid },
            testMode = { testMode },
            debug = { true },
            log = { logs += it },
            autoPresentation = { autoPresentation }
        )

        fun at(seconds: Double, step: () -> Unit = {}) {
            scheduler.advanceTo((seconds * 1000).toLong())
            step()
        }

        val showEvents: List<Int> get() = transport.events.filter { it.second == "show" }.map { it.first }
    }

    @Test
    fun `the list fixture arms known triggers, skips web ones and fills defaults`() {
        val list = PopupsV3List.parse(listFixture.toString())
        assertEquals(300L, list.ttlSeconds)
        val byId = list.popups.associateBy { it.id }
        assertEquals(listOf(512, 513, 514, 515, 516, 519), list.popups.map { it.id })
        assertEquals(PopupV3.Trigger.MANUAL, byId.getValue(516).trigger)
        assertEquals("once_session", byId.getValue(516).frequency)
        assertTrue(byId.getValue(516).targetScreens.isEmpty())
        assertEquals(0.0, byId.getValue(515).cooldownMinutes, 0.0)
        assertEquals(10.0, byId.getValue(514).triggerSeconds, 0.0)
        assertEquals(listOf("517", "518"), list.skipped.map { it.first })
        assertEquals(listOf("520" to "segments", "521" to "period"), list.excluded)
        assertEquals("7", byId.getValue(519).promoCodeListId)
        // Список промокодов есть, токена в дизайне нет — код не нужен.
        assertTrue(!byId.getValue(519).needsPromoCode)
    }

    @Test
    fun `excluded and skipped popups go to the debug log`() {
        val env = Env(emptyList())
        env.transport.listResult = { PopupsV3ListResult.Success(PopupsV3List.parse(listFixture.toString())) }
        env.engine.start()
        assertTrue(env.logs.toString(), env.logs.any { it.contains("popup 520 excluded by the server: segments") })
        assertTrue(env.logs.toString(), env.logs.any { it.contains("popup 517 skipped: trigger scroll_percent") })
        assertTrue(env.logs.toString(), env.logs.any { it.contains("popup 518 skipped: layout bar") })
    }

    @Test
    fun `app_start without screens shows on list load`() {
        val env = Env(listOf(popup(512)))
        env.engine.start()
        assertEquals(listOf(512), env.presenter.shown)
        assertEquals(listOf(512), env.showEvents)
    }

    @Test
    fun `app_start waits for a screen of its list`() {
        val env = Env(listOf(popup(512, "target_screens" to JSONArray(listOf("product")))))
        env.engine.start()
        env.engine.screenOpened("home")
        assertTrue(env.presenter.shown.isEmpty())
        env.engine.screenOpened("product")
        assertEquals(listOf(512), env.presenter.shown)
    }

    @Test
    fun `screen_open fires on a screen of its list`() {
        val env = Env(listOf(popup(513)))
        env.engine.start()
        env.engine.screenOpened("home")
        assertTrue(env.presenter.shown.isEmpty())
        env.engine.screenOpened("product")
        assertEquals(listOf(513), env.presenter.shown)
    }

    @Test
    fun `screen_open with an empty list fires on any screen`() {
        val env = Env(listOf(popup(513, "target_screens" to JSONArray())))
        env.engine.start()
        env.engine.screenOpened("home")
        assertEquals(listOf(513), env.presenter.shown)
    }

    @Test
    fun `screen_open fires when the list comes after the screen`() {
        val env = Env(listOf(popup(513)))
        env.engine.screenOpened("product")
        env.engine.start()
        assertEquals(listOf(513), env.presenter.shown)
    }

    @Test
    fun `time_on_screen — leaving early cancels`() {
        val env = Env(listOf(popup(514)))
        env.engine.start()
        env.at(0.0) { env.engine.screenOpened("home") }
        env.at(9.0) { env.engine.screenOpened("category") }
        env.at(20.0)
        assertTrue(env.presenter.shown.isEmpty())
    }

    @Test
    fun `time_on_screen — staying shows at the given second`() {
        val env = Env(listOf(popup(514)))
        env.engine.start()
        env.at(0.0) { env.engine.screenOpened("home") }
        env.at(9.9)
        assertTrue(env.presenter.shown.isEmpty())
        env.at(10.0)
        assertEquals(listOf(514), env.presenter.shown)
    }

    @Test
    fun `time_on_screen — the background resets the timer`() {
        val env = Env(listOf(popup(514)))
        env.engine.start()
        env.at(0.0) { env.engine.screenOpened("home") }
        env.at(5.0) { env.engine.onBackground() }
        env.at(30.0) { env.engine.onForeground() }
        env.at(39.0)
        assertTrue(env.presenter.shown.isEmpty())
        env.at(40.0)
        assertEquals(listOf(514), env.presenter.shown)
    }

    @Test
    fun `track_event fires on its event`() {
        val env = Env(listOf(popup(515)))
        env.engine.start()
        env.engine.onTrackEvent("other")
        assertTrue(env.presenter.shown.isEmpty())
        env.engine.onTrackEvent("promo_view")
        assertEquals(listOf(515), env.presenter.shown)
    }

    @Test
    fun `track_event on another screen does not wait for its screen`() {
        val env = Env(listOf(popup(515, "target_screens" to JSONArray(listOf("cart")))))
        env.engine.start()
        env.engine.screenOpened("home")
        env.engine.onTrackEvent("promo_view")
        env.engine.screenOpened("cart")
        assertTrue(env.presenter.shown.isEmpty())
    }

    @Test
    fun `track_event before the list waits for it`() {
        val env = Env(listOf(popup(515)))
        env.engine.onTrackEvent("promo_view")
        env.engine.start()
        assertEquals(listOf(515), env.presenter.shown)
    }

    @Test
    fun `manual show asks for the popup and does not record the frequency`() {
        val env = Env(listOf(popup(516)))
        env.engine.start()
        env.engine.screenOpened("home")
        assertTrue(env.presenter.shown.isEmpty())
        env.engine.showManual(516)
        assertEquals(listOf(516), env.transport.oneCalls)
        assertEquals(listOf(516), env.presenter.shown)
        assertEquals(listOf(516), env.showEvents)
        assertNull(env.storage.getLong(PopupsV3Frequency.lastKey(516)))
    }

    @Test
    fun `once_session — not again in the session, again in a new one`() {
        val storage = PopupsV3MemoryStorage()
        val first = Env(listOf(popup(512)), sid = "S1", storage = storage)
        first.engine.start()
        assertEquals(listOf(512), first.presenter.shown)
        first.presenter.close(512)

        val sameSession = Env(listOf(popup(512)), sid = "S1", storage = storage)
        sameSession.engine.start()
        assertTrue(sameSession.presenter.shown.isEmpty())

        val newSession = Env(listOf(popup(512)), sid = "S2", storage = storage)
        newSession.engine.start()
        assertEquals(listOf(512), newSession.presenter.shown)
    }

    @Test
    fun `once_day with a 60 minute cooldown`() {
        val env = Env(listOf(popup(513)))
        env.engine.start()
        val hour = 3600.0
        env.at(0.0) { env.engine.screenOpened("product") }
        assertEquals(listOf(513), env.presenter.shown)
        env.presenter.close(513)
        env.at(0.5 * hour) { env.engine.screenOpened("product") }
        env.at(23 * hour) { env.engine.screenOpened("product") }
        assertEquals(listOf(513), env.presenter.shown)
        assertTrue(env.logs.any { it.contains("cooldown 60 min") })
        assertTrue(env.logs.any { it.contains("once_day: shown recently") })
        env.at(24 * hour) { env.engine.screenOpened("product") }
        assertEquals(listOf(513, 513), env.presenter.shown)
    }

    @Test
    fun `every_page app_start — show, not again on the same screen, again on the next one`() {
        val env = Env(listOf(popup(512, "frequency" to "every_page")))
        env.engine.screenOpened("home")
        env.engine.start()
        assertEquals(listOf(512), env.presenter.shown)
        env.presenter.close(512)
        // Тот же экран: список пришёл заново (смена контекста) — app_start проверяется, частота не пускает.
        env.at(0.0) { env.engine.onContextChange("profile/set", "{}") }
        env.at(2.0)
        assertEquals(2, env.transport.listCalls)
        assertEquals(listOf(512), env.presenter.shown)
        env.engine.screenOpened("product")
        assertEquals(listOf(512, 512), env.presenter.shown)
    }

    @Test
    fun `app_start once_session is checked on every screen but shown once`() {
        val env = Env(listOf(popup(512)))
        env.engine.start()
        env.presenter.close(512)
        env.engine.screenOpened("home")
        env.engine.screenOpened("product")
        assertEquals(listOf(512), env.presenter.shown)
    }

    @Test
    fun `every_page — once per screen`() {
        val env = Env(listOf(popup(515, "frequency" to "every_page")))
        env.engine.start()
        env.engine.screenOpened("home")
        env.engine.onTrackEvent("promo_view")
        env.presenter.close(515)
        env.engine.onTrackEvent("promo_view")
        assertEquals(listOf(515), env.presenter.shown)
        env.engine.screenOpened("product")
        env.engine.onTrackEvent("promo_view")
        assertEquals(listOf(515, 515), env.presenter.shown)
    }

    @Test
    fun `test mode does not record the frequency`() {
        val storage = PopupsV3MemoryStorage()
        val first = Env(listOf(popup(512, "frequency" to "once_life")), testMode = true, storage = storage)
        first.engine.start()
        assertEquals(listOf(512), first.presenter.shown)
        assertTrue(storage.values.isEmpty())
        val again = Env(listOf(popup(512, "frequency" to "once_life")), testMode = true, storage = storage)
        again.engine.start()
        assertEquals(listOf(512), again.presenter.shown)
    }

    @Test
    fun `one modal at a time, the next one after it closes`() {
        val env = Env(listOf(popup(513), popup(513, "id" to 613)))
        env.engine.start()
        env.engine.screenOpened("product")
        assertEquals(listOf(513), env.presenter.shown)
        assertTrue(env.logs.any { it.contains("popup 613 postponed: another modal is open") })
        env.presenter.close(513)
        assertEquals(listOf(513, 613), env.presenter.shown)
    }

    @Test
    fun `a banner does not wait for the modal`() {
        val banner = popup(514, "trigger_type" to "app_start", "target_screens" to JSONArray())
        val env = Env(listOf(popup(512), banner))
        env.engine.start()
        assertEquals(listOf(512, 514), env.presenter.shown)
        assertEquals(2, env.presenter.open.size)
    }

    @Test
    fun `no promo code — no show and no show event`() {
        val design = JSONObject(Fixtures.read("popups-v3/element-promo-code.json")).getJSONObject("design")
        val env = Env(listOf(popup(519, "design" to design)))
        env.transport.promo = null
        env.engine.start()
        assertEquals(listOf("7"), env.transport.promoCalls)
        assertTrue(env.presenter.shown.isEmpty())
        assertTrue(env.showEvents.isEmpty())
        assertTrue(env.logs.any { it.contains("popup 519 skipped: no promo code") })
    }

    @Test
    fun `the promo code becomes the promo_code variable and is cached`() {
        val design = JSONObject(Fixtures.read("popups-v3/element-promo-code.json")).getJSONObject("design")
        val env = Env(listOf(popup(519, "design" to design, "frequency" to "every_page")))
        env.transport.promo = "ABC<123>"
        env.engine.start()
        assertEquals(mapOf("promo_code" to "ABC123"), env.presenter.variables[519])
        env.presenter.close(519)
        env.engine.showManual(519)
        assertEquals(listOf("7"), env.transport.promoCalls)
    }

    @Test
    fun `a promo code that never comes is given up after 10 seconds`() {
        val design = JSONObject(Fixtures.read("popups-v3/element-promo-code.json")).getJSONObject("design")
        val env = Env(listOf(popup(519, "design" to design)))
        val transport = object : PopupsV3Transport by env.transport {
            override fun promoCode(listId: String, callback: (String?) -> Unit) = Unit
        }
        val engine = PopupsV3Engine(
            transport, env.presenter, PopupsV3Frequency(env.storage, { env.clock.now }, { "S" }, { false }),
            env.scheduler, { env.clock.now }, { "S" }, { false }, { true }, { env.logs += it }
        )
        engine.start()
        env.at(10.0)
        assertTrue(env.presenter.shown.isEmpty())
        assertTrue(env.logs.any { it.contains("no answer in 10 s") })
    }

    @Test
    fun `a context change refetches the list after 2 seconds, the same data does not`() {
        val env = Env(listOf(popup(512)))
        env.engine.start()
        assertEquals(1, env.transport.listCalls)
        env.at(0.0) { env.engine.onContextChange("profile/set", """{"email":"a@b.c"}""") }
        env.at(1.9)
        assertEquals(1, env.transport.listCalls)
        env.at(2.0)
        assertEquals(2, env.transport.listCalls)
        env.at(3.0) { env.engine.onContextChange("profile/set", """{"email":"a@b.c"}""") }
        env.at(10.0)
        assertEquals(2, env.transport.listCalls)
        env.engine.onPurchase()
        env.at(12.0)
        assertEquals(3, env.transport.listCalls)
        // Открытый попап новый список не трогает, app_start в том же сеансе не стреляет снова.
        assertEquals(listOf(512), env.presenter.shown)
    }

    @Test
    fun `the list is cached for its ttl, client errors as an empty list, failures not at all`() {
        val env = Env(listOf(popup(512)))
        env.engine.start()
        env.engine.start()
        assertEquals(1, env.transport.listCalls)

        val failing = Env(emptyList())
        failing.transport.listResult = { PopupsV3ListResult.ClientError(402, "Payment required") }
        failing.engine.start()
        failing.engine.start()
        assertEquals(1, failing.transport.listCalls)
        failing.at(301.0) { failing.engine.start() }
        assertEquals(2, failing.transport.listCalls)

        val offline = Env(emptyList())
        offline.transport.listResult = { PopupsV3ListResult.Failure("timeout") }
        offline.engine.start()
        offline.engine.start()
        assertEquals(2, offline.transport.listCalls)
    }

    @Test
    fun `back from the background refetches an expired list`() {
        val env = Env(listOf(popup(512)))
        env.engine.start()
        env.engine.onBackground()
        env.at(100.0) { env.engine.onForeground() }
        assertEquals(1, env.transport.listCalls)
        env.engine.onBackground()
        env.at(400.0) { env.engine.onForeground() }
        assertEquals(2, env.transport.listCalls)
    }

    @Test
    fun `automatic presentation off — a trigger is skipped, a manual show still shows`() {
        val env = Env(listOf(popup(512)), autoPresentation = false)
        env.engine.start()
        env.engine.screenOpened("home")
        assertTrue(env.presenter.shown.isEmpty())
        assertTrue(env.logs.any { it.contains("popup 512 skipped: automatic popup presentation is off") })
        assertNull(env.storage.getLong(PopupsV3Frequency.lastKey(512)))

        env.engine.showManual(512)
        assertEquals(listOf(512), env.presenter.shown)
        assertEquals(listOf(512), env.showEvents)

        env.presenter.close(512)
        env.autoPresentation = true
        env.engine.screenOpened("cart")
        assertEquals("on again — the trigger shows", listOf(512, 512), env.presenter.shown)
    }

    @Test
    fun `an empty design is never shown`() {
        val design = JSONObject(Fixtures.read("popups-v3/empty-design.json")).getJSONObject("design")
        val env = Env(listOf(popup(512, "design" to design)))
        env.engine.start()
        assertTrue(env.presenter.shown.isEmpty())
        assertTrue(env.logs.any { it.contains("empty design") })
    }
}
