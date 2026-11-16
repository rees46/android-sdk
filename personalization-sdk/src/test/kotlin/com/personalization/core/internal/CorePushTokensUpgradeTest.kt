package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The push token after an upgrade (`core/storage.md`, migrations): a partition that holds only the keys
 * of releases before per-provider ones (`token`, `hms_token`, `last_push_token_date`,
 * `last_hms_push_token_date`) keeps its token and its week on both cores, so an upgrade does not send a
 * token the server got days ago. And an answer of the server that is a JSON array counts as sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorePushTokensUpgradeTest {

    private lateinit var context: Context
    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = testCoreDispatchers(scheduler)
    private val clock = FakeClock()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val sent = mutableListOf<JSONObject>()
    private val answers = mutableListOf<OnApiCallbackListener>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(DeviceStorage.NAME, PARTITION).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        SdkLogs.reset()
        SdkLogs.handlerExecutor = java.util.concurrent.Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun shop() = context.getSharedPreferences(PARTITION, Context.MODE_PRIVATE)

    private fun tokens() = CorePushTokens(
        shopId = SHOP,
        device = DeviceStorage(context),
        shop = shop(),
        send = { params, listener ->
            sent += params
            answers += listener
        },
        ready = { null },
        hasDid = { true },
        autoSend = true,
        dispatchers = dispatchers,
        clock = clock,
    )

    private fun run() = scheduler.runCurrent()

    private class Heard : OnApiCallbackListener() {
        val calls = CopyOnWriteArrayList<String>()

        override fun onSuccess(response: JSONObject?) {
            calls += "success:$response"
        }

        override fun onSuccess(response: JSONArray) {
            calls += "success:$response"
        }

        override fun onError(code: Int, msg: String?) {
            calls += "error:$code"
        }
    }

    @Test
    fun `on the new core a token sent under the old keys keeps its week, and goes again once the week is over`() {
        shop().edit()
            .putString("token", "old-fcm").putLong("last_push_token_date", clock.now - DAY_MS)
            .putString("hms_token", "old-hms").putLong("last_hms_push_token_date", clock.now - DAY_MS)
            .commit()
        val tokens = tokens()
        assertEquals("the token this shop sent last", "old-fcm", tokens.token(PushProvider.FCM))
        assertEquals("old-hms", tokens.token(PushProvider.HMS))

        tokens.received("old-fcm", PushProvider.FCM)
        tokens.received("old-hms", PushProvider.HMS)
        run()
        assertTrue("sent six days ago: not again", sent.isEmpty())

        // A week after the old send, not a week after the upgrade.
        clock.advance(CorePushTokens.WEEK_MS - DAY_MS)
        tokens.received("old-fcm", PushProvider.FCM)
        tokens.received("old-hms", PushProvider.HMS)
        run()

        assertEquals(listOf("old-fcm" to "firebase", "old-hms" to "huawei"), sent.map { it.getString("token") to it.getString("push_provider") })
    }

    @Test
    fun `on the new core a token other than the one under the old keys goes at once`() {
        shop().edit().putString("token", "old-fcm").putLong("last_push_token_date", clock.now - DAY_MS).commit()

        tokens().received("new-fcm", PushProvider.FCM)
        run()

        assertEquals(listOf("new-fcm"), sent.map { it.getString("token") })
    }

    @Test
    fun `the legacy core reads the token and its date under the old keys, and the new keys win once written`() {
        shop().edit()
            .putString("token", "old-fcm").putLong("last_push_token_date", 111L)
            .putString("hms_token", "old-hms").putLong("last_hms_push_token_date", 222L)
            .commit()
        val source = PreferencesDataSourceImpl().apply { initialize(context, PARTITION, null, SHOP) }

        assertEquals("old-fcm", source.getPushToken("fcm"))
        assertEquals(111L, source.getLastPushTokenDate("fcm"))
        assertEquals("old-hms", source.getPushToken("hms"))
        assertEquals(222L, source.getLastPushTokenDate("hms"))

        source.savePushToken("fcm", "new-fcm")
        source.saveLastPushTokenDate("fcm", 333L)
        assertEquals("new-fcm", source.getPushToken("fcm"))
        assertEquals(333L, source.getLastPushTokenDate("fcm"))
        assertEquals("the device's too", "new-fcm", DeviceStorage(context).pushToken("fcm"))
    }

    @Test
    fun `an answer that is a JSON array is a success, and the week counts from it`() {
        val tokens = tokens()
        val first = Heard()
        tokens.set("t", PushProvider.FCM, first)
        run()
        answers.removeAt(0).onSuccess(JSONArray().put("ok"))
        run()
        assertEquals(listOf("success:[\"ok\"]"), first.calls)

        val again = Heard()
        tokens.set("t", PushProvider.FCM, again)
        run()

        assertEquals("sent this week: no request", 1, sent.size)
        assertEquals(listOf("success:{}"), again.calls)
    }

    private companion object {
        const val SHOP = "upgrade-token-shop"
        val PARTITION = StorageLayout(SHOP, null).partitionName
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
