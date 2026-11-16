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
        val errors = CopyOnWriteArrayList<SdkError?>()
        override fun onSuccess(response: JSONObject?) {
            calls += "success"
        }

        override fun onError(code: Int, msg: String?) {
            calls += "error:$code"
            errors += CurrentError.get()
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
        // The reasons are the contract's (core/logging.md).
        assertEquals(listOf<Any?>("sent_recently"), reasons())

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
        assertEquals(listOf<Any?>("no_device_id", "push_only"), reasons())
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
        assertTrue("off the caller's thread", empty.calls.isEmpty())
        run()
        assertEquals(listOf("error:-1"), empty.calls)

        readiness = CompletableDeferred<SdkError?>().apply { complete(SdkError.ShopBlocked) }
        val blocked = Heard()
        tokens.set("t9", PushProvider.FCM, blocked)
        run()
        assertEquals(listOf("error:402"), blocked.calls)
        assertEquals("kept for the device all the same", "t9", tokens.token(PushProvider.FCM))
    }

    @Test
    fun `a shop that does not send tokens by itself keeps none from FCM, says why, and sends the one the app passes`() {
        val tokens = tokens(autoSend = false)
        tokens.received("from-fcm", PushProvider.FCM)
        run()
        assertNull(DeviceStorage(context).pushToken("fcm"))
        assertTrue(sent.isEmpty())
        assertEquals(listOf<Any?>("auto_send_off"), reasons())

        tokens.set("from-app", PushProvider.FCM, null)
        run()
        assertEquals("from-app", sent.single().second.getString("token"))
    }

    @Test
    fun `setPushToken waiting for ready when the instance is released hears notInitialized, and one after release keeps and sends nothing`() {
        readiness = CompletableDeferred()
        val tokens = tokens()
        val waiting = Heard()
        tokens.set("t1", PushProvider.FCM, waiting)
        run()
        assertTrue(waiting.calls.isEmpty())

        tokens.release()
        run()
        assertEquals(listOf("error:-1"), waiting.calls)
        assertEquals(listOf<SdkError?>(SdkError.NotInitialized), waiting.errors)

        val after = Heard()
        tokens.set("t2", PushProvider.FCM, after)
        run()
        assertEquals(listOf("error:-1"), after.calls)
        assertEquals(listOf<SdkError?>(SdkError.NotInitialized), after.errors)
        assertEquals("the token of before release", "t1", DeviceStorage(context).pushToken("fcm"))
        readiness.complete(null)
        run()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a claim whose process died before the answer frees the week after a few minutes`() {
        tokens().received("t1", PushProvider.FCM)
        run()
        assertEquals(1, sent.size)
        // The process dies before the answer: the next one finds the claim.
        answers.clear()
        val next = tokens()
        next.sendStored()
        run()
        assertEquals("a claim moments old is another process's send", 1, sent.size)

        // The claim is the provider's: an HMS token goes all the same.
        next.received("h1", PushProvider.HMS)
        run()
        assertEquals(listOf("t1", "h1"), sent.map { it.second.getString("token") })
        answers.clear()
        sent.removeAt(1)

        clock.advance(CorePushTokens.CLAIM_TTL_MS)
        next.sendStored()
        run()
        assertEquals(2, sent.size)
        ok()
        next.sendStored()
        run()
        assertEquals("the send that went counts the week", 2, sent.size)
        assertEquals("sent_recently", reasons().last())
    }

    @Test
    fun `a token the app passes waits for the did and answers once it went, as no error`() {
        did = ""
        val tokens = tokens()
        val heard = Heard()
        tokens.set("t1", PushProvider.FCM, heard)
        run()
        assertTrue(heard.calls.isEmpty())
        assertTrue(sent.isEmpty())

        did = "D1"
        scheduler.advanceTimeBy(CorePushTokens.DID_RECHECK_MS)
        run()
        assertEquals(1, sent.size)
        ok()
        assertEquals(listOf("success"), heard.calls)
    }

    @Test
    fun `a send begun before the customer was deleted records no week after it, and the next did sends the token again`() {
        val tokens = tokens()
        val before = Heard()
        tokens.set("t1", PushProvider.FCM, before)
        run()
        assertEquals("on its way", 1, sent.size)

        // deleteUserCredentials(): the customer is forgotten first, then their data is wiped.
        tokens.customerForgotten()
        val shop = context.getSharedPreferences(partition(SHOP), Context.MODE_PRIVATE)
        shop.edit().apply { StorageKeys.USER_CREDENTIALS.forEach { remove(it) } }.commit()
        did = "D2"

        // The next did's send does not wait for the forgotten customer's.
        val after = Heard()
        tokens.set("t1", PushProvider.FCM, after)
        run()
        assertEquals(2, sent.size)

        // The forgotten customer's send lands now: its listener hears it, but no week is recorded.
        answers.removeAt(0).onSuccess(JSONObject())
        run()
        assertEquals(listOf("success"), before.calls)
        assertTrue(after.calls.isEmpty())
        assertEquals(0L, shop.getLong(StorageKeys.pushTokenDate("fcm"), 0L))
        assertEquals("the next customer's claim stays", "t1", shop.getString(StorageKeys.pushTokenClaim("fcm"), null))

        ok()
        assertEquals(listOf("success"), after.calls)
        assertEquals(clock.now, shop.getLong(StorageKeys.pushTokenDate("fcm"), 0L))
        tokens.sendStored()
        run()
        assertEquals("the next customer's week counts", 2, sent.size)
    }

    @Test
    fun `a send that lands after the deletion and before the next did records nothing`() {
        val tokens = tokens()
        tokens.received("t1", PushProvider.FCM)
        run()
        tokens.customerForgotten()
        val shop = context.getSharedPreferences(partition(SHOP), Context.MODE_PRIVATE)
        shop.edit().apply { StorageKeys.USER_CREDENTIALS.forEach { remove(it) } }.commit()
        did = ""

        ok()

        assertEquals(0L, shop.getLong(StorageKeys.pushTokenDate("fcm"), 0L))
        assertNull(shop.getString(StorageKeys.pushTokenClaim("fcm"), null))
        did = "D2"
        tokens.sendStored()
        run()
        assertEquals("the token goes for the next did", 2, sent.size)
    }

    private companion object {
        const val SHOP = "token-shop"
        const val OTHER = "token-other"
    }
}
