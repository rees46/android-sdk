package com.personalization.core.internal

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch

/** The app's foreground from the process lifecycle: changes only, never the replay of the current state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProcessAppLifecycleTest {

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = Owner()
    private val appLifecycle = ProcessAppLifecycle { owner }

    @Test
    fun `a listener added in the foreground hears the trip to the background and back, not the replay`() {
        owner.registry.currentState = Lifecycle.State.RESUMED
        val heard = mutableListOf<Boolean>()

        appLifecycle.addListener { heard += it }
        assertTrue(appLifecycle.isForeground)
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.RESUMED

        assertEquals(listOf(false, true), heard)
    }

    @Test
    fun `an instance made in the background hears the first trip to the foreground`() {
        owner.registry.currentState = Lifecycle.State.CREATED
        val heard = mutableListOf<Boolean>()

        appLifecycle.addListener { heard += it }
        assertFalse(appLifecycle.isForeground)
        owner.registry.currentState = Lifecycle.State.STARTED

        assertEquals(listOf(true), heard)
    }

    @Test
    fun `a listener added off the main thread is attached on it, and cancelled stops hearing`() {
        owner.registry.currentState = Lifecycle.State.STARTED
        val heard = mutableListOf<Boolean>()
        val added = CountDownLatch(1)
        var handle: com.personalization.Cancellable? = null
        Thread {
            handle = appLifecycle.addListener { heard += it }
            added.countDown()
        }.start()
        added.await()
        shadowOf(Looper.getMainLooper()).idle()

        owner.registry.currentState = Lifecycle.State.CREATED
        handle?.cancel()
        owner.registry.currentState = Lifecycle.State.STARTED

        assertEquals(listOf(false), heard)
    }
}
