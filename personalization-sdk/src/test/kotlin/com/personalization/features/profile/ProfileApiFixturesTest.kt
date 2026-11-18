package com.personalization.features.profile

import com.personalization.api.models.profile.Gender
import com.personalization.api.models.profile.ProfileCar
import com.personalization.api.models.profile.ProfileKid
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.SdkError
import com.personalization.core.SdkResult
import com.personalization.core.internal.NamespaceFixtures
import com.personalization.core.internal.argInt
import com.personalization.core.internal.argMap
import com.personalization.core.internal.argString
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

/** `sdk.profile` against the contract's fixtures (`contract/0/profile.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileApiFixturesTest {

    private val fixtures = NamespaceFixtures("profile-shop")
    private val changes = CopyOnWriteArrayList<Pair<String, String>>()
    private lateinit var profile: ProfileApiImpl

    @Before
    fun setUp() {
        fixtures.start()
        profile = ProfileApiImpl(fixtures.calls) { type, signature -> changes += type to signature }
    }

    @After
    fun tearDown() = fixtures.stop()

    private val invoke = NamespaceFixtures.Invoke { method, args ->
        when (method) {
            "get" -> profile.get()
            "set" -> profile.set(request(args.getJSONObject("request")))
            "getLastOrder" -> profile.getLastOrder()
            "getOrders" -> profile.getOrders(
                shopSecret = args.getString("shopSecret"),
                did = args.argString("did"),
                email = args.argString("email"),
                phone = args.argString("phone"),
                loyaltyId = args.argString("loyaltyId"),
                externalId = args.argString("externalId"),
                dateFrom = args.argString("dateFrom"),
            )
            else -> error("no profile.$method")
        }
    }

    @Test
    fun `every method makes the contract's request, or fails with invalidArgument and sends nothing`() {
        fixtures.checkRequests("profile", invoke) { MockResponse().setBody("[]") }
        fixtures.checkErrors("profile", invoke)
    }

    /** `getOrders` returns the released `Order`, whose product the platforms name apart: its case lists the fields both name alike. */
    @Test
    fun `every method reads each answer of the contract into its result or its error`() {
        fixtures.checkAnswers("profile", invoke, listed = setOf("getOrders"))
    }

    @Test
    fun `a custom property's value of a kind the API does not take is invalidArgument`() {
        listOf<Any?>(Date(), listOf(Date()), mapOf("at" to Date()), mapOf(1 to "a"), Double.NaN).forEach { value ->
            val outcome = fixtures.outcome { profile.set(ProfileUpdateRequest(customProperties = mapOf("value" to value))) }

            assertTrue("$value: $outcome", outcome is SdkResult.Failure && outcome.error is SdkError.InvalidArgument)
        }
        assertEquals(0, fixtures.server.requestCount)
    }

    @Test
    fun `set tells popups v3 of the change once the server took it, and not when it failed`() {
        fixtures.server.enqueue(MockResponse().setBody("""{"status":"success"}"""))
        fixtures.server.enqueue(MockResponse().setResponseCode(400))

        fixtures.outcome { profile.set(ProfileUpdateRequest(location = "msk")) }
        fixtures.outcome { profile.set(ProfileUpdateRequest(location = "spb")) }

        assertEquals(listOf(ProfileApiImpl.SET_PATH to """{"location":"msk"}"""), changes)
    }

    @Test
    fun `an answer to getLastOrder without a body is no order`() {
        fixtures.server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(SdkResult.Success(emptyList<Any>()), fixtures.outcome { profile.getLastOrder() })
    }

    private fun request(json: JSONObject) = ProfileUpdateRequest(
        email = json.argString("email"),
        phone = json.argString("phone"),
        externalId = json.argString("externalId"),
        loyaltyId = json.argString("loyaltyId"),
        telegramId = json.argString("telegramId"),
        fbId = json.argString("fbId"),
        vkId = json.argString("vkId"),
        firstName = json.argString("firstName"),
        lastName = json.argString("lastName"),
        gender = json.argString("gender")?.let(::gender),
        birthday = json.argString("birthday"),
        age = json.argInt("age"),
        location = json.argString("location"),
        kids = json.optJSONArray("kids")?.let { array ->
            List(array.length()) { index ->
                val kid = array.getJSONObject(index)
                ProfileKid(gender = kid.argString("gender")?.let(::gender), birthday = kid.argString("birthday"))
            }
        },
        cars = json.optJSONArray("cars")?.let { array ->
            List(array.length()) { index ->
                val car = array.getJSONObject(index)
                ProfileCar(brand = car.argString("brand"), model = car.argString("model"), vds = car.argString("vds"))
            }
        },
        customProperties = json.argMap("customProperties"),
    )

    private fun gender(name: String): Gender = Gender.entries.first { it.name.lowercase() == name }
}
