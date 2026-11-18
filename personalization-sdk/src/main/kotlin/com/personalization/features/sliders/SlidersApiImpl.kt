package com.personalization.features.sliders

import com.personalization.api.managers.SlidersApi
import com.personalization.api.models.sliders.Slider
import com.personalization.api.models.sliders.SliderBanner
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.putList
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [SlidersApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class SlidersApiImpl(private val calls: NamespaceCalls) : SlidersApi {

    override suspend fun get(code: String, tags: List<String>?, locationId: String?): Slider {
        val params = calls.checked(GET) {
            if (code.isBlank()) invalidArgument("code is empty")
            // Tags go joined by commas: a tag with one would read as two.
            if (tags?.any { it.isBlank() || it.contains(',') } == true) invalidArgument("tags has an empty tag or one with a comma")
            JSONObject().put(CODE, code).putList(TAGS, tags).putIfNotNull(LOCATION, locationId)
        }
        return calls.request(GET, NetworkMethod.GET(PATH), params) { body -> readSlider(body, code) }
    }

    override fun get(code: String, tags: List<String>?, locationId: String?, listener: CompletionListener<Slider>) =
        calls.complete(listener) { get(code, tags, locationId) }

    internal companion object {
        const val GET = "sliders.get"
        const val PATH = "slider"

        private const val CODE = "code"
        private const val TAGS = "tags"
        private const val LOCATION = "location"

        /**
         * `payload.properties` and `payload.slides`; null (`decoding`) without the list of banners. Without
         * its own code the slider and its banners take [asked], the code it was asked for. A banner without
         * an id, which its events need, is left out.
         */
        fun readSlider(body: Any?, asked: String): Slider? {
            val payload = (body as? JSONObject)?.objectOrNull("payload") ?: return null
            val slides = payload.arrayOrNull("slides") ?: return null
            val properties = payload.objectOrNull("properties")
            val code = properties?.nonBlankStringOrNull(CODE) ?: asked
            return Slider(
                code = code,
                width = properties?.intOrNull("width") ?: 0,
                height = properties?.intOrNull("height") ?: 0,
                banners = slides.mapObjects { banner -> readBanner(banner, code) },
            )
        }

        private fun readBanner(banner: JSONObject, sliderCode: String): SliderBanner? {
            val id = banner.nonBlankStringOrNull("banner_id") ?: return null
            return SliderBanner(
                id = id,
                code = banner.nonBlankStringOrNull("recommended_code") ?: sliderCode,
                imageUrl = banner.nonBlankStringOrNull("image_url"),
                url = banner.nonBlankStringOrNull("url"),
                deeplinkIos = banner.nonBlankStringOrNull("deeplink_ios"),
                deeplinkAndroid = banner.nonBlankStringOrNull("deeplink_android"),
                html = banner.nonBlankStringOrNull("html"),
            )
        }
    }
}
