package com.personalization

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.params.SearchParams
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.core.BridgeApi
import com.personalization.core.BridgeErrors
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkResult
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.NotInitializedStubs
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The public managers of an instance not initialized: reading one does not throw any more, and a call of it
 * runs nothing and tells its callbacks `notInitialized`. `initialize` puts the real ones in their place.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class NotInitializedManagersTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
    }

    @After
    fun tearDown() {
        SdkRegistry.all().forEach { it.release() }
        CoreSelection.reset()
        SdkRegistry.reset()
        Rees46.reset()
        SdkLogs.reset()
    }

    @Test
    fun `every public manager of an interface type can be read before initialize`() {
        val sdk = SDK()

        listOf(
            sdk.recommendationManager, sdk.productsManager, sdk.cartManager, sdk.tracking, sdk.trackEventManager,
            sdk.searchManager, sdk.predictManager, sdk.ordersManager, sdk.loyaltyManager, sdk.profileManager,
            sdk.categoryManager, sdk.collectionManager, sdk.inAppNotificationManager, sdk.NPSRepository,
        ).forEach { manager ->
            assertNotNull(manager)
            assertTrue(manager.toString(), NotInitializedStubs.isStub(manager))
        }
        assertTrue(NotInitializedStubs.isStub(sdk.tracking.queued))
    }

    @Test
    fun `a call of a stub tells its callbacks notInitialized and sends nothing`() {
        val sdk = SDK()
        val heard = mutableListOf<Pair<Int, SdkError?>>()
        val listener = object : OnApiCallbackListener() {
            override fun onError(code: Int, msg: String?) {
                heard += code to BridgeErrors.currentError()
            }
        }
        val errors = mutableListOf<Int>()
        val results = mutableListOf<Any>()

        sdk.tracking.productView("37", listener = listener)
        sdk.tracking.addToCart(TrackingItem("37"), listener = listener)
        sdk.trackEventManager.trackEvent("e", null, null, null, null, null, listener)
        sdk.NPSRepository.review(5, "app", "c", null, null, listener)
        sdk.searchManager.searchFull("boots", SearchParams(), { results += it }, { code, _ -> errors += code })
        sdk.recommendationManager.getRecommendation("block", Params(), { results += it }, { code, _ -> errors += code })
        sdk.cartManager.getClientShoppingCartContent({ results += it }, { code, _ -> errors += code })

        assertEquals(List(4) { -1 to SdkError.NotInitialized }, heard)
        assertEquals(listOf(-1, -1, -1), errors)
        assertEquals(emptyList<Any>(), results)
    }

    @Test
    fun `a queued event of a stub completes with notInitialized on the main thread`() {
        val sdk = SDK()
        val ends = mutableListOf<Pair<SdkResult<Unit>, Boolean>>()

        sdk.tracking.queued.productView("37", listener = CompletionListener { ends += it to (Looper.myLooper() == Looper.getMainLooper()) })
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(SdkResult.Failure(SdkError.NotInitialized) to true), ends)
    }

    @Test
    fun `the instance's own methods over the managers do not throw before initialize either`() {
        val sdk = SDK()

        sdk.trackEvent("e")
        sdk.presentPopup(PopupDto(1, emptyList(), Position.CENTERED, 0, "", null, false, null))
        sdk.dismissCurrentPopup()
        assertEquals(null, sdk.getPushToken(PushProvider.FCM))
    }

    @Test
    fun `initialize puts the real managers in`() {
        val sdk = Rees46.initialize(context, Rees46Config(shopId = "stubs-shop", apiDomain = "http://127.0.0.1:9", autoSendPushToken = false))

        assertFalse(NotInitializedStubs.isStub(sdk.tracking))
        assertFalse(NotInitializedStubs.isStub(sdk.searchManager))
        assertFalse(NotInitializedStubs.isStub(sdk.inAppNotificationManager))
        assertFalse(NotInitializedStubs.isStub(sdk.NPSRepository))
    }
}
