package com.personalization.features.orders.impl

import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.orders.GetLastOrderProductsResponse
import com.personalization.api.responses.orders.Order
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contract of the client-order methods with the API, as the documentation describes it
 * (rees46.ru/help/integration/cdp/client-orders/history and …/last): the request each one sends and
 * how the documented answer is read. The network is a mock; `shop_id` is added later by the network
 * layer, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrdersManagerImplTest {

    private lateinit var network: SendNetworkMethodUseCase
    private lateinit var impl: OrdersManagerImpl

    private val params = slot<JSONObject>()
    private val listener = slot<OnApiCallbackListener>()

    @Before
    fun setUp() {
        network = mockk(relaxed = true)
        every { network.getAsync(any(), capture(params), capture(listener)) } just Runs
        impl = OrdersManagerImpl(network)
    }

    // orders/by_user

    @Test
    fun `the order history is a GET of orders by_user with the secret and every identifier given`() {
        impl.getUserOrders(
            shopSecret = "secret",
            did = "did-1",
            email = "client@example.com",
            phone = "79991234567",
            loyaltyId = "loyalty-1",
            externalId = "external-1",
            dateFrom = "2026-01-31",
            onSuccess = {},
            onError = { _, _ -> },
        )

        verify(exactly = 1) { network.getAsync("orders/by_user", any(), any()) }
        val sent = params.captured
        assertEquals("secret", sent.getString("shop_secret"))
        assertEquals("did-1", sent.getString("did"))
        assertEquals("client@example.com", sent.getString("email"))
        assertEquals("79991234567", sent.getString("phone"))
        assertEquals("loyalty-1", sent.getString("loyalty_id"))
        assertEquals("external-1", sent.getString("external_id"))
        assertEquals("2026-01-31", sent.getString("date_from"))
    }

    @Test
    fun `the order history sends only the identifiers given`() {
        impl.getUserOrders(
            shopSecret = "secret",
            did = null,
            email = "client@example.com",
            phone = null,
            loyaltyId = null,
            externalId = null,
            dateFrom = null,
            onSuccess = {},
            onError = { _, _ -> },
        )

        val sent = params.captured
        assertEquals("client@example.com", sent.getString("email"))
        listOf("did", "phone", "loyalty_id", "external_id", "date_from").forEach {
            assertFalse("$it was sent", sent.has(it))
        }
    }

    @Test
    fun `the documented order history answer is read into orders with their items`() {
        var orders: List<Order>? = null
        impl.getUserOrders("secret", "did-1", null, null, null, null, null, { orders = it }, { _, _ -> })

        listener.captured.onSuccess(JSONObject(USER_ORDERS_ANSWER))

        val order = orders!!.single()
        assertEquals(123456L, order.internalId)
        assertEquals("ORDER-1", order.id)
        assertEquals("2026-01-31 12:00:00", order.date)
        assertEquals("1500.5", order.value)
        assertEquals("1000", order.cashValue)
        assertEquals("500.5", order.bonusesValue)
        assertEquals("300", order.deliveryValue)
        assertEquals("SALE10", order.promocode)
        assertEquals(false, order.taxFree)
        assertEquals("courier", order.deliveryType)
        assertEquals("delivered", order.orderStatus)
        assertEquals("card", order.paymentType)

        val line = order.items.single()
        assertEquals(2, line.amount)
        assertEquals("750", line.price)
        assertEquals("delivered", line.status)
        assertEquals("800", line.originalPrice)
        assertEquals("line-1", line.lineId)
        assertEquals("SKU-1", line.item!!.id)
        assertEquals("Phone", line.item!!.name)
        assertEquals(1000.0, line.item!!.price, 0.0)
    }

    @Test
    fun `an order history answer without orders is an empty list`() {
        var orders: List<Order>? = null
        impl.getUserOrders("secret", "did-1", null, null, null, null, null, { orders = it }, { _, _ -> })

        listener.captured.onSuccess(JSONObject("""{"status":"success","data":{"orders":[]}}"""))

        assertEquals(emptyList<Order>(), orders)
    }

    @Test
    fun `an order history error reaches the app with its code and message`() {
        var error: Pair<Int, String?>? = null
        impl.getUserOrders("secret", "did-1", null, null, null, null, null, {}, { code, msg -> error = code to msg })

        listener.captured.onError(404, "Shop not found")

        assertEquals(404 to "Shop not found", error)
    }

    // orders/last_for_user

    @Test
    fun `the last order is a GET of orders last_for_user with the params given`() {
        impl.getLastOrderProducts(
            Params().put("did", "did-1").put("external_id", "external-1"),
            onGetLastOrderProducts = {},
            onError = { _, _ -> },
        )

        verify(exactly = 1) { network.getAsync("orders/last_for_user", any(), any()) }
        assertEquals("did-1", params.captured.getString("did"))
        assertEquals("external-1", params.captured.getString("external_id"))
    }

    @Test
    fun `the documented last order answer, an array of products, is read into products`() {
        var answer: GetLastOrderProductsResponse? = null
        impl.getLastOrderProducts(Params().put("did", "did-1"), { answer = it }, { _, _ -> })

        listener.captured.onSuccess(JSONArray("[$PRODUCT]"))

        val product = answer!!.products.single()
        assertEquals("SKU-1", product.id)
        assertEquals("Phone", product.name)
        assertEquals(1000.0, product.price, 0.0)
        assertEquals("https://shop.test/product/123", product.url)
        assertEquals("https://shop.test/pictures/123.png", product.imageUrl)
        assertEquals(listOf("1", "3"), product.categoryIds)
    }

    @Test
    fun `an empty last order is no products`() {
        var answer: GetLastOrderProductsResponse? = null
        impl.getLastOrderProducts(Params().put("did", "did-1"), { answer = it }, { _, _ -> })

        listener.captured.onSuccess(JSONArray("[]"))

        assertTrue(answer!!.products.isEmpty())
    }

    @Test
    fun `a last order error reaches the app with its code and message`() {
        var error: Pair<Int, String?>? = null
        impl.getLastOrderProducts(Params().put("did", "did-1"), {}, { code, msg -> error = code to msg })

        listener.captured.onError(500, null)

        assertEquals(500, error!!.first)
        assertNull(error!!.second)
    }

    private companion object {
        /** A product as the documentation shows it, with concrete values. */
        const val PRODUCT = """
            {
              "name": "Phone", "description": "A phone",
              "price_formatted": "1000 ₽", "price": 1000, "price_full": 1000.0,
              "price_full_formatted": "1000.00 ₽", "oldprice": 1200, "oldprice_full": 1200.0,
              "oldprice_formatted": "1200 ₽", "oldprice_full_formatted": "1200.00 ₽",
              "url": "https://shop.test/product/123", "url_handle": "/product/123",
              "discount": 20, "discount_formatted": "20%",
              "category_ids": ["1", "3"],
              "categories": [
                {"id": "1", "parent": null, "name": "Electronics", "url": "https://shop.test/c/1"},
                {"id": "3", "parent": "1", "name": "Phones", "url": "https://shop.test/c/3"}
              ],
              "image_url": "https://shop.test/pictures/123.png", "image_url_handle": "/pictures/123.png",
              "is_new": true, "currency": "₽", "id": "SKU-1", "barcode": "4600000000000",
              "brand": "Brand", "leftovers": "lots", "stock_quantity": 1000, "rating": 4,
              "fashion_sizes": [], "fashion_original_sizes": [], "fashion_colors": [],
              "model": "M1", "params": [],
              "image_url_resized": {"120": "https://shop.test/120.png", "520": "https://shop.test/520.png"}
            }
        """

        /** The documented `orders/by_user` answer, with concrete values. */
        const val USER_ORDERS_ANSWER = """
            {
              "status": "success",
              "data": {
                "orders": [
                  {
                    "_id": "123456", "id": "ORDER-1", "date": "2026-01-31 12:00:00",
                    "value": 1500.5, "cash_value": 1000, "bonuses_value": 500.5, "delivery_value": 300,
                    "location_id": "msk", "promocode": "SALE10", "offline": false,
                    "delivery_date": "2026-02-02", "internal_status": "completed", "stream": "android",
                    "channel": "app", "tax_free": false, "delivery_type": "courier",
                    "delivery_address": "Moscow", "order_status": "delivered", "payment_type": "card",
                    "custom_properties": {"prop_1": "a"},
                    "items": [
                      {
                        "amount": 2, "price": 750, "status": "delivered", "original_price": 800,
                        "barcode": "4600000000000", "line_id": "line-1", "cancel_reason": null,
                        "item": $PRODUCT
                      }
                    ],
                    "history": [
                      {"status": "delivered", "common_value": 1500.5, "cash_value": 1000,
                       "bonuses_value": 500.5, "delivery_value": 300, "discount_value": 0,
                       "date": "2026-02-02"}
                    ]
                  }
                ]
              }
            }
        """
    }
}
