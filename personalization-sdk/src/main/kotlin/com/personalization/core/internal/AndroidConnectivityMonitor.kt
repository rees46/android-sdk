package com.personalization.core.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.personalization.Cancellable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The device's network as [ConnectivityManager] sees it.
 *
 * It answers "no network" only when it knows: without a default network, or one without internet.
 * When it cannot tell (no permission, the system service fails), it answers "connected" and lets the
 * request find out, so a doubt never fails a request that would have gone through.
 */
internal class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val lock = Any()
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastKnown: Boolean? = null

    override val isConnected: Boolean
        get() = try {
            val manager = manager ?: return true
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return true
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: RuntimeException) {
            // SecurityException without ACCESS_NETWORK_STATE, or a failing system service.
            true
        }

    override fun addListener(listener: (connected: Boolean) -> Unit): Cancellable {
        listeners += listener
        startWatching()
        return Cancellable {
            listeners -= listener
            if (listeners.isEmpty()) stopWatching()
        }
    }

    private fun startWatching(): Unit = synchronized(lock) {
        val manager = manager ?: return
        if (callback != null) return
        lastKnown = isConnected
        val watcher = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed()
            override fun onLost(network: Network) = changed()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = changed()
        }
        try {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            manager.registerNetworkCallback(request, watcher)
            callback = watcher
        } catch (e: RuntimeException) {
            // Without the permission there are no changes to hear; isConnected still answers.
        }
    }

    private fun stopWatching(): Unit = synchronized(lock) {
        val watcher = callback ?: return
        callback = null
        try {
            manager?.unregisterNetworkCallback(watcher)
        } catch (e: RuntimeException) {
            // Already unregistered.
        }
    }

    private fun changed() {
        val connected = isConnected
        if (lastKnown == connected) return
        lastKnown = connected
        for (listener in listeners) {
            try {
                listener(connected)
            } catch (e: Exception) {
                // A listener of the core; one failing must not keep the others from hearing.
            }
        }
    }
}
