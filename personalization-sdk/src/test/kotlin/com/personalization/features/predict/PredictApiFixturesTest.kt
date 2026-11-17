package com.personalization.features.predict

import androidx.test.core.app.ApplicationProvider
import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.core.BridgeApi
import com.personalization.core.internal.NamespaceFixtures
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
    fun `the request has no field of its own, the customer is the device's did`() {
        assertEquals(1, fixtures.checkRequests(FILE, NAMESPACE, invoke))
    }

    @Test
    fun `every answer of the contract reads into the probability or its error, 503 of the API host among them`() {
        val json = NamespaceFixtures.Json { value ->
            (value as PurchaseProbability).let { mapOf("probability" to it.probability, "clientId" to it.clientId) }
        }

        assertTrue(fixtures.checkAnswers(FILE, NAMESPACE, invoke, json) >= 10)
    }

    @Test
    fun `an answer without a body or not an object is decoding`() {
        assertEquals(null, PredictApiImpl.readProbability(null))
        assertEquals(null, PredictApiImpl.readProbability(org.json.JSONArray()))
    }

    private companion object {
        const val SHOP = "predict-shop"
        const val FILE = "predict"
        const val NAMESPACE = "predict"
    }
}
