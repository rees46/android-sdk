package com.personalization.features

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.models.loyalty.LoyaltyLevel
import com.personalization.api.models.loyalty.LoyaltyMember
import com.personalization.api.models.loyalty.LoyaltyPromotion
import com.personalization.api.models.loyalty.LoyaltyRewardType
import com.personalization.api.models.loyalty.LoyaltyStatus
import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.api.models.sliders.Slider
import com.personalization.api.models.sliders.SliderBanner
import com.personalization.api.models.stories.StoriesBlock
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
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/**
 * `sdk.stories`, `sdk.predict`, `sdk.loyalty` and `sdk.sliders` as a bridge calls them: on the new core
 * they ask the server and answer in both forms; on the legacy core, where apps run, every method fails
 * with `notInitialized`, sends nothing and says why in the log.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesPredictLoyaltySlidersTest {

    private lateinit var context: Context
    private val server = MockWebServer()
    private val paths = LinkedBlockingQueue<String>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

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
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath?.removePrefix("/") ?: return MockResponse().setResponseCode(400)
                paths += path
                return MockResponse().setBody(ANSWERS[path] ?: "{}")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        server.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
        QueueStore.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = server.url("/").toString().trimEnd('/'),
        // `predict` asks API v3: the same server stands for the host of the shop's account.
        appDomain = server.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun awaitState(sdk: SDK, state: SdkState) = runBlocking {
        withTimeout(5_000) { sdk.instanceState.changes.first { it == state } }
    }

    private fun <T> outcome(block: suspend () -> T): SdkResult<T> = try {
        SdkResult.Success(runBlocking { withTimeout(10_000) { block() } })
    } catch (e: SdkException) {
        SdkResult.Failure(e.error)
    }

    /** The paths the server got, after the calls so far had time to arrive. */
    private fun sent(): List<String> {
        threads.awaitIdle()
        Thread.sleep(200)
        return generateSequence { paths.poll() }.toList()
    }

    /** An instance of [coreV2]'s core whose listener forms end on [main], which the test runs. */
    private fun sdk(main: HeldMain, coreV2: Boolean): SDK {
        val sdk = SDK().apply { dispatchers = CoreDispatchers(main = { main }) }
        sdk.initializeWith(context, config(), coreV2 = coreV2)
        awaitState(sdk, SdkState.READY)
        return sdk
    }

    /** One method of the four namespaces, in its two forms: [suspending] and the one ending in [listener]. */
    private class Method(
        val call: String,
        val path: String,
        val suspending: suspend (SDK) -> Any?,
        val listener: (SDK, CompletionListener<Any?>) -> Unit,
    )

    private val methods = listOf(
        Method("stories.get", "stories/main", { it.stories.get("main") }, { sdk, l -> sdk.stories.get("main", l.of()) }),
        Method("predict.getProbabilityToPurchase", "api/v3/cdp/users/probability-to-purchase", { it.predict.getProbabilityToPurchase() },
            { sdk, l -> sdk.predict.getProbabilityToPurchase(l.of()) }),
        Method("loyalty.join", "loyalty/members/join", { it.loyalty.join("79991234567") },
            { sdk, l -> sdk.loyalty.join("79991234567", null, null, null, null, l.of()) }),
        Method("loyalty.getStatus", "loyalty/members/status", { it.loyalty.getStatus("79991234567") },
            { sdk, l -> sdk.loyalty.getStatus("79991234567", l.of()) }),
        Method("loyalty.getPromotions", "loyalty/promos/for-client", { it.loyalty.getPromotions() },
            { sdk, l -> sdk.loyalty.getPromotions(null, null, null, null, l.of()) }),
        Method("sliders.get", "slider", { it.sliders.get("home") }, { sdk, l -> sdk.sliders.get("home", null, null, l.of()) }),
    )

    /** A listener of any result as one of [T]. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> CompletionListener<Any?>.of(): CompletionListener<T> = this as CompletionListener<T>

    @Test
    fun `a bridge's instance gets each namespace's answer from the server`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)

        val results = methods.map { method -> outcome { method.suspending(sdk) } }

        assertEquals(EXPECTED.map { SdkResult.Success(it) }, results)
        assertTrue(sent().containsAll(methods.map { it.path }))
    }

    @Test
    fun `the forms with a listener end once each, on the main thread`() {
        val main = HeldMain()
        val sdk = sdk(main, coreV2 = true)
        val ends = CopyOnWriteArrayList<Pair<SdkResult<Any?>, Boolean>>()

        methods.forEach { method -> method.listener(sdk, CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) }) }
        while (ends.size < methods.size && main.runNext()) Unit
        main.runNext(timeoutMs = 200)

        assertEquals(EXPECTED.map { SdkResult.Success(it) to true }.toSet(), ends.toSet())
        assertEquals(methods.size, ends.size)
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized from every method in both forms, sends nothing and logs each call`() {
        val main = HeldMain()
        val sdk = sdk(main, coreV2 = false)
        sent()

        methods.forEach { method ->
            val thrown = try {
                runBlocking { method.suspending(sdk) }
                null
            } catch (e: SdkException) {
                e
            }
            val ends = CopyOnWriteArrayList<SdkResult<Any?>>()
            method.listener(sdk, CompletionListener { ends += it })
            while (ends.isEmpty() && main.runNext()) Unit

            assertEquals(method.call, SdkError.NotInitialized, thrown?.error)
            assertTrue(method.call, thrown?.message.orEmpty().contains("Rees46.initializeBridge"))
            assertEquals(method.call, listOf<SdkResult<Any?>>(SdkResult.Failure(SdkError.NotInitialized)), ends)
            assertEquals(method.call, 2, logged.count { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == method.call })
        }
        val sent = sent()
        methods.forEach { method -> assertFalse(method.call, method.path in sent) }
    }

    @Test
    fun `on the legacy core wrong arguments are notInitialized too`() {
        val sdk = sdk(HeldMain(), coreV2 = false)

        val outcomes = listOf(
            outcome { sdk.stories.get("") },
            outcome { sdk.loyalty.join(" ", birthday = "17.05.1990") },
            outcome { sdk.loyalty.getPromotions(currentTime = "25:00") },
            outcome { sdk.sliders.get("home", tags = listOf("a,b")) },
        )

        assertEquals(List(4) { SdkResult.Failure(SdkError.NotInitialized) }, outcomes)
    }

    @Test
    fun `an instance never initialized gets notInitialized`() {
        val sdk = SDK()

        methods.forEach { method -> assertEquals(method.call, SdkResult.Failure(SdkError.NotInitialized), outcome { method.suspending(sdk) }) }
        assertTrue(sent().isEmpty())
    }

    @Test
    fun `a wrong argument is logged with its call, never with its value`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)
        sent()

        val outcome = outcome { sdk.loyalty.join("79991234567", birthday = "17.05.1990") }

        assertEquals(SdkResult.Failure(SdkError.InvalidArgument("birthday must be an existing date YYYY-MM-DD")), outcome)
        val line = logged.single { it.code == LogCodes.INVALID_ARGUMENT }
        assertEquals("loyalty.join", line.context["call"])
        assertFalse(line.message.contains("17.05.1990") || line.message.contains("79991234567"))
        assertFalse("loyalty/members/join" in sent())
    }

    @Test
    fun `Java does not see the namespaces`() {
        listOf("getStories", "getPredict", "getLoyalty", "getSliders").forEach { name ->
            val getters = SDK::class.java.methods.filter { it.name == name }
            assertTrue(name, getters.isNotEmpty() && getters.all { it.isSynthetic })
        }
    }

    private companion object {
        const val SHOP = "stories-predict-loyalty-sliders-shop"

        val ANSWERS = mapOf(
            "init" to """{"did":"D1","seance":"S1"}""",
            "stories/main" to """{"id":7,"stories":[]}""",
            "api/v3/cdp/users/probability-to-purchase" to """{"status":"success","payload":{"probability":0.42,"client_id":9}}""",
            "loyalty/members/join" to """{"status":"success","payload":{"identifier":"79991234567"}}""",
            "loyalty/members/status" to """{"status":"success","payload":{"member":true,"level":{"code":"gold","name":"Gold","expiration_date":null}}}""",
            "loyalty/promos/for-client" to """{"status":"success","payload":{"promotions":[{"id":1,"name":"A","reward_type":"discount"}]}}""",
            "slider" to """{"status":"success","payload":{"properties":{"code":"home","width":10,"height":2},"slides":[{"banner_id":1,"recommended_code":"home"}]}}""",
        )

        /** What [methods] return from [ANSWERS], in their order. */
        val EXPECTED: List<Any> = listOf(
            StoriesBlock("7", emptyList()),
            PurchaseProbability(0.42, "9"),
            LoyaltyMember("79991234567"),
            LoyaltyStatus(true, LoyaltyLevel("gold", "Gold", null)),
            listOf(LoyaltyPromotion(1, "A", null, null, null, null, null, null, LoyaltyRewardType.DISCOUNT)),
            Slider("home", 10, 2, listOf(SliderBanner("1", "home", null, null, null, null, null))),
        )
    }
}
