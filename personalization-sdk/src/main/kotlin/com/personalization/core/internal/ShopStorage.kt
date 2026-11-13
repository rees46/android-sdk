package com.personalization.core.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * A shop's partition: its `SharedPreferences` file ([StorageLayout.partitionName]) and its directory
 * under `noBackupFilesDir`. Beside what the legacy path keeps there ([StorageKeys]) it holds the
 * core's state: the last successful `/init` answer, the refused state and the queue's directory.
 *
 * The saved answer and the queue are files in `noBackupFilesDir`: Android's Auto Backup never copies
 * them to another device, and a large answer stays out of the preferences file that every request
 * rewrites. A disk failure is logged as `storage_failed` with the operation in `context.op`; nothing
 * here throws into the caller.
 */
internal class ShopStorage(context: Context, val layout: StorageLayout) {

    private val appContext: Context = context.applicationContext

    val preferences: SharedPreferences = appContext.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE)

    /** The shop's files. */
    val directory: File = File(File(appContext.noBackupFilesDir, ROOT_DIRECTORY), layout.partitionName)

    private val initAnswerFile = AtomicFile(File(directory, INIT_ANSWER_FILE))
    private val fileLock = Any()

    /** The last successful `/init` answer, or null when there is none or it cannot be read. */
    fun initAnswer(): JSONObject? = synchronized(fileLock) {
        if (!initAnswerFile.baseFile.exists()) return null
        try {
            JSONObject(String(initAnswerFile.readFully(), Charsets.UTF_8))
        } catch (e: IOException) {
            failed(OP_READ_INIT_ANSWER, e)
            null
        } catch (e: JSONException) {
            // A file cut short: the answer is gone, the next /init writes a new one.
            failed(OP_READ_INIT_ANSWER, e)
            initAnswerFile.delete()
            null
        }
    }

    /** Replaces the saved `/init` answer; false when the disk refused it. */
    fun saveInitAnswer(answer: JSONObject): Boolean = synchronized(fileLock) {
        if (!ensureDirectory(OP_WRITE_INIT_ANSWER)) return false
        val stream = try {
            initAnswerFile.startWrite()
        } catch (e: IOException) {
            failed(OP_WRITE_INIT_ANSWER, e)
            return false
        }
        try {
            stream.write(answer.toString().toByteArray(Charsets.UTF_8))
            initAnswerFile.finishWrite(stream)
            true
        } catch (e: IOException) {
            initAnswerFile.failWrite(stream)
            failed(OP_WRITE_INIT_ANSWER, e)
            false
        }
    }

    /** Whether the shop refused the SDK; it stays refused across launches until a `/init` succeeds. */
    var isRefused: Boolean
        get() = preferences.getBoolean(StorageKeys.REFUSED, false)
        set(value) {
            preferences.edit().putBoolean(StorageKeys.REFUSED, value).apply()
        }

    /** The directory of the shop's event queue, created if needed; null when the disk refused it. */
    fun queueDirectory(): File? {
        val queue = File(directory, QUEUE_DIRECTORY)
        if (queue.isDirectory) return queue
        return try {
            if (queue.mkdirs() || queue.isDirectory) queue else null.also { failed(OP_CREATE_QUEUE, null) }
        } catch (e: SecurityException) {
            failed(OP_CREATE_QUEUE, e)
            null
        }
    }

    /**
     * Forgets the customer: [StorageKeys.USER_CREDENTIALS] and the saved `/init` answer, and with
     * [includeQueue] the queue's directory. The A/B group, the push tokens, the advertising id, the
     * stories' state and the migration flags stay. Written with `commit()`: a crash right after must
     * not bring the customer back. False when part of it did not reach the disk.
     */
    fun deleteUserCredentials(includeQueue: Boolean): Boolean {
        var complete = attempt(OP_DELETE_CREDENTIALS) {
            val editor = preferences.edit()
            StorageKeys.USER_CREDENTIALS.forEach { editor.remove(it) }
            editor.commit()
        }
        complete = attempt(OP_DELETE_INIT_ANSWER) {
            synchronized(fileLock) {
                // Also drops a write left half-done, so it cannot come back as the answer.
                initAnswerFile.delete()
                !initAnswerFile.baseFile.exists()
            }
        } && complete
        if (includeQueue) {
            complete = attempt(OP_DELETE_QUEUE) {
                val queue = File(directory, QUEUE_DIRECTORY)
                !queue.exists() || queue.deleteRecursively()
            } && complete
        }
        return complete
    }

    /** Runs [block]; a false result or an exception is logged under [op]. */
    private inline fun attempt(op: String, block: () -> Boolean): Boolean {
        val done = try {
            block()
        } catch (e: Exception) {
            failed(op, e)
            return false
        }
        if (!done) failed(op, null)
        return done
    }

    private fun ensureDirectory(op: String): Boolean {
        if (directory.isDirectory || directory.mkdirs() || directory.isDirectory) return true
        failed(op, null)
        return false
    }

    private fun failed(op: String, throwable: Throwable?) {
        SdkLogs.log(LogLevel.WARNING, LogCodes.STORAGE_FAILED, "Storage operation failed: $op", layout.shopId, mapOf("op" to op), throwable)
    }

    internal companion object {
        /** Under `noBackupFilesDir`: one directory per partition. */
        const val ROOT_DIRECTORY = "personalization_sdk"
        const val INIT_ANSWER_FILE = "init.json"
        const val QUEUE_DIRECTORY = "queue"

        const val OP_READ_INIT_ANSWER = "read_init_answer"
        const val OP_WRITE_INIT_ANSWER = "write_init_answer"
        const val OP_CREATE_QUEUE = "create_queue_directory"
        const val OP_DELETE_CREDENTIALS = "delete_user_credentials"
        const val OP_DELETE_INIT_ANSWER = "delete_init_answer"
        const val OP_DELETE_QUEUE = "delete_queue"
    }
}
