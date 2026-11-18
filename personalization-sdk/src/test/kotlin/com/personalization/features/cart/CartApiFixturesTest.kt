package com.personalization.features.cart

import com.personalization.api.responses.cart.CartItem
import com.personalization.core.BridgeApi
import com.personalization.core.SdkResult
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argString
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `sdk.cart` against the contract's fixtures (`contract/0/cart.json`): the request each call makes — `clear`'s
 * with the shop's secret, `shop_id` and none of the device's fields —, the calls refused before one, and how
 * each answer is read.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CartApiFixturesTest {

    private val fixtures = NamespaceFixtures("cart-shop")
    private lateinit var cart: CartApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        cart = CartApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "get" -> cart.get()
            "clear" -> cart.clear(
                shopSecret = args.getString("shopSecret"),
                email = args.argString("email"),
                phone = args.argString("phone"),
                loyaltyId = args.argString("loyaltyId"),
                externalId = args.argString("externalId"),
                telegramId = args.argString("telegramId"),
            )
            else -> error("no cart.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("cart", invoke) { MockResponse().setBody("""{"status":"success","data":{"items":[]}}""") }
        fixtures.checkErrors("cart", invoke)
    }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("cart", invoke)
    }

    @Test
    fun `an answer without a body is an empty cart`() {
        fixtures.server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(emptyList<CartItem>()), fixtures.outcome { cart.get() })
    }
}
