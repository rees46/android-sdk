package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.PushProvider
import com.personalization.SdkLogMessage
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** A shop's partition: the core's state in it, and what forgetting the customer takes away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShopStorageTest {

    private lateinit var context: Context
    private val layout = StorageLayout(SHOP, null)
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(layout.partitionName, DeviceStorage.NAME, PreferencesPartition.keyFor(SHOP) + "_stories")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = com.personalization.SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun storage() = ShopStorage(context, layout)

    @Test
    fun `the last init answer is kept in a file of the shop, outside the preferences and the backup`() {
        assertNull(storage().initAnswer())

        storage().saveInitAnswer(JSONObject("""{"did":"d1","seance":"s1","popup":{"id":1}}"""))
        storage().saveInitAnswer(JSONObject("""{"did":"d1","seance":"s2"}"""))

        assertEquals("s2", storage().initAnswer()?.getString("seance"))
        val file = File(context.noBackupFilesDir, "personalization_sdk/personalization_sdk_shop-1/init.json")
        assertTrue(file.isFile)
        assertFalse(storage().preferences.all.values.any { it.toString().contains("seance") })
    }

    @Test
    fun `an answer that cannot be read is gone and the failure is logged without the answer`() {
        storage().saveInitAnswer(JSONObject("""{"did":"secret-did"}"""))
        File(storage().directory, ShopStorage.INIT_ANSWER_FILE).writeText("""{"did":"secret-""")

        assertNull(storage().initAnswer())
        assertNull(storage().initAnswer())

        val failure = logged.single { it.code == LogCodes.STORAGE_FAILED }
        assertEquals(LogLevel.WARNING, failure.level)
        assertEquals(ShopStorage.OP_READ_INIT_ANSWER, failure.context["op"])
        assertFalse(logged.any { "secret" in it.message || "secret" in it.context.toString() })
    }

    @Test
    fun `the refused state survives a restart until it is cleared`() {
        assertFalse(storage().isRefused)

        storage().isRefused = true
        assertTrue(storage().isRefused)

        storage().isRefused = false
        assertFalse(storage().isRefused)
    }

    @Test
    fun `the queue has a directory of the shop`() {
        val queue = storage().queuePath

        assertEquals(File(context.noBackupFilesDir, "personalization_sdk/personalization_sdk_shop-1/queue"), queue)
        // Another shop, another directory.
        assertEquals(
            File(context.noBackupFilesDir, "personalization_sdk/host_personalization_sdk_shop-1/queue"),
            ShopStorage(context, StorageLayout(SHOP, "host")).queuePath
        )
    }

    @Test
    fun `forgetting the customer wipes the credentials and keeps what belongs to the device`() {
        seedShop()
        val storage = storage()
        storage.saveInitAnswer(JSONObject("""{"did":"did-1"}"""))
        storage.isRefused = true
        val queue = storage.queuePath.also { it.mkdirs() }
        File(queue, "event-1").writeText("{}")

        assertTrue(runBlocking { LegacyCore(mockk<UserSettingsRepository>(relaxed = true)) { "" }.deleteUserCredentials(storage) })

        val left = storage.preferences.all
        listOf(
            "did", "sid", "sid_last_act", "tracking_source_type", "tracking_source_code", "tracking_source_saved_at",
            "push_token_date_fcm", "push_token_date_hms", "last_push_token_date", "last_hms_push_token_date", "refused",
        ).forEach { assertFalse(it, left.containsKey(it)) }
        assertEquals(
            mapOf(
                "shop_id" to SHOP,
                "stream" to "android",
                "segment_ab_testing" to "B",
                "google_ad_id" to "ad-1",
                "push_token_fcm" to "fcm-1",
                "token" to "fcm-old",
                "push_token_hms" to "hms-1",
                StorageKeys.MIGRATED_LEGACY_FILE to true,
                // Written in the wipe's own commit: a saved answer that outlived it gives no did.
                StorageKeys.CREDENTIALS_WIPED to true,
            ),
            left
        )
        assertTrue(storage.isWiped)
        assertNull(storage.initAnswer())
        // The legacy core has no queue: its directory is not its to delete.
        assertTrue(File(queue, "event-1").isFile)
        // The stories' state and the device level are untouched.
        assertEquals(setOf("slide-1"), context.getSharedPreferences(PreferencesPartition.keyFor(SHOP) + "_stories", Context.MODE_PRIVATE).getStringSet("7", null))
        assertEquals("A", DeviceStorage(context).segment())
        assertEquals("fcm-device", DeviceStorage(context).pushToken("fcm"))
    }

    @Test
    fun `on the new core the queue goes with the customer`() {
        seedShop()
        val storage = storage()
        val queue = storage.queuePath.also { it.mkdirs() }
        File(queue, "event-1").writeText("{}")
        val core = CoreV2(CoreHttpClient({ "https://example.invalid/" }, mockk(relaxed = true), FakeConnectivity(), SHOP), SHOP)

        assertTrue(runBlocking { core.deleteUserCredentials(storage) })
        core.release()

        assertFalse(queue?.exists() == true)
        assertFalse(storage.preferences.contains("did"))
    }

    @Test
    fun `after the wipe the push token goes to the next customer`() {
        seedShop()
        val source = PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) }
        val repository = PreferencesRepositoryImpl(source)
        val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
        val tokens = PushTokenManager(network, GetPreferencesValueUseCase(repository), SavePreferencesValueUseCase(repository))
        tokens.initialize(context, autoSendPushToken = true)

        // Sent this week to the customer before: not sent again.
        tokens.onTokenReceived("fcm-1", PushProvider.FCM)
        verify(exactly = 0) { network.postAsync("mobile_push_tokens", any(), any()) }

        storage().deleteUserCredentials(includeQueue = false)
        tokens.onTokenReceived("fcm-1", PushProvider.FCM)

        verify(exactly = 1) { network.postAsync("mobile_push_tokens", any(), any()) }
    }

    /** A shop of a running install: its customer, the device's data and the stories' state. */
    private fun seedShop() {
        val now = System.currentTimeMillis()
        storage().preferences.edit()
            .putString("did", "did-1")
            .putString("sid", "sid-1")
            .putLong("sid_last_act", now)
            .putString("shop_id", SHOP)
            .putString("stream", "android")
            .putString("segment_ab_testing", "B")
            .putString("google_ad_id", "ad-1")
            .putString("push_token_fcm", "fcm-1")
            .putString("token", "fcm-old")
            .putString("push_token_hms", "hms-1")
            .putLong("push_token_date_fcm", now)
            .putLong("push_token_date_hms", now)
            .putLong("last_push_token_date", now)
            .putLong("last_hms_push_token_date", now)
            .putString("tracking_source_type", "bulk")
            .putString("tracking_source_code", "c1")
            .putLong("tracking_source_saved_at", now)
            .putBoolean(StorageKeys.MIGRATED_LEGACY_FILE, true)
            .commit()
        DeviceStorage(context).adoptSegment("A")
        DeviceStorage(context).savePushToken("fcm", "fcm-device")
        context.getSharedPreferences(PreferencesPartition.keyFor(SHOP) + "_stories", Context.MODE_PRIVATE)
            .edit().putStringSet("7", setOf("slide-1")).commit()
    }

    private companion object {
        const val SHOP = "shop-1"
    }
}
