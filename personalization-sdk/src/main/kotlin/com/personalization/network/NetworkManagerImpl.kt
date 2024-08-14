package com.personalization.network

import com.personalization.RegisterManager
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.NetworkManager
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.usecases.network.InitNetworkUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import org.json.JSONObject
import java.util.Collections
import javax.inject.Inject

internal class NetworkManagerImpl @Inject constructor(
    private val registerManager: RegisterManager,
    private val initNetworkUseCase: InitNetworkUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase
): NetworkManager {

    override fun initialize(
        baseUrl: String
    ) {
        initNetworkUseCase.invoke(
            baseUrl = baseUrl
        )
    }

    private val queue: MutableList<Thread> = Collections.synchronizedList(ArrayList())

    override fun post(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        send(NetworkMethod.POST(method), params, listener)
    }

    override fun postAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendAsync { send(NetworkMethod.POST(method), params, listener) }
    }

    override fun get(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        send(NetworkMethod.GET(method), params, listener)
    }

    override fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendAsync { send(NetworkMethod.GET(method), params, listener) }
    }

    private fun send(networkMethod: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        sendNetworkMethodUseCase.invoke(
            networkMethod = networkMethod,
            params = params,
            listener = listener
        )
    }

    private fun sendAsync(sendFunction: () -> Unit) {
        val thread = Thread(sendFunction)
        if (getUserSettingsValueUseCase.getDid().isNotEmpty() && registerManager.isInitialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    override fun executeQueueTasks() {
        for (thread in queue) {
            thread.start()
        }
        queue.clear()
    }

    override fun addTaskToQueue(thread: Thread) {
        queue.add(thread)
    }
}
