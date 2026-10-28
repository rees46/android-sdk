package com.personalization.ui.widgets

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.personalization.SDK
import com.personalization.api.managers.CategoryManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.responses.category.CategoryResponse
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.PriceRange
import com.personalization.ui.components.PersonalizationButton
import com.personalization.ui.components.PersonalizationCheckboxWithLabel
import com.personalization.ui.components.PersonalizationEmptyState
import com.personalization.ui.components.PersonalizationFilters
import com.personalization.ui.components.PersonalizationTag
import io.mockk.every
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
 * The category screen: what goes into `getCategory`, that the category view is tracked once per
 * category, the filter sections built from a category answer, errors with Retry and an empty
 * category. Paging and layout are shared with the search screen and tested there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationCategoryScreenTest {

    private data class Call(
        val category: String,
        val limit: Int?,
        val page: Int?,
        val brands: String?,
        val locations: String?,
        val filters: Map<String, Any>?,
        val priceMin: Double?,
        val priceMax: Double?,
        val sortBy: String?,
        val sortDir: String?
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var categories: CategoryManager
    private lateinit var tracking: TrackingApi
    private lateinit var screen: PersonalizationCategoryScreen
    private val calls = mutableListOf<Call>()
    private val answers = mutableListOf<(CategoryResponse) -> Unit>()
    private val failures = mutableListOf<(Int, String?) -> Unit>()

    @Before
    fun setUp() {
        categories = mockk(relaxed = true)
        tracking = mockk(relaxed = true)
        every {
            categories.getCategory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            calls += Call(
                arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7), arg(8), arg(9)
            )
            answers += arg<(CategoryResponse) -> Unit>(10)
            failures += arg<(Int, String?) -> Unit>(11)
        }
        val sdk = mockk<SDK>(relaxed = true)
        every { sdk.categoryManager } returns categories
        every { sdk.tracking } returns tracking
        screen = PersonalizationCategoryScreen(context)
        screen.attach(sdk)
    }

    @Test
    fun `the request carries the category, the page and the applied filters with sorting`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        screen.locations = "1,2"
        screen.applied = PersonalizationListingScreen.Applied(
            brands = setOf("AENO", "Ardesto"),
            priceMin = "1 000",
            priceMax = "2000,5",
            facets = mapOf("Цвет" to setOf("Белый"), "Тип" to emptySet())
        )
        screen.sortBy = "price"
        screen.sortDir = "asc"
        idle()

        val first = calls.single()
        assertEquals("tehnika-dlja-kuhni", first.category)
        assertEquals(20, first.limit)
        assertEquals(1, first.page)
        assertEquals("AENO,Ardesto", first.brands)
        assertEquals("1,2", first.locations)
        assertEquals(mapOf("Цвет" to listOf("Белый")), first.filters)
        assertEquals(1000.0, first.priceMin)
        assertEquals(2000.5, first.priceMax)
        assertEquals("price", first.sortBy)
        assertEquals("asc", first.sortDir)

        answers.last()(response(total = 60, page = 0))
        screen.loadMore()

        assertEquals(2, calls.last().page)
        assertEquals("AENO,Ardesto", calls.last().brands)
        assertEquals("price", calls.last().sortBy)
    }

    @Test
    fun `without filters and sorting nothing extra goes out`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()

        assertEquals(Call("tehnika-dlja-kuhni", 20, 1, null, null, null, null, null, null, null), calls.single())
    }

    @Test
    fun `no category asks for nothing`() {
        screen.categoryId = "  "
        idle()

        assertTrue(calls.isEmpty())
        verify(exactly = 0) { tracking.categoryView(any(), any()) }
    }

    @Test
    fun `the category view is tracked once, not per page, filter, sort or retry`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        answers.last()(response(total = 60, page = 0))
        screen.loadMore()
        answers.last()(response(total = 60, page = 1))

        screen.applied = PersonalizationListingScreen.Applied(brands = setOf("AENO"))
        idle()
        screen.sortBy = "discount"
        screen.sortDir = "desc"
        idle()
        failures.last()(500, null)
        screen.retry()
        screen.reload()

        verify(exactly = 1) { tracking.categoryView("tehnika-dlja-kuhni", any()) }
        assertEquals(6, calls.size)

        screen.categoryId = "smartfony"
        idle()

        verify(exactly = 1) { tracking.categoryView("smartfony", any()) }
        verify(exactly = 2) { tracking.categoryView(any(), any()) }
    }

    @Test
    fun `a product tap sets no tracking source`() {
        val tapped = mutableListOf<String>()
        screen.onProductClick = { tapped += it.id }
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        answers.last()(response(total = 20, page = 0))

        screen.catalog.onProductClick?.invoke(screen.catalog.products.first())

        assertEquals(listOf("p0"), tapped)
        verify(exactly = 0) { tracking.setSource(any()) }
    }

    @Test
    fun `filter sections come from the category answer`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        val facets = (1..12).joinToString(",") { index ->
            """"f$index": {"count": ${index * 10}, "values": {"a": 1, "b": 2}}"""
        }
        answers.last()(
            response(total = 40, page = 0).copy(
                brands = listOf(Brand(count = 11, name = "AEL"), Brand(count = 20, name = "AENO")),
                priceRange = PriceRange(max = 1089990.0, min = 200.0),
                filters = JsonParser.parseString(
                    """{"merchant": {"count": 999, "values": {"Technodom": 40}}, $facets}"""
                ).asJsonObject
            )
        )

        screen.openFilters()

        val ids = screen.filters.sections.map { it.id }
        val expectedFacets = (12 downTo 3).map { "facet:f$it" }
        assertEquals(listOf("price", "brands") + expectedFacets, ids)
        val price = screen.filters.sections.first() as PersonalizationFilters.Section.Range
        assertEquals("200", price.fromPlaceholder)
        assertEquals("1089990", price.toPlaceholder)
    }

    @Test
    fun `a first page that fails shows the error and a retry`() {
        val errors = mutableListOf<Int>()
        screen.onError = { code, _ -> errors += code }
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()

        failures.last()(500, "server error")
        layOut(screen)

        assertEquals(listOf(500), errors)
        assertFalse(screen.catalog.isLoading)
        assertEquals(screen.errorText, screen.catalog.errorText)
        assertTrue(descendants<PersonalizationEmptyState>(screen).any { it.text == screen.errorText })

        retryButton().performClick()

        assertEquals(listOf(1, 1), calls.map { it.page })
        assertNull(screen.catalog.errorText)
        answers.last()(response(total = 40, page = 0))
        assertEquals(20, screen.products.size)
        verify(exactly = 1) { tracking.categoryView(any(), any()) }
    }

    @Test
    fun `a next page that fails turns load more into a retry of that page`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        answers.last()(response(total = 60, page = 0))
        screen.loadMore()

        failures.last()(500, null)

        assertNull(screen.catalog.errorText)
        assertEquals(screen.retryText, screen.catalog.loadMoreText)
        screen.retry()

        assertEquals(listOf(1, 2, 2), calls.map { it.page })
        answers.last()(response(total = 60, page = 1))
        assertEquals(40, screen.products.size)
        assertEquals(screen.loadMoreText, screen.catalog.loadMoreText)
    }

    @Test
    fun `an empty category shows the empty state with the products counter`() {
        assertNull(screen.foundPrefix)
        assertEquals("products", screen.foundSuffix)
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        assertNull(screen.catalog.emptyText)

        answers.last()(response(total = 0))

        assertEquals("No products in this category yet.", screen.catalog.emptyText)
        assertTrue(screen.products.isEmpty())
    }

    @Test
    fun `a JSON value with a title shows the title, and the request keeps the value`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        idle()
        answers.last()(response(total = 40, page = 0).copy(filters = facetsJson("stickers", stickers)))

        screen.openFilters()

        val options = facetOptions("stickers")
        assertEquals(
            listOf("Суперцена (12)", "$BROKEN (3)", "$NO_TITLE (2)", "$NUMBER_TITLE (1)", "$EMPTY_TITLE (1)", "plain (4)"),
            options.map { it.label.toString() }
        )
        assertEquals(stickers.map { it.first }, options.map { it.id })

        descendants<PersonalizationCheckboxWithLabel>(screen).single { it.text.toString() == "Суперцена (12)" }.performClick()
        descendants<PersonalizationButton>(screen).single { it.text == screen.applyText }.performClick()
        idle()

        assertEquals(mapOf("stickers" to listOf(SUPER_PRICE)), calls.last().filters)
        assertEquals(listOf("Суперцена"), tags())
    }

    @Test
    fun `the host title of a value wins over the JSON title, an empty one does not`() {
        screen.facetValueTitle = { facet, value ->
            when {
                facet != "stickers" -> "wrong facet"
                value == SUPER_PRICE -> "Super price"
                value == "plain" -> " "
                else -> null
            }
        }
        screen.categoryId = "tehnika-dlja-kuhni"
        screen.applied = PersonalizationListingScreen.Applied(facets = mapOf("stickers" to setOf(SUPER_PRICE)))
        idle()
        answers.last()(response(total = 40, page = 0).copy(filters = facetsJson("stickers", stickers)))

        screen.openFilters()

        val labels = facetOptions("stickers").map { it.label.toString() }
        assertEquals("Super price (12)", labels[0])
        assertEquals("$NO_TITLE (2)", labels[2])
        assertEquals("plain (4)", labels[5])
        assertEquals(listOf("Super price"), tags())
    }

    @Test
    fun `brand values carry the product count, a checked one missing from the answer goes without`() {
        screen.categoryId = "tehnika-dlja-kuhni"
        screen.applied = PersonalizationListingScreen.Applied(brands = setOf("Gone"))
        idle()
        answers.last()(
            response(total = 40, page = 0).copy(brands = listOf(Brand(count = 11, name = "AEL"), Brand(count = 20, name = "AENO")))
        )

        screen.openFilters()

        val brands = screen.filters.sections.single { it.id == "brands" } as PersonalizationFilters.Section.Options
        assertEquals(listOf("AEL (11)", "AENO (20)", "Gone"), brands.options.map { it.label.toString() })
        assertEquals(listOf("Gone"), tags())
    }

    private fun facetOptions(name: String): List<PersonalizationFilters.Option> =
        (screen.filters.sections.single { it.id == "facet:$name" } as PersonalizationFilters.Section.Options).options

    private fun tags(): List<String> = descendants<PersonalizationTag>(screen.title).map { it.text.toString() }

    private fun facetsJson(name: String, values: List<Pair<String, Int>>): JsonObject = JsonObject().apply {
        add(name, JsonObject().apply {
            addProperty("count", values.sumOf { it.second })
            add("values", JsonObject().apply { values.forEach { (value, count) -> addProperty(value, count) } })
        })
    }

    private val stickers = listOf(
        SUPER_PRICE to 12, BROKEN to 3, NO_TITLE to 2, NUMBER_TITLE to 1, EMPTY_TITLE to 1, "plain" to 4
    )

    private companion object {
        const val SUPER_PRICE =
            """{"slug": "super_price", "title": "Суперцена", "text_color": "#ffffff", "background_color": "#f48012", "priority": 1}"""
        const val BROKEN = """{"title": "Суперцена""""
        const val NO_TITLE = """{"slug": "new"}"""
        const val NUMBER_TITLE = """{"title": 5}"""
        const val EMPTY_TITLE = """{"title": "  "}"""
    }

    private fun retryButton(): PersonalizationButton =
        descendants<PersonalizationButton>(screen).single { it.text == screen.retryText }

    private inline fun <reified T : View> descendants(root: View): List<T> = collect(root, T::class.java)

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

    private fun response(total: Int, page: Int? = null): CategoryResponse = CategoryResponse(
        productsTotal = total,
        products = if (page == null) emptyList() else (0 until 20).map { index ->
            mockk<Product>(relaxed = true) { every { id } returns "p${page * 20 + index}" }
        }
    )
}
