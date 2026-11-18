package com.personalization.features.predict

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkRegistry
import com.personalization.api.params.PurchasePredictParams
import com.personalization.api.responses.predict.PurchasePredictResponse
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.LegacyCore
import com.personalization.core.internal.QueueStore
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.TrackingThreads
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The released `predictManager.getProbabilityToPurchase` on both cores: it asks `GET
 * api/v3/cdp/users/probability-to-purchase` on `Rees46Config.appDomain`, never the API host's
 * `predict/probability-to-purchase` (which answers `503`), reads the answer's `payload` into
 * [PurchasePredictResponse] and reports a failure through `onError(code, message)` as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PredictManagerTest {

    private lateinit var context: Context
    private val api = MockWebServer()
    private val app = MockWebServer()
    private val apiRequests = LinkedBlockingQueue<RecordedRequest>()
    private val appRequests = LinkedBlockingQueue<RecordedRequest>()
    private val threads = TrackingThreads()

    @Volatile
    private var appAnswer: MockResponse = MockResponse().setBody("{}")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        QueueStore.reset()
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
        api.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                apiRequests += request
                val path = request.requestUrl?.encodedPath?.removePrefix("/")
                return when (path) {
                    "init" -> MockResponse().setBody("""{"did":"D1","seance":"S1"}""")
                    OLD_PATH -> MockResponse().setResponseCode(503)
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        app.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                appRequests += request
                return appAnswer
            }
        }
        api.start()
        app.start()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        api.shutdown()
        app.shutdown()
        LegacyCore.threads = LegacyCore.DEFAULT_THREADS
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        QueueStore.reset()
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = api.url("/").toString().trimEnd('/'),
        appDomain = app.url("/").toString().trimEnd('/'),
        autoSendPushToken = false,
        logLevel = LogLevel.DEBUG,
    )

    private fun sdk(coreV2: Boolean): SDK {
        val sdk = SDK()
        sdk.initializeWith(context, config(), coreV2 = coreV2)
        runBlocking { withTimeout(5_000) { sdk.instanceState.changes.first { it == SdkState.READY } } }
        return sdk
    }

    /** The one callback [sdk]'s call ends in: the response, or `code:message`. */
    private fun call(sdk: SDK, params: PurchasePredictParams = PurchasePredictParams()): Any {
        val ends = LinkedBlockingQueue<Any>()
        sdk.predictManager.getProbabilityToPurchase(
            params = params,
            onSuccess = { ends += it },
            onError = { code, message -> ends += "$code:$message" },
        )
        val end = ends.poll(10, TimeUnit.SECONDS)
        assertNotNull("no callback", end)
        threads.awaitIdle()
        assertNull("one callback only", ends.poll(300, TimeUnit.MILLISECONDS))
        return end!!
    }

    private fun bothCores(block: (coreV2: Boolean, SDK) -> Unit) {
        listOf(false, true).forEach { coreV2 ->
            SdkRegistry.all().forEach { it.release() }
            SdkRegistry.reset()
            Rees46.reset()
            appRequests.clear()
            apiRequests.clear()
            block(coreV2, sdk(coreV2))
        }
    }

    @Test
    fun `it asks API v3 on the shop's appDomain with the device's did and no contacts, and reads the payload`() = bothCores { coreV2, sdk ->
        appAnswer = MockResponse().setBody("""{"status":"success","payload":{"probability":0.37,"client_id":81412233}}""")

        val end = call(sdk, PurchasePredictParams(email = "buyer@example.com", phone = "70000000000", telegramId = "1", loyaltyId = "L1"))

        assertEquals("core v2: $coreV2", PurchasePredictResponse(probability = 0.37, clientId = "81412233"), end)
        val request = appRequests.single()
        assertEquals("GET", request.method)
        assertEquals("/$PATH", request.requestUrl?.encodedPath)
        val query = request.requestUrl!!
        assertEquals(SHOP, query.queryParameter("shop_id"))
        assertEquals("D1", query.queryParameter("did"))
        listOf("email", "phone", "telegram_id", "loyalty_id").forEach { assertNull("core v2: $coreV2, $it", query.queryParameter(it)) }
        assertTrue("core v2: $coreV2", apiRequests.none { it.requestUrl?.encodedPath?.removePrefix("/") in setOf(OLD_PATH, PATH) })
    }

    @Test
    fun `a customer the server does not know is probability 0 with an empty client id`() = bothCores { coreV2, sdk ->
        appAnswer = MockResponse().setBody("""{"status":"success","payload":{"probability":0,"client_id":null}}""")

        assertEquals("core v2: $coreV2", PurchasePredictResponse(probability = 0.0, clientId = ""), call(sdk))
    }

    @Test
    fun `an answer outside 2xx is onError with its code and body`() = bothCores { coreV2, sdk ->
        listOf(400, 402, 403, 404).forEach { code ->
            val body = """{"status":"fail","message":"error $code"}"""
            appAnswer = MockResponse().setResponseCode(code).setBody(body)

            assertEquals("core v2: $coreV2", "$code:$body", call(sdk))
        }
    }

    @Test
    fun `a 2xx without a numeric probability is onError -1`() = bothCores { coreV2, sdk ->
        listOf(
            """{"status":"success","payload":{"client_id":5}}""",
            """{"status":"success","payload":{"probability":"high","client_id":5}}""",
            """{"probability":0.4,"client_id":5}""",
            "",
        ).forEach { body ->
            appAnswer = MockResponse().setBody(body)

            val end = call(sdk)

            assertTrue("core v2: $coreV2, $body: $end", end is String && end.startsWith("-1:"))
        }
    }

    private companion object {
        const val SHOP = "predict-manager-shop"
        const val PATH = "api/v3/cdp/users/probability-to-purchase"
        const val OLD_PATH = "predict/probability-to-purchase"
    }
}
