package com.personalization.features

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.NetworkLogger
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.api.responses.orders.Order
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.HeldMain
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import com.personalization.features.cart.CartApiImpl
import com.personalization.features.notifications.NotificationsApiImpl
import com.personalization.features.profile.ProfileApiImpl
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/**
 * The three server methods of the namespaces, with the shop's secret the caller passes —
 * `profile.getOrders`, `cart.clear`, `notifications.getHistory` — as a bridge calls them: the request
 * (path, method, fields: `shop_id` and the secret, none of the device's), the answer read into the released
 * models, the errors, `notInitialized` on the legacy core, and the secret never in the log nor in what the
 * app's network logger gets. Platform-local: the shared fixtures do not have these yet.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecretMethodsNamespaceTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val requests = LinkedBlockingQueue<RecordedRequest>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val networkLogged = CopyOnWriteArrayList<String>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

    /** The answers by path; `/init` answers with a `did`. */
    private val answers = ConcurrentHashMap<String, MockResponse>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.handler = SdkLogHandler { logged += it }
        SDK.networkLogger = NetworkLogger { method, url, body, code, response, _, error ->
            networkLogged += listOf(method, url, body, code.toString(), response, error).joinToString(" ")
        }
        context.getSharedPreferences(StorageLayout(SHOP, null).partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        listOf(PreferencesPartition.LEGACY_KEY, DeviceStorage.NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        LegacyCore.threads = threads
        answers[ProfileApiImpl.ORDERS_PATH] = MockResponse().setBody(ORDERS)
        answers[CartApiImpl.CLEAR_PATH] = MockResponse().setBody("""{"status":"success"}""")
        answers[NotificationsApiImpl.HISTORY_PATH] = MockResponse().setBody(HISTORY)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath?.removePrefix("/") ?: return MockResponse().setResponseCode(400)
                if (path == "init") return MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                requests += request
                return answers[path] ?: MockResponse().setBody("{}")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        SDK.networkLogger = null
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun bridge(): SDK {
        val sdk = Rees46.initializeBridge(context, config())
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    private fun <T> result(block: suspend () -> T): SdkResult<T> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { block() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The requests the server got besides `/init`, once the calls so far had time to arrive. */
    private fun sent(): List<RecordedRequest> {
        threads.awaitIdle()
        Thread.sleep(200)
        return generateSequence { requests.poll() }.toList()
    }

    private fun RecordedRequest.query(): Map<String, String?> =
        requestUrl!!.queryParameterNames.associateWith { requestUrl!!.queryParameter(it) }

    /** The request carries the shop and its secret and none of the device's fields. */
    private fun assertNoDeviceFields(query: Map<String, String?>) {
        assertEquals(SHOP, query["shop_id"])
        assertEquals(SECRET, query["shop_secret"])
        listOf("sid", "seance", "segment", "stream").forEach { assertFalse("$it in $query", it in query) }
    }

    private fun assertSecretNowhere() {
        assertTrue(logged.isNotEmpty())
        logged.forEach { message ->
            assertFalse(message.toString(), message.message.contains(SECRET))
            assertFalse(message.toString(), message.context.values.any { it.toString().contains(SECRET) })
        }
        assertTrue(networkLogged.isNotEmpty())
        networkLogged.forEach { assertFalse(it, it.contains(SECRET)) }
    }

    // region profile.getOrders

    @Test
    fun `getOrders asks orders by_user with the secret, the given ids and no field of the device, and reads the orders`() {
        val sdk = bridge()

        val orders = result { sdk.profile.getOrders(SECRET, did = "D-given", email = "a@example.com", dateFrom = "2026-10-01") }

        val request = sent().single()
        assertEquals("GET", request.method)
        assertEquals("/orders/by_user", request.requestUrl!!.encodedPath)
        val query = request.query()
        assertNoDeviceFields(query)
        assertEquals("D-given", query["did"])
        assertEquals("a@example.com", query["email"])
        assertEquals("2026-10-01", query["date_from"])
        assertEquals(setOf("shop_id", "shop_secret", "did", "email", "date_from"), query.keys)

        val order = (orders as SdkResult.Success).value.single()
        assertEquals(ORDER, order.copy(items = emptyList()))
        val item = order.items.single()
        assertEquals(1, item.amount)
        assertEquals("69990.0", item.price)
        assertEquals("created", item.status)
        assertEquals("284730", item.barcode)
        assertEquals("284730", item.item?.id)
    }

    @Test
    fun `getOrders without a did of the caller sends none, not the device's`() {
        val sdk = bridge()

        result { sdk.profile.getOrders(SECRET, externalId = "x-1") }

        val query = sent().single().query()
        assertFalse("did" in query)
        assertEquals("x-1", query["external_id"])
    }

    @Test
    fun `getOrders refuses an empty secret, an empty id, no id and a wrong date, without a request`() {
        val sdk = bridge()

        listOf<Pair<String, suspend () -> Any>>(
            "shopSecret must not be empty" to { sdk.profile.getOrders(" ", did = "D1") },
            "email must not be empty" to { sdk.profile.getOrders(SECRET, email = "") },
            "one of did, email, phone, loyaltyId, externalId is needed" to { sdk.profile.getOrders(SECRET) },
            "dateFrom must be a date YYYY-MM-DD" to { sdk.profile.getOrders(SECRET, did = "D1", dateFrom = "2026-02-30") },
        ).forEach { (message, call) ->
            assertEquals(message, SdkResult.Failure(SdkError.InvalidArgument(message)), result { call() })
        }
        assertEquals(emptyList<RecordedRequest>(), sent())
        assertTrue(logged.any { it.code == LogCodes.INVALID_ARGUMENT && it.context["call"] == ProfileApiImpl.GET_ORDERS })
    }

    @Test
    fun `getOrders ends with the server's 403 for a wrong secret and 400 for a customer it does not find`() {
        val sdk = bridge()

        answers[ProfileApiImpl.ORDERS_PATH] = MockResponse().setResponseCode(403).setBody("""{"status":"error","message":"Shop secret is missing"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(403)), result { sdk.profile.getOrders(SECRET, did = "D1") })
        answers[ProfileApiImpl.ORDERS_PATH] = MockResponse().setResponseCode(400).setBody("""{"status":"error","message":"Client not found"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(400)), result { sdk.profile.getOrders(SECRET, did = "D1") })
        answers[ProfileApiImpl.ORDERS_PATH] = MockResponse().setBody("""{"status":"success"}""")
        assertEquals(SdkResult.Failure(SdkError.Decoding), result { sdk.profile.getOrders(SECRET, did = "D1") })
        assertSecretNowhere()
    }

    @Test
    fun `the form of getOrders with a listener ends once, on the main thread`() {
        val main = HeldMain()
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = true)
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        val ends = CopyOnWriteArrayList<Pair<SdkResult<List<Order>>, Boolean>>()

        sdk.profile.getOrders(SECRET, "D1", null, null, null, null, null, CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        while (ends.isEmpty() && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(1, ends.size)
        assertEquals(ORDER.id, (ends.single().first as SdkResult.Success).value.single().id)
        assertTrue(ends.single().second)
    }

    // endregion

    // region cart.clear

    @Test
    fun `cart clear asks products cart clear with GET, the secret and the given ids, and ends in success`() {
        val sdk = bridge()

        assertEquals(SdkResult.Success(Unit), result { sdk.cart.clear(SECRET, phone = "+70000000000", telegramId = "t-1") })

        val request = sent().single()
        assertEquals("GET", request.method)
        assertEquals("/products/cart/clear", request.requestUrl!!.encodedPath)
        val query = request.query()
        assertNoDeviceFields(query)
        assertFalse("did" in query)
        assertEquals(setOf("shop_id", "shop_secret", "phone", "telegram_id"), query.keys)
        assertEquals("+70000000000", query["phone"])
        assertEquals("t-1", query["telegram_id"])
    }

    @Test
    fun `cart clear refuses no id, and ends with the server's errors and with decoding for an answer that is no success`() {
        val sdk = bridge()

        val message = "one of email, phone, loyaltyId, externalId, telegramId is needed"
        assertEquals(SdkResult.Failure(SdkError.InvalidArgument(message)), result { sdk.cart.clear(SECRET) })
        assertEquals(SdkResult.Failure(SdkError.InvalidArgument("shopSecret must not be empty")), result { sdk.cart.clear("", externalId = "x-1") })
        assertEquals(emptyList<RecordedRequest>(), sent())

        answers[CartApiImpl.CLEAR_PATH] = MockResponse().setResponseCode(403).setBody("""{"status":"error","message":"Shop secret is missing"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(403)), result { sdk.cart.clear(SECRET, externalId = "x-1") })
        answers[CartApiImpl.CLEAR_PATH] = MockResponse().setResponseCode(400).setBody("""{"status":"error","message":"Client not found"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(400)), result { sdk.cart.clear(SECRET, externalId = "x-1") })
        answers[CartApiImpl.CLEAR_PATH] = MockResponse().setBody("""{"status":"error","message":"no"}""")
        assertEquals(SdkResult.Failure(SdkError.Decoding), result { sdk.cart.clear(SECRET, externalId = "x-1") })
        answers[CartApiImpl.CLEAR_PATH] = MockResponse().setBody("{}")
        assertEquals(SdkResult.Failure(SdkError.Decoding), result { sdk.cart.clear(SECRET, externalId = "x-1") })
        assertSecretNowhere()
    }

    // endregion

    // region notifications.getHistory

    @Test
    fun `getHistory asks notifications with the secret and the fields the root method sends, and reads the history`() {
        val sdk = bridge()

        val history = result {
            sdk.notifications.getHistory(
                SECRET, "bulk,chain", phone = "+70000000000", channel = "mobile_push,email", limit = 5, page = 2, dateFrom = "2026-10-01",
            )
        }

        val request = sent().single()
        assertEquals("GET", request.method)
        assertEquals("/notifications", request.requestUrl!!.encodedPath)
        val query = request.query()
        assertNoDeviceFields(query)
        assertFalse("did" in query)
        assertEquals(
            mapOf(
                "shop_id" to SHOP, "shop_secret" to SECRET, "type" to "bulk,chain", "phone" to "+70000000000",
                "channel" to "mobile_push,email", "date_from" to "2026-10-01", "page" to "2", "limit" to "5",
            ),
            query,
        )
        val message = (history as SdkResult.Success<GetAllNotificationsResponse>).value.payload.messages.single()
        assertEquals("success", history.value.status)
        assertEquals("bulk", message.type)
        assertEquals("mobile_push", message.channel)
        assertEquals(12, message.campaignId)
        assertEquals("Sale", message.subject)
        assertTrue(message.statistics.opened)
        assertFalse(message.statistics.clicked)
    }

    @Test
    fun `getHistory sends a did only when the caller gives one`() {
        val sdk = bridge()

        result { sdk.notifications.getHistory(SECRET, "bulk", channel = "email", dateFrom = "2026-10-01", did = "D-given") }

        assertEquals("D-given", sent().single().query()["did"])
    }

    @Test
    fun `getHistory refuses what the server cannot take, without a request, and ends with its 400 and 404`() {
        val sdk = bridge()

        listOf<Pair<String, suspend () -> Any>>(
            "shopSecret must not be empty" to { sdk.notifications.getHistory("", "bulk", phone = "+7", dateFrom = "2026-10-01") },
            "type must not be empty" to { sdk.notifications.getHistory(SECRET, " ", phone = "+7", dateFrom = "2026-10-01") },
            "one of phone, email, externalId, loyaltyId, did is needed" to { sdk.notifications.getHistory(SECRET, "bulk", dateFrom = "2026-10-01") },
            "dateFrom must be a date YYYY-MM-DD" to { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7") },
            "dateFrom must be a date YYYY-MM-DD" to { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "01.10.2026") },
            "limit must be at least 1" to { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", limit = 0, dateFrom = "2026-10-01") },
            "channel must not be empty" to { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", channel = "", dateFrom = "2026-10-01") },
        ).forEach { (message, call) ->
            assertEquals(message, SdkResult.Failure(SdkError.InvalidArgument(message)), result { call() })
        }
        assertEquals(emptyList<RecordedRequest>(), sent())

        answers[NotificationsApiImpl.HISTORY_PATH] = MockResponse().setResponseCode(400).setBody("""{"status":"error","message":"Incorrect shop credentials"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(400)), result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") })
        answers[NotificationsApiImpl.HISTORY_PATH] = MockResponse().setResponseCode(404).setBody("""{"status":"error","message":"User is not found"}""")
        assertEquals(SdkResult.Failure(SdkError.Http(404)), result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") })
        answers[NotificationsApiImpl.HISTORY_PATH] = MockResponse().setBody("""{"status":"success","payload":{}}""")
        assertEquals(SdkResult.Failure(SdkError.Decoding), result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") })
        assertSecretNowhere()
    }

    @Test
    fun `getHistory without a channel reads the server's empty list`() {
        val sdk = bridge()
        answers[NotificationsApiImpl.HISTORY_PATH] = MockResponse().setBody("""{"status":"success","payload":{"messages":[]}}""")

        val history = result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") }

        assertEquals(emptyList<Any>(), (history as SdkResult.Success).value.payload.messages)
        assertFalse("channel" in sent().single().query())
    }

    // endregion

    @Test
    fun `the network logger gets the secret hidden and the log never has it`() {
        val sdk = bridge()

        result { sdk.profile.getOrders(SECRET, did = "D1") }
        result { sdk.cart.clear(SECRET, externalId = "x-1") }
        result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") }
        sent()

        assertSecretNowhere()
        val urls = networkLogged.filter { "init" !in it }
        assertEquals(3, urls.size)
        assertTrue(urls.toString(), urls.all { "shop_secret=%3Chidden%3E" in it })
    }

    @Test
    fun `on the legacy core every server method fails with notInitialized, sends nothing and logs core_flag_mismatch`() {
        val sdk = Rees46.initialize(context, config(), false)
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        sent()

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), result { sdk.profile.getOrders(SECRET, did = "D1") })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), result { sdk.cart.clear(SECRET, externalId = "x-1") })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), result { sdk.notifications.getHistory(SECRET, "bulk", phone = "+7", dateFrom = "2026-10-01") })

        val paths = sent().map { it.requestUrl!!.encodedPath }
        assertTrue(paths.toString(), paths.none { it in setOf("/orders/by_user", "/products/cart/clear", "/notifications") })
        listOf(ProfileApiImpl.GET_ORDERS, CartApiImpl.CLEAR, NotificationsApiImpl.GET_HISTORY).forEach { call ->
            assertEquals(call, 1, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == call })
        }
        assertNull(networkLogged.firstOrNull { SECRET in it })
    }

    private companion object {
        const val SHOP = "secret-methods-shop"
        const val SECRET = "s3cr3t-0f-the-shop"

        val ORDER = Order(
            internalId = 7_645_628L, id = "sdk-test-1", date = "2026-11-17T19:48:47.000Z", value = "69990.0",
            internalStatus = "new", stream = "android", taxFree = false, deliveryType = "sdk_test", paymentType = "sdk_test",
        )

        /** A `by_user` answer as the server sent it to the demo shop's test device (the item's product cut short). */
        const val ORDERS = """{"status":"success","data":{"orders":[{"_id":7645628,"id":"sdk-test-1",
            "date":"2026-11-17T19:48:47.000Z","value":"69990.0","cash_value":null,"bonuses_value":null,"delivery_value":null,
            "location_id":null,"promocode":null,"offline":null,"marketplace":null,"delivery_date":null,"internal_status":"new",
            "stream":"android","channel":null,"tax_free":false,"delivery_type":"sdk_test","delivery_address":null,
            "order_status":null,"payment_type":"sdk_test","bank_issuer":null,"bank_pos_processor":null,
            "bank_loyalty_program":null,"bank_total_installment":null,"payment_card_provider":null,"gift_package":null,
            "payment_structure":null,"value_raw":null,"custom_properties":{},"history":[],
            "items":[{"amount":1,"price":"69990.0","status":"created","original_price":null,"discount_product":null,
            "discount_coupon":null,"discount_bonuses":null,"delivery_company":null,"barcode":"284730","line_id":null,
            "cancel_reason":null,"item":{"id":"284730","name":"Наушники Apple AirPods 4 MXP63ZE/A","price":69990,
            "currency":"₽","brand":"Apple","category_ids":["naushniki"],"categories":[{"id":"naushniki","name":"Наушники"}]}}]}]}}"""

        /** The shape of a `notifications` answer. */
        const val HISTORY = """{"status":"success","payload":{"messages":[{"type":"bulk","channel":"mobile_push",
            "campaign_id":12,"template_id":null,"code":"c-1","date":"2026-11-10","sent_at":"2026-11-10T10:00:00.000+03:00",
            "client_id":77,"subject":"Sale","body":"-20%","url":"https://example.com/sale","icon":null,"picture":null,
            "error":null,"error_message":null,"statistics":{"opened":true,"clicked":false,"hard_bounced":false,
            "soft_bounced":false,"complained":false,"unsubscribed":false,"purchased":false,"delivered":true}}]}}"""
    }
}
