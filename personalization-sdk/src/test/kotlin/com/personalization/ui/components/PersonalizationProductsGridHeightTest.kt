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
 * Neighbours in a grid row are as tall as the tallest card, with the price and the button at the
 * bottom: a rating, a discount or an old price moved to its own line must not shift the button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationProductsGridHeightTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `cards of a row share the height and the buttons line up`() {
        val grid = PersonalizationProductsGrid(context).apply {
            showRating = true
            products = listOf(
                PersonalizationProduct(id = "1", name = "Bag", price = "590 ₽", actionText = "Add to cart"),
                PersonalizationProduct(
                    id = "2", brand = "Bang & Olufsen", name = "TV", price = "799 990 ₽",
                    oldPrice = "1 574 990 ₽", discount = "-49%", ratingValue = "4,7", reviews = 12,
                    actionText = "Add to cart"
                )
            )
        }

        layOut(grid)

        val cards = cardsOf(grid)
        assertEquals(2, cards.size)
        assertEquals(cards[0].height, cards[1].height)
        assertEquals(bottomIn(cards[0], buttonOf(cards[0])), bottomIn(cards[1], buttonOf(cards[1])))
    }

    @Test
    fun `a next page with a taller card stretches the cards already shown`() {
        val short = (1..2).map { PersonalizationProduct(id = "$it", name = "Item $it", price = "$it ₽", actionText = "Add") }
        val grid = PersonalizationProductsGrid(context).apply {
            showRating = true
            products = short
        }
        layOut(grid)
        val before = cardsOf(grid).first().height

        grid.products = short + PersonalizationProduct(
            id = "3", name = "Item 3", price = "3 ₽", ratingValue = "4,1", reviews = 3, actionText = "Add"
        )
        layOut(grid)

        val cards = cardsOf(grid)
        assertTrue(cards.first().height > before)
        assertEquals(1, cards.map { it.height }.distinct().size)
    }

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, 720, view.measuredHeight)
    }

    private fun cardsOf(root: View): List<PersonalizationProductCard> = when (root) {
        is PersonalizationProductCard -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { cardsOf(root.getChildAt(it)) }
        else -> emptyList()
    }

    private fun buttonOf(card: View): PersonalizationButton =
        (card as ViewGroup).let { all(it) }.filterIsInstance<PersonalizationButton>().single()

    private fun all(root: View): List<View> = when (root) {
        is ViewGroup -> listOf(root) + (0 until root.childCount).flatMap { all(root.getChildAt(it)) }
        else -> listOf(root)
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
}
