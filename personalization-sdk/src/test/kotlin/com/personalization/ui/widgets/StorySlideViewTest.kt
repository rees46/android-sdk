package com.personalization.ui.widgets

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.Insets
import com.personalization.Product
import com.personalization.stories.models.Slide
import com.personalization.stories.models.elements.ButtonElement
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** One slide's touch routing: what reaches the controls while the products sheet is open. */
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

    private class FakeHost : StorySlideHost {
        override val texts = StoriesTexts("Promo code", "Copied", "More info", "Used products", "Hide products", "Retry", "Close", "Sound on", "Sound off")
        override val muted = true
        override var sheetOpen = false
        override val insets: Insets = Insets.NONE
        val products = mutableListOf<Product>()

        override fun loadImage(view: ImageView, url: String, onReady: (() -> Unit)?, onFailed: (() -> Unit)?) = Unit
        override fun onTap(x: Float, width: Int, rtl: Boolean) = Unit
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
