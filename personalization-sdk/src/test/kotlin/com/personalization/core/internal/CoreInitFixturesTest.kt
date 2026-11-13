package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkState
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.models.NetworkMethod
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The shared contract fixtures of `/init` (`contract/0/init.json`) against the new core: the real
 * client over a local server, the real storage, and the state each answer leaves. A repeat would come
 * a minute later, so the state checked is the first answer's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreInitFixturesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val servers = mutableListOf<MockWebServer>()
    private val releases = mutableListOf<() -> Unit>()

    @After
    fun tearDown() {
        releases.forEach { it() }
        servers.forEach { it.shutdown() }
        SdkLogs.reset()
    }

    @Test
    fun `every case of the init fixtures`() {
        val cases = ContractFixtures.cases("init")
        assertTrue(cases.isNotEmpty())
        cases.forEachIndexed { index, case -> check(index, case) }
    }

    private fun check(index: Int, case: JSONObject) {
        val name = case.getString("name")
        val layout = StorageLayout("fixture-shop-$index", null)
        context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
        val storage = ShopStorage(context, layout)
        case.optJSONObject("saved")?.let { storage.saveInitAnswer(it) }
        val settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = layout.shopId) })
        settings.updateShopId(layout.shopId)

        val response = case.getJSONObject("response")
        val server = MockWebServer().also { servers += it }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/init") return MockResponse().setBody("{}")
                if (response.isNull("status")) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                val body = response.opt("body")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                return MockResponse().setResponseCode(response.getInt("status")).setBody(body)
            }
        }
        server.start()

        val connectivity = FakeConnectivity(connected = true)
        val client = CoreHttpClient(
            { server.url("/").toString() }, settings, connectivity, layout.shopId,
            timeouts = CoreTimeouts(initReadMs = 300),
        )
        val core = CoreV2(client, layout.shopId)
        val state = InstanceState(shopId = { layout.shopId })
        val init = CoreInit(
            setup = InitSetup(layout.shopId),
            send = { params -> client.send(NetworkMethod.GET(CoreHttpClient.INIT_PATH), params) },
            settings = settings,
            storage = storage,
            state = state,
            connectivity = connectivity,
            lifecycle = FakeAppLifecycle(),
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
            backoff = Backoff(baseMs = 60_000),
        )
        core.init = init
        releases += { core.release() }
        init.start()

        val expect = case.getJSONObject("expect")
        val expected = SdkState.valueOf(expect.getString("state").uppercase())
        // Every case moves the state its start set: the first answer decides.
        assertNotEquals(name, expected, state.current)
        runBlocking { withTimeout(5_000) { state.changes.first { it == expected } } }
        assertEquals(name, expected, state.current)
        if (expect.has("deviceId")) assertEquals(name, expect.getString("deviceId"), settings.getDid())
        if (expect.has("sessionId")) assertEquals(name, expect.getString("sessionId"), settings.getSid())
        if (expect.optBoolean("methodsWorkAsReady")) {
            // A request goes at once instead of waiting for /init.
            val answered = CountDownLatch(1)
            core.send(NetworkMethod.GET("products/get"), JSONObject(), object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) = answered.countDown()
            })
            assertTrue(name, answered.await(2, TimeUnit.SECONDS))
        }
        if (expected == SdkState.REFUSED) assertTrue(name, storage.isRefused)
    }
}
