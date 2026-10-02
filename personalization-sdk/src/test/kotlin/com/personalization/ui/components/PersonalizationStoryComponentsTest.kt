package com.personalization.ui.components

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stories V2 building blocks: price text, countdown, text block frame, promo copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationStoryComponentsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `the currency is split off a formatted price`() {
        assertEquals(PersonalizationStoryAmount("1 190 900", "₽"), PersonalizationStoryAmount.parse("1 190 900 ₽"))
        assertEquals(PersonalizationStoryAmount("19 990", "₸"), PersonalizationStoryAmount.parse(" 19 990 ₸ "))
        // A trailing token with digits is not a currency.
        assertEquals(PersonalizationStoryAmount("1 190 900", null), PersonalizationStoryAmount.parse("1 190 900"))
        assertEquals(PersonalizationStoryAmount("$99", null), PersonalizationStoryAmount.parse("$99"))
    }

    @Test
    fun `the countdown is split into days, hours, minutes and seconds`() {
        val remaining = ((2 * 24 + 18) * 3600L + 33 * 60 + 24) * 1000
        assertEquals(PersonalizationStoryTimerParts(2, 18, 33, 24), PersonalizationStoryTimerParts.of(remaining))
        // The last second still shows 1, not 0.
        assertEquals(PersonalizationStoryTimerParts(0, 0, 0, 1), PersonalizationStoryTimerParts.of(400))
        assertEquals(PersonalizationStoryTimerParts(0, 0, 0, 0), PersonalizationStoryTimerParts.of(-5000))
    }

    @Test
    fun `the timer shows the remaining time and reports the end`() {
        var now = 1_000_000L
        var finished = 0
        val timer = PersonalizationStoryTimer(activity).apply {
            clock = { now }
            onFinished = { finished++ }
            endTimeMillis = now + 65_000
        }
        assertEquals("0 d", unitText(timer, 0))
        assertEquals("00 h", unitText(timer, 1))
        assertEquals("01 m", unitText(timer, 2))
        assertEquals("05 s", unitText(timer, 3))

        now += 70_000
        timer.endTimeMillis = timer.endTimeMillis
        assertEquals("00 s", unitText(timer, 3))
        assertEquals(1, finished)
    }

    @Test
    fun `a solid text block gets the kit frame, a transparent one none`() {
        val block = PersonalizationStoryTextBlock(activity).apply {
            size = PersonalizationStoryTextBlock.Size.LARGE
            blockColor = Color.BLUE
        }
        assertEquals(dp(16), block.paddingLeft)
        assertEquals(dp(12), block.paddingTop)

        // Medium (30) keeps the small padding: only text bigger than Medium gets 12.
        block.size = PersonalizationStoryTextBlock.Size.MEDIUM
        assertEquals(dp(8), block.paddingTop)
        block.textSizeSp = 36f
        assertEquals(dp(12), block.paddingTop)

        block.blockColor = null
        assertEquals(0, block.paddingLeft)
        assertNull(block.background)
    }

    @Test
    fun `the price shows only the block of its type`() {
        val price = PersonalizationStoryPrice(activity).apply {
            price = "1 190 900 ₽"
            oldPrice = "245 000 ₽"
            discount = "10%"
            promoCode = "PROMO"
        }
        val (discount, promo) = rightBlocks(price)

        price.type = PersonalizationStoryPrice.Type.PRICE
        assertEquals(View.GONE, discount.visibility)
        assertEquals(View.GONE, promo.visibility)

        price.type = PersonalizationStoryPrice.Type.DISCOUNT
        assertEquals(View.VISIBLE, discount.visibility)
        assertEquals(View.GONE, promo.visibility)

        price.type = PersonalizationStoryPrice.Type.PROMO_CODE
        assertEquals(View.GONE, discount.visibility)
        assertEquals(View.VISIBLE, promo.visibility)
    }

    @Test
    fun `tapping the promo code copies it and reports`() {
        val content: ViewGroup = activity.findViewById(android.R.id.content)
        var copied: String? = null
        val price = PersonalizationStoryPrice(activity).apply {
            type = PersonalizationStoryPrice.Type.PROMO_CODE
            this.price = "990 ₽"
            promoCode = "YAGODA-MALINA"
            onPromoCopied = { copied = it }
        }
        content.addView(price)

        rightBlocks(price).second.performClick()

        assertEquals("YAGODA-MALINA", copied)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("YAGODA-MALINA", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue((0 until content.childCount).any { content.getChildAt(it) is PersonalizationToast })
    }

    @Test
    fun `a pinned cover shows the star, others do not`() {
        val cover = PersonalizationStoryCover(activity)
        val star = cover.getChildAt(1)

        cover.state = PersonalizationStoryCover.State.PINNED
        assertEquals(View.VISIBLE, star.visibility)

        cover.state = PersonalizationStoryCover.State.VIEWED
        assertEquals(View.GONE, star.visibility)
    }

    @Test
    fun `the sound button names the current sound state and is selected while the sound is on`() {
        val used = PersonalizationStoryUsedProducts(activity).apply {
            showSoundButton = true
            muted = true
        }
        val sound = firstImage(used) { it.contentDescription == "Sound off" }!!
        assertFalse(sound.isSelected)

        used.muted = false
        assertEquals("Sound on", sound.contentDescription)
        assertTrue(sound.isSelected)
    }

    private fun firstImage(view: View, matches: (ImageView) -> Boolean): ImageView? {
        if (view is ImageView && matches(view)) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) firstImage(view.getChildAt(index), matches)?.let { return it }
        return null
    }

    private fun rightBlocks(price: PersonalizationStoryPrice): Pair<View, View> =
        price.getChildAt(1) to price.getChildAt(2)

    private fun unitText(timer: PersonalizationStoryTimer, index: Int): String =
        (timer.getChildAt(index) as android.widget.TextView).text.toString().replace(' ', ' ')

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
