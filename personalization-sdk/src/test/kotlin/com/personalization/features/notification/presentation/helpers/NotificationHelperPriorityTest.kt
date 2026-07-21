package com.personalization.features.notification.presentation.helpers

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.sdk.data.models.dto.notification.NotificationData
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the heads-up settings of the notification the SDK builds itself for data-mode pushes.
 *
 * The SDK's channel is IMPORTANCE_HIGH, which is what decides the behaviour from Android 8 on. This
 * class runs below that, where there are no channels: there a pop-up requires both a high priority
 * and a sound or vibration, so a push carrying neither lands silently in the shade. minSdk is 19,
 * so those devices are still in scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class NotificationHelperPriorityTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationHelper: NotificationHelper

    private val data = NotificationData(
        id = "42",
        title = "Title",
        body = "Body",
        icon = null,
        type = "bulk",
        actions = null,
        actionUrls = null,
        image = null,
        event = null
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = mockk(relaxed = true)
        notificationHelper = NotificationHelper(notificationManager)
    }

    private fun buildNotification(): Notification {
        val notificationSlot = slot<Notification>()
        notificationHelper.createNotification(context = context, data = data, images = null)
        verify { notificationManager.notify(any(), capture(notificationSlot)) }
        return notificationSlot.captured
    }

    @Test
    fun `notification is high priority so pre-Oreo devices raise a heads-up pop-up`() {
        assertEquals(Notification.PRIORITY_HIGH, buildNotification().priority)
    }

    @Test
    fun `notification requests sound and vibration, without which priority alone stays silent`() {
        val defaults = buildNotification().defaults

        assertEquals(Notification.DEFAULT_SOUND, defaults and Notification.DEFAULT_SOUND)
        assertEquals(Notification.DEFAULT_VIBRATE, defaults and Notification.DEFAULT_VIBRATE)
    }
}
