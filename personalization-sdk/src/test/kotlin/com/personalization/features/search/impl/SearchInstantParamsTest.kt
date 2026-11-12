package com.personalization.features.search.impl

import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchInstantParamsTest {

    private val params = slot<JSONObject>()
    private val network = mockk<SendNetworkMethodUseCase> {
        every { get(any(), capture(params), any()) } just runs
    }
    private val manager = SearchManagerImpl(network)

    @Test
    fun `excluded merchants are sent without locations`() {
        manager.searchInstant(
            query = "shoes",
            locations = null,
            excludedMerchants = listOf("m1", "m2"),
            excludedBrands = null,
            onSearchInstant = {},
            onError = { _, _ -> }
        )

        assertEquals("m1,m2", params.captured.getString("excluded_merchants"))
        assertFalse(params.captured.has("locations"))
    }

    @Test
    fun `excluded merchants are sent with locations`() {
        manager.searchInstant(
            query = "shoes",
            locations = "msk",
            excludedMerchants = listOf("m1"),
            excludedBrands = null,
            onSearchInstant = {},
            onError = { _, _ -> }
        )

        assertEquals("m1", params.captured.getString("excluded_merchants"))
        assertEquals("msk", params.captured.getString("locations"))
    }
}
