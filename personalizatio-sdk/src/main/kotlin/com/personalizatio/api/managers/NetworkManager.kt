package com.personalizatio.api.managers

import com.personalizatio.api.OnApiCallbackListener
import org.json.JSONObject

interface NetworkManager {

    /**
     * Direct query execution
     */
    fun post(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun postAsync(method: String, params: JSONObject, listener: OnApiCallbackListener? = null)

    /**
     * Direct query execution
     */
    fun get(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?)

    fun executeQueueTasks() {}

    fun addTaskToQueue(thread: Thread)
}