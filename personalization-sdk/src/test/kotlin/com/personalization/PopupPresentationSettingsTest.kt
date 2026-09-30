package com.personalization

import com.personalization.features.inAppNotification.impl.PopupPresentation
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The public popup settings on [SDK] and what they hand the popup manager. The graph is stood in for
 * by assigning [SDK.popupPresentation] directly — a real initialize would make a network call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupPresentationSettingsTest {

    @Test
    fun `settings can be made before the instance is initialized`() {
        val sdk = SDK()

        sdk.popupPresentationListener = PopupPresentationListener { _, _ -> null }
        sdk.enableAutoPopupPresentation = false
    }

    @Test
    fun `the listener is handed the instance it was set on`() {
        val sdk = SDK().apply { popupPresentation = PopupPresentation() }
        var seen: SDK? = null

        sdk.popupPresentationListener = PopupPresentationListener { instance, _ ->
            seen = instance
            null
        }
        val activity = sdk.popupPresentation.listener!!.invoke(popup())

        assertSame(sdk, seen)
        assertNull(activity)
    }

    @Test
    fun `turning automatic presentation off reaches the popup manager`() {
        val sdk = SDK().apply { popupPresentation = PopupPresentation() }

        sdk.enableAutoPopupPresentation = false

        assertFalse(sdk.popupPresentation.autoPresentation)
    }

    @Test
    fun `clearing the listener hands popups back to the SDK`() {
        val sdk = SDK().apply { popupPresentation = PopupPresentation() }
        sdk.popupPresentationListener = PopupPresentationListener { _, _ -> null }

        sdk.popupPresentationListener = null

        assertNull(sdk.popupPresentation.listener)
    }

    private fun popup() = PopupDto(
        id = 7,
        channels = emptyList(),
        position = Position.CENTERED,
        delay = 0,
        html = "",
        components = null,
        webPushSystem = false,
        popupActions = null
    )
}
