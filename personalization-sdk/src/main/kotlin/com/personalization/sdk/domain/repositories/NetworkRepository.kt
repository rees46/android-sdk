package com.personalization.sdk.domain.repositories

import com.personalization.api.OnApiCallbackListener
import org.json.JSONObject

interface NetworkRepository {

    fun initialize(
        baseUrl: String
    )

    fun post(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener? = null
    )

    fun postAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener? = null
    )

    fun get(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener? = null
    )

    fun getAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener? = null
    )

    fun executeQueueTasks()

    fun addTaskToQueue(
        thread: Thread
    )
}
