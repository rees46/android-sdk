package com.personalization.core.internal

import com.personalization.core.SdkError
import org.json.JSONArray
import org.json.JSONObject

/** The outcome of one request of the new client. */
internal sealed interface HttpResult {

    /** A `2xx` answer: a [JSONObject] (empty for an empty answer) or a [JSONArray]. */
    class Success(val body: Any) : HttpResult

    /**
     * [errorBody] is the body of an answer outside `2xx`, kept only for the old callbacks, which got it
     * as their message. It is never written to the log. [retryAfterMs] is the answer's `Retry-After`, in
     * milliseconds from now, when it had one.
     */
    class Failure(val error: SdkError, val errorBody: String? = null, val retryAfterMs: Long? = null) : HttpResult
}
