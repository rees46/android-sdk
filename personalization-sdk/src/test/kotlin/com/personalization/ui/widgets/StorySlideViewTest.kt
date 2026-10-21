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

    @Test
    fun `the close cross still gets the touch while the sheet is open`() {
        host.sheetOpen = true
        val view = laidOut(slideWithProducts())

        assertFalse(view.onInterceptTouchEvent(down(pointIn(view.navigation.closeButton))))
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
    private fun laidOut(slide: Slide): StorySlideView =
        StorySlideView(activity, 0, 0, slide, 1, host).also { view ->
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

    private fun <T : View> View.first(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) getChildAt(index).first(type)?.let { return it }
        return null
    }

    private class FakeHost : StorySlideHost {
        override val texts = StoriesTexts("Promo code", "Copied", "More info", "Used products", "Hide products", "Retry", "Close", "Sound on", "Sound off")
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
    }
}
