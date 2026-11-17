package com.personalization.core.internal

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/**
 * The core of one SDK instance: how its requests travel. The request funnel
 * ([com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl], behind
 * `SendNetworkMethodUseCase`) hands every request to the instance's core, so the namespaces are the
 * same code for both cores.
 *
 * [LegacyCore] keeps today's paths. [CoreV2] is the new core, the bridges' (`Rees46.initializeBridge`): its
 * own client, `/init` ([CoreInit]), session ([SessionWindow]) and event queue ([EventQueue]).
 */
internal interface Core {

    /** Whether this is the new core. */
    val isV2: Boolean

    /**
     * Sends one request. [params] carry the method's own fields; the core adds the common ones. The
     * answer reaches [listener] once, the way the old methods get it: `onSuccess` with a `JSONObject` or
     * `JSONArray`, or `onError(code, msg)`.
     */
    fun send(method: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?)

    /** Stops the core: requests in flight are cancelled where the core can cancel them. */
    fun release()

    /**
     * Forgets the customer in [storage]: what [ShopStorage.deleteUserCredentials] wipes, and whatever
     * the core keeps of its own. Runs on a background thread; false when part of it did not reach the disk.
     */
    suspend fun deleteUserCredentials(storage: ShopStorage): Boolean
}
