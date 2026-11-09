package com.personalization.ui.components

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two children share a line while they fit; otherwise the second one moves under the first.
 * Sizes are fixed views, so the cases do not depend on Robolectric's font metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationWrapRowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `children that fit share the line and the pinned one sits at the right edge`() {
        val first = Fixed(100, 40)
        val second = Fixed(80, 20)
        val row = row(pinEnd = true, first, second)

        layOut(row, width = 300)

        assertFalse(row.isWrapped)
        assertEquals(40, row.measuredHeight)
        assertEquals(220, second.left)
        assertEquals(10, second.top)
    }

    @Test
    fun `without the pin the second child follows the first after the gap`() {
        val first = Fixed(100, 20)
        val second = Fixed(80, 20)
        val row = row(pinEnd = false, first, second)

        layOut(row, width = 300)

        assertFalse(row.isWrapped)
        assertEquals(108, second.left)
    }

    @Test
    fun `children that do not fit wrap, the pinned one to the right under the first`() {
        val first = Fixed(100, 40)
        val second = Fixed(80, 20)
        val row = row(pinEnd = true, first, second)

        layOut(row, width = 180)

        assertTrue(row.isWrapped)
        assertEquals(40 + 4 + 20, row.measuredHeight)
        assertEquals(100, second.left)
        assertEquals(44, second.top)
    }

    @Test
    fun `a wrapped child without the pin starts at the left edge`() {
        val first = Fixed(100, 20)
        val second = Fixed(80, 20)
        val row = row(pinEnd = false, first, second)

        layOut(row, width = 150)

        assertTrue(row.isWrapped)
        assertEquals(0, second.left)
        assertEquals(24, second.top)
    }

    @Test
    fun `a hidden second child leaves the first alone on its line`() {
        val first = Fixed(100, 40)
        val second = Fixed(80, 20).apply { visibility = View.GONE }
        val row = row(pinEnd = true, first, second)

        layOut(row, width = 120)

        assertFalse(row.isWrapped)
        assertEquals(40, row.measuredHeight)
    }

    @Test
    fun `a child wider than the row is narrowed to it`() {
        val first = Fixed(100, 20)
        val second = Fixed(500, 20)
        val row = row(pinEnd = true, first, second)

        layOut(row, width = 200)

        assertTrue(row.isWrapped)
        assertEquals(200, second.width)
        assertEquals(0, second.left)
    }

    @Test
    fun `wrapping above puts the second child over the first`() {
        val first = Fixed(100, 30)
        val second = Fixed(80, 20)
        val row = PersonalizationWrapRow(context, 8, 4, pinEnd = false, wrapAbove = true).apply {
            addView(first)
            addView(second)
        }

        layOut(row, width = 150)

        assertTrue(row.isWrapped)
        assertEquals(0, second.top)
        assertEquals(24, first.top)
        assertEquals(0, first.left)
    }

    @Test
    fun `bottom alignment lines children up by their bottoms`() {
        val first = Fixed(100, 30)
        val second = Fixed(80, 20)
        val row = PersonalizationWrapRow(context, 8, 4, pinEnd = false, alignBottom = true).apply {
            addView(first)
            addView(second)
        }

        layOut(row, width = 300)

        assertFalse(row.isWrapped)
        assertEquals(first.bottom, second.bottom)
    }

    private fun row(pinEnd: Boolean, vararg children: View) =
        PersonalizationWrapRow(context, horizontalGap = 8, verticalGap = 4, pinEnd = pinEnd).apply {
            children.forEach { addView(it) }
        }

    private fun layOut(row: View, width: Int) {
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    }

    /** A view of a fixed natural size that still respects an AT_MOST width. */
    private inner class Fixed(private val w: Int, private val h: Int) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
                MeasureSpec.UNSPECIFIED -> w
                else -> minOf(w, MeasureSpec.getSize(widthMeasureSpec))
            }
            setMeasuredDimension(width, h)
        }
    }
}
