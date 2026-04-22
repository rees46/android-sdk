package com.personalization.features.trackEvent.impl

import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only checks that do not require a real [org.json.JSONObject] implementation (AGP stubs break JSON in unit tests).
 */
class PurchaseTrackingJsonBuilderJvmTest {

    @Test
    fun buildOrError_rejectsEmptyOrderId() {
        val r = PurchaseTrackingJsonBuilder.buildOrError(
            PurchaseTrackingRequest(
                orderId = " ",
                orderPrice = 1.0,
                items = listOf(PurchaseItemRequest("x", 1, 1.0)),
            ),
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun buildOrError_rejectsEmptyItems() {
        val r = PurchaseTrackingJsonBuilder.buildOrError(
            PurchaseTrackingRequest(
                orderId = "a",
                orderPrice = 1.0,
                items = emptyList(),
            ),
        )
        assertTrue(r.isFailure)
    }
}
