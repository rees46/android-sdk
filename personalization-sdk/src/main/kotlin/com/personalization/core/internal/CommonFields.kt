package com.personalization.core.internal

import com.personalization.sdk.data.models.params.UserBasicParams
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import org.json.JSONObject

/**
 * The fields every request of a shop carries, added in one place for both cores: `shop_id`, `did`,
 * `sid` and `seance` with one value (some endpoints read only the old name), `segment` (the A/B group)
 * and `stream`. A `GET` sends them in the query, a `POST` in its JSON body.
 *
 * A clicked push is not among them: `from` and `code` on `/init` record one more click of the mailing
 * on every launch, so the click travels as the `source` of `/push` events instead
 * ([com.personalization.handlers.notifications.NotificationHandler]).
 */
internal object CommonFields {

    /** Adds the common fields to [params] and returns it. A non-blank `segment` or `stream` of the call wins. */
    fun addTo(params: JSONObject, settings: UserSettingsRepository): JSONObject {
        val sid = settings.getSid()
        params.put(UserBasicParams.SHOP_ID, settings.getShopId())
        params.put(UserBasicParams.DID, settings.getDid())
        params.put(UserBasicParams.SID, sid)
        params.put(UserBasicParams.SEANCE, sid)
        // Read even when the call has its own: the first read assigns the device's A/B group.
        params.put(UserBasicParams.SEGMENT, ownOr(params, UserBasicParams.SEGMENT, settings.getSegmentForABTesting()))
        params.put(UserBasicParams.STREAM, ownOr(params, UserBasicParams.STREAM, settings.getStream()))
        return params
    }

    /** The call's own non-blank [key] (a strict `trackPurchase` sets its own `segment`), otherwise [stored]. */
    private fun ownOr(params: JSONObject, key: String, stored: String): String {
        if (!params.has(key) || params.isNull(key)) return stored
        return params.get(key).toString().ifBlank { stored }
    }
}
