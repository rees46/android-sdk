package com.personalization.api.models.sliders

/** A banner slider by its code (`sdk.sliders.get(code)`): the app draws the carousel. */
data class Slider(
    /** The slider's code (`payload.properties.code`). */
    val code: String,
    /** The block's width, px; 0 when the answer has none. */
    val width: Int,
    /** The block's height, px; 0 when the answer has none. */
    val height: Int,
    /** The banners for the customer of this device, already chosen by period, segments and location (`payload.slides`). */
    val banners: List<SliderBanner>,
)

/**
 * One banner of a slider. [id] and [code] are what [com.personalization.api.managers.TrackingApi.bannerView]
 * and `bannerClick` take.
 */
data class SliderBanner(
    /** The banner's id for its events (`banner_id`, a number in the answer). */
    val id: String,
    /** The slider's code, the same on every banner of it (`recommended_code`). */
    val code: String,
    val imageUrl: String?,
    /** The landing page. */
    val url: String?,
    val deeplinkIos: String?,
    val deeplinkAndroid: String?,
    /** The banner's ready markup. */
    val html: String?,
)
