package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.sync.Mutex
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * One event in a shop's queue: the request's own fields ([body], without the common ones, which go on
 * at send time), when it was made, and what its answers so far did to it.
 */
internal class QueuedEvent(
    /** Its file's name without the extension: zero-padded, so the names sort in the queue's order. */
    val id: String,
    val path: String,
    val body: JSONObject,
    val createdAt: Long,
    /** Answers to repeat (`5xx`, `408`, `410`, `417`, `426`, `429`) it has had; at 10 it is dropped. */
    val attempts: Int = 0,
    /** It had a `402`, `403` or `404`, and `/init` found the shop fine since: the next one drops it. */
    val shopChecked: Boolean = false,
) {
    fun copy(attempts: Int = this.attempts, shopChecked: Boolean = this.shopChecked) =
        QueuedEvent(id, path, body, createdAt, attempts, shopChecked)

    fun toJson(): JSONObject = JSONObject()
        .put(FIELD_VERSION, VERSION)
        .put(FIELD_PATH, path)
        .put(FIELD_BODY, body)
        .put(FIELD_CREATED_AT, createdAt)
        .put(FIELD_ATTEMPTS, attempts)
        .put(FIELD_SHOP_CHECKED, shopChecked)

    companion object {
        private const val VERSION = 1
        private const val FIELD_VERSION = "v"
        private const val FIELD_PATH = "path"
        private const val FIELD_BODY = "body"
        private const val FIELD_CREATED_AT = "created_at"
        private const val FIELD_ATTEMPTS = "attempts"
        private const val FIELD_SHOP_CHECKED = "shop_checked"

        /** Null for a file that is not an event of this format. */
        fun parse(id: String, text: String): QueuedEvent? = try {
            val json = JSONObject(text)
            val path = json.optString(FIELD_PATH)
            val body = json.optJSONObject(FIELD_BODY)
            if (json.optInt(FIELD_VERSION) != VERSION || path.isEmpty() || body == null || !json.has(FIELD_CREATED_AT)) {
                null
            } else {
                QueuedEvent(id, path, body, json.getLong(FIELD_CREATED_AT), json.optInt(FIELD_ATTEMPTS), json.optBoolean(FIELD_SHOP_CHECKED))
            }
        } catch (e: JSONException) {
            null
        }
    }
}

/**
 * The files of one shop's queue, in its `queue/` directory under `noBackupFilesDir`: one JSON file per
 * event, named by a sequence so the names sort in order. One store per directory in the process, shared
 * by every [EventQueue] of the shop, so two instances of one shop never send an event twice:
 * - the queue made last is the [owner] and the only one that sends; the others write what they accept
 *   and wake it;
 * - [sending] is held for an event from its send to its answer: a new owner waits for the old one's
 *   event in flight;
 * - an event's old-style listener waits here, so whoever sends it calls it.
 *
 * A write goes to a temporary file first and is renamed over the event's file: a crash midway leaves
 * the old file or the new one, never a part. Disk failures are `storage_failed`; nothing here throws.
 */
internal class QueueStore private constructor(private val directory: File, private val shopId: String?) {

    private val lock = Any()
    private val owners = ArrayList<EventQueue>()
    private val listeners = HashMap<String, Waiting>()
    private var sequence = 0L

    /** Held from an event's send to its answer. */
    val sending = Mutex()

    /** An event's listener and the queue that accepted it. */
    class Waiting(val queue: EventQueue, val listener: OnApiCallbackListener)

    /** An event [remove] took off the queue, with its listener if it had one. */
    class Removed(val waiting: Waiting?)

    /** [queue] becomes the owner: it sends from now on. */
    fun claim(queue: EventQueue) = synchronized(lock) {
        owners -= queue
        owners += queue
    }

    /** [queue] stops: the queue made before it, if one is live, sends again. Returns the new owner. */
    fun leave(queue: EventQueue): EventQueue? = synchronized(lock) {
        owners -= queue
        owners.lastOrNull()
    }

    val owner: EventQueue? get() = synchronized(lock) { owners.lastOrNull() }

    fun isOwner(queue: EventQueue): Boolean = synchronized(lock) { owners.lastOrNull() === queue }

    /** The ids on disk in order; a temporary file left by a crash is removed. */
    private fun ids(): List<String> {
        val names = directory.list() ?: return emptyList()
        val ids = ArrayList<String>(names.size)
        for (name in names) {
            when {
                name.endsWith(EXTENSION) -> ids += name.removeSuffix(EXTENSION)
                name.endsWith(TEMPORARY) -> File(directory, name).delete()
            }
        }
        ids.sort()
        return ids
    }

    /** How many events wait. */
    fun size(): Int = synchronized(lock) { ids().size }

    /** The oldest event that can be read; a file that cannot is dropped (`queue_dropped`, `unreadable`). */
    fun head(): QueuedEvent? = synchronized(lock) {
        for (id in ids()) {
            read(id)?.let { return it }
            File(directory, id + EXTENSION).delete()
            listeners.remove(id)
            SdkLogs.log(LogLevel.WARNING, LogCodes.QUEUE_DROPPED, "An event that could not be read was dropped", shopId,
                mapOf("reason" to REASON_UNREADABLE))
        }
        null
    }

