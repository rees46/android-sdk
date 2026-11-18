package com.personalization.features.subscriptions

import com.personalization.api.models.subscriptions.BackInStockProperties
import com.personalization.api.models.subscriptions.SubscriptionsUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argBoolean
import com.personalization.core.internal.argString
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
import java.util.concurrent.CopyOnWriteArrayList

/** `sdk.subscriptions` against the contract's fixtures (`contract/0/subscriptions.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubscriptionsApiFixturesTest {

    private val fixtures = NamespaceFixtures("subscriptions-shop")
    private val changes = CopyOnWriteArrayList<String>()
    private lateinit var subscriptions: SubscriptionsApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        subscriptions = SubscriptionsApiImpl(fixtures.calls) { type, _ -> changes += type }
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        val email = args.argString("email")
        val phone = args.argString("phone")
        when (method) {
            "manage" -> subscriptions.manage(manageRequest(args.getJSONObject("request")))
            "check" -> subscriptions.check()
            "subscribeForPriceDrop" -> subscriptions.subscribeForPriceDrop(args.getString("itemId"), args.getDouble("currentPrice"), email, phone)
            "unsubscribeFromPriceDrop" -> subscriptions.unsubscribeFromPriceDrop(args.argStrings("itemIds")!!, email, phone)
            "subscribeForBackInStock" -> subscriptions.subscribeForBackInStock(
                args.getString("itemId"),
                args.optJSONObject("properties")?.let {
                    BackInStockProperties(it.argString("fashionSize"), it.argString("fashionColor"), it.argString("barcode"))
                },
                email,
                phone,
            )
            "unsubscribeFromBackInStock" -> subscriptions.unsubscribeFromBackInStock(args.argStrings("itemIds")!!, email, phone)
            else -> error("no subscriptions.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("subscriptions", invoke)
        fixtures.checkErrors("subscriptions", invoke)
    }

    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("subscriptions", invoke)
    }

    @Test
    fun `only manage tells popups v3 of the change, once the server took it`() {
        repeat(3) { fixtures.server.enqueue(MockResponse().setBody("{}")) }
        fixtures.server.enqueue(MockResponse().setResponseCode(500))

        fixtures.outcome { subscriptions.manage(SubscriptionsUpdateRequest(emailBulk = true)) }
        fixtures.outcome { subscriptions.subscribeForPriceDrop("37", 10.0) }
        fixtures.outcome { subscriptions.unsubscribeFromBackInStock(listOf("37")) }
        fixtures.outcome { subscriptions.manage(SubscriptionsUpdateRequest(emailBulk = false)) }

        assertEquals(listOf(SubscriptionsApiImpl.MANAGE_PATH), changes)
    }

    /** The request from the fixture's names, each of them a field of [SubscriptionsUpdateRequest] by the same name. */
    private fun manageRequest(json: JSONObject): SubscriptionsUpdateRequest {
        val flags = FLAGS.associateWith { json.argBoolean(it) }
        return SubscriptionsUpdateRequest(
            email = json.argString("email"), phone = json.argString("phone"), externalId = json.argString("externalId"),
            loyaltyId = json.argString("loyaltyId"), telegramId = json.argString("telegramId"),
            emailBulk = flags["emailBulk"], emailChain = flags["emailChain"], emailTransactional = flags["emailTransactional"],
            smsBulk = flags["smsBulk"], smsChain = flags["smsChain"], smsTransactional = flags["smsTransactional"],
            webPushBulk = flags["webPushBulk"], webPushChain = flags["webPushChain"], webPushTransactional = flags["webPushTransactional"],
            mobilePushBulk = flags["mobilePushBulk"], mobilePushChain = flags["mobilePushChain"],
            mobilePushTransactional = flags["mobilePushTransactional"],
            telegramBulk = flags["telegramBulk"], telegramChain = flags["telegramChain"], telegramTransactional = flags["telegramTransactional"],
            whatsappBulk = flags["whatsappBulk"], whatsappChain = flags["whatsappChain"], whatsappTransactional = flags["whatsappTransactional"],
            maxBulk = flags["maxBulk"], maxChain = flags["maxChain"], maxTransactional = flags["maxTransactional"],
            walletBulk = flags["walletBulk"], walletChain = flags["walletChain"], walletTransactional = flags["walletTransactional"],
        )
    }

    private companion object {
        val FLAGS = listOf("email", "sms", "webPush", "mobilePush", "telegram", "whatsapp", "max", "wallet")
            .flatMap { channel -> listOf("Bulk", "Chain", "Transactional").map { channel + it } }
    }
}
