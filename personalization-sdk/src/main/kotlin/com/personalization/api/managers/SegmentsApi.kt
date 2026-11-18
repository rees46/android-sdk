package com.personalization.api.managers

import com.personalization.api.models.segments.Segment
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's segments and the device's A/B group — the `segments` namespace. The shop sets up static
 * segments in its account, and the SDK adds the customer to them or takes them out; the server works out
 * dynamic ones itself. The API finds the customer by the device's `did`, which the SDK adds with the other
 * common fields itself. With `email` or `phone` the server applies the call to the contact's owner, without
 * moving the device to it.
 *
 * For the Flutter and React Native bridges: [add], [remove] and [get] run on the new core only
 * (`Rees46.initializeBridge`); on an instance of the legacy core they fail with [SdkError.NotInitialized]
 * without a request. [getAbGroup] asks nothing of the server.
 *
 * [add], [remove] and [get] throw [SdkException] with [SdkError.NotInitialized] (the legacy core, an
 * instance not initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (before any
 * request), [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or
 * [SdkError.Cancelled] (`release()`). Nothing repeats a request.
 */
@BridgeApi
interface SegmentsApi {

    /**
     * Adds the customer to the static segment [segmentId] (`POST /segments/add`); nothing changes for a
     * customer already in it. A [segmentId] not above 0, or an empty contact, is `invalidArgument`.
     */
    suspend fun add(segmentId: Int, email: String? = null, phone: String? = null)

    /** Takes the customer out of the static segment [segmentId] (`POST /segments/remove`); as [add]. */
    suspend fun remove(segmentId: Int, email: String? = null, phone: String? = null)

    /**
     * The segments the customer is in now, static and dynamic (`GET /segments/get`); an empty list without
     * any, `http(404)` for a `did` the server does not know.
     */
    suspend fun get(): List<Segment>

    /**
     * The device's A/B group, `"A"` or `"B"`, at once and without errors: the SDK picks it at random once
     * and sends it with every request (`segment`), and the server splits A/B tests by it. Empty for an
     * instance never initialized.
     */
    fun getAbGroup(): String

    /** [add] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun add(segmentId: Int, email: String?, phone: String?, listener: CompletionListener<Unit>)

    /** [remove] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun remove(segmentId: Int, email: String?, phone: String?, listener: CompletionListener<Unit>)

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(listener: CompletionListener<List<Segment>>)
}
