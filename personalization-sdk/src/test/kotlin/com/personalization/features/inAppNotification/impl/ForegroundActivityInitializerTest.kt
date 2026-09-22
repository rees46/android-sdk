package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tracker starts at process start through androidx.startup, so a shop initialized long after
 * the first activity resumed still has somewhere to show its popups. No SDK is initialized here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForegroundActivityInitializerTest {

    @Test
    fun `activities are followed from process start, before any SDK exists`() {
        ForegroundActivityInitializer().create(ApplicationProvider.getApplicationContext())

        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        assertSame(activity, ForegroundActivity.get())
    }

    @Test
    fun `an activity that stopped is let go`() {
        ForegroundActivityInitializer().create(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()

        controller.pause().stop()

        assertNull(ForegroundActivity.get())
    }
}
