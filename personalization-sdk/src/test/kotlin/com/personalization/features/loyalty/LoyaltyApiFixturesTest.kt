package com.personalization.features.loyalty

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.loyalty.LoyaltyMember
import com.personalization.api.models.loyalty.LoyaltyPromotion
import com.personalization.api.models.loyalty.LoyaltyRewardType
import com.personalization.api.models.loyalty.LoyaltyStatus
import com.personalization.api.models.profile.Gender
import com.personalization.core.BridgeApi
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argBoolean
import com.personalization.core.internal.argStrings
import com.personalization.core.internal.argString
import com.personalization.core.internal.isBirthday
import com.personalization.core.internal.isCalendarDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** `loyalty.join`, `getStatus` and `getPromotions` against the contract's fixtures (`contract/0/loyalty.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoyaltyApiFixturesTest {

    private val fixtures = NamespaceFixtures(SHOP)
    private lateinit var loyalty: LoyaltyApiImpl

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "join" -> loyalty.join(
                phone = args.getString("phone"),
                firstName = args.argString("firstName"),
                lastName = args.argString("lastName"),
                gender = args.argString("gender")?.let { name -> Gender.entries.single { it.name.lowercase() == name } },
                birthday = args.argString("birthday"),
            )
            "getStatus" -> loyalty.getStatus(args.getString("phone"))
            "getPromotions" -> loyalty.getPromotions(
                locationId = args.argString("locationId"),
                promoCodes = args.argStrings("promoCodes"),
                chargeBonuses = args.argBoolean("chargeBonuses"),
                currentTime = args.argString("currentTime"),
            )
            else -> error("unknown method $method")
        }
    }

    private val json = NamespaceFixtures.Json { value ->
        when (value) {
            is LoyaltyMember -> mapOf("identifier" to value.identifier)
            is LoyaltyStatus -> mapOf(
                "isMember" to value.isMember,
                "level" to value.level?.let { mapOf("code" to it.code, "name" to it.name, "expirationDate" to it.expirationDate) },
            )
            is List<*> -> value.map { promotion(it as LoyaltyPromotion) }
            else -> error("unexpected result $value")
        }
    }

    @Before
    fun setUp() {
        fixtures.start(ApplicationProvider.getApplicationContext())
        loyalty = LoyaltyApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    @Test
    fun `each method makes the contract's request, promo codes as a Rails array`() {
        assertEquals(5, fixtures.checkRequests(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `wrong arguments are invalidArgument without a request`() {
        assertEquals(7, fixtures.checkErrors(FILE, invoke, NAMESPACE))
    }

    @Test
    fun `every answer of the contract reads into the method's model or its error`() {
        assertTrue(fixtures.checkAnswers(FILE, invoke, NAMESPACE, json) >= 15)
    }

    @Test
    fun `an empty list of promo codes is not sent, and chargeBonuses false is`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"status":"success","payload":{"promotions":[]}}"""))

        runBlocking { withTimeout(10_000) { loyalty.getPromotions(promoCodes = emptyList(), chargeBonuses = false) } }

        val url = fixtures.server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals(setOf("charge_bonuses"), url.queryParameterNames - NamespaceFixtures.COMMON_FIELDS)
        assertEquals("false", url.queryParameter("charge_bonuses"))
    }

    @Test
    fun `a time outside HH colon MM of a day is invalidArgument without a request`() {
        listOf("9:30", "24:00", "12:60", "12:30:00", "").forEach { time ->
            val error = try {
                runBlocking { loyalty.getPromotions(currentTime = time) }
                null
            } catch (e: SdkException) {
                e.error
            }
            assertTrue("$time: $error", error is SdkError.InvalidArgument)
        }
        assertNull(fixtures.server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a promotion without a whole id is left out`() {
        val body = JSONObject("""{"status":"success","payload":{"promotions":[{"name":"A"},{"id":"x"},{"id":2.0,"name":"B"}]}}""")

        assertEquals(listOf(2L), LoyaltyApiImpl.readPromotions(body)?.map { it.id })
    }

    @Test
    fun `a birthday is an existing date of the calendar as YYYY-MM-DD`() {
        listOf("1990-05-17", "2000-02-29", "0999-12-31", "3000-01-01").forEach { assertTrue(it, isCalendarDate(it)) }
        listOf(
            "1990-5-17", "17.05.1990", "1990-02-30", "2001-02-29", "1900-02-29", "1990-13-01", "1990-00-10", "1990-05-00",
            "1990-05-17T00:00", " 1990-05-17", "", "１９９０-05-17",
        ).forEach { assertFalse(it, isCalendarDate(it)) }
        // profile.set takes the years 1000 to 2999 only.
        listOf("1000-01-01", "2999-12-31").forEach { assertTrue(it, isBirthday(it)) }
        listOf("0999-12-31", "3000-01-01", "2001-02-29").forEach { assertFalse(it, isBirthday(it)) }
    }

    private fun promotion(promotion: LoyaltyPromotion): Map<String, Any?> = mapOf(
        "id" to promotion.id, "name" to promotion.name, "description" to promotion.description,
        "startDate" to promotion.startDate, "finishDate" to promotion.finishDate, "stream" to promotion.stream,
        "badgeLabel" to promotion.badgeLabel, "badgeColor" to promotion.badgeColor,
        "rewardType" to when (promotion.rewardType) {
            LoyaltyRewardType.DISCOUNT -> "discount"
            LoyaltyRewardType.BONUSES_EARN -> "bonusesEarn"
            LoyaltyRewardType.BONUSES_USAGE -> "bonusesUsage"
            null -> null
        },
    )

    private companion object {
        const val SHOP = "loyalty-shop"
        const val FILE = "loyalty"
        const val NAMESPACE = "loyalty"
    }
}
