package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
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
 * A disk that refuses (`core/resilience.md`): the shop's saved `/init` answer and the queue's files fail
 * as `storage_failed` with the operation, never as an exception at the caller, and the data stays as it
 * was; and the migration of the 2.4x store copies every kind of value it holds, each with its type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StorageFailuresTest {

    private lateinit var context: Context
    private val layout = StorageLayout(SHOP, null)
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** Directories the test made read-only: given back their rights after it. */
    private val locked = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(layout.partitionName, PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        QueueStore.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        locked.forEach { it.setWritable(true) }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        QueueStore.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun readOnly(directory: File) {
        assertTrue(directory.setWritable(false))
        locked += directory
    }

    private fun failures(op: String) = logged.filter { it.code == LogCodes.STORAGE_FAILED && it.context["op"] == op }

    // The saved answer of /init.

    @Test
    fun `a saved answer the disk cannot read is none, and its file is left for a later read`() {
        val storage = ShopStorage(context, layout)
        val answer = File(storage.directory, ShopStorage.INIT_ANSWER_FILE)
        // Not a file of an answer cut short, which goes, but one the disk cannot read.
        assertTrue(answer.mkdirs())

        assertNull(storage.initAnswer())
        assertEquals(1, failures(ShopStorage.OP_READ_INIT_ANSWER).size)
        assertNull(storage.savedDid())
        assertTrue("left as it was", answer.exists())
    }

    @Test
    fun `an answer the disk does not take is not saved, and the one before stays`() {
        val storage = ShopStorage(context, layout)
        assertTrue(storage.saveInitAnswer(JSONObject().put("did", "D1")))
        // Neither the file nor its directory takes a write.
        assertTrue(File(storage.directory, ShopStorage.INIT_ANSWER_FILE).setWritable(false))
        readOnly(storage.directory)

        assertFalse(storage.saveInitAnswer(JSONObject().put("did", "D2")))

        assertEquals(1, failures(ShopStorage.OP_WRITE_INIT_ANSWER).size)
        assertEquals("D1", storage.savedDid())
    }

    @Test
    fun `a shop whose directory cannot be made saves no answer`() {
        val storage = ShopStorage(context, layout)
        // Something else took the directory's name.
        assertTrue(storage.directory.parentFile?.mkdirs() == true)
        assertTrue(storage.directory.createNewFile())

        assertFalse(storage.saveInitAnswer(JSONObject().put("did", "D1")))

        assertEquals(1, failures(ShopStorage.OP_WRITE_INIT_ANSWER).size)
        assertNull(storage.initAnswer())
    }

    // The queue's files.

    private fun queueDirectory() = ShopStorage(context, layout).queuePath.also { it.mkdirs() }

    /** An event's file as the queue writes it. */
    private fun writeEvent(directory: File, id: String) {
        val event = QueuedEvent(id, "push", JSONObject().put("event", "view"), createdAt = 1_000L)
        File(directory, "$id.json").writeText(event.toJson().toString())
    }

    /** A queue of the shop, for the store's calls that name one; it is never started. */
    private fun idleQueue(store: QueueStore): EventQueue {
        val settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) })
        val storage = ShopStorage(context, layout)
        val state = InstanceState(shopId = { SHOP })
        val init = CoreInit(
            setup = InitSetup(SHOP),
            send = { HttpResult.Success(JSONObject()) },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = FakeConnectivity(),
            lifecycle = FakeAppLifecycle(),
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
        )
        return EventQueue(SHOP, store, init, state, send = { _, _ -> HttpResult.Success(JSONObject()) }, connectivity = FakeConnectivity(), lifecycle = FakeAppLifecycle())
    }

    @Test
    fun `a cleared event the disk keeps is skipped, never sent again, and removed once the disk lets it go`() {
        val directory = queueDirectory()
        writeEvent(directory, "0000000000000001-1-00000001")
        writeEvent(directory, "0000000000000002-1-00000002")
        val store = QueueStore.of(directory, SHOP)
        readOnly(directory)

        val (count, _, then) = store.clear { "wiped" }

        assertEquals(2, count)
        assertEquals("what follows the clear still runs", "wiped", then)
        assertEquals(2, failures(QueueStore.OP_DELETE).size)
        assertTrue(store.head(mutableListOf()) is QueueStore.Head.Empty)
        assertEquals("still on the disk", 2, directory.list()?.count { it.endsWith(".json") })

        directory.setWritable(true)
        assertTrue(store.head(mutableListOf()) is QueueStore.Head.Empty)
        assertEquals(0, directory.list()?.count { it.endsWith(".json") })
    }

    @Test
    fun `an event that overflows the queue goes even when the disk cannot read it`() {
        val directory = queueDirectory()
        val store = QueueStore.of(directory, SHOP)
        val queue = idleQueue(store)
        val dropped = mutableListOf<Pair<QueuedEvent?, QueueStore.Waiting?>>()
        val first = store.add(queue, "push", JSONObject().put("n", 1), 1_000L, null, cap = 2, dropped = dropped) ?: throw AssertionError("not written")
        store.add(queue, "push", JSONObject().put("n", 2), 2_000L, null, cap = 2, dropped = dropped) ?: throw AssertionError("not written")
        val oldest = File(directory, first.id + ".json")
        assertTrue(oldest.setReadable(false))

        val third = store.add(queue, "push", JSONObject().put("n", 3), 3_000L, null, cap = 2, dropped = dropped)

        assertTrue(third != null)
        assertEquals("dropped without what it was", listOf<QueuedEvent?>(null), dropped.map { it.first })
        assertFalse(oldest.exists())
        assertEquals(1, failures(QueueStore.OP_READ).size)
        assertEquals(2, directory.list()?.count { it.endsWith(".json") })
    }

    @Test
    fun `a queue whose lock file cannot be opened does not send, and sends once it can`() {
        val directory = queueDirectory()
        val lock = File(directory, QueueStore.LOCK_FILE).also { assertTrue(it.mkdir()) }
        val store = QueueStore.of(directory, SHOP)

        assertFalse(store.lockForSending())
        assertEquals(1, failures(QueueStore.OP_LOCK).size)

        assertTrue(lock.delete())
        assertTrue(store.lockForSending())
        store.unlockForSending()
    }

    // The migration of the 2.4x store.

    @Test
    fun `the 2_4x store's numbers, flags and sets reach the partition with their own types`() {
        context.getSharedPreferences(PreferencesPartition.LEGACY_KEY, Context.MODE_PRIVATE).edit()
            .putString("shop_id", SHOP)
            .putString("did", "did-24x")
            .putInt("int_value", 7)
            .putLong("sid_last_act", 123L)
            .putFloat("float_value", 1.5f)
            .putBoolean("bool_value", true)
            .putStringSet("set_value", setOf("a", "b"))
            .commit()

        StorageMigrations.run(context, layout)

        val partition = context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE)
        assertEquals("did-24x", partition.getString("did", null))
        assertEquals(7, partition.getInt("int_value", 0))
        assertEquals(123L, partition.getLong("sid_last_act", 0L))
        assertEquals(1.5f, partition.getFloat("float_value", 0f), 0f)
        assertTrue(partition.getBoolean("bool_value", false))
        assertEquals(setOf("a", "b"), partition.getStringSet("set_value", null))
    }

    private companion object {
        const val SHOP = "storage-failures-shop"
    }
}
