package com.personalization.demo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live end-to-end check of the SDK's cores against the real API and the demo shop.
 *
 * A process has one core, decided by its first initialization, and [DemoApplication] initializes the
 * demo shop itself; so these tests run only in a process the application left alone
 * (`cleanProcess=true`, see [DemoTestRunner]), one core per run, and each is selected by its own
 * argument. Without them every test here is skipped.
 *
 * [roundTrip], with `coreV2=true` or `coreV2=false`: a fresh install initializes the demo shop, runs a
 * live search, tracks a product view, an add to cart, a purchase and a custom event, and checks that
 * every request got a `2xx`:
 * ```
 * ./gradlew :demo-app:connectedRees46DebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.personalization.demo.CoreLiveE2ETest \
 *   -Pandroid.testInstrumentationRunnerArguments.cleanProcess=true \
 *   -Pandroid.testInstrumentationRunnerArguments.coreV2=true
 * ```
 *
 * The new core's queue across a restart without a network, in two runs of `am instrument` (the end of
 * a run kills the process; the network stays off between them):
 * - `offlineStep=1` ([offlineRestart1_queueWithoutNetwork]): initializes online, turns the network
 *   off, tracks a purchase and a custom event, and checks they wait on disk;
 * - `offlineStep=2` ([offlineRestart2_deliverAfterRestart]): a new process initializes offline on the
 *   stored `did`, turns the network on, and checks that the queue goes without a flush.
 * ```
 * adb shell am instrument -w -e cleanProcess true -e offlineStep 1 \
 *   -e class com.personalization.demo.CoreLiveE2ETest \
 *   com.personalization.demo.test/com.personalization.demo.DemoTestRunner
 * ```
 *
 * Every purchase is a real order of the demo shop, named `sdk-core-e2e-<core>-<time>`. The `did` and the
 * order id are written to logcat under the tag [TAG] (`RESULT` lines), for a check on the server.
 */
