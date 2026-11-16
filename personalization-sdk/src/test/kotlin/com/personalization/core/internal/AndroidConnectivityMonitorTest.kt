package com.personalization.core.internal

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowNetworkCapabilities

/** "No network" only when the system says so: no default network, or one without internet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidConnectivityMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    @Test
    fun `a default network with internet is connected`() {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)

        assertTrue(AndroidConnectivityMonitor(context).isConnected)
    }

    @Test
    fun `a default network without internet is not`() {
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, ShadowNetworkCapabilities.newInstance())

        assertFalse(AndroidConnectivityMonitor(context).isConnected)
    }

    @Test
    fun `no default network is not connected`() {
        shadowOf(manager).setDefaultNetworkActive(false)
        shadowOf(manager).setActiveNetworkInfo(null)

        assertFalse(AndroidConnectivityMonitor(context).isConnected)
    }
}
