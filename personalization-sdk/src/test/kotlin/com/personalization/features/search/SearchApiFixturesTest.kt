package com.personalization.features.search

import com.personalization.api.models.products.SortBy
import com.personalization.api.models.products.SortDirection
import com.personalization.api.models.search.AgentDialogResponse
import com.personalization.api.models.search.AgentMessage
import com.personalization.api.models.search.AgentMessageRole
import com.personalization.api.models.search.AgentProduct
import com.personalization.api.models.search.AgentResponse
import com.personalization.api.models.search.AgentSession
import com.personalization.api.models.search.SearchBlankResponse
import com.personalization.api.models.search.SearchFullRequest
import androidx.test.core.app.ApplicationProvider
import com.personalization.core.SdkError
import com.personalization.core.SdkResult
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.CoreTimeouts
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.FakeConnectivity
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.startedCoreInit
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argBoolean
import com.personalization.core.internal.argDouble
import com.personalization.core.internal.argInt
import com.personalization.core.internal.argLong
import com.personalization.core.internal.argString
import com.personalization.core.internal.argStringLists
import com.personalization.core.internal.argStrings
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `sdk.search` against the contract's fixtures (`contract/0/search.json`), the search agent among them. */
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
            "agentDialog" -> search.agentDialog(args.argLong("sessionId"), args.argStrings("locations"))
            "agent" -> search.agent(args.getString("message"), args.argLong("sessionId"), args.argStrings("locations"))
            else -> error("no search.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("search", invoke) { MockResponse().setBody("{}") }
        fixtures.checkErrors("search", invoke)
    }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("search", invoke)
    }

    @Test
    fun `an empty search's answer without a body is an empty result`() {
        fixtures.server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(SearchBlankResponse()), fixtures.outcome { search.blank() })
    }

    @Test
    fun `the agent's methods go to the host of API v3, and the other searches to the API's`() {
        val app = MockWebServer().apply { start() }
        val api = MockWebServer().apply { start() }
        val settings = mockk<UserSettingsRepository>(relaxed = true) {
            every { getShopId() } returns "agent-shop"
            every { getDid() } returns "did-1"
            every { getSid() } returns "sid-1"
        }
        val connectivity = FakeConnectivity(connected = true)
        val client = CoreHttpClient({ api.url("/").toString() }, settings, connectivity, "agent-shop", appUrl = { app.url("/").toString() })
        val core = CoreV2(client, "agent-shop").also {
            it.init = startedCoreInit(ApplicationProvider.getApplicationContext(), settings, connectivity, "agent-shop")
        }
        val search = SearchApiImpl(NamespaceCalls(core = { core }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { "agent-shop" }))
        try {
            app.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
            app.enqueue(MockResponse().setBody("""{"session":{"id":1},"message":{"id":2}}"""))
            api.enqueue(MockResponse().setBody("{}"))

            assertTrue(fixtures.outcome { search.agentDialog() } is SdkResult.Success)
            assertTrue(fixtures.outcome { search.agent("hi") } is SdkResult.Success)
            assertTrue(fixtures.outcome { search.blank() } is SdkResult.Success)

            assertEquals(listOf("GET", "POST").map { it to "/" + SearchApiImpl.AGENT_PATH }, List(2) {
                app.takeRequest(5, TimeUnit.SECONDS)!!.let { it.method to it.requestUrl!!.encodedPath }
            })
            assertEquals("/" + SearchApiImpl.BLANK_PATH, api.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
            assertEquals(2, app.requestCount)
            assertEquals(1, api.requestCount)
        } finally {
            core.release()
            app.close()
            api.close()
        }
    }

    @Test
    fun `the agent's answer is read for 60 seconds, longer than the client's read timeout, which the dialog keeps`() {
        val slow = NamespaceFixtures("agent-timeout-shop", CoreTimeouts(readMs = 300))
        slow.start()
        try {
            val search = SearchApiImpl(slow.calls)
            val late = { body: String -> MockResponse().setBody(body).setHeadersDelay(1, TimeUnit.SECONDS) }
            slow.server.enqueue(late("""{"sessions":[]}"""))
            slow.server.enqueue(late("""{"session":{"id":1},"message":{"id":2,"role":"assistant"}}"""))

            assertEquals(SdkResult.Failure(SdkError.Timeout), slow.outcome { search.agentDialog() })
            assertTrue(slow.outcome { search.agent("hi") } is SdkResult.Success)
        } finally {
            slow.stop()
        }
    }

    @Test
    fun `a message without its id is kept with a null id, in the answer and in the dialog, and is never decoding`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"session":{"id":13},"message":{"role":"assistant","content":"Hi"}}"""))
        fixtures.server.enqueue(
            MockResponse().setBody(
                """{"sessions":[],"session":{"id":13,"messages":[{"role":"user","content":"hi"},{"id":2,"role":"assistant","content":"Hello"}]}}""",
            ),
        )

        assertEquals(
            SdkResult.Success(
                AgentResponse(
                    session = AgentSession(id = 13),
                    message = AgentMessage(id = null, role = AgentMessageRole.ASSISTANT, content = "Hi"),
                ),
            ),
            fixtures.outcome { search.agent("hi") },
        )
        assertEquals(
            SdkResult.Success(
                AgentDialogResponse(
                    session = AgentSession(
                        id = 13,
                        messages = listOf(
                            AgentMessage(id = null, role = AgentMessageRole.USER, content = "hi"),
                            AgentMessage(id = 2, role = AgentMessageRole.ASSISTANT, content = "Hello"),
                        ),
                    ),
                ),
            ),
            fixtures.outcome { search.agentDialog() },
        )
    }

    @Test
    fun `a product without a reason has an empty one, and a null reason is empty too`() {
        fixtures.server.enqueue(
            MockResponse().setBody(
                """{"session":{"id":1},"message":{"id":2,"products":[{"id":"37","name":"A"},{"id":"38","reason":null},{"id":"39","reason":"Fits"}]}}""",
            ),
        )

        val products = ((fixtures.outcome { search.agent("hi") } as SdkResult.Success).value as AgentResponse).message.products

        assertEquals(listOf("", "", "Fits"), products.map { it.reason })
        assertEquals(AgentProduct(id = "37", name = "A"), products[0])
    }

    @Test
    fun `an answer without its message object, or with a session without its id, is still decoding`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"session":{"id":1}}"""))
        fixtures.server.enqueue(MockResponse().setBody("""{"session":{"title":"x"},"message":{"id":2}}"""))

        assertEquals(SdkResult.Failure(SdkError.Decoding), fixtures.outcome { search.agent("hi") })
        assertEquals(SdkResult.Failure(SdkError.Decoding), fixtures.outcome { search.agent("hi") })
    }

    @Test
    fun `agentEnabled is what the last init answer said, false without one`() {
        var enabled = false
        val search = SearchApiImpl(fixtures.calls) { enabled }

        assertFalse(search.agentEnabled)
        enabled = true
        assertTrue(search.agentEnabled)
        assertFalse(SearchApiImpl(fixtures.calls).agentEnabled)
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
