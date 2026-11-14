package com.personalization.core.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
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

    /**
     * Takes the `did` an app brings in `Rees46Config.deviceId`, once for the shop: on the first launch
     * that brings one, while the shop has no `did` of its own. A shop that has one keeps it and logs
     * `device_id_kept`. Either way the flag [StorageKeys.MIGRATED_CONFIG_DEVICE_ID] is set and later
     * launches leave the config's `did` alone, so it does not come back after `deleteUserCredentials()`
     * even when the app passes it on every launch. True when it was taken.
     */
    fun adoptConfiguredDid(deviceId: String?, settings: UserSettingsRepository): Boolean {
        val configured = deviceId?.trim().orEmpty()
        if (configured.isEmpty() || preferences.getBoolean(StorageKeys.MIGRATED_CONFIG_DEVICE_ID, false)) return false
        val own = settings.getDid()
        val taken = own.isEmpty()
        if (taken) {
            settings.updateDid(configured)
        } else if (own != configured) {
            SdkLogs.log(
                LogLevel.WARNING,
                LogCodes.DEVICE_ID_KEPT,
                "Rees46Config.deviceId is not taken: the shop keeps the did it has",
                layout.shopId,
            )
        }
        preferences.edit().putBoolean(StorageKeys.MIGRATED_CONFIG_DEVICE_ID, true).commit()
        return taken
    }

    /** The shop's `did` as its partition holds it; empty when there is none. */
    fun storedDid(): String = preferences.getString(StorageKeys.DID, null).orEmpty()

    /**
     * Whether [adoptConfiguredDid] would take [deviceId] now: one is brought, the shop has none, and no
     * launch considered one before.
     */
    fun takesConfiguredDid(deviceId: String?): Boolean =
        deviceId?.trim().orEmpty().isNotEmpty() &&
            !preferences.getBoolean(StorageKeys.MIGRATED_CONFIG_DEVICE_ID, false) &&
            storedDid().isEmpty()

    /** Whether the customer was wiped and no `/init` has succeeded since ([StorageKeys.CREDENTIALS_WIPED]). */
    var isWiped: Boolean
        get() = preferences.getBoolean(StorageKeys.CREDENTIALS_WIPED, false)
        set(value) {
            preferences.edit().putBoolean(StorageKeys.CREDENTIALS_WIPED, value).apply()
        }

    /** Where the shop's event queue lives; [QueueStore] creates it with its first event. */
    val queuePath: File get() = File(directory, QUEUE_DIRECTORY)

    /**
     * Forgets the customer: [StorageKeys.USER_CREDENTIALS] and the saved `/init` answer, and with
     * [includeQueue] the queue's directory. The A/B group, the push tokens, the advertising id, the
     * stories' state and the migration flags stay. Written with `commit()`: a crash right after must
     * not bring the customer back. The same commit sets [isWiped], so a saved answer whose file outlives
     * the wipe gives its `did` to nobody. False when part of it did not reach the disk.
     *
     * The stores a migration reads lose the customer too ([StorageKeys.SOURCE_CREDENTIALS], nothing
     * else of them): a partition restored without its migration flags, or lost, would otherwise read
     * the forgotten `did` back from them.
     */
    fun deleteUserCredentials(includeQueue: Boolean): Boolean {
        var complete = attempt(OP_DELETE_CREDENTIALS) {
            val editor = preferences.edit()
            StorageKeys.USER_CREDENTIALS.forEach { editor.remove(it) }
            editor.putBoolean(StorageKeys.CREDENTIALS_WIPED, true)
            editor.commit()
        }
        listOfNotNull(layout.namedFile, layout.legacyFile).forEach { name ->
            complete = attempt(OP_DELETE_SOURCE_CREDENTIALS) { forgetInSource(name) } && complete
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

    /** Removes the customer's identity from the store [name] of an older release, when it is this shop's. */
    private fun forgetInSource(name: String): Boolean {
        val source = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        // The store of releases before partitions may be another shop's: its customer is not this one.
        if (name == layout.legacyFile && !StorageMigrations.belongsTo(source, layout.shopId)) return true
        if (StorageKeys.SOURCE_CREDENTIALS.none(source::contains)) return true
        val editor = source.edit()
        StorageKeys.SOURCE_CREDENTIALS.forEach { editor.remove(it) }
        return editor.commit()
    }

    /**
     * Forgets the device for `needReInitialization`: the `did`, the session and the saved `/init`
     * answer, so the next `/init` gets a new `did`; [isWiped] as [deleteUserCredentials] sets it. False
     * when part of it did not reach the disk.
     */
    fun forgetDevice(): Boolean {
        val complete = attempt(OP_FORGET_DEVICE) {
            preferences.edit()
                .remove(StorageKeys.DID).remove(StorageKeys.SID).remove(StorageKeys.SEANCE).remove(StorageKeys.SID_LAST_ACT)
                .putBoolean(StorageKeys.CREDENTIALS_WIPED, true)
                .commit()
        }
        return attempt(OP_DELETE_INIT_ANSWER) {
            synchronized(fileLock) {
                initAnswerFile.delete()
                !initAnswerFile.baseFile.exists()
            }
        } && complete
    }

    /** Ends the session: its `sid` and the time of its last activity go, so `/init` asks for a new one. */
    fun forgetSession() {
        preferences.edit().remove(StorageKeys.SID).remove(StorageKeys.SID_LAST_ACT).apply()
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
        const val OP_DELETE_CREDENTIALS = "delete_user_credentials"
        const val OP_DELETE_INIT_ANSWER = "delete_init_answer"
        const val OP_DELETE_QUEUE = "delete_queue"
        const val OP_DELETE_SOURCE_CREDENTIALS = "delete_old_storage_credentials"
        const val OP_FORGET_DEVICE = "forget_device"
    }
}
