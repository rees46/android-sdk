package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The catalogue is one scrolling list: header, cards and footer are its rows. Given a bounded
 * height it creates cards for what is on screen only, however many pages it holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationCatalogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a long catalogue creates cards for the screen, not for every product`() {
        val catalog = PersonalizationCatalog(context).apply { products = products(200) }

        layOut(catalog)

        val cards = descendants<PersonalizationProductCard>(catalog).size
        assertTrue("expected a screenful of cards, got $cards", cards in 1..30)
    }

    @Test
    fun `scrolling to the end reuses cards instead of adding them`() {
        val catalog = PersonalizationCatalog(context).apply { products = products(200) }
        layOut(catalog)
        val before = descendants<PersonalizationProductCard>(catalog).size

        repeat(40) { catalog.scrollBy(0, 1500) }
        catalog.scrollToPosition(catalog.adapter!!.itemCount - 1)
        layOut(catalog)

        assertTrue(descendants<PersonalizationProductCard>(catalog).size <= before + 4)
        assertFalse("reached the end", catalog.canScrollVertically(1))
    }

    @Test
    fun `the loader takes the place of count and load more`() {
        val catalog = PersonalizationCatalog(context).apply {
            products = products(4)
            setCount("Showing", 4, "of", 40)
            loadMoreText = "Load more"
        }
        layOut(catalog)
        assertEquals(1, descendants<PersonalizationCount>(catalog).size)
        assertEquals(0, descendants<PersonalizationLoader>(catalog).size)

        catalog.isLoading = true
        layOut(catalog)

        assertEquals(0, descendants<PersonalizationCount>(catalog).size)
        assertEquals(1, descendants<PersonalizationLoader>(catalog).size)
        assertTrue(descendants<PersonalizationButton>(catalog).none { it.text == "Load more" })
    }

    @Test
    fun `no products and an empty text show the empty state under the header`() {
        val header = View(context)
        val catalog = PersonalizationCatalog(context).apply {
            setHeader(header)
            emptyText = "Nothing found"
        }

        layOut(catalog)

        assertEquals(1, descendants<PersonalizationEmptyState>(catalog).size)
        assertTrue(header.isAttachedToWindow || header.parent != null)
    }

    @Test
    fun `switching to the list keeps the products`() {
        val catalog = PersonalizationCatalog(context).apply { products = products(6) }

        catalog.view = PersonalizationProductsGrid.View.LIST
        layOut(catalog)

        assertEquals(6, catalog.products.size)
        assertTrue(descendants<PersonalizationProductCard>(catalog).isNotEmpty())
    }

    @Test
    fun `inside another scroll it lays out whole and does not scroll itself`() {
        val catalog = PersonalizationCatalog(context).apply {
            products = products(6)
            setCount("Showing", 6, "of", 6)
        }

        // Height left open, as a vertical scroll parent measures its content.
        catalog.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        catalog.layout(0, 0, 1080, catalog.measuredHeight)

        assertEquals(6, descendants<PersonalizationProductCard>(catalog).size)
        assertEquals(1, descendants<PersonalizationCount>(catalog).size)
        assertFalse(catalog.canScrollVertically(1))
    }

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 1920)
    }

    private inline fun <reified T : View> descendants(root: View): List<T> =
        collect(root, T::class.java)

    private fun <T : View> collect(root: View, type: Class<T>): List<T> {
        val own = if (type.isInstance(root)) listOf(type.cast(root)!!) else emptyList()
        val nested = if (root is ViewGroup) {
            (0 until root.childCount).flatMap { collect(root.getChildAt(it), type) }
        } else {
            emptyList()
        }
        return own + nested
    }

    private fun products(count: Int) =
        (0 until count).map { PersonalizationProduct(id = "$it", name = "Item $it", price = "$it") }
}
