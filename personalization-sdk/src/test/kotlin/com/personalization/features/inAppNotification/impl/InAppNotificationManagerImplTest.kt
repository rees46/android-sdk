package com.personalization.features.inAppNotification.impl

import android.content.Context
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.personalization.api.managers.TrackEventManager
import com.personalization.inAppNotification.view.component.dialog.alert.ALERT_DIALOG_TAG
import com.personalization.sdk.data.models.dto.popUp.CloseAction
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position
import dagger.Lazy
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Where a server popup ends up. The host no longer has to hand a FragmentManager over: the popup
 * goes to the activity on screen, and one that has nowhere to go is dropped without being counted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InAppNotificationManagerImplTest {

    private lateinit var context: Context
    private lateinit var tracking: TrackEventManager
    private lateinit var manager: InAppNotificationManagerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ForegroundActivity.install(context)
        tracking = mockk(relaxed = true)
        manager = InAppNotificationManagerImpl(context, mockk(relaxed = true), Lazy { tracking })
    }

    @Test
    fun `a popup is shown in the activity on screen`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        manager.shopPopUp(popup())
        idle()

        assertNotNull(activity.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 1) { tracking.trackPopupShown(POPUP_ID, null) }
    }

    @Test
    fun `with no activity on screen the popup is dropped and not counted`() {
        manager.shopPopUp(popup())
        idle()

        verify(exactly = 0) { tracking.trackPopupShown(any(), any()) }
    }

    @Test
    fun `a handed-over manager whose activity is gone gives way to the one on screen`() {
        val gone = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        @Suppress("DEPRECATION")
        manager.initFragmentManager(gone.get().supportFragmentManager)
        gone.pause().stop().destroy()
        val onScreen = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        manager.shopPopUp(popup())
        idle()

        assertNotNull(onScreen.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 1) { tracking.trackPopupShown(POPUP_ID, null) }
    }

    @Test
    fun `an activity that has stopped is not a place for a popup`() {
        val stopped = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        stopped.pause().stop()

        manager.shopPopUp(popup())
        idle()

        assertNull(stopped.get().supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 0) { tracking.trackPopupShown(any(), any()) }
    }

    @Test
    fun `the same popup twice within a minute is shown once`() {
        Robolectric.buildActivity(FragmentActivity::class.java).setup()

        manager.shopPopUp(popup())
        manager.shopPopUp(popup())
        idle()

        verify(exactly = 1) { tracking.trackPopupShown(POPUP_ID, null) }
    }

    @Test
    fun `a listener picks the activity, not the tracker`() {
        val picked = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val onScreen = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        manager.presentation.listener = { picked }

        manager.shopPopUp(popup())
        idle()

        assertNotNull(picked.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        assertNull(onScreen.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 1) { tracking.trackPopupShown(POPUP_ID, null) }
    }

    @Test
    fun `a popup the listener keeps back is not counted, and is offered again`() {
        val onScreen = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val offered = mutableListOf<Int>()
        manager.presentation.listener = { popup -> offered += popup.id; null }

        manager.shopPopUp(popup())
        manager.shopPopUp(popup())
        idle()

        // Kept back means not remembered either: a host drawing it itself gets every copy.
        assertEquals(listOf(POPUP_ID, POPUP_ID), offered)
        assertNull(onScreen.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 0) { tracking.trackPopupShown(any(), any()) }
    }

    @Test
    fun `a listener has the final word even with automatic presentation off`() {
        val picked = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        manager.presentation.autoPresentation = false
        manager.presentation.listener = { picked }

        manager.shopPopUp(popup())
        idle()

        assertNotNull(picked.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
    }

    @Test
    fun `with automatic presentation off and no listener nothing is shown`() {
        val onScreen = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        manager.presentation.autoPresentation = false

        manager.shopPopUp(popup())
        idle()

        assertNull(onScreen.supportFragmentManager.findFragmentByTag(ALERT_DIALOG_TAG))
        verify(exactly = 0) { tracking.trackPopupShown(any(), any()) }
    }

    @Test
    fun `a listener that throws costs the popup, not the app`() {
        Robolectric.buildActivity(FragmentActivity::class.java).setup()
        manager.presentation.listener = { error("host bug") }

        manager.shopPopUp(popup())
        idle() // would rethrow if the exception escaped the SDK's callback

        verify(exactly = 0) { tracking.trackPopupShown(any(), any()) }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun popup() = PopupDto(
        id = POPUP_ID,
        channels = emptyList(),
        position = Position.CENTERED,
        delay = 0,
        html = "",
        components = Components(
            text = "Body",
            image = "",
            button = "",
            header = "Title",
            textEnabled = "true",
            imageEnabled = "false",
            headerEnabled = "true"
        ),
        webPushSystem = false,
        popupActions = PopupActions(link = null, close = CloseAction("Close"), pushSubscribe = null)
    )

    private companion object {
        const val POPUP_ID = 7
    }
}
