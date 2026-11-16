package com.personalization.core.internal

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The system's network callback behind [AndroidConnectivityMonitor]: registered once for its listeners and
 * given back with the last of them, and each change of the network told to every listener once — a call
 * that changes nothing tells nobody, and a listener that throws keeps no other from hearing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidConnectivityCallbacksTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    @Before
    fun setUp() {
        internet(true)
    }

    /** The default network with internet, or without it. */
    private fun internet(on: Boolean): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (on) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        return capabilities
    }

    private fun callbacks(): Set<ConnectivityManager.NetworkCallback> = shadowOf(manager).networkCallbacks

    @Test
    fun `each change of the network reaches every listener once, and one that throws stops no other`() {
        val monitor = AndroidConnectivityMonitor(context)
        val heard = CopyOnWriteArrayList<Boolean>()
        monitor.addListener { throw IllegalStateException("a listener of the core") }
        monitor.addListener { heard += it }
        val callback = callbacks().single()
        val network = manager.activeNetwork ?: throw AssertionError("no default network")

        internet(false)
        callback.onLost(network)
        // The system repeats itself: nothing changed, nobody hears.
        callback.onLost(network)
        val back = internet(true)
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, back)

        assertEquals(listOf(false, true), heard)
        assertTrue(monitor.isConnected)
    }

    @Test
    fun `the system's callback is registered once for all listeners and given back with the last of them`() {
        val monitor = AndroidConnectivityMonitor(context)
        val first = monitor.addListener {}
        val second = monitor.addListener {}
        assertEquals(1, callbacks().size)

        first.cancel()
        assertEquals("one listener is left", 1, callbacks().size)
        second.cancel()
        assertTrue(callbacks().isEmpty())

        // A listener that comes later watches again, from what the network is then.
        val heard = CopyOnWriteArrayList<Boolean>()
        internet(false)
        monitor.addListener { heard += it }
        val callback = callbacks().single()
        callback.onCapabilitiesChanged(manager.activeNetwork ?: throw AssertionError("no default network"), internet(true))
        assertEquals(listOf(true), heard)
    }
}
