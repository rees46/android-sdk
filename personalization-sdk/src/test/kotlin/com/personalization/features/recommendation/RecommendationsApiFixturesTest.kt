package com.personalization.features.recommendation

import com.personalization.api.models.recommendations.RecommendationRequest
import com.personalization.api.models.recommendations.RecommendationResponse
import com.personalization.core.SdkResult
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argBoolean
import com.personalization.core.internal.argInt
import com.personalization.core.internal.argString
import com.personalization.core.internal.argStrings
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `sdk.recommendations` against the contract's fixtures (`contract/0/recommendations.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecommendationsApiFixturesTest {

    private val fixtures = NamespaceFixtures("recommendations-shop")
    private lateinit var recommendations: RecommendationsApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        recommendations = RecommendationsApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "get" -> recommendations.get(
                args.getString("code"),
                args.optJSONObject("request")?.let(::request) ?: RecommendationRequest(),
            )
            else -> error("no recommendations.$method")
        }
    }

    @Test
    fun `get makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("recommendations", invoke) { MockResponse().setBody("""{"recommends":[]}""") }
        fixtures.checkErrors("recommendations", invoke)
    }

    @Test
    fun `get reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("recommendations", invoke)
    }

    @Test
    fun `a blank title or link of the block is no value, as on iOS`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"title":"","link":"  ","recommends":["1"]}"""))

        val result = fixtures.outcome { recommendations.get("block-1") }

        assertTrue("$result", result is SdkResult.Success)
        val response = (result as SdkResult.Success).value as RecommendationResponse
        assertNull(response.title)
        assertNull(response.link)
        assertEquals(listOf("1"), response.itemIds)
    }

    private fun request(json: JSONObject) = RecommendationRequest(
        itemId = json.argString("itemId"),
        categoryId = json.argString("categoryId"),
        searchQuery = json.argString("searchQuery"),
        excludedItemIds = json.argStrings("excludedItemIds"),
        brands = json.argStrings("brands"),
        locations = json.argStrings("locations"),
        limit = json.argInt("limit"),
        extended = json.argBoolean("extended") ?: true,
        withLocations = json.argBoolean("withLocations") ?: false,
    )
}