    fun contains(id: String): Boolean = synchronized(lock) { File(directory, id + EXTENSION).isFile }

    /**
     * Writes a new event from [path], [body] and [createdAt] and keeps [listener] for it. Before, the
     * oldest events go while the queue would hold more than [cap]; they come back in [dropped]. Returns
     * the event, or null when the disk refused it (it is then not accepted: the caller decides).
     */
    fun add(
        queue: EventQueue,
        path: String,
        body: JSONObject,
        createdAt: Long,
        listener: OnApiCallbackListener?,
        cap: Int,
        dropped: MutableList<Pair<QueuedEvent?, Waiting?>>,
    ): QueuedEvent? = synchronized(lock) {
        val ids = ids()
        val over = ids.size - cap + 1
        if (over > 0) {
            ids.take(over).forEach { id -> dropped += read(id) to listeners.remove(id); File(directory, id + EXTENSION).delete() }
        }
        val last = ids.lastOrNull()?.toLongOrNull() ?: 0L
        sequence = maxOf(sequence, last) + 1
        val event = QueuedEvent(String.format(Locale.ROOT, ID_FORMAT, sequence), path, body, createdAt)
        if (!write(event)) return null
        if (listener != null) listeners[event.id] = Waiting(queue, listener)
        event
    }

    /** Rewrites [event] (its attempts, its check) if it is still there; false when it is gone or the disk refused. */
    fun update(event: QueuedEvent): Boolean = synchronized(lock) {
        if (!File(directory, event.id + EXTENSION).isFile) return false
        write(event)
    }

    /** Removes [id] with its listener; null when it was gone already (cleared, or dropped meanwhile). */
    fun remove(id: String): Removed? = synchronized(lock) {
        val file = File(directory, id + EXTENSION)
        if (!file.isFile) return null
        if (!file.delete() && file.exists()) failed(OP_DELETE, null)
        Removed(listeners.remove(id))
    }

    /** The listener [id] has, without taking it. */
    fun listenerOf(id: String): Waiting? = synchronized(lock) { listeners[id] }

    /**
     * Removes every event and hands back their listeners, then runs [then] while no event can be
     * accepted (the customer's data goes with it, `deleteUserCredentials()`). Returns how many went and
     * [then]'s result.
     */
    fun <T> clear(then: () -> T): Triple<Int, List<Waiting>, T> = synchronized(lock) {
        val ids = ids()
        ids.forEach { id ->
            val file = File(directory, id + EXTENSION)
            if (!file.delete() && file.exists()) failed(OP_DELETE, null)
        }
        val waiting = listeners.values.toList()
        listeners.clear()
        Triple(ids.size, waiting, then())
    }

    /** Takes the listeners [queue] accepted: it stops, their events stay on disk for a later launch. */
    fun takeListeners(queue: EventQueue): List<Waiting> = synchronized(lock) {
        val mine = listeners.filterValues { it.queue === queue }
        mine.keys.forEach(listeners::remove)
        mine.values.toList()
    }

    private fun read(id: String): QueuedEvent? = try {
        QueuedEvent.parse(id, File(directory, id + EXTENSION).readText(Charsets.UTF_8))
    } catch (e: IOException) {
        failed(OP_READ, e)
        null
    }

    /** A temporary file synced to the disk, then renamed over the event's file. */
    private fun write(event: QueuedEvent): Boolean {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            failed(OP_WRITE, null)
            return false
        }
        val temporary = File(directory, event.id + TEMPORARY)
        return try {
            FileOutputStream(temporary).use { stream ->
                stream.write(event.toJson().toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (temporary.renameTo(File(directory, event.id + EXTENSION))) {
                true
            } else {
                temporary.delete()
                failed(OP_WRITE, null)
                false
            }
        } catch (e: IOException) {
            temporary.delete()
            failed(OP_WRITE, e)
            false
        }
    }

    private fun failed(op: String, throwable: Throwable?) {
        SdkLogs.log(LogLevel.WARNING, LogCodes.STORAGE_FAILED, "Storage operation failed: $op", shopId, mapOf("op" to op), throwable)
    }

    internal companion object {
        private const val EXTENSION = ".json"
        private const val TEMPORARY = ".tmp"
        private const val ID_FORMAT = "%019d"
        const val OP_READ = "read_queued_event"
        const val OP_WRITE = "write_queued_event"
        const val OP_DELETE = "delete_queued_event"

        private const val REASON_UNREADABLE = "unreadable"

        private val stores = HashMap<String, QueueStore>()

        /** The process's store of [directory]. */
        fun of(directory: File, shopId: String?): QueueStore = synchronized(stores) {
            stores.getOrPut(directory.absolutePath) { QueueStore(directory, shopId) }
        }

        /** Forgets every store, as a new process would; for tests. */
        internal fun reset() = synchronized(stores) { stores.clear() }
    }
}
