package com.personalization.ui.widgets

import android.os.SystemClock
import com.personalization.Cancellable

/**
 * Кэш ответов для виджетов с данными — в памяти процесса.
 *
 * Ключ — магазин, вид запроса и его параметры ([Key]). Ответ живёт [ttlMillis] (10 минут),
 * записей не больше [capacity] (32): при переполнении уходят самые старые. Одинаковые
 * запросы, пока первый в полёте, в сеть второй раз не идут — все ждут его ответа.
 * Ошибки не кэшируются: следующий такой же запрос снова идёт в сеть.
 *
 * Запрос в полёте не может навсегда занять ключ. Старше [flightTimeoutMillis] (30 секунд)
 * он новых ожидающих не принимает — стартует новая загрузка, новое поколение; отписка
 * последнего ожидающего и [clear] снимают его сразу. Ответ старого поколения доходит
 * только до своих ожидающих и в кэш не идёт, если после его старта была новая загрузка
 * того же ключа или [clear].
 *
 * Колбэки зовутся там, где пришёл ответ: из кэша — сразу, на потоке вызова, из сети —
 * на её потоке. На главный поток перекладывает виджет.
 */
internal class ResponseCache(
    private val ttlMillis: Long = TTL_MILLIS,
    private val capacity: Int = CAPACITY,
    private val flightTimeoutMillis: Long = FLIGHT_TIMEOUT_MILLIS,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {

    /**
     * [kind] — вид запроса (`recommendation`, `category`…), [params] — всё, от чего зависит
     * ответ. Параметры копируются: менять переданную карту после создания ключа безопасно.
     */
    class Key(val shopId: String, val kind: String, params: Map<String, String?> = emptyMap()) {
        val params: Map<String, String?> = params.toMap()

        override fun equals(other: Any?): Boolean =
            other is Key && other.shopId == shopId && other.kind == kind && other.params == params

        override fun hashCode(): Int = (shopId.hashCode() * 31 + kind.hashCode()) * 31 + params.hashCode()

        override fun toString(): String = "Key($shopId, $kind, $params)"
    }

    private class Entry(val value: Any, val storedAt: Long, val generation: Long)

    private class Waiter(val onSuccess: (Any) -> Unit, val onError: (Int, String?) -> Unit) {
        // Отписанный ожидающий не зовётся, даже если ответ уже раздаётся.
        @Volatile
        var active = true
    }

    private class Flight(val generation: Long, val startedAt: Long) {
        val waiters = mutableListOf<Waiter>()
    }

    private val lock = Any()

    // Порядок записи: первая — самая старая, её и вытесняем.
    private val entries = LinkedHashMap<Key, Entry>()
    private val inFlight = HashMap<Key, Flight>()
    private var generation = 0L

    // Поколения не новее этого стартовали до последнего clear(): их ответы не сохраняются.
    private var clearedGeneration = 0L

    /**
     * Ответ по [key]: свежий из кэша, ожидание уже идущего запроса или новый запрос [load].
     * [force] — мимо готового ответа и мимо идущего запроса, сразу в сеть: так повторяет
     * кнопка «Повторить».
     *
     * Ручка отписывает колбэки этого вызова. Если ждать больше некому, запрос перестаёт
     * занимать ключ; его ответ всё равно сохранится, если более новой загрузки не было.
     */
    fun <T : Any> get(
        key: Key,
        load: (onSuccess: (T) -> Unit, onError: (Int, String?) -> Unit) -> Unit,
        onSuccess: (T) -> Unit,
        onError: (Int, String?) -> Unit,
        force: Boolean = false
    ): Cancellable {
        @Suppress("UNCHECKED_CAST")
        val waiter = Waiter({ onSuccess(it as T) }, onError)
        var cached: Any? = null
        var joined: Flight? = null
        var started: Flight? = null
        synchronized(lock) {
            if (!force) cached = fresh(key)
            if (cached == null) {
                val running = inFlight[key]
                val flight = if (!force && running != null && clock() - running.startedAt < flightTimeoutMillis) {
                    running
                } else {
                    Flight(++generation, clock()).also {
                        inFlight[key] = it
                        started = it
                    }
                }
                flight.waiters += waiter
                joined = flight
            }
        }
        cached?.let {
            waiter.onSuccess(it)
            return Cancellable.NOOP
        }
        val flight = checkNotNull(joined)
        started?.let { own ->
            try {
                load({ value -> succeed(key, own, value) }, { code, message -> fail(key, own, code, message) })
            } catch (throwable: Throwable) {
                fail(key, own, ERROR_CODE, throwable.message)
            }
        }
        return Cancellable {
            waiter.active = false
            synchronized(lock) {
                flight.waiters.remove(waiter)
                if (flight.waiters.isEmpty() && inFlight[key] === flight) inFlight.remove(key)
            }
        }
    }

    /** Забыть все ответы и запросы в полёте; ожидающие получат свои ответы, но в кэш они не лягут. */
    fun clear() {
        synchronized(lock) {
            entries.clear()
            inFlight.clear()
            clearedGeneration = generation
        }
    }

    private fun fresh(key: Key): Any? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt < ttlMillis) return entry.value
        entries.remove(key)
        return null
    }

    private fun succeed(key: Key, flight: Flight, value: Any) {
        val waiters = synchronized(lock) {
            val newerRunning = inFlight[key].let { it != null && it !== flight }
            val newerStored = (entries[key]?.generation ?: 0L) > flight.generation
            if (!newerRunning && !newerStored && flight.generation > clearedGeneration) {
                entries.remove(key)
                entries[key] = Entry(value, clock(), flight.generation)
                while (entries.size > capacity) entries.remove(entries.keys.first())
            }
            finish(key, flight)
        }
        deliver(waiters) { it.onSuccess(value) }
    }

    private fun fail(key: Key, flight: Flight, code: Int, message: String?) {
        val waiters = synchronized(lock) { finish(key, flight) }
        deliver(waiters) { it.onError(code, message) }
    }

    /** Снимает запрос с ключа, если ключ всё ещё за ним, и отдаёт его ожидающих. */
    private fun finish(key: Key, flight: Flight): List<Waiter> {
        if (inFlight[key] === flight) inFlight.remove(key)
        val waiters = flight.waiters.toList()
        flight.waiters.clear()
        return waiters
    }

    /**
     * Раздаёт ответ всем ожидающим: колбэк, бросивший исключение, не лишает ответа
     * остальных — исключение уходит дальше, когда раздача закончена.
     */
    private fun deliver(waiters: List<Waiter>, call: (Waiter) -> Unit) {
        var failure: Throwable? = null
        waiters.forEach { waiter ->
            if (!waiter.active) return@forEach
            try {
                call(waiter)
            } catch (throwable: Throwable) {
                failure?.addSuppressed(throwable) ?: run { failure = throwable }
            }
        }
        failure?.let { throw it }
    }

    companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
        const val CAPACITY = 32
        const val FLIGHT_TIMEOUT_MILLIS = 30 * 1000L

        /** Запрос упал, не дойдя до сети. */
        const val ERROR_CODE = -1

        /** Общий кэш процесса для всех виджетов. */
        val shared = ResponseCache()
    }
}
