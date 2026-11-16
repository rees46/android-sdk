package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushTokenManager
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.util.concurrent.Executor

/**
 * The legacy `/init` as 2.49.0 sent it, which the branch keeps: a failure without an answer or a `5xx` is
 * repeated up to five times after a linear pause of 1 to 5 seconds, then given up with `init_failed`; a
 * `4xx` is not repeated. A known device keeps its `did`, its session and its ready state through all of it
 * and hears `onInitFinished(false)` once; a new device stays initializing with its requests queued. The
 * pauses run on the test scheduler, which stands in for the main thread they are posted to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegisterManagerRetryTest {

    private val scheduler = TestCoroutineScheduler()
    private val update = mockk<UpdateUserSettingsValueUseCase>(relaxed = true)
    private val get = mockk<GetUserSettingsValueUseCase>(relaxed = true)
    private val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
    private val queue = mockk<ExecuteQueueTasksUseCase>(relaxed = true)
    private val pushTokens = mockk<PushTokenManager>(relaxed = true)

    /** Each `/init` that went, with the listener its answer goes to, in order. */
    private val inits = CopyOnWriteArrayList<OnApiCallbackListener>()
    private val states = CopyOnWriteArrayList<SdkState>()
    private val finished = CopyOnWriteArrayList<Boolean>()
    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor
    private lateinit var manager: RegisterManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        context = ApplicationProvider.getApplicationContext()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
        every { get.getShopId() } returns SHOP
        every { network.get(eq("init"), any(), any()) } answers { inits += thirdArg<OnApiCallbackListener>() }
        manager = RegisterManager(update, get, network, queue, mockk(relaxed = true), pushTokens)
        manager.onStateChange = { states += it }
        manager.onInitFinished = { finished += it }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun knownDevice() {
        every { get.getDid() } returns "D"
        every { get.getSid() } returns "S"
        every { get.getSidLastActTime() } returns System.currentTimeMillis()
    }

    private fun after(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun codes(code: String) = logged.filter { it.code == code }

    @Test
    fun `a known device repeats a failed init five times after 1 to 5 seconds, then gives up once and stays ready`() {
        knownDevice()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        assertEquals(1, inits.size)

        (1..5).forEach { attempt ->
            inits.last().onError(503, null)
            // The pause before attempt n is n seconds: not a moment sooner.
            after(attempt * 1_000L - 1)
            assertEquals("before repeat $attempt", attempt, inits.size)
            after(1)
            assertEquals("repeat $attempt", attempt + 1, inits.size)
        }
        inits.last().onError(503, null)
        after(60_000)

        assertEquals("six in all, then no more", 6, inits.size)
        assertEquals(5, codes(LogCodes.INIT_RETRY).size)
        assertTrue(codes(LogCodes.INIT_RETRY).all { it.level == LogLevel.WARNING })
        assertEquals((1..5).toList(), codes(LogCodes.INIT_RETRY).map { it.context["attempt"] })
        val failed = codes(LogCodes.INIT_FAILED).single()
        assertEquals(LogLevel.WARNING, failed.level)
        assertEquals(6, failed.context["attempts"])
        assertEquals(listOf(false), finished)
        verify(exactly = 0) { update.updateDid(any()) }
        verify(exactly = 1) { update.updateSid(any()) }
        assertEquals(listOf(SdkState.READY), states)
    }

    @Test
    fun `a new device whose init never answers stays initializing with its requests queued`() {
        every { get.getDid() } returns ""
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        repeat(6) { attempt ->
            inits.last().onError(-1, null)
            after((attempt + 1) * 1_000L)
        }
        after(60_000)

        assertEquals(6, inits.size)
        assertEquals(listOf(SdkState.INITIALIZING), states)
        verify(exactly = 0) { queue.invoke() }
        verify(exactly = 0) { update.updateDid(any()) }
        assertTrue("a new device hears no end of init", finished.isEmpty())
        assertEquals(1, codes(LogCodes.INIT_FAILED).size)
    }

    @Test
    fun `a release between a failure and its repeat sends no more init`() {
        knownDevice()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        inits.single().onError(500, null)

        manager.stop()
        after(60_000)

        assertEquals(1, inits.size)
    }

    @Test
    fun `a restart during the pause drops the old start's repeat, only the restart's init goes`() {
        knownDevice()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        inits.single().onError(500, null)

        every { get.getDid() } returns ""
        manager.restartAsNewDevice(manager.beginRestart())
        assertEquals("the restart's init", 2, inits.size)
        after(60_000)

        assertEquals("the old start's repeat never went", 2, inits.size)
        inits[1].onSuccess(JSONObject("""{"did":"D2","seance":"S2"}"""))
        verify(exactly = 1) { update.updateDid("D2") }
    }

    @Test
    fun `a 4xx is not repeated, it fails at once and a known device hears the end of init`() {
        knownDevice()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        inits.single().onError(404, null)
        after(60_000)

        assertEquals(1, inits.size)
        assertTrue(codes(LogCodes.INIT_RETRY).isEmpty())
        val failed = codes(LogCodes.INIT_FAILED).single()
        assertEquals(LogLevel.ERROR, failed.level)
        assertEquals(404, failed.context["status"])
        assertEquals(listOf(false), finished)
        assertEquals(listOf(SdkState.READY), states)
    }

    @Test
    fun `a new device's answer without a did or a seance, or with no body, takes nothing and stays initializing`() {
        every { get.getDid() } returns ""
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        inits.single().onSuccess(null as JSONObject?)
        inits.single().onSuccess(JSONObject("""{"seance":"S"}"""))
        inits.single().onSuccess(JSONObject("""{"did":"D"}"""))

        verify(exactly = 0) { update.updateDid(any()) }
        verify(exactly = 0) { queue.invoke() }
        assertEquals(listOf(SdkState.INITIALIZING), states)
        assertEquals(3, codes(LogCodes.DECODING_FAILED).size)
        assertTrue(finished.isEmpty())
    }

    private companion object {
        const val SHOP = "retry-shop"
    }
}
