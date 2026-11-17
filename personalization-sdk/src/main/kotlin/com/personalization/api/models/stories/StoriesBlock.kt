package com.personalization.api.models.stories

/*
 * The stories block of `sdk.stories.get(code)` — the contract's model, the one the Flutter SDK already
 * reads. Its own package: `Story` and `Slide` of `com.personalization.stories.models` belong to the old
 * stories viewer. A string field the answer leaves out is an empty string, as in Flutter; only the fields
 * the contract marks as optional are null.
 */

/** A stories block (`GET /stories/{code}`): its id on the server and its stories, in the order of the bar. */
data class StoriesBlock(
    val id: String,
    val stories: List<Story>,
)

/** One story: a cover in the bar and the slides the viewer plays. */
data class Story(
    val id: String,
    val name: String,
    /** The cover's image URL. */
    val avatar: String,
    /** Seen on this device, as the server remembers it. */
    val viewed: Boolean = false,
    /** Pinned by the shop to the start of the bar. */
    val pinned: Boolean = false,
    /** The slide to start from: the first one not seen yet. */
    val startPosition: Int = 0,
    val slides: List<StorySlide>,
)

/** What a slide shows behind its elements. */
enum class StorySlideType {
    /** `image`: [StorySlide.background] is a picture. */
    IMAGE,

    /** `video`: [StorySlide.background] is a video, [StorySlide.preview] its poster. */
    VIDEO,

    /** Any other type (`""` in the answer): a product slide, whose product is a [StoryProductElement]. */
    PRODUCT,
}

/** One slide of a story. */
data class StorySlide(
    /** A number for a slide with media, a string like `39_0` for a product slide. */
    val id: String,
    val type: StorySlideType,
    /** The image or video URL; on a product slide, the one of the stories' settings. */
    val background: String,
    /** `#RRGGBB` behind the background; empty when not set. */
    val backgroundColor: String,
    /** A video's poster; empty when there is none. */
    val preview: String,
    /** Seconds on screen: [DEFAULT_DURATION] when the answer has none, or 0. */
    val duration: Int,
    val elements: List<StoryElement>,
) {
    companion object {
        /** Seconds a slide stays when the answer does not say: the same on every platform. */
        const val DEFAULT_DURATION: Int = 10
    }
}

/**
 * An element of a slide, by the answer's `type`. Elements of other types, `header` and the polls (behind
 * the API v3 gate) are left out.
 */
sealed interface StoryElement

/** `button`: a call to action with a link. */
data class StoryButtonElement(
    val title: String,
    val link: String,
    val linkAndroid: String,
    val linkIos: String,
    /** The old viewer's colours; the UI kit draws its own button. */
    val background: String,
    val color: String,
    /** `text_bold`, or `bold`. */
    val textBold: Boolean,
) : StoryElement

/** `product`: the product of a product slide; [item] is null when the answer has none. */
data class StoryProductElement(
    val title: String,
    val item: StoryProduct?,
) : StoryElement

/** `products`: the products of a slide behind a show / hide toggle. */
data class StoryProductsElement(
    /** The toggle's label while the products are hidden (`labels.show_carousel`). */
    val showLabel: String,
    /** The toggle's label while they are shown (`labels.hide_carousel`). */
    val hideLabel: String,
    val products: List<StoryProduct>,
) : StoryElement

/** `text_block`: a text over a slide. */
data class StoryTextBlockElement(
    /** `text_input`. */
    val text: String,
    /** [DEFAULT_FONT_SIZE] when not set. */
    val fontSize: Double,
    /** `serif`, `sans-serif`, `monospace`, or empty. */
    val fontType: String,
    /** `bold`, or `text_bold`. */
    val bold: Boolean,
    val italic: Boolean,
    /** `left`, `center`, `right`, or empty. */
    val textAlign: String,
    /** `#RRGGBB`, or empty. */
    val textColor: String,
    /** `#RRGGBB` behind the text (`text_background_color`), or empty. */
    val backgroundColor: String,
    /** 0 to 1, from `"50%"`, `"50"` or `0.5`; 0 — no frame behind the text. */
    val backgroundOpacity: Double,
    /** Line height as a multiple of the font size; 0 — the font's own. */
    val lineSpacing: Double,
    /** The top of the block, in percent of the slide's height. */
    val yOffset: Double,
    /** A code the block copies on a tap; null when it has none. */
    val promocode: String?,
) : StoryElement {
    companion object {
        const val DEFAULT_FONT_SIZE: Double = 14.0
    }
}

/** A product of a stories block: what the UI kit shows and links to. */
data class StoryProduct(
    val id: String,
    val name: String,
    val brand: String,
    /** The resized image: the one to show. */
    val picture: String,
    /** The original image (`image_url`). */
    val imageUrl: String,
    val price: Double,
    val priceFormatted: String,
    /** The price before the discount (`oldprice`); null when it is not above [price]. */
    val oldPrice: Double?,
    val oldPriceFormatted: String,
    /** `discount_percent`, 0 when the shop did not load it into its catalog. */
    val discountPercent: Double,
    val promocode: String,
    /** The price with [promocode] (`price_with_promocode`); 0 when not sent. */
    val priceWithPromocode: Double,
    val priceWithPromocodeFormatted: String,
    val currency: String,
    val url: String,
    val deeplinkAndroid: String,
    val deeplinkIos: String,
)
