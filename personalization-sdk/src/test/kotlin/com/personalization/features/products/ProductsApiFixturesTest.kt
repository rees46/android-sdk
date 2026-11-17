package com.personalization.features.products

import com.personalization.api.models.products.CategoryRequest
import com.personalization.api.models.products.ProductsListRequest
import com.personalization.api.models.products.SortBy
import com.personalization.api.models.products.SortDirection
import com.personalization.api.responses.products.product.ProductParam
import com.personalization.core.SdkResult
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
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

/** `sdk.products` against the contract's fixtures (`contract/0/products.json`): the requests and how each answer reads. */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProductsApiFixturesTest {

    private val fixtures = NamespaceFixtures("products-shop")
    private lateinit var products: ProductsApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        products = ProductsApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "getInfo" -> products.getInfo(args.getString("itemId"))
            "getList" -> products.getList(args.optJSONObject("request")?.let(::listRequest) ?: ProductsListRequest())
            "getCounters" -> products.getCounters(args.getString("itemId"))
            "getCategory" -> products.getCategory(
                args.getString("categoryId"),
                args.optJSONObject("request")?.let(::categoryRequest) ?: CategoryRequest(),
            )
            else -> error("no products.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("products", invoke) { MockResponse().setBody("""{"products":[]}""") }
        fixtures.checkErrors("products", invoke)
    }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("products", invoke)
    }

    /**
     * The product card (`products/get`) sends `params` as an object of the parameter's name → `{"value", "searchable"}`,
     * not as the lists' `[{"key", "values"}]`: the shape of the live answer, anonymized.
     */
    @Test
    fun `a product card's params come as an object of the parameter's name, read in the card's order`() {
        fixtures.server.enqueue(
            MockResponse().setBody(
                """{"uniqid":"1001","id":"1001","name":"Наушники","url":"https://shop.example/product/1001","price":"69990.0",
                   "currency":"₽","params":{"merchant":{"value":["Shop"],"searchable":true},
                   "stickers":{"value":["{\"slug\": \"0_0_24\", \"title\": \"0-0-24\"}"],"searchable":true},
                   "Цвет":{"value":["Белый"],"searchable":true},"Время автономной работы, ч":{"value":[5, 30.5],"searchable":false},
                   "Модель":{"value":"A1","searchable":true},"Серия":["S1","S2"],"":{"value":["no key"]}},
                   "locations":{"7":{"price":69990,"stock_quantity":9999,"discount_percent":30}}}""",
            ),
        )

        val card = (fixtures.outcome { products.getInfo("1001") } as SdkResult.Success).value as com.personalization.api.models.products.Product

        assertEquals(
            listOf(
                ProductParam("merchant", listOf("Shop")),
                ProductParam("stickers", listOf("""{"slug": "0_0_24", "title": "0-0-24"}""")),
                ProductParam("Цвет", listOf("Белый")),
                ProductParam("Время автономной работы, ч", listOf("5", "30.5")),
                ProductParam("Модель", listOf("A1")),
                ProductParam("Серия", listOf("S1", "S2")),
            ),
            card.params,
        )
        assertEquals(69990.0, card.price, 0.0)
    }

    @Test
    fun `an answer without a body is no product card`() {
        fixtures.server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(null), fixtures.outcome { products.getInfo("37") })
    }

    private fun listRequest(json: JSONObject) = ProductsListRequest(
        categoryIds = json.argStrings("categoryIds"),
        brands = json.argStrings("brands"),
        merchants = json.argStrings("merchants"),
        locations = json.argStrings("locations"),
        priceMin = json.argDouble("priceMin"),
        priceMax = json.argDouble("priceMax"),
        filters = json.argStringLists("filters"),
        sortBy = json.argString("sortBy")?.let(::SortBy),
        sortDir = json.argString("sortDir")?.let(::sortDirection),
        limit = json.argInt("limit"),
        page = json.argInt("page"),
    )

    private fun categoryRequest(json: JSONObject) = CategoryRequest(
        brands = json.argStrings("brands"),
        locations = json.argStrings("locations"),
        filters = json.argStringLists("filters"),
        priceMin = json.argDouble("priceMin"),
        priceMax = json.argDouble("priceMax"),
        sortBy = json.argString("sortBy")?.let(::SortBy),
        sortDir = json.argString("sortDir")?.let(::sortDirection),
        limit = json.argInt("limit"),
        page = json.argInt("page"),
    )

    companion object {
        fun sortDirection(value: String): SortDirection = SortDirection.entries.first { it.value == value }
    }
}
