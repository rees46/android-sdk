package com.personalization.features.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.Rees46
import com.personalization.Rees46Config
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.SdkRegistry
import com.personalization.api.models.search.AgentDialogResponse
import com.personalization.api.models.search.AgentSession
import com.personalization.core.BridgeApi
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
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
 * The search agent as a bridge reaches it: `sdk.search.agentEnabled` follows the `/init` answer's
 * `search.blank_mode`, and the agent's requests go to `Rees46Config.appDomain`; on the legacy core the
 * agent's methods fail with `notInitialized` and `agentEnabled` stays false.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgentSearchNamespaceTest {

    private lateinit var context: Context
    private val api = MockWebServer()
    private val app = MockWebServer()
    private val apiPaths = LinkedBlockingQueue<String>()
    private val appPaths = LinkedBlockingQueue<String>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private val threads = TrackingThreads()

    @Volatile
    private var initAnswer = """{"did":"D1","seance":"S1","search":{"enabled":true,"type":"instant","blank_mode":"agent"}}"""

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
        api.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath?.removePrefix("/") ?: return MockResponse().setResponseCode(400)
                apiPaths += path
                return if (path == "init") MockResponse().setBody(initAnswer) else MockResponse().setBody("{}")
            }
        }
        app.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                appPaths += request.requestUrl?.encodedPath?.removePrefix("/").orEmpty()
                return MockResponse().setBody("""{"sessions":[{"id":12,"title":"Flash drive"}],"session":null}""")
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
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun config() = Rees46Config(
        shopId = SHOP,
        apiDomain = api.url("/").toString().trimEnd('/'),
        appDomain = app.url("/").toString().trimEnd('/'),
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

    @Test
    fun `a bridge's instance knows the agent is on from init, and asks it on the shop's appDomain`() {
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)

        assertTrue(sdk.search.agentEnabled)
        assertEquals(
            SdkResult.Success(AgentDialogResponse(sessions = listOf(AgentSession(id = 12, title = "Flash drive")))),
            outcome { sdk.search.agentDialog() },
        )
        assertEquals(SearchApiImpl.AGENT_PATH, appPaths.poll())
        assertTrue(apiPaths.none { it == SearchApiImpl.AGENT_PATH })
    }

    @Test
    fun `a blank mode other than agent, or none, is false`() {
        initAnswer = """{"did":"D1","seance":"S1","search":{"blank_mode":"history"}}"""
        val sdk = Rees46.initializeBridge(context, config())
        awaitState(sdk, SdkState.READY)

        assertFalse(sdk.search.agentEnabled)
    }

    @Test
    fun `an app's instance on the legacy core gets notInitialized from the agent, whatever its arguments, and agentEnabled is false`() {
        val sdk = SDK()
        sdk.initializeWith(context, config(), coreV2 = false)
        awaitState(sdk, SdkState.READY)

        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.search.agentDialog(12) })
        assertEquals(SdkResult.Failure(SdkError.NotInitialized), outcome { sdk.search.agent("") })
        assertFalse(sdk.search.agentEnabled)
        threads.awaitIdle()
        assertTrue(appPaths.isEmpty())
        listOf(SearchApiImpl.AGENT_DIALOG, SearchApiImpl.AGENT).forEach { call ->
            assertTrue(call, logged.any { it.code == LogCodes.CORE_FLAG_MISMATCH && it.context["call"] == call })
        }
    }

    private companion object {
        const val SHOP = "agent-namespace-shop"
    }
}
