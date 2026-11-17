package com.personalization.api.managers

import com.personalization.api.models.sliders.Slider
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Banner sliders — the `sliders` namespace: `sdk.sliders.get(code)`. The app draws the carousel and
 * reports a banner shown and tapped with [TrackingApi.bannerView] and [TrackingApi.bannerClick].
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface SlidersApi {

    /**
     * The slider [code] with its banners for the customer of this device (`GET /slider`). [tags] keeps the
     * banners with those tags of the dashboard; [locationId] is the customer's location (its `external_id`),
     * without which a banner limited to locations is not shown. A slider without banners is an empty list.
     *
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [code], or an empty tag or one with
     * a comma (tags go joined by commas), [SdkError.NotInitialized] (the legacy core, an instance not
     * initialized or released), [SdkError.ShopBlocked], [SdkError.Network], [SdkError.Timeout],
     * [SdkError.Http] (`404` for an unknown or inactive slider, `402` when the shop does not pay for
     * sliders), [SdkError.Decoding] or [SdkError.Cancelled] (`release()`).
     */
    suspend fun get(code: String, tags: List<String>? = null, locationId: String? = null): Slider

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(code: String, tags: List<String>?, locationId: String?, listener: CompletionListener<Slider>)
}
