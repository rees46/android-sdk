package com.personalization.ui.widgets

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.personalization.Fixtures
import com.personalization.Params
import com.personalization.SDK
import com.personalization.api.managers.CollectionManager
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.responses.collection.CollectionResponse
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
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
 * The data-bound recommender block: where its title comes from, when it hides, what it reports,
 * how it shares answers through the cache, and what a tap tracks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationRecommenderBlockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gson = Gson()
    private val cache = ResponseCache()
    private lateinit var recommendations: RecommendationManager
    private lateinit var collections: CollectionManager
    private lateinit var tracking: TrackingApi
    private lateinit var sdk: SDK

    private val sent = mutableListOf<Pair<String, JSONObject>>()
    private val answers = mutableListOf<(GetExtendedRecommendationResponse) -> Unit>()
    private val failures = mutableListOf<(Int, String?) -> Unit>()
    private val collectionAnswers = mutableListOf<(CollectionResponse) -> Unit>()
    private val loaded = mutableListOf<Int>()
    private val errors = mutableListOf<Int>()
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        recommendations = mockk(relaxed = true)
        every { recommendations.getExtendedRecommendation(any(), any(), any(), any()) } answers {
            sent += firstArg<String>() to secondArg<Params>().build()
            answers += thirdArg<(GetExtendedRecommendationResponse) -> Unit>()
            failures += arg<(Int, String?) -> Unit>(3)
        }
        collections = mockk(relaxed = true)
        every { collections.getCollection(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            collectionAnswers += arg<(CollectionResponse) -> Unit>(6)
        }
        tracking = mockk(relaxed = true)
        every { tracking.setSource(any()) } answers { events += "source ${firstArg<TrackingSource>().code}" }
        sdk = mockk(relaxed = true)
        every { sdk.recommendationManager } returns recommendations
        every { sdk.collectionManager } returns collections
        every { sdk.tracking } returns tracking
    }

    @Test
    fun `the title comes from the answer`() {
        val block = block()

        answers.last()(recommendation())

        assertEquals(View.VISIBLE, block.visibility)
        assertEquals("Популярные", block.productsBlock.text.toString())
        assertTrue(block.productsBlock.hasTitleRow)
        assertEquals(listOf(2), loaded)
    }

    @Test
    fun `a title of the host wins over the answer`() {
        val block = block { code = CODE; title = "For you" }

        answers.last()(recommendation())

        assertEquals("For you", block.productsBlock.text.toString())
    }

    @Test
    fun `an empty title leaves the block without a title row`() {
        val block = block { code = CODE; title = "" }

        answers.last()(recommendation())

        assertEquals(View.VISIBLE, block.visibility)
        assertFalse(block.productsBlock.hasTitleRow)
    }

    @Test
    fun `a collection has no title`() {
        val block = block { collectionId = "summer" }

        collectionAnswers.last()(CollectionResponse(recommendation().products))

        assertEquals(View.VISIBLE, block.visibility)
        assertFalse(block.productsBlock.hasTitleRow)
        assertEquals(listOf(2), loaded)
    }

    @Test
    fun `fewer products than the minimum hide the block and report zero`() {
        val block = block { code = CODE; minProducts = 3 }

        answers.last()(recommendation())

        assertEquals(View.GONE, block.visibility)
        assertTrue(block.products.isEmpty())
        assertEquals(listOf(0), loaded)
    }

    @Test
    fun `an empty answer hides the block and reports zero`() {
        val block = block()

        answers.last()(gson.fromJson("{}", GetExtendedRecommendationResponse::class.java))

        assertEquals(View.GONE, block.visibility)
        assertEquals(listOf(0), loaded)
    }

    @Test
    fun `the block is hidden while it loads and measures to nothing`() {
        val block = block()

        block.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)

        assertEquals(View.GONE, block.visibility)
        assertEquals(0, block.measuredHeight)
    }

    @Test
    fun `an error hides the block and reaches onError`() {
        val block = block()

        failures.last()(500, "boom")

        assertEquals(View.GONE, block.visibility)
        assertEquals(listOf(500), errors)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `reload keeps the shown block on screen until the answer`() {
        val block = block()
        answers.last()(recommendation())

        block.reload()

        assertEquals(View.VISIBLE, block.visibility)
        assertEquals(2, block.products.size)
        answers.last()(gson.fromJson("{}", GetExtendedRecommendationResponse::class.java))
        assertEquals(View.GONE, block.visibility)
        assertEquals(listOf(2, 0), loaded)
    }

    @Test
    fun `a failed reload hides the block and reaches onError`() {
        val block = block()
        answers.last()(recommendation())

        block.reload()
        failures.last()(504, "timeout")

        assertEquals(View.GONE, block.visibility)
        assertTrue(block.products.isEmpty())
        assertEquals(listOf(504), errors)
    }

    @Test
    fun `a second block with the same parameters shows the cached answer without a request`() {
        val first = block()
        answers.last()(recommendation())

        val second = block()

        verify(exactly = 1) { recommendations.getExtendedRecommendation(any(), any(), any(), any()) }
        assertEquals(View.VISIBLE, second.visibility)
        assertEquals(2, second.products.size)
        // Из сети блок проявляется, из кэша — сразу, без fade.
        assertEquals(0f, first.alpha, 0f)
        assertEquals(1f, second.alpha, 0f)
        assertEquals(listOf(2, 2), loaded)
    }

    @Test
    fun `reload goes to the network even with a cached answer`() {
        val block = block()
        answers.last()(recommendation())

        block.reload()

        verify(exactly = 2) { recommendations.getExtendedRecommendation(any(), any(), any(), any()) }
    }

    @Test
    fun `limit trims the products`() {
        val block = block { code = CODE; limit = 1 }

        answers.last()(recommendation())

        assertEquals(1, block.products.size)
        assertEquals(1, block.productsBlock.products.size)
        assertEquals(listOf(1), loaded)
    }

    @Test
    fun `a tap on a recommendation sets the dynamic source with the block code, then tells the host`() {
        val block = block { code = CODE; onProductClick = { events += "host ${it.id}" } }
        answers.last()(recommendation())

        block.productsBlock.onProductClick!!.invoke(block.products[0])

        verify { tracking.setSource(TrackingSource(TrackingSourceType.DYNAMIC, CODE)) }
        assertEquals(listOf("source $CODE", "host 52173"), events)
    }

    @Test
    fun `the card button of a recommendation sets the dynamic source too, then tells the host`() {
        val block = block { code = CODE; actionText = "Add to cart"; onProductAction = { events += "action ${it.id}" } }
        answers.last()(recommendation())

        block.productsBlock.onProductAction!!.invoke(block.products[1])

        verify { tracking.setSource(TrackingSource(TrackingSourceType.DYNAMIC, CODE)) }
        assertEquals(listOf("source $CODE", "action ${block.products[1].id}"), events)
    }

    @Test
    fun `a tap or a card button on a collection product sets no source`() {
        val block = block {
            collectionId = "summer"
            onProductClick = { events += "host ${it.id}" }
            onProductAction = { events += "action ${it.id}" }
        }
        collectionAnswers.last()(CollectionResponse(recommendation().products))

        block.productsBlock.onProductClick!!.invoke(block.products[0])
        block.productsBlock.onProductAction!!.invoke(block.products[0])

        verify(exactly = 0) { tracking.setSource(any()) }
        assertEquals(listOf("host 52173", "action 52173"), events)
    }

    @Test
    fun `a recommended product carries its discount, links and the action text`() {
        val block = block { code = CODE; actionText = "Add to cart" }

        answers.last()(recommendation())

        val card = block.products[0]
        assertEquals("174 990 ₽", card.oldPrice)
        assertEquals("-6%", card.discount)
        assertEquals("https://www.technodom.kz/p/holodilnik-beko-rdsk-mw-52173", card.deeplink)
        assertEquals(recommendation().products[0].url, card.url)
        assertEquals("Add to cart", card.actionText)
        assertNull(block.products[1].discount)
    }

    @Test
    fun `item, category and locations go into the request and the cache key`() {
        block { code = CODE; itemId = "52173"; categoryId = "7"; locations = "1,2" }
        answers.last()(recommendation())

        block { code = CODE; itemId = "52174" }

        val (code, params) = sent.first()
        assertEquals(CODE, code)
        assertEquals("52173", params.getString("item"))
        assertEquals("7", params.getString("category_id"))
        assertEquals("1,2", params.getString("locations"))
        assertEquals(2, sent.size)
    }

    @Test
    fun `parameters changed in a row cost one request, and the old products hide meanwhile`() {
        val block = block()
        answers.last()(recommendation())

        block.itemId = "52173"
        block.categoryId = "7"
        idle()

        assertEquals(2, sent.size)
        assertEquals(View.GONE, block.visibility)
        answers.last()(recommendation())
        assertEquals(View.VISIBLE, block.visibility)
    }

    @Test
    fun `code and collection together are a configuration error`() {
        val block = block { code = CODE; collectionId = "summer" }

        assertEquals(View.GONE, block.visibility)
        assertEquals(listOf(ResponseCache.ERROR_CODE), errors)
        assertTrue(sent.isEmpty())
        assertTrue(collectionAnswers.isEmpty())
    }

    @Test
    fun `the rating is off by default`() {
        val block = block()

        assertFalse(block.showRating)
        assertFalse(block.productsBlock.showRating)
    }

    private fun block(configure: PersonalizationRecommenderBlock.() -> Unit = { code = CODE }) =
        PersonalizationRecommenderBlock(context).apply {
            cache = this@PersonalizationRecommenderBlockTest.cache
            onLoaded = { loaded += it }
            onError = { code, _ -> errors += code }
            configure()
            attach(sdk)
        }

    private fun recommendation(): GetExtendedRecommendationResponse =
        gson.fromJson(Fixtures.read("recommend_extended.json"), GetExtendedRecommendationResponse::class.java)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val CODE = "e6249bb15043644bf25b135006149962"
    }
}
