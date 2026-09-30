package com.personalization.ui.components

import android.app.Activity
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** A toast lands over the window content, one at a time, and leaves by itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationToastTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val content: ViewGroup = activity.findViewById(android.R.id.content)

    @Test
    fun `shows over the content at the chosen edge`() {
        val toast = PersonalizationToast.show(content, "Copied", PersonalizationToast.Position.TOP)

        assertNotNull(toast)
        assertSame(content, toast!!.parent)
        assertEquals("Copied", toast.text.toString())
        val params = toast.layoutParams as FrameLayout.LayoutParams
        assertEquals(Gravity.CENTER_HORIZONTAL or Gravity.TOP, params.gravity)
    }

    @Test
    fun `a new toast replaces the one still on screen`() {
        PersonalizationToast.show(content, "Copied")
        val second = PersonalizationToast.show(content, "Code copied to clipboard")

        assertEquals(listOf(second), toasts())
    }

    @Test
    fun `leaves by itself after the duration`() {
        PersonalizationToast.show(content, "Copied", durationMs = 2000L)

        shadowOf(Looper.getMainLooper()).idleFor(1900, TimeUnit.MILLISECONDS)
        assertEquals(1, toasts().size)

        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        assertEquals(0, toasts().size)
    }

    @Test
    fun `a toast the host put in its layout is not taken for a shown one`() {
        val own = PersonalizationToast(activity).apply { text = "Static" }
        content.addView(own)

        PersonalizationToast.show(content, "Copied")

        assertEquals(2, toasts().size)
        assertSame(content, own.parent)
    }

    private fun toasts(): List<PersonalizationToast> =
        (0 until content.childCount).map { content.getChildAt(it) }.filterIsInstance<PersonalizationToast>()
}
