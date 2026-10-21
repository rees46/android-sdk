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
 * Колбэки зовутся там, где пришёл ответ: из кэша — сразу, на потоке вызова, из сети —
 * на её потоке. На главный поток перекладывает виджет.
 */
internal class ResponseCache(
    private val ttlMillis: Long = TTL_MILLIS,
    private val capacity: Int = CAPACITY,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {

    /** [kind] — вид запроса (`recommendation`, `category`…), [params] — всё, от чего зависит ответ. */
    data class Key(val shopId: String, val kind: String, val params: Map<String, String?> = emptyMap())

    private class Entry(val value: Any, val storedAt: Long)

    private class Waiter(val onSuccess: (Any) -> Unit, val onError: (Int, String?) -> Unit)

    private val lock = Any()

    // Порядок записи: первая — самая старая, её и вытесняем.
    private val entries = LinkedHashMap<Key, Entry>()
    private val inFlight = HashMap<Key, MutableList<Waiter>>()

    /**
     * Ответ по [key]: свежий из кэша, ожидание уже идущего запроса или новый запрос [load].
     *
     * Ручка отписывает колбэки этого вызова; сам запрос при этом доходит до конца, и его
     * ответ попадает в кэш — он пригодится следующему показу.
     */
    fun <T : Any> get(
        key: Key,
        load: (onSuccess: (T) -> Unit, onError: (Int, String?) -> Unit) -> Unit,
        onSuccess: (T) -> Unit,
        onError: (Int, String?) -> Unit
    ): Cancellable {
        @Suppress("UNCHECKED_CAST")
        val waiter = Waiter({ onSuccess(it as T) }, onError)
        var cached: Any? = null
        var start = false
        synchronized(lock) {
            cached = fresh(key)
            if (cached == null) {
                val waiting = inFlight[key]
                if (waiting != null) {
                    waiting += waiter
                } else {
                    inFlight[key] = mutableListOf(waiter)
                    start = true
                }
            }
        }
        cached?.let {
            waiter.onSuccess(it)
            return Cancellable.NOOP
        }
        if (start) {
            try {
                load({ value -> succeed(key, value) }, { code, message -> fail(key, code, message) })
            } catch (throwable: Throwable) {
                fail(key, ERROR_CODE, throwable.message)
            }
        }
        return Cancellable { synchronized(lock) { inFlight[key]?.remove(waiter) } }
    }

    /** Забыть все ответы; запросы в полёте доходят как обычно. */
    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun fresh(key: Key): Any? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt < ttlMillis) return entry.value
        entries.remove(key)
        return null
    }

    private fun succeed(key: Key, value: Any) {
        val waiters = synchronized(lock) {
            entries.remove(key)
            entries[key] = Entry(value, clock())
            while (entries.size > capacity) entries.remove(entries.keys.first())
            inFlight.remove(key).orEmpty()
        }
        waiters.forEach { it.onSuccess(value) }
    }

    private fun fail(key: Key, code: Int, message: String?) {
        val waiters = synchronized(lock) { inFlight.remove(key).orEmpty() }
        waiters.forEach { it.onError(code, message) }
    }

    companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
        const val CAPACITY = 32

        /** Запрос упал, не дойдя до сети. */
        const val ERROR_CODE = -1

        /** Общий кэш процесса для всех виджетов. */
        val shared = ResponseCache()
    }
}
