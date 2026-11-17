package com.personalization.features.search

import com.personalization.api.models.products.SortBy
import com.personalization.api.models.products.SortDirection
import com.personalization.api.models.search.SearchBlankResponse
import com.personalization.api.models.search.SearchFullRequest
import com.personalization.core.SdkResult
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argBoolean
import com.personalization.core.internal.argDouble
import com.personalization.core.internal.argInt
import com.personalization.core.internal.argString
import com.personalization.core.internal.argStringLists
import com.personalization.core.internal.argStrings
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `sdk.search` against the contract's fixtures (`contract/0/search.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchApiFixturesTest {

    private val fixtures = NamespaceFixtures("search-shop")
    private lateinit var search: SearchApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        search = SearchApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "blank" -> search.blank()
            "instant" -> search.instant(
                args.getString("query"),
                locations = args.argStrings("locations"),
                excludedMerchants = args.argStrings("excludedMerchants"),
                excludedBrands = args.argStrings("excludedBrands"),
            )
            "full" -> search.full(args.getString("query"), args.optJSONObject("request")?.let(::fullRequest) ?: SearchFullRequest())
            else -> error("no search.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() =
        fixtures.checkRequests("search", invoke) { MockResponse().setBody("{}") }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() =
        fixtures.checkAnswers("search", invoke)

    @Test
    fun `an empty search's answer without a body is an empty result`() {
        fixtures.server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(SearchBlankResponse()), fixtures.outcome { search.blank() })
    }

    private fun fullRequest(json: JSONObject) = SearchFullRequest(
        categoryIds = json.argStrings("categoryIds"),
        brands = json.argStrings("brands"),
        excludedBrands = json.argStrings("excludedBrands"),
        merchants = json.argStrings("merchants"),
        excludedMerchants = json.argStrings("excludedMerchants"),
        locations = json.argStrings("locations"),
        priceMin = json.argDouble("priceMin"),
        priceMax = json.argDouble("priceMax"),
        colors = json.argStrings("colors"),
        fashionSizes = json.argStrings("fashionSizes"),
        excludedItemIds = json.argStrings("excludedItemIds"),
        filters = json.argStringLists("filters"),
        sortBy = json.argString("sortBy")?.let(::SortBy),
        sortDir = json.argString("sortDir")?.let { dir -> SortDirection.entries.first { it.value == dir } },
        disableClarification = json.argBoolean("disableClarification") ?: false,
        categoryLimit = json.argInt("categoryLimit"),
        brandLimit = json.argInt("brandLimit"),
        limit = json.argInt("limit"),
        page = json.argInt("page"),
    )
}
