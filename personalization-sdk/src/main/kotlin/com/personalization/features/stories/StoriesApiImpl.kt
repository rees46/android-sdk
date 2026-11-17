package com.personalization.features.stories

import com.personalization.api.managers.StoriesApi
import com.personalization.api.models.stories.StoriesBlock
import com.personalization.api.models.stories.Story
import com.personalization.api.models.stories.StoryButtonElement
import com.personalization.api.models.stories.StoryElement
import com.personalization.api.models.stories.StoryProduct
import com.personalization.api.models.stories.StoryProductElement
import com.personalization.api.models.stories.StoryProductsElement
import com.personalization.api.models.stories.StorySlide
import com.personalization.api.models.stories.StorySlideType
import com.personalization.api.models.stories.StoryTextBlockElement
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.pathSegment
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.valueOrNull
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [StoriesApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class StoriesApiImpl(private val calls: NamespaceCalls) : StoriesApi {

    override suspend fun get(code: String): StoriesBlock {
        calls.checked(GET) { if (code.isBlank()) invalidArgument("code is empty") }
        return calls.request(GET, NetworkMethod.GET(path(code)), read = ::readBlock)
    }

    override fun get(code: String, listener: CompletionListener<StoriesBlock>) = calls.complete(listener) { get(code) }

    internal companion object {
        const val GET = "stories.get"
        const val PATH = "stories/"

        /** `stories/{code}`, the code encoded as one segment of the path. */
        fun path(code: String): String = PATH + pathSegment(code)

        /*
         * Read as the Flutter SDK reads the same answer, and the answer of API v3 with it: numbers may come
         * as strings, ids as numbers; a field that is missing takes its default. Only an answer that is not
         * a JSON object is `decoding`.
         */

        fun readBlock(body: Any?): StoriesBlock? {
            if (body !is JSONObject) return null
            return StoriesBlock(
                id = body.stringOrNull("id").orEmpty(),
                stories = body.arrayOrNull("stories")?.mapObjects(::readStory).orEmpty(),
            )
        }

        private fun readStory(story: JSONObject) = Story(
            id = story.stringOrNull("id").orEmpty(),
            name = story.stringOrNull("name").orEmpty(),
            avatar = story.stringOrNull("avatar").orEmpty(),
            viewed = story.booleanOrNull("viewed") ?: false,
            pinned = story.booleanOrNull("pinned") ?: false,
            startPosition = story.intOrNull("start_position") ?: 0,
            slides = story.arrayOrNull("slides")?.mapObjects(::readSlide).orEmpty(),
        )

        private fun readSlide(slide: JSONObject) = StorySlide(
            id = slide.stringOrNull("id").orEmpty(),
            type = when (slide.stringOrNull("type")) {
                "image" -> StorySlideType.IMAGE
                "video" -> StorySlideType.VIDEO
                else -> StorySlideType.PRODUCT
            },
            background = slide.stringOrNull("background").orEmpty(),
            backgroundColor = slide.stringOrNull("background_color").orEmpty(),
            preview = slide.stringOrNull("preview").orEmpty(),
            duration = slide.intOrNull("duration")?.takeIf { it > 0 } ?: StorySlide.DEFAULT_DURATION,
            elements = slide.arrayOrNull("elements")?.mapObjects(::readElement).orEmpty(),
        )

        /** The element by its `type`; null — left out — for `header`, `poll` and the types the SDK does not know. */
        private fun readElement(element: JSONObject): StoryElement? = when (element.stringOrNull("type")) {
            "button" -> StoryButtonElement(
                title = element.text("title"),
                link = element.text("link"),
                linkAndroid = element.text("link_android"),
                linkIos = element.text("link_ios"),
                background = element.text("background"),
                color = element.text("color"),
                textBold = element.flag("text_bold") || element.flag("bold"),
            )
            "product" -> StoryProductElement(
                title = element.text("title"),
                item = element.objectOrNull("item")?.takeIf { it.length() > 0 }?.let(::readProduct),
            )
            "products" -> element.objectOrNull("labels").let { labels ->
                StoryProductsElement(
                    showLabel = labels?.text("show_carousel").orEmpty(),
                    hideLabel = labels?.text("hide_carousel").orEmpty(),
                    products = element.arrayOrNull("products")?.mapObjects(::readProduct).orEmpty(),
                )
            }
            "text_block" -> StoryTextBlockElement(
                text = element.text("text_input"),
                fontSize = element.doubleOrNull("font_size") ?: StoryTextBlockElement.DEFAULT_FONT_SIZE,
                fontType = element.text("font_type"),
                bold = element.flag("bold") || element.flag("text_bold"),
                italic = element.flag("italic"),
                textAlign = element.text("text_align"),
                textColor = element.text("text_color"),
                backgroundColor = element.text("text_background_color"),
                backgroundOpacity = opacity(element.valueOrNull("text_background_color_opacity")),
                lineSpacing = element.doubleOrNull("text_line_spacing") ?: 0.0,
                yOffset = element.doubleOrNull("y_offset") ?: 0.0,
                promocode = element.stringOrNull("promocode")?.takeIf { it.isNotEmpty() },
            )
            else -> null
        }

        private fun readProduct(product: JSONObject): StoryProduct {
            val price = product.doubleOrNull("price") ?: 0.0
            return StoryProduct(
                id = product.text("id"),
                name = product.text("name"),
                brand = product.text("brand"),
                picture = product.text("picture"),
                imageUrl = product.text("image_url"),
                price = price,
                priceFormatted = product.text("price_formatted"),
                oldPrice = product.doubleOrNull("oldprice")?.takeIf { it > price },
                oldPriceFormatted = product.text("oldprice_formatted"),
                discountPercent = product.doubleOrNull("discount_percent") ?: 0.0,
                promocode = product.text("promocode"),
                priceWithPromocode = product.doubleOrNull("price_with_promocode") ?: 0.0,
                priceWithPromocodeFormatted = product.text("price_with_promocode_formatted"),
                currency = product.text("currency"),
                url = product.text("url"),
                deeplinkAndroid = product.text("deeplink_android"),
                deeplinkIos = product.text("deeplink_ios"),
            )
        }

        /**
         * An opacity from 0 to 1. A string is percent, with the sign or without (`"50%"`, `"50"`), as the
         * dashboard sends it; a number above 1 is percent too, otherwise a fraction.
         */
        fun opacity(value: Any?): Double {
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace("%", "").trim().toDoubleOrNull()
                else -> null
            }
            if (number == null || !number.isFinite() || number <= 0) return 0.0
            val fraction = if (value is String || number > 1) number / 100 else number
            return fraction.coerceIn(0.0, 1.0)
        }

        private fun JSONObject.text(key: String): String = stringOrNull(key).orEmpty()

        private fun JSONObject.flag(key: String): Boolean = booleanOrNull(key) ?: false
    }
}
