package com.personalization.sdk.domain.repositories

import com.personalization.api.OnApiCallbackListener
import org.json.JSONObject

interface NetworkRepository {

    fun initialize(
        baseUrl: String
    )

    /**
     * Direct query execution
     */
    fun post(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun postAsync(method: String, params: JSONObject, listener: OnApiCallbackListener? = null)

    /**
     * Asynchronous execution of a request with shop secret key if did is not specified and initialization has not been completed
     */
    fun postSecretAsync(method: String, params: JSONObject, listener: OnApiCallbackListener? = null)

    /**
     * Direct query execution
     */
    fun get(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    /**
     * Asynchronous execution of a request with shop secret key if did is not specified and initialization has not been completed
     */
    fun getSecretAsync(method: String, params: JSONObject, listener: OnApiCallbackListener? = null)

    fun executeQueueTasks()

    fun addTaskToQueue(thread: Thread)
}
