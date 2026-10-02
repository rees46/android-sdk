package com.personalization.ui.widgets

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The viewer's helpers: timers that stand still in the background. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoriesViewerSupportTest {

    private val looper = shadowOf(Looper.getMainLooper())
    private val handler = Handler(Looper.getMainLooper())

    @Test
    fun `a frozen timer stands still and then runs out its remaining time`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.start(10_000)
        idle(4_000)

        timer.freeze(true)
        idle(60_000)
        assertEquals(0, fired)
        assertTrue(timer.isScheduled)

        timer.freeze(false)
        idle(5_999)
        assertEquals(0, fired)
        idle(1)
        assertEquals(1, fired)
        assertFalse(timer.isScheduled)
    }

    @Test
    fun `a timer started while frozen counts only after the thaw`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.freeze(true)
        timer.start(15_000)
        idle(30_000)
        assertEquals(0, fired)

        timer.freeze(false)
        idle(15_000)
        assertEquals(1, fired)
    }

    @Test
    fun `a cancelled or restarted timer does not fire for the old start`() {
        var fired = 0
        val timer = StoryTimer(handler) { fired++ }
        timer.start(10_000)
        timer.freeze(true)
        timer.cancel()
        timer.freeze(false)
        idle(20_000)
        assertEquals(0, fired)

        timer.start(10_000)
        idle(6_000)
        timer.start(10_000)
        idle(6_000)
        assertEquals(0, fired)
        idle(4_000)
        assertEquals(1, fired)
    }

    private fun idle(ms: Long) = looper.idleFor(ms, TimeUnit.MILLISECONDS)
}
