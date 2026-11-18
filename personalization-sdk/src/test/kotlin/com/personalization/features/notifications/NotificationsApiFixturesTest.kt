package com.personalization.features.notifications

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.PushProvider
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkRegistry
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.ContractFixtures
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.argInt
import com.personalization.core.internal.argString
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * `sdk.notifications` against the contract's fixtures (`contract/0/notifications.json`) on a bridge's
 * instance and a local server: the request each call makes, the calls refused before one, and how
 * `setPushToken` reads each answer. `getHistory`, a server method that asks rather than queues, runs over
 * [NamespaceFixtures]: its request with the shop's secret, `shop_id` and none of the device's fields.
 *
 * A case of one platform says so with `platform` (`android`, `ios`): the cases of iOS are skipped, and so is
 * one whose provider Android's [PushProvider] cannot name (`apns`, which only a major release may add).
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationsApiFixturesTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val requests = LinkedBlockingQueue<Pair<String, JSONObject>>()
    private val seen = CopyOnWriteArrayList<String>()
    private val defaultSources = PushTokenManager.sourceFactory

    /** The answer to `mobile_push_tokens`. */
    @Volatile
    private var tokenAnswer: MockResponse = MockResponse().setBody("""{"status":"success"}""")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        PushHub.reset()
        PushTokenManager.sourceFactory = { emptyList() }
        context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        network(connected = true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath.removePrefix("/")
                val text = request.body.readUtf8()
                val body = if (request.method == "POST" && text.isNotEmpty()) JSONObject(text) else JSONObject()
                requests += path to body
                seen += path
                return when (path) {
                    "init" -> MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                    "mobile_push_tokens" -> tokenAnswer
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        PushTokenManager.sourceFactory = defaultSources
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        PushHub.reset()
    }

    private fun network(connected: Boolean) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (connected) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
    }

    /** A bridge's instance, ready; its listeners end on the thread that ends the call. */
    private fun bridgeSdk(): SDK {
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { Dispatchers.Unconfined }) }
        sdk.initializeWith(
            context,
            Rees46Config(shopId = SHOP, apiDomain = server.url("/").toString().trimEnd('/'), autoSendPushToken = false, logLevel = LogLevel.DEBUG),
            coreV2 = true,
        )
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private fun forAndroid(case: JSONObject): Boolean = case.optString("platform").let { it.isEmpty() || it == "android" }

    /** A case of the push events or the token, which this test's instance runs; not one of [HISTORY]. */
    private fun ofEventsOrToken(case: JSONObject): Boolean = forAndroid(case) && case.getJSONObject("call").getString("method") != HISTORY

    /** Android's provider of a case; null for one it cannot name. */
    private fun provider(name: String): PushProvider? = PushProvider.fromId(name)

    /** Runs the case's call and returns how it ended; null when Android cannot make it. */
    private fun call(sdk: SDK, case: JSONObject): SdkResult<Unit>? {
        val call = case.getJSONObject("call")
        assertEquals("notifications", call.getString("namespace"))
        val args = call.getJSONObject("args")
        return when (val method = call.getString("method")) {
            "setPushToken" -> {
                val provider = provider(args.getString("provider")) ?: return null
                try {
                    runBlocking { withTimeout(10_000) { sdk.notifications.setPushToken(args.getString("token"), provider) } }
                    SdkResult.Success(Unit)
                } catch (e: SdkException) {
                    SdkResult.Failure(e.error)
                }
            }
            else -> {
                val ends = LinkedBlockingQueue<SdkResult<Unit>>()
                val listener = CompletionListener<Unit> { ends += it }
                val code = args.getString("code")
                val type = args.getString("type")
                when (method) {
                    "trackDelivered" -> sdk.notifications.trackDelivered(code, type, listener)
                    "trackOpened" -> sdk.notifications.trackOpened(code, type, listener)
                    "trackClicked" -> sdk.notifications.trackClicked(code, type, listener)
                    "trackClosed" -> sdk.notifications.trackClosed(code, type, listener)
                    else -> throw AssertionError("unknown method $method")
                }
                ends.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("$method did not end")
            }
        }
    }

    /** The path a case's call sends to. */
    private fun pathOf(case: JSONObject): String = when (val method = case.getJSONObject("call").getString("method")) {
        "setPushToken" -> "mobile_push_tokens"
        else -> "track/" + method.removePrefix("track").lowercase()
    }

    /** The next request to [path], skipping others; null when none came in time. */
    private fun next(path: String): JSONObject? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return null
            val request = requests.poll(left, TimeUnit.NANOSECONDS) ?: return null
            if (request.first == path) return request.second
        }
    }

    @Test
    fun `each call makes the contract's request`() {
        val sdk = bridgeSdk()
        val cases = ContractFixtures.requests("notifications").filter(::ofEventsOrToken)
        assertTrue(cases.size >= 6)
        cases.forEach { case ->
            val name = case.getString("name")
            val expected = case.getJSONObject("request")
            val outcome = call(sdk, case)
            assertEquals(name, SdkResult.Success(Unit), outcome)
            // The events wait in the queue: it goes now.
            runBlocking { withTimeout(5_000) { sdk.flush() } }

            assertEquals(name, "POST", expected.getString("method"))
            val body = next(expected.getString("path")) ?: throw AssertionError("$name: no request to ${expected.getString("path")}")
            val fields = expected.getJSONObject("body")
            fields.keys().forEach { key -> assertEquals("$name: $key", fields.get(key), body.opt(key)) }
            // The core adds the common fields; the core's own tests check their values.
            assertEquals(name, SHOP, body.optString("shop_id"))
            assertEquals(name, "D1", body.optString("did"))
        }
    }

    @Test
    fun `a call refused by the contract sends nothing and ends in its error`() {
        val sdk = bridgeSdk()
        val cases = ContractFixtures.cases("notifications").filter { it.has("error") && ofEventsOrToken(it) }
        assertTrue(cases.size >= 3)
        var made = 0
        cases.forEach { case ->
            val name = case.getString("name")
            seen.clear()
            val outcome = call(sdk, case)
            if (outcome == null) {
                // A provider of the other platform: Android's type has no such value, so the call cannot be made.
                assertTrue(name, PushProvider.entries.none { it.id == case.getJSONObject("call").getJSONObject("args").getString("provider") })
                return@forEach
            }
            made++
            val expected = ContractFixtures.errorOf(case.getJSONObject("error"))

            assertTrue("$name: $outcome", outcome is SdkResult.Failure && outcome.error.javaClass == expected.javaClass)
            runBlocking { withTimeout(5_000) { sdk.flush() } }
            assertFalse(name, pathOf(case) in seen)
        }
        assertTrue(made >= 3)
    }

    @Test
    fun `setPushToken reads every answer of the contract into success or its error`() {
        val sdk = bridgeSdk()
        val cases = ContractFixtures.answers("notifications").filter(::ofEventsOrToken)
        assertTrue(cases.size >= 6)
        cases.forEach { case ->
            val name = case.getString("name")
            val response = ContractFixtures.mockResponse(case)
            // No answer: the request finds no network.
            network(connected = response != null)
            response?.let { tokenAnswer = it }
            val expect = case.getJSONObject("expect")

            val outcome = call(sdk, case)

            if (expect.has("result")) {
                assertTrue(name, expect.isNull("result"))
                assertEquals(name, SdkResult.Success(Unit), outcome)
            } else {
                assertEquals(name, SdkResult.Failure(ContractFixtures.errorOf(expect.getJSONObject("error"))), outcome)
            }
        }
        network(connected = true)
    }

    /** Runs [block] over a new core of its own on a mock server, with `notifications` over it. */
    private fun history(block: (NamespaceFixtures, NamespaceFixtures.Invoke) -> Unit) {
        val fixtures = NamespaceFixtures("history-shop")
        fixtures.start(context)
        try {
            val notifications = NotificationsApiImpl(SDK(), fixtures.calls)
            block(fixtures, NamespaceFixtures.Invoke { method, args ->
                assertEquals(HISTORY, method)
                notifications.getHistory(
                    shopSecret = args.getString("shopSecret"),
                    type = args.getString("type"),
                    phone = args.argString("phone"),
                    email = args.argString("email"),
                    externalId = args.argString("externalId"),
                    loyaltyId = args.argString("loyaltyId"),
                    channel = args.argString("channel"),
                    limit = args.argInt("limit"),
                    page = args.argInt("page"),
                    dateFrom = args.argString("dateFrom"),
                    did = args.argString("did"),
                )
            })
        } finally {
            fixtures.stop()
        }
    }

    @Test
    fun `getHistory makes the contract's request with the secret and shop_id alone, or fails with invalidArgument and sends nothing`() =
        history { fixtures, invoke ->
            val history = setOf(HISTORY)
            assertEquals(2, fixtures.checkRequests("notifications", invoke, methods = history) {
                MockResponse().setBody("""{"status":"success","payload":{"messages":[]}}""")
            })
            assertEquals(4, fixtures.checkErrors("notifications", invoke, methods = history))
        }

    /** The answer's model is the released [com.personalization.api.responses.notifications.GetAllNotificationsResponse]: the case of iOS names its own. */
    @Test
    fun `getHistory reads each answer of the contract into its result or its error`() = history { fixtures, invoke ->
        assertEquals(3, fixtures.checkAnswers("notifications", invoke, methods = setOf(HISTORY), listed = setOf(HISTORY)))
    }

    private companion object {
        const val SHOP = "notifications-fixtures-shop"
        const val HISTORY = "getHistory"
    }
}