@RunWith(AndroidJUnit4::class)
class CoreLiveE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val recorder = Recorder()
    private var scenario: ActivityScenario<HttpLogActivity>? = null

    @Before
    fun setUp() {
        assumeTrue("needs cleanProcess=true (see DemoTestRunner)", DemoTestRunner.cleanProcess)
    }

    @After
    fun tearDown() {
        scenario?.close()
        Rees46.setLogHandler(null)
    }

    @Test
    fun roundTrip() {
        val coreV2 = arguments.getString(ARG_CORE_V2)?.toBooleanStrictOrNull()
        assumeTrue("needs coreV2=true or coreV2=false", coreV2 != null)
        val core = if (coreV2 == true) "v2" else "legacy"
        assertTrue("the network is off: turn it on before this test", isOnline())
        clearSdkStorage()
        val sdk = start(coreV2 == true)
        assertEquals("first state", SdkState.READY, awaitState(sdk, READY_TIMEOUT_MS, SdkState.READY))

        val (itemId, price) = searchItem(sdk)

        val orderId = "sdk-core-e2e-$core-${System.currentTimeMillis()}"
        val view = Callback("productView")
        val cart = Callback("addToCart")
        val purchase = Callback("purchase")
        val custom = Callback("custom")
        sdk.tracking.productView(itemId, listener = view)
        sdk.tracking.addToCart(TrackingItem(id = itemId, quantity = 1, price = price), listener = cart)
        sdk.tracking.purchase(purchaseOf(orderId, itemId, price), listener = purchase)
        sdk.tracking.custom(
            event = CUSTOM_EVENT,
            category = CUSTOM_CATEGORY,
            label = core,
            value = 1,
            listener = custom,
        )
        // Before any check can fail: the order is on its way and the server can be asked for it.
        Log.i(TAG, "TRACKED core=$core did=${sdk.deviceId} order=$orderId item=$itemId")

        // The new core sends its queue now; the legacy one has none and answers at once.
        val flushed = flush(sdk)
        assertTrue("flush: $flushed", flushed is SdkResult.Success)

        for (callback in listOf(view, cart, purchase, custom)) {
            assertTrue("${callback.name}: no answer within ${LISTENER_TIMEOUT_MS / 1000} s", callback.await(LISTENER_TIMEOUT_MS))
            assertEquals("${callback.name} failed", null, callback.error)
        }

        val did = sdk.deviceId
        assertNotNull("deviceId after READY", did)
        assertNotNull("sessionId after READY", sdk.sessionId)
        // The log handler runs on a thread of its own: the last answers may reach it after the listeners.
        awaitCondition(LOG_SETTLE_MS) {
            val sent = recorder.requests().filter { it.method == "POST" }
            sent.count { it.path == "push" } >= 3 && sent.any { it.path == "push/custom" }
        }
        val requests = recorder.requests()
        requests.forEach { Log.i(TAG, "request core=$core ${it.method} ${it.path} ${it.status}") }
        val failures = recorder.failures()
        failures.forEach { Log.w(TAG, "failure core=$core ${it.code} ${it.context}") }
        assertTrue("failures in the log: ${failures.map { "${it.code} ${it.context}" }}", failures.isEmpty())
        val not2xx = requests.filter { it.status !in 200..299 }
        assertTrue("requests without a 2xx: $not2xx", not2xx.isEmpty())
        // Both cores log each answer by method and path.
        assertSent(requests, "GET", "init", atLeast = 1)
        assertSent(requests, "GET", "search", atLeast = 1)
        assertSent(requests, "POST", "push", exactly = 3)
        assertSent(requests, "POST", "push/custom", exactly = 1)
        if (coreV2 == true) assertEquals("queued events left", 0, queuedEvents())
        Log.i(TAG, "RESULT core=$core did=$did order=$orderId item=$itemId states=${recorder.states} requests=${requests.size}")
    }

    @Test
    fun offlineRestart1_queueWithoutNetwork() {
        assumeTrue("needs offlineStep=1", arguments.getString(ARG_OFFLINE_STEP) == "1")
        assertTrue("the network is off: turn it on before step 1", isOnline())
        clearSdkStorage()
        val sdk = start(coreV2 = true)
        assertEquals("first state", SdkState.READY, awaitState(sdk, READY_TIMEOUT_MS, SdkState.READY))
        val (itemId, price) = searchItem(sdk)
        val did = sdk.deviceId
        assertNotNull("deviceId after READY", did)

        setNetwork(on = false)
        val mark = recorder.messages.size
        val orderId = "sdk-core-e2e-v2-offline-${System.currentTimeMillis()}"
        val purchase = Callback("purchase")
        val custom = Callback("custom")
        sdk.tracking.purchase(purchaseOf(orderId, itemId, price), listener = purchase)
        sdk.tracking.custom(event = CUSTOM_EVENT, category = CUSTOM_CATEGORY, label = "v2-offline", value = 1, listener = custom)

        assertTrue("both events written to the queue", awaitCondition(10_000) { queuedEvents() == 2 })
        // Tried at once and held by the missing network.
        val flushed = flush(sdk)
        assertTrue("flush: $flushed", flushed is SdkResult.Success)
        Thread.sleep(3_000)
        val sentWhileOffline = recorder.requests(from = mark).filter { it.path.startsWith("push") }
        assertTrue("events sent without a network: $sentWhileOffline", sentWhileOffline.isEmpty())
        assertEquals("queued events", 2, queuedEvents())
        assertTrue("the purchase's listener is kept until the event goes", purchase.pending())
        assertTrue("the custom event's listener is kept until the event goes", custom.pending())
        // The network stays off: the end of this run kills the process with the queue on disk.
        Log.i(TAG, "RESULT step=offline1 core=v2 did=$did order=$orderId item=$itemId queued=${queuedEvents()}")
    }

    @Test
    fun offlineRestart2_deliverAfterRestart() {
        assumeTrue("needs offlineStep=2", arguments.getString(ARG_OFFLINE_STEP) == "2")
        try {
            assertTrue("the network is on: step 1 leaves it off", !isOnline())
            assertEquals("events left on disk by step 1", 2, queuedEvents())
            val sdk = start(coreV2 = true)
            // A stored did is READY at once; its /init fails offline and is repeated later.
            assertTrue("deviceId from step 1", awaitCondition(READY_TIMEOUT_MS) { sdk.deviceId != null })
            val did = sdk.deviceId
            arguments.getString(ARG_EXPECT_DID)?.let { assertEquals("did kept across the restart", it, did) }

            Thread.sleep(5_000)
            val sentWhileOffline = recorder.requests().filter { it.path.startsWith("push") }
            assertTrue("events sent without a network: $sentWhileOffline", sentWhileOffline.isEmpty())
            assertEquals("queued events while offline", 2, queuedEvents())

            setNetwork(on = true)
            val online = System.currentTimeMillis()
            // No flush: the network's return is the queue's trigger.
            val delivered = awaitCondition(DELIVERY_TIMEOUT_MS) {
                val sent = recorder.requests().filter { it.method == "POST" }
                sent.any { it.path == "push" && it.status in 200..299 } &&
                    sent.any { it.path == "push/custom" && it.status in 200..299 }
            }
            val afterMs = System.currentTimeMillis() - online
            val requests = recorder.requests()
            requests.forEach { Log.i(TAG, "request step=offline2 ${it.method} ${it.path} ${it.status}") }
            recorder.failures().forEach { Log.i(TAG, "failure step=offline2 ${it.code} ${it.context}") }
            assertTrue("queue not delivered within ${DELIVERY_TIMEOUT_MS / 1000} s of the network's return", delivered)
            assertTrue("queue files removed", awaitCondition(10_000) { queuedEvents() == 0 })
            val pushes = requests.filter { it.method == "POST" && it.path.startsWith("push") }
            assertTrue("pushes without a 2xx: $pushes", pushes.all { it.status in 200..299 })
            assertEquals("each queued event sent once", 2, pushes.size)
            Log.i(TAG, "RESULT step=offline2 core=v2 did=$did deliveredAfterMs=$afterMs states=${recorder.states} requests=${requests.size}")
        } finally {
            if (!isOnline()) setNetwork(on = true)
        }
    }

    // region SDK

    private fun start(coreV2: Boolean): SDK {
        // In the foreground, as an app that tracks is.
        scenario = ActivityScenario.launch(HttpLogActivity::class.java)
        Rees46.setLogHandler(recorder)
        val sdk = Rees46.initialize(
            context = context.applicationContext,
            config = Rees46Config(
                shopId = BuildConfig.SHOP_ID,
                // No token of the emulator for the demo shop: the test is about the core.
                autoSendPushToken = false,
                logLevel = LogLevel.DEBUG,
                coreV2 = coreV2,
            ),
        )
        sdk.onStateChange { state -> recorder.states += state }
        return sdk
    }

    private fun awaitState(sdk: SDK, timeoutMs: Long, wanted: SdkState): SdkState {
        awaitCondition(timeoutMs) { sdk.state == wanted }
        return sdk.state
    }

    /**
     * The first product of a live instant search: its id and price. Not the full search: the demo shop's
     * answer to it has a facet whose `values` is a list, which `SearchFilter` does not parse.
     */
    private fun searchItem(sdk: SDK): Pair<String, Double> {
        val done = CountDownLatch(1)
        var found: Pair<String, Double>? = null
        var error: Int? = null
        sdk.searchManager.searchInstant(
            query = SEARCH_QUERY,
            onSearchInstant = { response ->
                found = response.products.firstOrNull()?.let { it.id to it.price }
                done.countDown()
            },
            onError = { code, _ ->
                error = code
                done.countDown()
            },
        )
        assertTrue("search: no answer", done.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals("search failed", null, error)
        val item = found
        assertNotNull("search found no product", item)
        return item!!
    }

    private fun purchaseOf(orderId: String, itemId: String, price: Double) = PurchaseTrackingRequest(
        orderId = orderId,
        orderPrice = price,
        items = listOf(PurchaseItemRequest(id = itemId, amount = 1, price = price)),
        deliveryType = DELIVERY_TYPE,
        paymentType = PAYMENT_TYPE,
        isTaxFree = true,
    )

    private fun flush(sdk: SDK): SdkResult<Unit>? {
        val done = CountDownLatch(1)
        var result: SdkResult<Unit>? = null
        sdk.flush { result = it; done.countDown() }
        done.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }

    private fun assertSent(requests: List<Request>, method: String, path: String, exactly: Int? = null, atLeast: Int = 0) {
        val sent = requests.count { it.method == method && it.path == path && it.status in 200..299 }
        if (exactly != null) assertEquals("$method $path answered 2xx", exactly, sent)
        assertTrue("$method $path answered 2xx $sent times, expected at least $atLeast", sent >= atLeast)
    }

    // endregion

    // region Device

    /** A fresh install as far as the SDK knows: its preferences and its files under `noBackupFilesDir`. */
    private fun clearSdkStorage() {
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            if (name.startsWith("personalization_sdk") || name.contains("_personalization_sdk_") || name == LEGACY_STORAGE) {
                context.deleteSharedPreferences(name)
            }
        }
        File(context.noBackupFilesDir, SDK_FILES).deleteRecursively()
    }

    /** Events in the shop's queue on disk: one JSON file each. */
    private fun queuedEvents(): Int =
        File(context.noBackupFilesDir, SDK_FILES).walkTopDown()
            .count { it.isFile && it.name.endsWith(".json") && it.parentFile?.name == "queue" }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun setNetwork(on: Boolean) {
        val state = if (on) "disable" else "enable"
        val radios = if (on) "enable" else "disable"
        shell("cmd connectivity airplane-mode $state")
        shell("svc wifi $radios")
        shell("svc data $radios")
        val reached = awaitCondition(NETWORK_TIMEOUT_MS) {
            if (on) isOnline() else context.getSystemService(ConnectivityManager::class.java).activeNetwork == null
        }
        assertTrue("the network did not turn ${if (on) "on" else "off"}", reached)
        Log.i(TAG, "network ${if (on) "on" else "off"}")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    // endregion

    /** A request's answer as the SDK logged it. */
    private data class Request(val method: String, val path: String, val status: Int?)

    /** The SDK's log, by code and context; it carries no personal data. */
    private class Recorder : SdkLogHandler {
        val messages = CopyOnWriteArrayList<SdkLogMessage>()
        val states = CopyOnWriteArrayList<SdkState>()

        override fun onLog(message: SdkLogMessage) {
            messages += message
            Log.d(TAG, "log ${message.level} ${message.code} ${message.context}")
        }

        fun requests(from: Int = 0): List<Request> = messages.drop(from).filter { it.code == CODE_REQUEST }.map {
            Request(it.context["method"]?.toString().orEmpty(), it.context["path"]?.toString().orEmpty(), (it.context["status"] as? Number)?.toInt())
        }

        fun failures(): List<SdkLogMessage> = messages.filter { it.code in FAILURE_CODES }
    }

    /** A listener of the legacy callbacks: one answer, success or error. */
    private class Callback(val name: String) : OnApiCallbackListener() {
        private val done = CountDownLatch(1)

        @Volatile
        var error: Int? = null

        override fun onSuccess(response: JSONObject?) = done.countDown()

        override fun onSuccess(response: JSONArray) = done.countDown()

        override fun onError(code: Int, msg: String?) {
            error = code
            done.countDown()
        }

        fun await(timeoutMs: Long): Boolean = done.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun pending(): Boolean = done.count > 0
    }

    private companion object {
        const val TAG = "SdkCoreE2E"

        const val ARG_CORE_V2 = "coreV2"
        const val ARG_OFFLINE_STEP = "offlineStep"
        const val ARG_EXPECT_DID = "expectDid"

        const val SEARCH_QUERY = "a"
        // An event registered for the demo shop: an unknown one is answered 400 "Event not found".
        const val CUSTOM_EVENT = "flutter_example"
        const val CUSTOM_CATEGORY = "sdk_core_e2e"
        const val DELIVERY_TYPE = "courier"
        const val PAYMENT_TYPE = "card"

        const val SDK_FILES = "personalization_sdk"
        const val LEGACY_STORAGE = "DEFAULT_STORAGE_KEY"

        const val CODE_REQUEST = "request"
        val FAILURE_CODES = setOf(
            "request_failed", "request_cancelled", "decoding_failed", "queue_dropped",
            "init_failed", "init_refused", "init_retry", "callback_failed",
        )

        const val READY_TIMEOUT_MS = 30_000L
        const val LISTENER_TIMEOUT_MS = 60_000L
        const val NETWORK_TIMEOUT_MS = 60_000L
        const val DELIVERY_TIMEOUT_MS = 150_000L
        const val LOG_SETTLE_MS = 5_000L
        const val POLL_MS = 200L
    }
}
