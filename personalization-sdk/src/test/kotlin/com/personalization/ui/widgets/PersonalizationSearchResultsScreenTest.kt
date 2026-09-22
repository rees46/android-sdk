package com.personalization.ui.widgets

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.SDK
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.responses.search.SearchFullResponse
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Before
    fun setUp() {
        search = mockk(relaxed = true)
        tracking = mockk(relaxed = true)
        every { search.searchFull(any(), any(), any(), any()) } answers {
            answers += thirdArg<(SearchFullResponse) -> Unit>()
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

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun response(total: Int): SearchFullResponse = mockk(relaxed = true) {
        every { products } returns emptyList()
        every { productsTotal } returns total
    }
}
