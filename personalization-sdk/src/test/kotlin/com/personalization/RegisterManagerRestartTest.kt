package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.push.PushTokenManager
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** After `deleteUserCredentials()` the instance starts over as a new device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegisterManagerRestartTest {

    private val update = mockk<UpdateUserSettingsValueUseCase>(relaxed = true)
    private val get = mockk<GetUserSettingsValueUseCase>(relaxed = true)
    private val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
    private val queue = mockk<ExecuteQueueTasksUseCase>(relaxed = true)
    private val pushTokens = mockk<PushTokenManager>(relaxed = true)
    private val inits = CopyOnWriteArrayList<OnApiCallbackListener>()
    private lateinit var manager: RegisterManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { network.get(eq("init"), any(), any()) } answers { inits += thirdArg<OnApiCallbackListener>() }
        every { pushTokens.initialize(any(), any()) } just Runs
        manager = RegisterManager(update, get, network, queue, mockk(relaxed = true), pushTokens)
    }

    @Test
    fun `the new did is asked for before the push token is fetched again`() {
        every { get.getDid() } returns "old-did"
        manager.initialize(context, context.contentResolver, autoSendPushToken = true)
        // A known device starts its session from storage: the waiting requests go and the token is fetched.
        verify(exactly = 1) { queue.invoke() }
        verify(exactly = 1) { pushTokens.initialize(any(), true) }

        every { get.getDid() } returns ""
        manager.restartAsNewDevice(manager.beginRestart())

        // Without a did nothing waiting goes out, and the token is not fetched.
        assertEquals(2, inits.size)
        verify(exactly = 1) { queue.invoke() }
        verify(exactly = 1) { pushTokens.initialize(any(), any()) }

        inits[1].onSuccess(JSONObject("""{"did":"new-did","seance":"S2"}"""))

        verify(exactly = 1) { update.updateDid("new-did") }
        verify(exactly = 2) { queue.invoke() }
        verify(exactly = 2) { pushTokens.initialize(any(), true) }
    }

    @Test
    fun `an answer to the init sent before the restart is dropped`() {
        every { get.getDid() } returns ""
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        manager.restartAsNewDevice(manager.beginRestart())

        inits[0].onSuccess(JSONObject("""{"did":"stale-did","seance":"S1"}"""))
        inits[0].onError(500, null)
        inits[1].onSuccess(JSONObject("""{"did":"fresh-did","seance":"S2"}"""))

        verify(exactly = 0) { update.updateDid("stale-did") }
        verify(exactly = 1) { update.updateDid("fresh-did") }
        // The dropped error schedules no retry of the old start.
        assertEquals(2, inits.size)
    }

    /**
     * The window of `deleteUserCredentials()`: the restart begins, an answer to the earlier `/init`
     * lands while the did is being wiped, and only then is `/init` sent again. The late answer must not
     * write the forgotten did back.
     */
    @Test
    fun `an answer landing between the start of a restart and its init writes nothing`() {
        every { get.getDid() } returns ""
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        val start = manager.beginRestart()
        inits[0].onSuccess(JSONObject("""{"did":"stale-did","seance":"S1"}"""))
        // The wipe runs here.
        manager.restartAsNewDevice(start)
        inits[1].onSuccess(JSONObject("""{"did":"fresh-did","seance":"S2"}"""))

        verify(exactly = 0) { update.updateDid("stale-did") }
        verify(exactly = 0) { update.updateSid("S1") }
        verify(exactly = 1) { update.updateDid("fresh-did") }
    }

    @Test
    fun `two restarts never share a start, and only the later one asks init`() {
        every { get.getDid() } returns ""
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        val starts = java.util.Collections.synchronizedSet(HashSet<Int>())
        val threads = List(8) { Thread { repeat(50) { starts += manager.beginRestart() } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(400, starts.size)

        val first = manager.beginRestart()
        val second = manager.beginRestart()
        manager.restartAsNewDevice(first)
        assertEquals(1, inits.size)
        manager.restartAsNewDevice(second)
        assertEquals(2, inits.size)
    }

    @Test
    fun `a restart before initialize does nothing`() {
        manager.restartAsNewDevice(manager.beginRestart())

        assertEquals(0, inits.size)
    }
}
