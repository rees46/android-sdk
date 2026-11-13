package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The push token on the new core (`core/push.md`): one per device, sent by each shop on its own week, a
 * new one at once, only once the instance is ready and has a did, the week taken before the request and
 * given back when it fails, and `push_provider` for FCM and HMS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorePushTokensTest {

    private lateinit var context: Context
    private val scheduler = TestCoroutineScheduler()
    private val dispatchers = testCoreDispatchers(scheduler)
    private val clock = FakeClock()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** What went, by shop, and the listeners waiting for an answer the test gives. */
    private val sent = mutableListOf<Pair<String, JSONObject>>()
    private val answers = mutableListOf<OnApiCallbackListener>()

    /** Whether the instance is ready; until it is, [ready] waits. */
    private var readiness = CompletableDeferred<SdkError?>()
    private var did = "D1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(DeviceStorage.NAME, partition(SHOP), partition(OTHER)).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.register(OTHER, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        readiness = CompletableDeferred<SdkError?>().apply { complete(null) }
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun partition(shop: String) = StorageLayout(shop, null).partitionName

    private fun tokens(shop: String = SHOP, autoSend: Boolean = true, sends: Boolean = true) = CorePushTokens(
        shopId = shop,
        device = DeviceStorage(context),
        shop = context.getSharedPreferences(partition(shop), Context.MODE_PRIVATE),
        send = { params, listener ->
            sent += shop to params
            answers += listener
        },
        ready = { readiness.await() },
        hasDid = { did.isNotEmpty() },
        autoSend = autoSend,
        sends = sends,
        dispatchers = dispatchers,
        clock = clock,
    )

    private fun run() = scheduler.runCurrent()

    private fun ok() {
        answers.removeAt(0).onSuccess(JSONObject().put("status", "success"))
        run()
    }

    private fun fail(code: Int) {
        answers.removeAt(0).onError(code, null)
        run()
    }

    private class Heard : OnApiCallbackListener() {
        val calls = CopyOnWriteArrayList<String>()
        override fun onSuccess(response: JSONObject?) {
            calls += "success"
        }

        override fun onError(code: Int, msg: String?) {
            calls += "error:$code"
        }
    }

    private fun reasons() = logged.filter { it.code == LogCodes.PUSH_TOKEN_SKIPPED }.map { it.context["reason"] }

    @Test
    fun `a new token goes at once with its provider, the same one at most once a week`() {
        val tokens = tokens()
        tokens.received("t1", PushProvider.FCM)
        run()
        ok()
        assertEquals(JSONObject().put("platform", "android").put("token", "t1").put("push_provider", "firebase").toString(), sent.single().second.toString())

        tokens.received("t1", PushProvider.FCM)
        run()
        assertEquals("the same token this week", 1, sent.size)
        assertEquals(listOf<Any?>(CorePushTokens.REASON_SENT_THIS_WEEK), reasons())

        tokens.received("t2", PushProvider.FCM)
        run()
        ok()
        assertEquals("a new token at once", 2, sent.size)

        clock.advance(CorePushTokens.WEEK_MS)
        tokens.sendStored()
        run()
        ok()
        assertEquals("a week later", listOf("t1", "t2", "t2"), sent.map { it.second.getString("token") })

        tokens.received("h1", PushProvider.HMS)
        run()
        assertEquals("huawei", sent.last().second.getString("push_provider"))
        assertTrue(logged.any { it.code == LogCodes.PUSH_TOKEN_SENT })
    }

    @Test
    fun `the token is the device's and each shop sends it on its own week`() {
        val shop = tokens(SHOP)
        val other = tokens(OTHER)
        shop.received("t1", PushProvider.FCM)
        run()
        ok()

        assertEquals("t1", DeviceStorage(context).pushToken("fcm"))
        assertEquals("t1", other.token(PushProvider.FCM))
        other.sendStored()
        run()
        ok()
        shop.sendStored()
        other.sendStored()
        run()

        assertEquals(listOf(SHOP, OTHER), sent.map { it.first })
    }

    @Test
    fun `nothing goes before the instance is ready, nor without a did, nor from a push-only instance`() {
        readiness = CompletableDeferred()
        val tokens = tokens()
        tokens.received("t1", PushProvider.FCM)
        run()
        assertTrue("waits for ready", sent.isEmpty())
        readiness.complete(null)
        run()
        assertEquals(1, sent.size)

        did = ""
        tokens(OTHER).sendStored()
        run()
        assertEquals(1, sent.size)

        did = "D1"
        tokens(OTHER, sends = false).sendStored()
        run()
        assertEquals(1, sent.size)
        assertEquals(listOf<Any?>(CorePushTokens.REASON_NO_DID, CorePushTokens.REASON_PUSH_ONLY), reasons())
    }

    @Test
    fun `two sends of one token at once make one request, and both hear its answer`() {
        val tokens = tokens()
        val first = Heard()
        val second = Heard()
        tokens.set("t1", PushProvider.FCM, first)
        tokens.set("t1", PushProvider.FCM, second)
        tokens.received("t1", PushProvider.FCM)
        run()

        assertEquals(1, sent.size)
        ok()
        assertEquals(listOf("success"), first.calls)
        assertEquals(listOf("success"), second.calls)
    }

    @Test
    fun `a failed send gives the week back, so the token goes again`() {
        val tokens = tokens()
        val heard = Heard()
        tokens.set("t1", PushProvider.FCM, heard)
        run()
        fail(500)
        assertEquals(listOf("error:500"), heard.calls)
        assertTrue(logged.any { it.code == LogCodes.PUSH_TOKEN_FAILED })

        tokens.sendStored()
        run()
        assertEquals(2, sent.size)
    }

    @Test
    fun `setPushToken waits for ready, says success without a request inside the week, and refuses an empty token or a refused shop`() {
        val tokens = tokens()
        val first = Heard()
        tokens.set("t1", PushProvider.HMS, first)
        run()
        ok()
        val again = Heard()
        tokens.set("t1", PushProvider.HMS, again)
        run()
        assertEquals(1, sent.size)
        assertEquals(listOf("success"), again.calls)

        val empty = Heard()
        tokens.set("", PushProvider.FCM, empty)
        assertEquals(listOf("error:-1"), empty.calls)

        readiness = CompletableDeferred<SdkError?>().apply { complete(SdkError.ShopBlocked) }
        val blocked = Heard()
        tokens.set("t9", PushProvider.FCM, blocked)
        run()
        assertEquals(listOf("error:402"), blocked.calls)
        assertEquals("kept for the device all the same", "t9", tokens.token(PushProvider.FCM))
    }

    @Test
    fun `a shop that does not send tokens by itself keeps none from FCM, and sends the one the app passes`() {
        val tokens = tokens(autoSend = false)
        tokens.received("from-fcm", PushProvider.FCM)
        run()
        assertNull(DeviceStorage(context).pushToken("fcm"))
        assertTrue(sent.isEmpty())

        tokens.set("from-app", PushProvider.FCM, null)
        run()
        assertEquals("from-app", sent.single().second.getString("token"))
    }

    private companion object {
        const val SHOP = "token-shop"
        const val OTHER = "token-other"
    }
}
