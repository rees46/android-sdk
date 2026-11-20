package com.personalization.features.segments

import com.personalization.api.managers.SegmentsApi
import com.personalization.api.models.segments.Segment
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireContact
import com.personalization.core.internal.stringOrNull
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONArray
import org.json.JSONObject

/**
 * [SegmentsApi] over the namespaces' path ([NamespaceCalls]). [abGroup] is the device's A/B group the SDK
 * keeps, null for an instance never initialized. [contextChanged] tells popups v3 that the segments
 * changed, once the server took the change, so their list is asked for again.
 */
@OptIn(BridgeApi::class)
internal class SegmentsApiImpl(
    private val calls: NamespaceCalls,
    private val abGroup: () -> String?,
    private val contextChanged: (type: String, signature: String) -> Unit = { _, _ -> },
) : SegmentsApi {

    override suspend fun add(segmentId: Int, email: String?, phone: String?) = change(ADD, ADD_METHOD, segmentId, email, phone)

    override suspend fun remove(segmentId: Int, email: String?, phone: String?) = change(REMOVE, REMOVE_METHOD, segmentId, email, phone)

    override suspend fun get(): List<Segment> = calls.request(GET, NetworkMethod.GET(GET_PATH), read = ::readSegments)

    override fun getAbGroup(): String = abGroup().orEmpty()

    override fun add(segmentId: Int, email: String?, phone: String?, listener: CompletionListener<Unit>) =
        calls.complete(listener) { add(segmentId, email, phone) }

    override fun remove(segmentId: Int, email: String?, phone: String?, listener: CompletionListener<Unit>) =
        calls.complete(listener) { remove(segmentId, email, phone) }

    override fun get(listener: CompletionListener<List<Segment>>) = calls.complete(listener) { get() }

    /** Adds the customer to the segment or takes them out ([method]): `segments/add` or `segments/remove`. */
    private suspend fun change(call: String, method: String, segmentId: Int, email: String?, phone: String?) {
        val params = calls.checked(call) {
            if (segmentId <= 0) invalidArgument("segmentId must be above 0")
            requireContact(email, "email")
            requireContact(phone, "phone")
            JSONObject().put("segment_id", segmentId).putIfNotNull("email", email).putIfNotNull("phone", phone)
        }
        val signature = "$method $params"
        calls.request(call, NetworkMethod.POST("$SEGMENTS/$method"), params) { Unit }
        contextChanged(SEGMENTS, signature)
    }

    internal companion object {
        const val ADD = "segments.add"
        const val REMOVE = "segments.remove"
        const val GET = "segments.get"
        const val SEGMENTS = "segments"
        const val ADD_METHOD = "add"
        const val REMOVE_METHOD = "remove"
        const val GET_PATH = "segments/get"

        /**
         * The customer's segments: an array of `{id, type}`; a segment without an id is left out, one without a
         * type has an empty one, and an answer without a body is no segment. An answer that is not an array is
         * null (`decoding`).
         */
        fun readSegments(body: Any?): List<Segment>? = when (body) {
            null -> emptyList()
            is JSONArray -> body.mapObjects { segment ->
                segment.intOrNull("id")?.let { Segment(id = it, type = segment.stringOrNull("type").orEmpty()) }
            }
            else -> null
        }
    }
}
