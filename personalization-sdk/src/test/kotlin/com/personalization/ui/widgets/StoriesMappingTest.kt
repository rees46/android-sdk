package com.personalization.ui.widgets

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stories block data → kit components, on the shapes the stories endpoint returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesMappingTest {

    // --- Price (C10) ----------------------------------------------------------------------------

    @Test
    fun `a promo code beats the discount and crosses out the regular price`() {
        val price = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "discount_percent" to 10,
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.PROMO_CODE, price.type)
        assertEquals("900 ₽", price.price)
        assertEquals("1 000 ₽", price.oldPrice)
        assertEquals("PROMO", price.promoCode)
    }

    @Test
    fun `a promo code crosses out the old price when there is one`() {
        val price = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 1500, "oldprice_formatted" to "1 500 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 500 ₽", price.oldPrice)
    }

    @Test
    fun `under a promo code an old price counts only above the regular price`() {
        // 950 is above the promo price but below the regular one: the regular price is crossed out.
        val belowRegular = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 950, "oldprice_formatted" to "950 ₽",
                "promocode" to "PROMO", "price_with_promocode" to 900, "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 000 ₽", belowRegular.oldPrice)
        // An old price equal to the regular one is no markdown either.
        val equalToRegular = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "oldprice" to 1000, "oldprice_formatted" to "1 000.00 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "900 ₽"
            )
        )
        assertEquals("1 000 ₽", equalToRegular.oldPrice)
    }

    @Test
    fun `under a promo code the regular price is crossed out only when it is above the promo price`() {
        val same = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "promocode" to "PROMO", "price_with_promocode" to "1000.00", "price_with_promocode_formatted" to "1 000 ₽"
            )
        )
        assertNull(same.oldPrice)
        val higherPromo = StoriesMapping.price(
            product(
                "price" to 1000, "price_formatted" to "1 000 ₽",
                "promocode" to "PROMO", "price_with_promocode_formatted" to "1 100 ₽"
            )
        )
        assertNull(higherPromo.oldPrice)
    }

    @Test
    fun `a promo code without a promo price shows the regular price and crosses nothing`() {
        val price = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "promocode" to "PROMO")
        )
        assertEquals("1 000 ₽", price.price)
        assertNull(price.oldPrice)
    }

    @Test
    fun `a discount percent shows the discount block with the old price`() {
        val price = StoriesMapping.price(
            product(
                "price" to 19990, "price_formatted" to "19 990 ₽",
                "oldprice" to 27990, "oldprice_formatted" to "27 990 ₽",
                "discount_percent" to 28
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.DISCOUNT, price.type)
        assertEquals("28%", price.discount)
        assertEquals("27 990 ₽", price.oldPrice)
    }

    @Test
    fun `a fractional discount keeps its fraction`() {
        val price = StoriesMapping.price(product("price" to 100, "price_formatted" to "100 ₽", "discount_percent" to 12.5))
        assertEquals("12.5%", price.discount)
    }

    @Test
    fun `an old price equal to the price is not shown`() {
        // The endpoint sends oldprice == price for goods without a markdown.
        val price = StoriesMapping.price(
            product(
                "price" to 14990, "price_formatted" to "14 990 ₽",
                "oldprice" to 14990, "oldprice_formatted" to "14 990 ₽"
            )
        )
        assertEquals(PersonalizationStoryPrice.Type.PRICE, price.type)
        assertNull(price.oldPrice)
    }

    @Test
    fun `an old price lower than the price is not shown, compared as decimals`() {
        val lower = StoriesMapping.price(
            product(
                "price" to 99.99, "price_formatted" to "99,99 €",
                "oldprice" to 99.5, "oldprice_formatted" to "99,50 €"
            )
        )
        assertNull(lower.oldPrice)
        // 99.99 > 99.5 only as a decimal: as Ints both are 99.
        val higher = StoriesMapping.price(
            product(
                "price" to 99.5, "price_formatted" to "99,50 €",
                "oldprice" to 99.99, "oldprice_formatted" to "99,99 €"
            )
        )
        assertEquals("99,99 €", higher.oldPrice)
    }

    @Test
    fun `formatted prices parse as decimals`() {
        assertEquals(BigDecimal("1190900"), Decimals.parse("1 190 900 ₽"))
        assertEquals(BigDecimal("19.99"), Decimals.parse("19,99 €"))
        assertEquals(BigDecimal("1190.50"), Decimals.parse("$1,190.50"))
        assertEquals(BigDecimal("1190"), Decimals.parse("1,190 ₸"))
        assertNull(Decimals.parse("₽"))
        assertNull(Decimals.parse(""))
    }

    @Test
    fun `a carousel product gets a signed discount only with a real markdown`() {
        val card = StoriesMapping.cardProduct(
            product(
                "id" to "1", "name" to "Mouse", "price" to 100, "price_formatted" to "100 ₽",
                "oldprice" to 150, "oldprice_formatted" to "150 ₽", "discount" to 33
            )
        )
        assertEquals("-33%", card.discount)
        assertEquals("150 ₽", card.oldPrice)

        val same = StoriesMapping.cardProduct(
            product("price" to 100, "price_formatted" to "100 ₽", "oldprice" to 100, "oldprice_formatted" to "100 ₽")
        )
        assertNull(same.oldPrice)
    }

    // --- Seen, start slide and ring (A3, A4, A5) ------------------------------------------------

    @Test
    fun `a story is viewed when the server says so or every slide is seen`() {
        val story = story(slides = 2)
        assertFalse(StoriesMapping.isViewed(story, setOf("s0")))
        assertTrue(StoriesMapping.isViewed(story, setOf("s0", "s1")))
        assertTrue(StoriesMapping.isViewed(story(viewed = true), emptySet()))
    }

    @Test
    fun `the start slide is the first unseen, the server position, or the start`() {
        assertEquals(2, StoriesMapping.startSlide(story(startPosition = 2, slides = 5), emptySet()))
        assertEquals(1, StoriesMapping.startSlide(story(startPosition = 3, slides = 5), setOf("s0", "s2")))
        assertEquals(0, StoriesMapping.startSlide(story(viewed = true, startPosition = 2, slides = 5), emptySet()))
        assertEquals(0, StoriesMapping.startSlide(story(slides = 2), setOf("s0", "s1")))
        assertEquals(4, StoriesMapping.startSlide(story(startPosition = 9, slides = 5), emptySet()))
    }

    @Test
    fun `the ring lights seen slides by index`() {
        val item = StoriesMapping.item(story(startPosition = 2, slides = 5), setOf("s1", "s3"))
        assertEquals(PersonalizationStoryCover.State.DEFAULT, item.state)
        assertEquals(5, item.segments)
        assertEquals(setOf(1, 3), item.seenSegments)
    }

    @Test
    fun `a viewed story has a plain ring, a pinned one stays pinned`() {
        val viewed = StoriesMapping.item(story(slides = 3), setOf("s0", "s1", "s2"))
        assertEquals(PersonalizationStoryCover.State.VIEWED, viewed.state)
        assertEquals(1, viewed.segments)
        assertTrue(viewed.seenSegments.isEmpty())

        val pinnedViewed = StoriesMapping.item(story(pinned = true, viewed = true, slides = 3), emptySet())
        assertEquals(PersonalizationStoryCover.State.PINNED, pinnedViewed.state)
        assertEquals(1, pinnedViewed.segments)
        assertTrue(pinnedViewed.seenSegments.isEmpty())

        val pinnedPartly = StoriesMapping.item(story(pinned = true, slides = 3), setOf("s2"))
        assertEquals(PersonalizationStoryCover.State.PINNED, pinnedPartly.state)
        assertEquals(setOf(2), pinnedPartly.seenSegments)
    }

    @Test
    fun `seen slides persist per shop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("stories_seen_test", Context.MODE_PRIVATE)
        StoriesSeenStore(preferences).markSeen(7, "70")
        assertEquals(setOf("70"), StoriesSeenStore(preferences).seen(7))
        assertTrue(StoriesSeenStore(preferences).seen(8).isEmpty())

        val shopA = StoriesSeenStore.forShop(context, "shop-a")
        shopA.markSeen(1, "10")
        assertTrue(StoriesSeenStore.forShop(context, "shop-b").seen(1).isEmpty())
        assertEquals(setOf("10"), StoriesSeenStore.forShop(context, "shop-a").seen(1))
    }

    // --- Tracking, duration (A6, A7) ------------------------------------------------------------

    @Test
    fun `only positive integer ids are tracked`() {
        assertTrue(StoriesMapping.isTrackable(40, "36"))
        assertFalse(StoriesMapping.isTrackable(39, "39_0"))
        assertFalse(StoriesMapping.isTrackable(39, "0"))
        assertFalse(StoriesMapping.isTrackable(39, "-3"))
        assertFalse(StoriesMapping.isTrackable(39, "036"))
        assertFalse(StoriesMapping.isTrackable(0, "36"))
    }

    @Test
    fun `a slide without a duration runs 10 seconds`() {
        assertEquals(10_000L, StoriesMapping.durationMs(slide()))
        assertEquals(10_000L, StoriesMapping.durationMs(slide("duration" to 0)))
        assertEquals(6_000L, StoriesMapping.durationMs(slide("duration" to 6)))
    }

    // --- Slide look (C1, C3) --------------------------------------------------------------------

    @Test
    fun `only a bare media slide gets the strong scrim`() {
        val photo = slide("type" to "image", "background" to "https://x/1.jpg")
        assertEquals(StoriesMapping.SCRIM_STRONG, StoriesMapping.scrimAlpha(photo))
        val withText = slide(
            "type" to "image", "background" to "https://x/1.jpg",
            "elements" to JSONArray().put(JSONObject().put("type", "text_block").put("text_input", "Hi"))
        )
        assertEquals(StoriesMapping.SCRIM_LIGHT, StoriesMapping.scrimAlpha(withText))
        val blankText = slide(
            "type" to "image", "background" to "https://x/1.jpg",
            "elements" to JSONArray().put(JSONObject().put("type", "text_block").put("text_input", "  "))
        )
        assertEquals(StoriesMapping.SCRIM_STRONG, StoriesMapping.scrimAlpha(blankText))
        assertEquals(StoriesMapping.SCRIM_LIGHT, StoriesMapping.scrimAlpha(slide()))
    }

    @Test
    fun `the letterbox takes the slide colour, black without it`() {
        assertEquals(Color.parseColor("#112233"), StoriesMapping.letterboxColor(slide("background_color" to "#112233")))
        assertEquals(Color.BLACK, StoriesMapping.letterboxColor(slide("background_color" to "")))
    }

    // --- Links and captions (C9, C11, C12) ------------------------------------------------------

    @Test
    fun `a button opens its own link, without one the product`() {
        val product = product("url" to "https://shop/p/1", "deeplink_android" to "app://p/1")
        val own = ButtonElement(JSONObject().put("title", "Buy").put("link", "https://web").put("link_android", "app://own"))
        assertEquals("app://own", StoriesMapping.buttonLink(own, product))
        val bare = ButtonElement(JSONObject().put("title", "Buy"))
        assertEquals("app://p/1", StoriesMapping.buttonLink(bare, product))
        assertEquals("https://shop/p/2", StoriesMapping.buttonLink(bare, product("url" to "https://shop/p/2")))
        assertEquals("", StoriesMapping.buttonLink(bare, null))
    }

    @Test
    fun `the promo caption comes from the admin title, else the host text`() {
        val titled = ProductElement(JSONObject().put("title", "Ваш код"))
        assertEquals("Ваш код", StoriesMapping.promoCaption(titled, "Promo code"))
        val blank = ProductElement(JSONObject().put("title", " "))
        assertEquals("Promo code", StoriesMapping.promoCaption(blank, "Promo code"))
    }

    @Test
    fun `a text block promocode is read, a null one is not`() {
        assertEquals("SALE", StoriesMapping.textPromocode(TextBlockElement(JSONObject().put("promocode", " SALE "))))
        assertNull(StoriesMapping.textPromocode(TextBlockElement(JSONObject())))
        assertNull(StoriesMapping.textPromocode(TextBlockElement(JSONObject().put("promocode", JSONObject.NULL))))
    }

    // --- Text block promo on a product slide (H) -----------------------------------------------

    @Test
    fun `a product without a promo shows the text block promo at the regular price`() {
        val price = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "discount_percent" to 10),
            slidePromo = "SLIDE"
        )
        assertEquals(PersonalizationStoryPrice.Type.PROMO_CODE, price.type)
        assertEquals("1 000 ₽", price.price)
        assertNull(price.oldPrice)
        assertEquals("SLIDE", price.promoCode)
    }

    @Test
    fun `the product promo wins over the text block promo`() {
        val price = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "promocode" to "OWN", "price_with_promocode_formatted" to "900 ₽"),
            slidePromo = "SLIDE"
        )
        assertEquals("OWN", price.promoCode)
        assertEquals("900 ₽", price.price)
    }

    @Test
    fun `a text block promo crosses out only an old price above the regular price`() {
        val higher = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "oldprice" to 1500, "oldprice_formatted" to "1 500 ₽"),
            slidePromo = "SLIDE"
        )
        assertEquals("1 500 ₽", higher.oldPrice)
        val lower = StoriesMapping.price(
            product("price" to 1000, "price_formatted" to "1 000 ₽", "oldprice" to 900, "oldprice_formatted" to "900 ₽"),
            slidePromo = "SLIDE"
        )
        assertNull(lower.oldPrice)
    }

    @Test
    fun `the slide promo is the first text block with a code, blank and null ones are skipped`() {
        val blocks = JSONArray()
            .put(JSONObject().put("type", "text_block").put("text_input", "Hello"))
            .put(JSONObject().put("type", "text_block").put("text_input", "x").put("promocode", JSONObject.NULL))
            .put(JSONObject().put("type", "text_block").put("text_input", "y").put("promocode", " "))
            .put(JSONObject().put("type", "text_block").put("text_input", "Code A1").put("promocode", "A1"))
            .put(JSONObject().put("type", "text_block").put("text_input", "Code B2").put("promocode", "B2"))
        assertEquals("A1", StoriesMapping.slidePromocode(slide("elements" to blocks)))
        assertNull(StoriesMapping.slidePromocode(slide("elements" to JSONArray())))
    }

    // --- Text blocks (C15) ----------------------------------------------------------------------

    @Test
    fun `a text block background takes its opacity, zero means no background`() {
        val half = StoriesMapping.textBlockColor(textBlock("#0087E8", "50%"))!!
        assertEquals(127, Color.alpha(half))
        assertEquals(0x87, Color.green(half))
        assertNull(StoriesMapping.textBlockColor(textBlock("#0087E8", "0%")))
        assertNull(StoriesMapping.textBlockColor(textBlock("not a colour", "100%")))
    }

    @Test
    fun `on api v3 the opacity of a text block is a percent, as calculateOpacity of the web SDK`() {
        assertEquals(40f, StoriesMapping.opacityPercent("40%"))
        assertEquals(40f, StoriesMapping.opacityPercent(" 40 "))
        assertEquals("a number is a percent: 0.5 is half a percent", 0.5f, StoriesMapping.opacityPercent("0.5"))
        assertEquals(1f, StoriesMapping.opacityPercent("1"))
        assertEquals(12.5f, StoriesMapping.opacityPercent("12.5%"))
        assertEquals(0f, StoriesMapping.opacityPercent("0%"))
        assertNull("out of 0…100", StoriesMapping.opacityPercent("150"))
        assertNull(StoriesMapping.opacityPercent("-10"))
        assertNull(StoriesMapping.opacityPercent("half"))
        assertNull(StoriesMapping.opacityPercent(null))

        fun color(vararg pairs: Pair<String, Any>) = StoriesMapping.textBlockColorV3(TextBlockElement(JSONObject().apply { pairs.forEach { put(it.first, it.second) } }))
        assertEquals(0x80FF5733.toInt(), color("text_background_color" to "#ff5733", "text_background_color_opacity" to "50"))
        assertEquals(0xBFFF5533.toInt(), color("text_background_color" to "#f53", "text_background_color_opacity" to "75%"))
        assertEquals(0x80FF5733.toInt(), color("text_background_color" to "rgb(255, 87, 51)", "text_background_color_opacity" to 50))
        assertEquals(0x01000000, color("text_background_color" to "#000000", "text_background_color_opacity" to 0.4))
        assertEquals("no opacity — the colour is opaque", 0xFFFF5733.toInt(), color("text_background_color" to "#ff5733"))
        assertEquals(0xFFFF5733.toInt(), color("text_background_color" to "#ff5733", "text_background_color_opacity" to ""))
        assertEquals("an invalid opacity leaves the colour as it is", 0xFFFF5733.toInt(), color("text_background_color" to "#ff5733", "text_background_color_opacity" to "110"))
        assertEquals(0xFFFF5733.toInt(), color("text_background_color" to "#ff5733", "text_background_color_opacity" to "abc"))
        assertNull("0 % — no background", color("text_background_color" to "#000000", "text_background_color_opacity" to "0%"))
        assertNull(color("text_background_color" to "#000000", "text_background_color_opacity" to "0"))
        // As `!opacity` of calculateOpacity: the number 0 and false are no opacity — the colour is opaque.
        assertEquals(0xFF000000.toInt(), color("text_background_color" to "#000000", "text_background_color_opacity" to 0))
        assertEquals(0xFF000000.toInt(), color("text_background_color" to "#000000", "text_background_color_opacity" to 0.0))
        assertEquals(0xFF000000.toInt(), color("text_background_color" to "#000000", "text_background_color_opacity" to false))
        assertNull("no colour — none, the legacy #FFFFFF is not applied", color("text_background_color_opacity" to "50%"))
        assertNull(color())
        assertNull(color("text_background_color" to "white", "text_background_color_opacity" to "50%"))
        // A colour with its own alpha: a valid opacity replaces it, without one the colour keeps it.
        assertEquals(0x80FF5733.toInt(), color("text_background_color" to "#FF573340", "text_background_color_opacity" to "50%"))
        assertEquals(0xFFFF5733.toInt(), color("text_background_color" to "rgba(255, 87, 51, 0.25)", "text_background_color_opacity" to 100))
        assertEquals(0x40FF5733, color("text_background_color" to "#FF573340"))
        assertEquals(0x40FF5733, color("text_background_color" to "#FF573340", "text_background_color_opacity" to 0))
        assertEquals(0x40FF5733, color("text_background_color" to "#FF573340", "text_background_color_opacity" to "150"))
    }

    @Test
    fun `on api v3 bold and italic accept the legacy keys, the font size is the feed's own`() {
        fun element(json: String) = TextBlockElement(JSONObject(json))
        assertTrue(element("""{"text_bold":true}""").boldV3)
        assertFalse("bold wins", element("""{"bold":false,"text_bold":true}""").boldV3)
        assertTrue(element("""{"italic":"true"}""").italicV3)
        assertTrue(element("""{"text_italic":1}""").italicV3)
        assertFalse(element("""{}""").italicV3)
        assertEquals(24f, element("""{"font_size":"24"}""").ownFontSize)
        assertNull(element("""{"font_size":0}""").ownFontSize)
        assertNull(element("""{}""").ownFontSize)
        // The old fields stay as they were.
        assertFalse(element("""{"text_bold":true}""").isBold)
        assertEquals(14, element("""{}""").fontSize)
    }

    @Test
    fun `on api v3 a slide is media by its type and painted with its own background_color`() {
        val card = 0xFFFFFFFF.toInt()
        val image = slide("type" to "image", "background" to "https://cdn.test/a.jpg")
        val typeless = slide("type" to "", "background" to "https://cdn.test/a.jpg")
        assertTrue(StoriesMapping.isMedia(image, true))
        assertFalse(StoriesMapping.isMedia(typeless, true))
        assertTrue(StoriesMapping.isMedia(typeless, false))
        assertTrue(StoriesMapping.isVideo(slide("type" to "video"), true))
        assertFalse(StoriesMapping.isVideo(slide("type" to "video"), false))
        assertEquals(Color.BLACK, StoriesMapping.slideColor(image, true, card))
        assertEquals(card, StoriesMapping.slideColor(typeless, true, card))
        assertEquals(Color.RED, StoriesMapping.slideColor(slide("type" to "", "background_color" to "#FF0000"), true, card))
        assertNull(StoriesMapping.ownColor(slide("type" to "")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(StoriesMapping.isDark(context, slide("type" to "", "background_color" to "#202020"), true))
        assertFalse(StoriesMapping.isDark(context, slide("type" to "", "background_color" to "#202020"), false))
    }

    @Test
    fun `on api v3 a coloured slide takes the kit's light or dark text, whichever contrasts more`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun dark(color: String) = StoriesMapping.isDark(context, slide("type" to "", "background_color" to color), true)
        // Mid greys were dark by luminance 0.5; by WCAG contrast the dark text reads better on them.
        assertFalse(dark("#808080"))
        assertFalse(dark("#B0B0B0"))
        assertFalse(dark("#FFFFFF"))
        assertFalse(dark("#FFEB3B"))
        assertTrue(dark("#D32F2F"))
        assertTrue(dark("#202020"))
        assertTrue("half transparent white over the black window", dark("rgba(255, 255, 255, 0.2)"))
        assertTrue(StoriesMapping.prefersLightText(Color.BLACK, Color.WHITE, Color.BLACK))
        assertFalse(StoriesMapping.prefersLightText(Color.WHITE, Color.WHITE, Color.BLACK))
        // A light colour keeps the light tokens even in a dark host theme; no colour — the host theme.
        val light = StoriesMapping.slideContext(StorySupportTestContexts.night(context), slide("type" to "", "background_color" to "#FFFFFF"), true)
        assertEquals(Color.BLACK, com.personalization.ui.PersonalizationTheme.color(light, com.personalization.R.color.personalization_text_primary))
        val host = StorySupportTestContexts.night(context)
        assertTrue(StoriesMapping.slideContext(host, slide("type" to ""), true) === host)
    }

    @Test
    fun `on api v3 colours are parsed by one rule - #RGB, #RRGGBB, #RRGGBBAA, rgb and rgba`() {
        assertEquals(0xFFFF5533.toInt(), StoriesColor.parse("#f53"))
        assertEquals(0xFFFF5733.toInt(), StoriesColor.parse(" #FF5733 "))
        assertEquals("the alpha comes last, as in CSS", 0x80FF5733.toInt(), StoriesColor.parse("#FF573380"))
        assertEquals(0xFFFF5733.toInt(), StoriesColor.parse("rgb(255, 87, 51)"))
        assertEquals(Color.argb(128, 255, 87, 51), StoriesColor.parse("rgba(255,87,51,0.5)"))
        assertEquals(Color.argb(128, 255, 0, 0), StoriesColor.parse("rgb(100% 0% 0% / 50%)"))
        assertEquals("out of range is clamped", Color.argb(255, 255, 0, 0), StoriesColor.parse("rgba(300, -5, 0, 2)"))
        listOf(null, "", "red", "#12", "#12345", "#1234", "rgb(1, 2)", "rgb(a, b, c)", "hsl(0, 0%, 0%)", "transparent").forEach {
            assertNull(it, StoriesColor.parse(it))
        }
    }

    @Test
    fun `on api v3 the cover follows viewed of the feed, without slide marks`() {
        val story = story(slides = 3)
        assertEquals(PersonalizationStoryCover.State.DEFAULT, StoriesMapping.itemV3(story).state)
        assertEquals(3, StoriesMapping.itemV3(story).segments)
        assertTrue(StoriesMapping.itemV3(story).seenSegments.isEmpty())
        story.isViewed = true
        assertEquals(PersonalizationStoryCover.State.VIEWED, StoriesMapping.itemV3(story).state)
        assertEquals(1, StoriesMapping.itemV3(story).segments)
    }

    @Test
    fun `text alignment maps start, centre and end`() {
        fun align(value: String) = StoriesMapping.textAlign(TextBlockElement(JSONObject().put("text_align", value)))
        assertEquals(PersonalizationStoryTextBlock.Align.START, align("left"))
        assertEquals(PersonalizationStoryTextBlock.Align.START, align("start"))
        assertEquals(PersonalizationStoryTextBlock.Align.CENTER, align("center"))
        assertEquals(PersonalizationStoryTextBlock.Align.END, align("right"))
        assertEquals(PersonalizationStoryTextBlock.Align.END, align("end"))
    }

    @Test
    fun `a missing line spacing falls back to the preset of that size`() {
        assertNull(StoriesMapping.lineHeightSp(TextBlockElement(JSONObject().put("font_size", 20))))
        assertEquals(30f, StoriesMapping.lineHeightSp(TextBlockElement(JSONObject().put("font_size", 20).put("text_line_spacing", 1.5)))!!, 0.01f)
        assertEquals(48f, PersonalizationStoryTextBlock.Size.lineHeightFor(48f), 0.01f)
        assertEquals(36f, PersonalizationStoryTextBlock.Size.lineHeightFor(30f), 0.01f)
        assertEquals(24f, PersonalizationStoryTextBlock.Size.lineHeightFor(20f), 0.01f)
        // round(size × ratio): 14 × 1.2 = 16.8 → 17.
        assertEquals(17f, PersonalizationStoryTextBlock.Size.lineHeightFor(14f), 0.01f)
        // 39 is as far from Large (48) as from Medium (30): the tie goes to Large, ratio 1.0.
        assertEquals(39f, PersonalizationStoryTextBlock.Size.lineHeightFor(39f), 0.01f)
        assertEquals(PersonalizationStoryTextBlock.Size.LARGE, PersonalizationStoryTextBlock.Size.nearest(39f))
    }

    // --- Loading (C16) --------------------------------------------------------------------------

    @Test
    fun `stories without slides are dropped`() {
        val response = JSONObject().put(
            "stories",
            JSONArray()
                .put(storyJson(id = 1, slides = 2))
                .put(storyJson(id = 2, slides = 0))
        )
        assertEquals(listOf(1), StoriesLoader.parse(response).map { it.id })
        assertTrue(StoriesLoader.parse(null).isEmpty())
    }

    private fun product(vararg fields: Pair<String, Any>): Product =
        Product(JSONObject().apply { fields.forEach { (key, value) -> put(key, value) } })

    private fun slide(vararg fields: Pair<String, Any>): Slide =
        Slide(JSONObject().put("id", "1").apply { fields.forEach { (key, value) -> put(key, value) } })

    private fun story(
        pinned: Boolean = false,
        viewed: Boolean = false,
        startPosition: Int = 0,
        slides: Int = 3
    ): Story = Story(storyJson(1, slides, pinned, viewed, startPosition))

    private fun storyJson(
        id: Int,
        slides: Int,
        pinned: Boolean = false,
        viewed: Boolean = false,
        startPosition: Int = 0
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "Story")
        .put("pinned", pinned)
        .put("viewed", viewed)
        .put("start_position", startPosition)
        .put("slides", JSONArray().apply {
            repeat(slides) { put(JSONObject().put("id", "s$it").put("elements", JSONArray())) }
        })

    private fun textBlock(color: String, opacity: String) = TextBlockElement(
        JSONObject()
            .put("text_input", "Text")
            .put("text_background_color", color)
            .put("text_background_color_opacity", opacity)
    )
}

/** A night version of a context, as the host's dark theme gives it. */
private object StorySupportTestContexts {
    fun night(base: Context): Context {
        val configuration = android.content.res.Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return base.createConfigurationContext(configuration)
    }
}
