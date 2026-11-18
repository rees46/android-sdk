package com.personalization.features.predict

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.core.BridgeApi
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.NamespaceFixtures
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `predict.getProbabilityToPurchase` against the contract's fixtures (`contract/0/predict.json`). */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PredictApiFixturesTest {

    private val fixtures = NamespaceFixtures(SHOP)
    private lateinit var predict: PredictApiImpl

    private val invoke = NamespaceFixtures.Invoke { method, _ ->
        check(method == "getProbabilityToPurchase") { "unknown method $method" }
        predict.getProbabilityToPurchase()
    }

    @Before
    fun setUp() {
        fixtures.start(ApplicationProvider.getApplicationContext())
        predict = PredictApiImpl(fixtures.calls)
    }

    @After
    fun tearDown() = fixtures.stop()

    @Test
    fun `the request goes to API v3 with no field of its own, the customer is the device's did`() {
        assertEquals(1, fixtures.checkRequests(FILE, invoke, NAMESPACE))
        assertTrue(PredictApiImpl.PATH.startsWith(CoreHttpClient.API_V3_PATH))
    }

    @Test
    fun `every answer of the contract reads into the probability or its error, the codes of API v3 among them`() {
        val json = NamespaceFixtures.Json { value ->
            (value as PurchaseProbability).let { mapOf("probability" to it.probability, "clientId" to it.clientId) }
        }

        assertTrue(fixtures.checkAnswers(FILE, invoke, NAMESPACE, json) >= 12)
    }

    @Test
    fun `an answer without a body, not an object or without a payload is decoding`() {
        assertEquals(null, PredictApiImpl.readProbability(null))
        assertEquals(null, PredictApiImpl.readProbability(org.json.JSONArray()))
        assertEquals(null, PredictApiImpl.readProbability(JSONObject("""{"probability":0.4,"client_id":1}""")))
        assertEquals(
            PurchaseProbability(0.73, "12345678901"),
            PredictApiImpl.readProbability(JSONObject("""{"status":"success","payload":{"probability":"0.73","client_id":12345678901}}""")),
        )
    }

    private companion object {
        const val SHOP = "predict-shop"
        const val FILE = "predict"
        const val NAMESPACE = "predict"
    }
}
