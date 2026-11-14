package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.sync.Mutex
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.Locale
import kotlin.random.Random

/**
 * The queue's two lanes: each keeps its own order, its pause after a failure and its count of failures.
 */
internal enum class QueueLane {
    /** The push events (`track/delivered` and the others): they need only a `did`, never a session. */
    PUSH,

    /** The other events: they need the instance ready and a live session. */
    TRACKING;

    internal companion object {
        fun of(path: String): QueueLane = if (path in EventQueue.PUSH_EVENT_PATHS) PUSH else TRACKING
    }
}

/**
 * One event in a shop's queue: the request's own fields ([body], without the common ones, which go on a
 * copy at send time and never reach the disk), when it was made, and what its answers so far did to it.
 */
internal class QueuedEvent(
    /** Its file's name without the extension: it sorts in the queue's order. */
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

    val lane: QueueLane get() = QueueLane.of(path)

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
 * event, named by the time it was made (in microseconds, rising within the process), the process and a
 * random part: the names sort in order, and two instances or processes never pick the same one. One store per directory in the process, shared by
 * every [EventQueue] of the shop, so two instances of one shop never send an event twice:
 * - the queue made last is the [owner] and the only one that sends; the others write what they accept
 *   and wake it;
 * - [sending] is held for an event from its send to its answer: a new owner waits for the old one's
 *   event in flight;
 * - only the process that holds the file lock of the directory ([lockForSending]) sends: another
 *   process of the app writes its events and leaves the sending to it;
 * - an event's old-style listener waits here, so whoever sends it calls it.
 *
 * A write goes to a temporary file first and is renamed over the event's file: a crash midway leaves
 * the old file or the new one, never a part. Disk failures are `storage_failed`; nothing here throws.
 * An event whose file could not be rewritten keeps its new state in memory, and one whose file could
 * not be removed after it went is skipped from then on.
 *
 * Three locks, never one inside another the other way round: [files] for the disk (held while a file is
 * synced or the directory deleted), then [listenersLock]; [ownersLock] alone. The main thread takes only
 * the last two, so it never waits for the disk.
 */
internal class QueueStore private constructor(private val directory: File, private val shopId: String?) {

    private val files = Any()
    private val listenersLock = Any()
    private val ownersLock = Any()

    private val owners = ArrayList<EventQueue>()
    private val listeners = HashMap<String, Waiting>()

    /** Events that went but whose file could not be removed: skipped, removed again by a later listing. */
    private val gone = HashSet<String>()

    /** The state of events whose file could not be rewritten: kept for this process. */
    private val patched = HashMap<String, QueuedEvent>()

    /**
     * What this process knows of the events on disk without reading their files again: each one's lane and
     * when it was made, from its write or its first read. A [head] that skips a lane passes over its events
     * without reading them on every pass. Guarded by [files].
     */
    private val index = HashMap<String, Entry>()

    /** The time of the last name, in microseconds: names of one process rise with the calls. */
    private var lastStamp = 0L

    private var lockChannel: FileChannel? = null
    private var processLock: FileLock? = null

    /** Moves on each [clear]: an event read before it is the cleared customer's and does not go. */
    @Volatile
    var generation = 0L
        private set

    /** Held from an event's send to its answer. */
    val sending = Mutex()

    /** An event's listener and the queue that accepted it. */
    class Waiting(val queue: EventQueue, val listener: OnApiCallbackListener)

    /** An event as [index] knows it. */
    class Entry(val lane: QueueLane, val createdAt: Long)

    /** An event [remove] took off the queue, with its listener if it had one. */
    class Removed(val waiting: Waiting?)

    /** What [head] found. */
    sealed interface Head {
        object Empty : Head

        /** The oldest event could not be read now (the disk failed): it stays for a later try. */
        object Unavailable : Head
        class Event(val event: QueuedEvent) : Head
    }

    /** [queue] becomes the owner: it sends from now on. */
    fun claim(queue: EventQueue) = synchronized(ownersLock) {
        owners -= queue
        owners += queue
    }

    /** [queue] stops: the queue made before it, if one is live, sends again. Returns the new owner. */
    fun leave(queue: EventQueue): EventQueue? = synchronized(ownersLock) {
        owners -= queue
        owners.lastOrNull()
    }

    val owner: EventQueue? get() = synchronized(ownersLock) { owners.lastOrNull() }

    fun isOwner(queue: EventQueue): Boolean = synchronized(ownersLock) { owners.lastOrNull() === queue }

    /**
     * Takes the directory's file lock for this process; true while it holds it. Another process of the
     * app that holds it is the one that sends.
     */
    fun lockForSending(): Boolean = synchronized(files) {
        if (processLock?.isValid == true) return true
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return false
        var channel: FileChannel? = null
        try {
            channel = RandomAccessFile(File(directory, LOCK_FILE), "rw").channel
            val lock = channel.tryLock()
            if (lock == null) {
                channel.close()
                return false
            }
            lockChannel = channel
            processLock = lock
            true
        } catch (e: OverlappingFileLockException) {
            // Held through another channel of this process, which only a test does.
            channel?.close()
            false
        } catch (e: Exception) {
            channel?.close()
            failed(OP_LOCK, e)
            false
        }
    }

    /** Gives the file lock back, for another process to send. */
    fun unlockForSending() = synchronized(files) { unlockLocked() }

    /** Must hold [files]. */
    private fun unlockLocked() {
        try {
            processLock?.release()
        } catch (e: Exception) {
            // A lock on a file deleted meanwhile, or a channel closed: nothing is held any more.
        }
        try {
            lockChannel?.close()
        } catch (e: Exception) {
            // As above.
        }
        processLock = null
        lockChannel = null
    }

    /**
     * Must hold [files]. The ids on disk in order. A temporary file is removed only once it is old: a
     * fresh one may be another process's write in progress. An event that went but stayed on disk is
     * removed again, and skipped while that fails.
     */
    private fun ids(): List<String> {
        val names = directory.list() ?: return emptyList()
        val now = System.currentTimeMillis()
        val ids = ArrayList<String>(names.size)
        for (name in names) {
            when {
                name.endsWith(EXTENSION) -> {
                    val id = name.removeSuffix(EXTENSION)
                    if (id in gone) {
                        val file = File(directory, name)
                        if (file.delete() || !file.exists()) gone -= id
                    } else {
                        ids += id
                    }
                }
                name.endsWith(TEMPORARY) -> File(directory, name).let { file ->
                    if (now - file.lastModified() > STALE_TEMPORARY_MS) file.delete()
                }
            }
        }
        gone.retainAll { File(directory, it + EXTENSION).exists() }
        ids.sort()
        // Sent or cleared by another process meanwhile: forgotten here too.
        if (index.isNotEmpty()) index.keys.retainAll(ids.toHashSet())
        return ids
    }

    /**
     * The oldest event whose lane is not [skipped]. A file that is not an event (it does not parse) is dropped
     * (`queue_dropped`, `unreadable`) and its listener goes to [unreadable], to hear an error; a file the
     * disk cannot read now stays, and the head is [Head.Unavailable].
     */
    fun head(unreadable: MutableList<Waiting>, skipped: (QueueLane) -> Boolean = { false }): Head = synchronized(files) {
        for (id in ids()) {
            // Known already: a skipped lane's event is not read again.
            val known = index[id]
            if (known != null && skipped(known.lane)) continue
            val file = File(directory, id + EXTENSION)
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (e: FileNotFoundException) {
                // Gone meanwhile (another process sent it), or the disk refused it.
                if (!file.exists()) continue
                failed(OP_READ, e)
                return Head.Unavailable
            } catch (e: IOException) {
                failed(OP_READ, e)
                return Head.Unavailable
            }
            val event = QueuedEvent.parse(id, text)
            if (event != null) {
                index[id] = Entry(event.lane, event.createdAt)
                if (skipped(event.lane)) continue
                return Head.Event(patched[id] ?: event)
            }
            file.delete()
            patched -= id
            index -= id
            synchronized(listenersLock) { listeners.remove(id) }?.let { unreadable += it }
            SdkLogs.log(LogLevel.WARNING, LogCodes.QUEUE_DROPPED, "An event that could not be read was dropped", shopId,
                mapOf("reason" to REASON_UNREADABLE))
        }
        Head.Empty
    }

    /**
     * Runs [block] while the event [id] is still there and no [clear] came since [generation], and this
     * process holds the file lock; null otherwise. The queue stamps an event's fields here, so a deletion
     * of the customer either comes after them — the event goes as the deleted customer's — or before,
     * and the event does not go.
     */
    fun <T> ifCurrent(generation: Long, id: String, block: () -> T): T? = synchronized(files) {
        if (generation != this.generation || id in gone || processLock?.isValid != true) return null
        if (!File(directory, id + EXTENSION).isFile) return null
        block()
    }

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
    ): QueuedEvent? = synchronized(files) {
        val ids = ids()
        val over = ids.size - cap + 1
        if (over > 0) {
            ids.take(over).forEach { id ->
                val old = read(id)
                File(directory, id + EXTENSION).delete()
                patched -= id
                index -= id
                dropped += old to synchronized(listenersLock) { listeners.remove(id) }
            }
        }
        val event = QueuedEvent(newId(createdAt), path, body, createdAt)
        if (!write(event)) return null
        index[event.id] = Entry(event.lane, createdAt)
        if (listener != null) synchronized(listenersLock) { listeners[event.id] = Waiting(queue, listener) }
        event
    }

    /**
     * Must hold [files]. Sorts by time: the event's in microseconds, kept above the last name's so that
     * two events of one millisecond keep the order of the calls.
     */
    private fun newId(createdAt: Long): String {
        val stamp = maxOf(createdAt * 1_000, lastStamp + 1)
        lastStamp = stamp
        return String.format(Locale.ROOT, ID_FORMAT, stamp, PROCESS, Random.nextInt() and 0x7fffffff)
    }

    /**
     * Rewrites [event] (its attempts, its check) if it is still there. False when it is gone or the disk
     * refused: the new state is kept for this process all the same.
     */
    fun update(event: QueuedEvent): Boolean = synchronized(files) {
        if (event.id in gone || !File(directory, event.id + EXTENSION).isFile) return false
        patched[event.id] = event
        if (!write(event)) return false
        patched -= event.id
        true
    }

    /**
     * Removes [id] with its listener; null when it was gone already (cleared, or dropped meanwhile). A
     * file the disk keeps is skipped from now on.
     */
    fun remove(id: String): Removed? = synchronized(files) {
        val file = File(directory, id + EXTENSION)
        if (id in gone || !file.isFile) return null
        if (!file.delete() && file.exists()) {
            failed(OP_DELETE, null)
            gone += id
        }
        patched -= id
        index -= id
        Removed(synchronized(listenersLock) { listeners.remove(id) })
    }

    /**
     * Removes every event and hands back their listeners, then runs [then] while no event can be
     * accepted (the customer's data goes with it, `deleteUserCredentials()`). Moves the [generation] first,
     * so an event read before does not go, and gives back the file lock, which [then] may delete. Returns
     * how many went and [then]'s result.
     */
    fun <T> clear(then: () -> T): Triple<Int, List<Waiting>, T> = synchronized(files) {
        generation++
        unlockLocked()
        val ids = ids()
        ids.forEach { id ->
            val file = File(directory, id + EXTENSION)
            if (!file.delete() && file.exists()) {
                failed(OP_DELETE, null)
                gone += id
            }
        }
        patched.clear()
        index.clear()
        val waiting = synchronized(listenersLock) { listeners.values.toList().also { listeners.clear() } }
        Triple(ids.size, waiting, then())
    }

    /** Takes the listeners [queue] accepted: it stops, their events stay on disk for a later launch. */
    fun takeListeners(queue: EventQueue): List<Waiting> = synchronized(listenersLock) {
        val mine = listeners.filterValues { it.queue === queue }
        mine.keys.forEach(listeners::remove)
        mine.values.toList()
    }

    /** Must hold [files]. */
    private fun read(id: String): QueuedEvent? = try {
        QueuedEvent.parse(id, File(directory, id + EXTENSION).readText(Charsets.UTF_8))
    } catch (e: IOException) {
        failed(OP_READ, e)
        null
    }

    /** Must hold [files]. A temporary file synced to the disk, then renamed over the event's file. */
    private fun write(event: QueuedEvent): Boolean {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            failed(OP_WRITE, null)
            return false
        }
        val temporary = File(directory, event.id + TEMPORARY)
        return try {
            // JSONObject.toString() gives null for a value JSON cannot hold: not written.
            val text: String = event.toJson().toString() ?: throw IOException("The event is not JSON")
            FileOutputStream(temporary).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (temporary.renameTo(File(directory, event.id + EXTENSION))) {
                true
            } else {
                temporary.delete()
                failed(OP_WRITE, null)
                false
            }
        } catch (e: Exception) {
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
        const val LOCK_FILE = ".lock"

        /** Time, the process and a random part: sortable, and unique across instances and processes. */
        private const val ID_FORMAT = "%016d-%d-%08x"

        /** A temporary file older than this is a crash's leftover, not a write in progress. */
        const val STALE_TEMPORARY_MS = 5 * 60 * 1000L

        const val OP_READ = "read_queued_event"
        const val OP_WRITE = "write_queued_event"
        const val OP_DELETE = "delete_queued_event"
        const val OP_LOCK = "lock_queue"

        private const val REASON_UNREADABLE = "unreadable"

        private val PROCESS: Int by lazy { android.os.Process.myPid() }

        private val stores = HashMap<String, QueueStore>()

        /** The process's store of [directory]. */
        fun of(directory: File, shopId: String?): QueueStore = synchronized(stores) {
            stores.getOrPut(directory.absolutePath) { QueueStore(directory, shopId) }
        }

        /** Forgets every store, as a new process would; for tests. */
        internal fun reset() = synchronized(stores) {
            stores.values.forEach { it.unlockForSending() }
            stores.clear()
        }

        /** A store of [directory] as another process has it; for tests. */
        internal fun otherProcess(directory: File, shopId: String?): QueueStore = QueueStore(directory, shopId)
    }
}
