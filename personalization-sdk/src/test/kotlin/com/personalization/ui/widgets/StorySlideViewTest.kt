package com.personalization.ui.widgets

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.Insets
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationStoryCard
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** One slide's touch routing: the controls over an open products sheet, the product picture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StorySlideViewTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val host = FakeHost()

    // --- API v3: the slide as the web SDK draws it, in the kit's look ------------------------------

    @Test
    fun `on api v3 the media is decided by type and the slide is painted with its background_color`() {
        host.v3 = true
        val image = laidOut(Slide(JSONObject("""{"id":"1","type":"image","background":"https://cdn.test/a.jpg","background_color":"#FF0000"}""")))
        assertTrue(image.isMedia)
        assertEquals(0xFFFF0000.toInt(), (image.background as android.graphics.drawable.ColorDrawable).color)
        // A background without the type image or video is no media; the light colour keeps the kit's text.
        val plain = laidOut(Slide(JSONObject("""{"id":"2","type":"","background":"https://cdn.test/a.jpg","background_color":"#FFF4E0"}""")))
        assertFalse(plain.isMedia)
        assertFalse(plain.isDark)
        assertEquals(0xFFFFF4E0.toInt(), (plain.background as android.graphics.drawable.ColorDrawable).color)
        assertTrue(host.slideImages.none { it.endsWith("a.jpg") && plain.isMedia })
        // A dark colour paints the slide in the dark tokens.
        assertTrue(laidOut(Slide(JSONObject("""{"id":"3","type":"","background_color":"#101010"}"""))).isDark)
        // No background_color: the kit's card colour; a video is media even before its address is known.
        val card = laidOut(Slide(JSONObject("""{"id":"4","type":""}""")))
        assertEquals(com.personalization.ui.PersonalizationTheme.color(activity, com.personalization.R.color.personalization_background_card), (card.background as android.graphics.drawable.ColorDrawable).color)
        assertTrue(laidOut(Slide(JSONObject("""{"id":"5","type":"video","background":""}"""))).isVideo)

        // The old feed keeps its rule: a background is media, the card colour without one.
        host.v3 = false
        assertTrue(laidOut(Slide(JSONObject("""{"id":"2","type":"","background":"https://cdn.test/a.jpg"}"""))).isMedia)
    }

    @Test
    fun `on api v3 the header shows its icon, title and subtitle and is tapped as a whole`() {
        host.v3 = true
        val view = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"header","title":"Магазин","subtitle":"Новинки","icon":"https://cdn.test/i.png","link":"https://shop.test/new"}]}""")))
        val texts = view.all().filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(texts.containsAll(listOf("Магазин", "Новинки")))
        assertTrue(host.images.contains("https://cdn.test/i.png"))
        val title = view.all().filterIsInstance<android.widget.TextView>().first { it.text == "Магазин" }
        val subtitle = view.all().filterIsInstance<android.widget.TextView>().first { it.text == "Новинки" }
        val density = view.resources.displayMetrics.density
        val icon = (title.parent.parent as ViewGroup).getChildAt(0)
        assertEquals("icon 40", Math.round(40 * density), icon.width)
        assertEquals("gap 12", Math.round(12 * density), (icon.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
        assertEquals("title body-small", 14f * view.resources.displayMetrics.scaledDensity, title.textSize, 0.01f)
        assertEquals("subtitle caption", 12f * view.resources.displayMetrics.scaledDensity, subtitle.textSize, 0.01f)
        assertTrue("under the navigation row", windowTop(title.parent.parent as View) >= windowTop(view.navigation) + view.navigation.height)
        (title.parent.parent as View).performClick()
        assertEquals(listOf("https://shop.test/new"), host.headers)

        host.v3 = false
        val old = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[{"type":"header","title":"Магазин"}]}""")))
        assertTrue("the old feed draws no header", old.all().filterIsInstance<android.widget.TextView>().none { it.text == "Магазин" })
    }

    @Test
    fun `on api v3 a product slide without its own button gets no more info button`() {
        val slide = """{"id":"39_0","type":"","elements":[{"type":"product","item":{"id":"p1","name":"Sneakers","price_formatted":"100 ₽"}}]}"""
        assertTrue(laidOut(Slide(JSONObject(slide))).all().filterIsInstance<PersonalizationButton>().any { it.text == "More info" })
        host.v3 = true
        assertTrue(laidOut(Slide(JSONObject(slide))).all().filterIsInstance<PersonalizationButton>().none { it.text == "More info" })
    }

    @Test
    fun `on api v3 a text block takes the kit's body size, bold and italic from either key, any opacity`() {
        host.v3 = true
        fun block(json: String): PersonalizationStoryTextBlock =
            laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[{"type":"text_block","text_input":"Hi",$json}]}""")))
                .first(PersonalizationStoryTextBlock::class.java)!!
        val plain = block(""""font_size":null""")
        assertEquals(16f, plain.textSizeSp)
        assertFalse(plain.bold)
        assertNull(plain.blockColor)
        val legacy = block(""""text_bold":true,"text_italic":"1","font_size":"24","text_background_color":"#000000","text_background_color_opacity":0.5""")
        assertEquals(24f, legacy.textSizeSp)
        assertTrue(legacy.bold)
        assertTrue(legacy.italic)
        assertEquals("0.5 is half a percent, as on the web", 0x01000000, legacy.blockColor)
        val own = block(""""bold":false,"text_bold":true,"text_background_color":"#FFFFFF","text_background_color_opacity":40,"font_type":"Monospaced"""")
        assertFalse("bold wins over text_bold", own.bold)
        assertEquals(0x66FFFFFF, own.blockColor)
        assertEquals("monospaced", own.fontFamily)

        host.v3 = false
        assertEquals("the old feed keeps its 14", 14f, block(""""font_size":null""").textSizeSp)
    }

    @Test
    fun `on api v3 a video shows the sound button until it is known to have no audio, then the empty row is gone`() {
        host.v3 = true
        val view = laidOut(Slide(JSONObject("""{"id":"1","type":"video","background":"https://cdn.test/v.mp4"}""")))
        val used = view.first(com.personalization.ui.components.PersonalizationStoryUsedProducts::class.java)!!
        assertTrue("shown while the audio is not known", used.showSoundButton)
        view.setSoundAvailable(true)
        assertTrue(used.showSoundButton)
        view.setSoundAvailable(false)
        assertFalse(used.showSoundButton)
        assertFalse("a row with nothing in it takes no space", used.isShown)

        // With products the row stays for the products button.
        val withProducts = laidOut(Slide(JSONObject("""{"id":"2","type":"video","background":"https://cdn.test/v.mp4","elements":[
            {"type":"products","products":[{"id":"p1","name":"Sneakers","price":"100","url":"https://shop.test/p1"}]}]}""")))
        withProducts.setSoundAvailable(false)
        val row = withProducts.first(com.personalization.ui.components.PersonalizationStoryUsedProducts::class.java)!!
        assertFalse(row.showSoundButton)
        assertTrue(row.isShown)

        host.v3 = false
        val legacy = laidOut(Slide(JSONObject("""{"id":"1","type":"video","background":"https://cdn.test/v.mp4"}""")))
        legacy.setSoundAvailable(false)
        assertTrue(legacy.first(com.personalization.ui.components.PersonalizationStoryUsedProducts::class.java)!!.showSoundButton)
    }

    @Test
    fun `talkback reads the share and the close first, then the header, the text and the buttons`() {
        host.v3 = true
        val view = laidOut(Slide(JSONObject("""{"id":"1","type":"","elements":[
            {"type":"header","title":"Магазин","link":"https://shop.test/new"},
            {"type":"text_block","text_input":"Скидки","y_offset":10},
            {"type":"button","title":"Go","link":"https://shop.test/go"}]}""")), shareUrl = "https://shop.test/s")
        val nodes = ArrayList<View>()
        view.addChildrenForAccessibility(nodes)
        assertTrue(nodes.size > 3)
        assertEquals(setOf(view.navigation.shareButton, view.navigation.closeButton), nodes.take(2).toSet())
        assertTrue(nodes.drop(2).none { it === view.navigation.shareButton || it === view.navigation.closeButton })
    }

    @Test
    fun `the close cross still gets the touch while the sheet is open`() {
        host.sheetOpen = true
        val view = laidOut(slideWithProducts())

        assertFalse(view.onInterceptTouchEvent(down(pointIn(view.navigation.closeButton))))
    }

    @Test
    fun `share sits left of the cross only with a link and gets the touch over an open sheet`() {
        assertEquals(View.GONE, laidOut(slideWithProducts()).navigation.shareButton.visibility)

        host.sheetOpen = true
        val view = laidOut(slideWithProducts(), shareUrl = "https://shop.test/?r46_slide=1")
        val share = view.navigation.shareButton
        val close = view.navigation.closeButton
        assertEquals(View.VISIBLE, share.visibility)
        assertEquals("Share", share.contentDescription)
        assertEquals(close.width, share.width)
        assertEquals(close.left - share.right, (8 * activity.resources.displayMetrics.density).toInt())
        assertEquals(close.alpha, share.alpha)
        assertFalse(view.onInterceptTouchEvent(down(pointIn(share))))

        share.performClick()
        assertEquals(listOf("https://shop.test/?r46_slide=1"), host.shared)
    }

    @Test
    fun `a touch elsewhere outside the open sheet only closes the sheet`() {
        host.sheetOpen = true
        val view = laidOut(slideWithProducts())

        val middle = pointIn(view, 0.5f, 0.4f)
        assertTrue(view.onInterceptTouchEvent(down(middle)))
        host.sheetOpen = false
        assertFalse(view.onInterceptTouchEvent(down(middle)))
    }

    @Test
    fun `a tap on the product picture pages the story and does not open the product`() {
        val view = laidOut(productSlide())
        val card = view.first(PersonalizationStoryCard::class.java)!!
        val picture = card.imageView.parent as View
        assertFalse(picture.isClickable)

        // The right edge of the picture lies in the right third of the slide: a tap there pages forward.
        val slideAt = IntArray(2).also(view::getLocationOnScreen)
        val pictureAt = IntArray(2).also(picture::getLocationOnScreen)
        val x = (pictureAt[0] - slideAt[0] + picture.width - 2).toFloat()
        val y = (pictureAt[1] - slideAt[1] + picture.height / 2).toFloat()
        assertTrue("the picture must reach the right third", x > view.width * 2 / 3f)
        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))

        assertEquals(1, host.taps.size)
        assertEquals(StoriesViewerState.Zone.NEXT, StoriesViewerState.zone(host.taps[0], view.width, false))
        assertTrue(host.products.isEmpty())
    }

    @Test
    fun `a product slide shows its text block promo in the card and draws no text block`() {
        val slide = Slide(
            JSONObject()
                .put("id", "4")
                .put(
                    "elements",
                    JSONArray()
                        .put(JSONObject().put("type", "product").put("item", JSONObject().put("id", "p1").put("name", "Sneakers").put("price_formatted", "100 ₽")))
                        .put(JSONObject().put("type", "text_block").put("text_input", "Your code SALE10").put("promocode", "SALE10"))
                )
        )
        val view = laidOut(slide)

        val card = view.first(PersonalizationStoryCard::class.java)!!
        assertEquals("SALE10", card.price.promoCode)
        assertEquals(PersonalizationStoryPrice.Type.PROMO_CODE, card.price.type)
        assertNull(view.first(PersonalizationStoryTextBlock::class.java))
    }

    @Test
    fun `a text block stands at y_offset of the whole slide, inside the area between the header and the buttons`() {
        fun laidOutBlock(offset: Any): Pair<StorySlideView, PersonalizationStoryTextBlock> {
            val slide = Slide(
                JSONObject()
                    .put("id", "5")
                    .put(
                        "elements",
                        JSONArray()
                            .put(JSONObject().put("type", "text_block").put("text_input", "Text").put("y_offset", offset))
                            .put(JSONObject().put("type", "button").put("title", "Buy"))
                    )
            )
            val view = laidOut(slide)
            return view to view.first(PersonalizationStoryTextBlock::class.java)!!
        }

        // null — the top of the area, right under the header.
        val (view, top) = laidOutBlock(JSONObject.NULL)
        val area = top.parent as View
        val areaTop = windowTop(area) - windowTop(view)
        assertTrue("the area starts under the header", areaTop > 0)
        assertEquals(0, top.top)
        // 5 is under the header — the block moves down to the top of the area.
        assertEquals(0, laidOutBlock(5).second.top)
        // 50 is half of the whole slide, not of the area.
        val (half, middle) = laidOutBlock(50)
        assertTrue("${middle.top}", Math.abs(half.height / 2 - areaTop - middle.top) <= 1)
        // 95 — the block stays whole at the bottom of the area, above the buttons.
        val (low, bottom) = laidOutBlock(95)
        assertEquals((bottom.parent as View).height, bottom.bottom)
        val button = low.all().filterIsInstance<PersonalizationButton>().single { it.text?.toString() == "Buy" }
        assertTrue(windowTop(bottom) + bottom.height <= windowTop(button))
    }

    @Test
    fun `the placement reads y_offset as the poll does, null is 0, the block stays inside the area`() {
        assertEquals(
            listOf(null, null, null, 0f, 100f, 40f, 12.5f),
            listOf(null, JSONObject.NULL, "abc", -10, 150, "40", 12.5).map(StorySlidePlacement::offset)
        )
        val block = TextBlockElement(JSONObject().put("y_offset", "12.5"))
        assertEquals(12.5f, block.offset)
        assertEquals("V1 keeps its whole percents", 12, block.yOffset)

        // A slide 1000 high, the area 100…800, a block 50 high.
        assertEquals("null — the top of the area", 0, StorySlidePlacement.top(null, 1000, 100, 700, 50))
        assertEquals("under the header — the top of the area", 0, StorySlidePlacement.top(5f, 1000, 100, 700, 50))
        assertEquals("of the whole slide", 400, StorySlidePlacement.top(50f, 1000, 100, 700, 50))
        assertEquals("the bottom of the area", 650, StorySlidePlacement.top(95f, 1000, 100, 700, 50))
        assertEquals("taller than the area — its top", 0, StorySlidePlacement.top(50f, 1000, 100, 700, 900))
    }

    @Test
    fun `only the slide background may play a gif, the video poster stays on the first frame`() {
        StorySlideView(activity, 0, 0, mediaSlide("image", "https://example.com/a.gif"), 1, host)
        StorySlideView(activity, 0, 0, mediaSlide("video", "https://example.com/v.mp4", "https://example.com/p.gif"), 1, host)

        assertEquals(listOf("https://example.com/a.gif"), host.slideImages)
        assertEquals(listOf("https://example.com/p.gif"), host.images)
    }

    private fun mediaSlide(type: String, background: String, preview: String = ""): Slide = Slide(
        JSONObject()
            .put("id", "3")
            .put("type", type)
            .put("background", background)
            .put("preview", preview)
            .put("elements", JSONArray())
    )

    /** On screen: an unattached view has no location, and the slide hit-tests by location. */
    private fun laidOut(slide: Slide, shareUrl: String? = null): StorySlideView =
        StorySlideView(activity, 0, 0, slide, 1, host, shareUrl).also { view ->
            activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(view.isAttachedToWindow && view.width > 0)
        }

    /** A screen point at the fractions of [view]'s size — raw coordinates, as the slide reads them. */
    private fun pointIn(view: View, fx: Float = 0.5f, fy: Float = 0.5f): Pair<Float, Float> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0] + view.width * fx to location[1] + view.height * fy
    }

    private fun down(point: Pair<Float, Float>): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point.first, point.second, 0)
    }

    private fun slideWithProducts(): Slide = Slide(
        JSONObject()
            .put("id", "1")
            .put(
                "elements",
                JSONArray().put(
                    JSONObject()
                        .put("type", "products")
                        .put("products", JSONArray().put(JSONObject().put("id", "p1").put("name", "Sneakers")))
                )
            )
    )

    private fun productSlide(): Slide = Slide(
        JSONObject()
            .put("id", "2")
            .put(
                "elements",
                JSONArray().put(
                    JSONObject()
                        .put("type", "product")
                        .put("item", JSONObject().put("id", "p1").put("name", "Sneakers").put("price_formatted", "100 ₽"))
                )
            )
    )

    private fun windowTop(view: View): Int = IntArray(2).also(view::getLocationInWindow)[1]

    private fun View.all(): List<View> =
        if (this !is ViewGroup) listOf(this) else listOf(this) + (0 until childCount).flatMap { getChildAt(it).all() }

    private fun <T : View> View.first(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) getChildAt(index).first(type)?.let { return it }
        return null
    }

    private class FakeHost : StorySlideHost {
        override val texts = StoriesTexts("Promo code", "Copied", "More info", "Used products", "Hide products", "Retry", "Close", "Sound on", "Sound off", "Share")
        override var v3 = false
        val headers = mutableListOf<String>()

        override fun onHeader(header: HeaderElement) {
            headers += header.link
        }
        override val muted = true
        override var sheetOpen = false
        override val insets: Insets = Insets.NONE
        val products = mutableListOf<Product>()

        val images = mutableListOf<String>()
        val slideImages = mutableListOf<String>()

        override fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)?, onFailed: (() -> Unit)?) {
            images += url
        }

        override fun loadSlideImage(
            view: ImageView,
            url: String,
            onAnimation: (Long?, GifDrawable) -> Unit,
            onReady: () -> Unit,
            onFailed: () -> Unit
        ) {
            slideImages += url
        }
        val taps = mutableListOf<Float>()

        override fun onTap(x: Float, width: Int, rtl: Boolean) {
            taps += x
        }
        override fun onHold(hold: Boolean) = Unit
        override fun onOutsideSheetTap() = Unit
        override fun onSheet(open: Boolean) = Unit
        override fun onButton(button: ButtonElement, product: Product?) = Unit
        override fun onProduct(product: Product) {
            products += product
        }
        override fun onCarouselProduct(product: Product) = Unit
        override fun onPromoCopied() = Unit
        override fun onSoundToggle() = Unit
        override fun onRetry(view: StorySlideView) = Unit
        override fun onClose() = Unit
        val shared = mutableListOf<String>()

        override fun onShare(url: String) {
            shared += url
        }
    }
}
