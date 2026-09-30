package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A list row is as tall as the larger of its image and its text: a short 4:3 image must not cut
 * the button off, and a tall 3:4 image still pushes the price and button to its bottom edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationProductCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Robolectric has no real fonts: every text line comes out 35 px whatever its line height,
    // so the full card is taller than even a 3:4 image here. The cases where the image must set
    // the height use a card without brand and rating.

    @Test
    fun `a landscape image does not cut the button off`() {
        val card = listCard(PersonalizationProductImage.Aspect.LANDSCAPE, full = true)

        layOut(card)

        assertTrue(card.measuredHeight > imageOf(card).measuredHeight)
        assertEquals(card.measuredHeight, bottomIn(card, buttonOf(card)))
    }

    @Test
    fun `a portrait image sets the row height and the button sits at its bottom`() {
        val card = listCard(PersonalizationProductImage.Aspect.PORTRAIT, full = false)

        layOut(card)

        assertEquals(imageOf(card).measuredHeight, card.measuredHeight)
        assertEquals(card.measuredHeight, bottomIn(card, buttonOf(card)))
    }

    @Test
    fun `changing the aspect after the type follows the new image height`() {
        val card = listCard(PersonalizationProductImage.Aspect.LANDSCAPE, full = false)
        layOut(card)

        card.imageAspect = PersonalizationProductImage.Aspect.PORTRAIT
        layOut(card)

        assertEquals(imageOf(card).measuredHeight, card.measuredHeight)
        assertEquals(card.measuredHeight, bottomIn(card, buttonOf(card)))
    }

    private fun listCard(aspect: PersonalizationProductImage.Aspect, full: Boolean) =
        PersonalizationProductCard(context).apply {
            type = PersonalizationProductCard.Type.LIST
            imageAspect = aspect
            name = "Trail running jacket"
            price = "$175"
            actionText = "Add to cart"
            if (full) {
                brand = "Salomon"
                setRating("4.5", 77)
            }
        }

    /** Measured the way the kit's lists measure a row: exact width, height by content. */
    private fun layOut(card: View) {
        card.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    }

    private fun bottomIn(root: View, view: View): Int {
        var top = 0
        var current: View = view
        while (current !== root) {
            top += current.top
            current = current.parent as View
        }
        return top + view.height
    }

    private fun all(root: View): List<View> = when (root) {
        is ViewGroup -> listOf(root) + (0 until root.childCount).flatMap { all(root.getChildAt(it)) }
        else -> listOf(root)
    }

    private fun imageOf(card: View) = all(card).filterIsInstance<PersonalizationProductImage>().single()
    private fun buttonOf(card: View) = all(card).filterIsInstance<PersonalizationButton>().single()
}
