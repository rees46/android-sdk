package com.personalization.ui.widgets

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.personalization.SDK
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.params.SearchParams
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.ColorFilter
import com.personalization.api.responses.search.IndustrialFilters
import com.personalization.api.responses.search.SearchFilter
import com.personalization.api.responses.search.SearchFullResponse
import com.personalization.api.responses.search.SizeFilter
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationEmptyState
import com.personalization.ui.components.PersonalizationFilters
import com.personalization.ui.components.PersonalizationProductCard
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The results screen's request bookkeeping: when "No results" may show, that a dropped request
 * cannot leave the loader running, and that settings changed together cost one request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationSearchResultsScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var search: SearchManager
    private lateinit var tracking: TrackingApi
    private lateinit var screen: PersonalizationSearchResultsScreen
    private val answers = mutableListOf<(SearchFullResponse) -> Unit>()
    private val failures = mutableListOf<(Int, String?) -> Unit>()
    private val pages = mutableListOf<Int>()

    @Before
    fun setUp() {
        search = mockk(relaxed = true)
        tracking = mockk(relaxed = true)
        every { search.searchFull(any(), any(), any(), any()) } answers {
            pages += secondArg<SearchParams>().build().getInt("page")
            answers += thirdArg<(SearchFullResponse) -> Unit>()
            failures += arg<(Int, String?) -> Unit>(3)
        }
        val sdk = mockk<SDK>(relaxed = true)
        every { sdk.searchManager } returns search
        every { sdk.tracking } returns tracking
        every { tracking.search(any(), any(), any()) } just Runs
        screen = PersonalizationSearchResultsScreen(context)
        screen.attach(sdk)
    }

    @Test
    fun `no results is not shown while the first page loads`() {
        screen.query = "jacket"
        idle()

        assertNull(screen.catalog.emptyText)
    }

    @Test
    fun `no results is shown once the server answers with nothing`() {
        screen.query = "jacket"
        idle()

        answers.last()(response(total = 0))

        assertEquals(screen.emptyText, screen.catalog.emptyText)
    }

    @Test
    fun `the found count stays hidden until the first page of each request answers`() {
        screen.query = "jacket"
        idle()

        assertFalse(foundShown())

        answers.last()(response(total = 40, page = 0))
        assertTrue(foundShown())
        assertTrue(descendants<TextView>(screen).any { it.text.toString() == "40" })

        screen.sortBy = "price"
        idle()
        assertFalse(foundShown())

        failures.last()(500, "server error")
        assertFalse(foundShown())
    }

    @Test
    fun `a blank query shows neither results nor no results`() {
        screen.query = "   "
        idle()

        assertNull(screen.catalog.emptyText)
        verify(exactly = 0) { search.searchFull(any(), any(), any(), any()) }
    }

    @Test
    fun `clearing the query mid-request stops the loader`() {
        screen.query = "jacket"
        idle()

        screen.query = ""
        idle()
        answers.last()(response(total = 40)) // arrives late and is dropped

        assertFalse(screen.catalog.isLoading)
        assertNull(screen.catalog.emptyText)
    }

    @Test
    fun `sort field and direction set together cost one request and one search event`() {
        screen.query = "jacket"
        idle()

        screen.sortBy = "price"
        screen.sortDir = "asc"
        idle()

        verify(exactly = 2) { search.searchFull(any(), any(), any(), any()) }
        verify(exactly = 2) { tracking.search("jacket", any(), any()) }
    }

    @Test
    fun `ten loaded pages keep a screenful of cards, not two hundred`() {
        screen.query = "jacket"
        idle()
        answers.last()(response(total = 200, page = 0))
        for (page in 1 until 10) {
            screen.loadMore()
            answers.last()(response(total = 200, page = page))
        }

        layOut(screen)

        assertEquals(200, screen.products.size)
        val cards = cards(screen)
        assertTrue("expected a screenful of cards, got $cards", cards in 1..30)
    }

    @Test
    fun `infinite scroll asks for the next page near the end`() {
        screen.infiniteScroll = true
        screen.query = "jacket"
        idle()
        answers.last()(response(total = 200, page = 0))
        layOut(screen)

        screen.catalog.scrollBy(0, 20_000)

        verify(exactly = 2) { search.searchFull(any(), any(), any(), any()) }
    }

    @Test
    fun `the rating is off unless the host switches it on`() {
        assertFalse(screen.showRating)
        assertFalse(screen.catalog.showRating)

        screen.showRating = true

        assertTrue(screen.catalog.showRating)
    }

    @Test
    fun `a first page that fails shows the error and a retry instead of nothing`() {
        val errors = mutableListOf<Int>()
        screen.onError = { code, _ -> errors += code }
        screen.query = "jacket"
        idle()

        failures.last()(500, "server error")
        layOut(screen)

        assertEquals(listOf(500), errors)
        assertFalse(screen.catalog.isLoading)
        assertNull(screen.catalog.emptyText)
        assertEquals(screen.errorText, screen.catalog.errorText)
        assertTrue(descendants<PersonalizationEmptyState>(screen).any { it.text == screen.errorText })

        retryButton().performClick()

        assertEquals(listOf(1, 1), pages)
        assertNull(screen.catalog.errorText)
        assertTrue(screen.catalog.isLoading)
        verify(exactly = 1) { tracking.search("jacket", any(), any()) }
        answers.last()(response(total = 40, page = 0))
        assertEquals(20, screen.products.size)
    }

    @Test
    fun `a next page that fails turns load more into a retry of that page`() {
        screen.query = "jacket"
        idle()
        answers.last()(response(total = 60, page = 0))
        screen.loadMore()

        failures.last()(500, null)
        layOut(screen)

        assertFalse(screen.catalog.isLoading)
        assertNull(screen.catalog.errorText)
        assertEquals(screen.retryText, screen.catalog.loadMoreText)
        assertEquals(20, screen.products.size)

        screen.catalog.scrollToPosition(screen.catalog.adapter!!.itemCount - 1)
        layOut(screen)
        retryButton().performClick()

        assertEquals(listOf(1, 2, 2), pages)
        verify(exactly = 1) { tracking.search("jacket", any(), any()) }
        answers.last()(response(total = 60, page = 1))
        assertEquals(40, screen.products.size)
        assertEquals(screen.loadMoreText, screen.catalog.loadMoreText)
    }

    @Test
    fun `empty error and retry texts fall back to the defaults`() {
        val errorDefault = screen.errorText
        val retryDefault = screen.retryText
        screen.errorText = ""
        screen.retryText = null
        screen.query = "jacket"
        idle()

        failures.last()(500, null)

        assertEquals(errorDefault, screen.errorText)
        assertEquals(retryDefault, screen.retryText)
        assertEquals(errorDefault, screen.catalog.errorText)
        assertEquals(retryDefault, screen.catalog.retryText)
    }

    @Test
    fun `with an empty retry text a failed page under infinite scroll still offers the button`() {
        screen.infiniteScroll = true
        screen.retryText = ""
        screen.query = "jacket"
        idle()
        answers.last()(response(total = 60, page = 0))
        screen.loadMore()

        failures.last()(500, null)

        assertEquals("Retry", screen.catalog.loadMoreText)
    }

    @Test
    fun `after a failed page infinite scroll waits for the retry button`() {
        screen.infiniteScroll = true
        screen.query = "jacket"
        idle()
        answers.last()(response(total = 200, page = 0))
        layOut(screen)
        screen.catalog.scrollBy(0, 20_000)
        failures.last()(500, null)
        layOut(screen)

        screen.catalog.scrollBy(0, -2_000)
        screen.catalog.scrollBy(0, 20_000)

        assertEquals(listOf(1, 2), pages)
        assertEquals(screen.retryText, screen.catalog.loadMoreText)
    }

    @Test
    fun `the filters screen shows the facets with most products, ten by default`() {
        screen.query = "samsung"
        idle()
        val answer = response(total = 40, page = 0)
        every { answer.filters } returns (1..12).associate { index ->
            "f$index" to SearchFilter(count = index * 10, values = mapOf("a" to 1, "b" to 2))
        } + ("merchant" to SearchFilter(count = 999, values = mapOf("Shop" to 40)))
        every { answer.brands } returns emptyList()
        every { answer.industrialFilters } returns null
        answers.last()(answer)

        screen.openFilters()

        assertEquals((12 downTo 3).map { "facet:f$it" }, facetSections())

        screen.maxFacets = null
        screen.openFilters()

        assertEquals((12 downTo 1).map { "facet:f$it" }, facetSections())

        screen.facets = linkedSetOf("f1", "merchant")
        screen.openFilters()

        assertEquals(listOf("facet:f1", "facet:merchant"), facetSections())
    }

    @Test
    fun `filter values carry the product count unless the host turns it off`() {
        screen.query = "jacket"
        idle()
        val answer = response(total = 40, page = 0)
        every { answer.brands } returns listOf(Brand(count = 7, name = "Acme"))
        every { answer.industrialFilters } returns IndustrialFilters(
            colors = listOf(ColorFilter(color = "Red", count = 3)),
            fashionSizes = listOf(SizeFilter(size = "M", count = 5))
        )
        every { answer.filters } returns mapOf("material" to SearchFilter(count = 9, values = mapOf("Wool" to 4, "Silk" to 5)))
        answers.last()(answer)

        screen.openFilters()

        assertEquals(listOf("Acme (7)"), labels("brands"))
        assertEquals(listOf("Red (3)"), labels("colors"))
        assertEquals(listOf("M (5)"), labels("sizes"))
        assertEquals(listOf("Wool (4)", "Silk (5)"), labels("facet:material"))

        screen.showValueCounts = false
        screen.openFilters()

        assertEquals(listOf("Acme"), labels("brands"))
        assertEquals(listOf("Red"), labels("colors"))
        assertEquals(listOf("M"), labels("sizes"))
        assertEquals(listOf("Wool", "Silk"), labels("facet:material"))
    }

    private fun labels(section: String): List<String> =
        (screen.filters.sections.single { it.id == section } as PersonalizationFilters.Section.Options)
            .options.map { it.label.toString() }

    private fun facetSections(): List<String> =
        screen.filters.sections.map { it.id }.filter { it.startsWith("facet:") }

    private fun retryButton(): PersonalizationButton =
        descendants<PersonalizationButton>(screen).single { it.text == screen.retryText }

    private inline fun <reified T : View> descendants(root: View): List<T> = collect(root, T::class.java)

    /** The "Found N products" words, not the filter and sort buttons next to them. */
    private fun foundShown(): Boolean {
        layOut(screen)
        val prefix = descendants<TextView>(screen).single { it.text.toString() == screen.foundPrefix.toString() }
        return (prefix.parent as View).visibility == View.VISIBLE
    }

    private fun <T : View> collect(root: View, type: Class<T>): List<T> = when {
        type.isInstance(root) -> listOf(type.cast(root)!!)
        root is ViewGroup -> (0 until root.childCount).flatMap { collect(root.getChildAt(it), type) }
        else -> emptyList()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 1920)
    }

    private fun cards(root: View): Int = when (root) {
        is PersonalizationProductCard -> 1
        is ViewGroup -> (0 until root.childCount).sumOf { cards(root.getChildAt(it)) }
        else -> 0
    }

    private fun response(total: Int, page: Int? = null): SearchFullResponse = mockk(relaxed = true) {
        every { products } returns if (page == null) emptyList() else (0 until 20).map { index ->
            mockk<Product>(relaxed = true) { every { id } returns "p${page * 20 + index}" }
        }
        every { productsTotal } returns total
    }
}
