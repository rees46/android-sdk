package com.personalization.sdk.data.repositories.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.core.internal.ConnectivityMonitor
import com.personalization.core.internal.Core
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.LegacyCore
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The funnel every request of an instance goes through: the namespaces call it and it hands each
 * request to the instance's [Core], legacy or new. On the legacy core it holds the requests that wait
 * for a `did`; the new core holds a request until the instance is ready itself.
 */
@Singleton
class NetworkRepositoryImpl @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) : NetworkRepository {

    private lateinit var baseUrl: String

    private val legacyCore = LegacyCore(userSettingsRepository) { baseUrl }

    /** The core the requests go to: the legacy one until [useCoreV2]. */
    @Volatile
    internal var core: Core = legacyCore
        private set

    /** How long a legacy request may wait for the next bytes of an answer; tests shorten it. */
    internal var readTimeoutMs: Int
        get() = legacyCore.readTimeoutMs
        set(value) {
            legacyCore.readTimeoutMs = value
        }

    /**
     * Requests made before the device has a `did`. Guarded by [queueLock] together with the `did`
     * check in [sendAsync]: a request either sees the `did` or lands here before [executeQueueTasks]
     * drains, so none is left behind and none starts twice.
     */
    private val queueLock = Any()
    private val queue = ArrayList<Thread>()

    override fun initialize(
        baseUrl: String
    ) {
        this.baseUrl = baseUrl
    }

    /** Sends this instance's requests through the new core from now on. */
    internal fun useCoreV2(
        shopId: String,
        connectivity: ConnectivityMonitor,
        dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
        client: CoreHttpClient = CoreHttpClient(
            baseUrl = { baseUrl },
            settings = userSettingsRepository,
            connectivity = connectivity,
            shopId = shopId,
        ),
    ): CoreV2 = CoreV2(client, shopId, dispatchers).also { core = it }

    override fun post(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendMethod(
            networkMethod = NetworkMethod.POST(method),
            params = params,
            listener = listener
        )
    }

    override fun postAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendMethod(
                networkMethod = NetworkMethod.POST(method),
                params = params,
                listener = listener
            )
        }
    }

    override fun postSecretAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        postAsync(method, params, listener)
    }

    override fun get(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendMethod(
            networkMethod = NetworkMethod.GET(method),
            params = params,
            listener = listener
        )
    }

    override fun getAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendMethod(
                networkMethod = NetworkMethod.GET(method),
                params = params,
                listener = listener
            )
        }
    }

    override fun getSecretAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        getAsync(method, params, listener)
    }

    private fun sendAsync(sendFunction: () -> Unit) {
        // The new core waits for the instance to be ready itself, within the request's timeout.
        if (core.isV2) {
            sendFunction()
            return
        }
        val thread = Thread(sendFunction)
        val startNow = synchronized(queueLock) {
            if (userSettingsRepository.getDid().isNotEmpty()) {
                true
            } else {
                queue.add(thread)
                false
            }
        }
        if (startNow) thread.start()
    }

    override fun executeQueueTasks() {
        val tasks = synchronized(queueLock) {
            ArrayList(queue).also { queue.clear() }
        }
        tasks.forEach { it.start() }
    }

    override fun addTaskToQueue(thread: Thread) {
        synchronized(queueLock) { queue.add(thread) }
    }

    /**
     * Stops the instance's core. The requests still waiting for a `did` are let go and fail with
     * `notInitialized`, as every request made after this does.
     */
    internal fun release() {
        core.release()
        executeQueueTasks()
    }

    private fun sendMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?,
    ) {
        userSettingsRepository.updateSidLastActTime()
        core.send(networkMethod, params, listener)
    }
}
