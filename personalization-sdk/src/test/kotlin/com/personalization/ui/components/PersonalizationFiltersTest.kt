package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dozens of facets do not push Apply off the screen: the title and the buttons stay put and only
 * the sections scroll. Without a set height (the screen inside a host's scroll) it is as tall as
 * its content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationFiltersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `many sections scroll between the title and the buttons that stay on screen`() {
        val filters = filters(sections = 30)

        filters.measure(exact(720), exact(1200))
        filters.layout(0, 0, 720, 1200)

        assertEquals(1200, filters.measuredHeight)
        assertTrue(bottomIn(filters, button(filters, "Apply")) <= 1200)
        assertTrue(bottomIn(filters, button(filters, "Reset")) <= 1200)
    }

    @Test
    fun `without a set height the screen is as tall as its sections`() {
        val few = filters(sections = 1)
        val many = filters(sections = 10)

        few.measure(exact(720), unspecified())
        many.measure(exact(720), unspecified())

        assertTrue(many.measuredHeight > few.measuredHeight)
    }

    private fun filters(sections: Int) = PersonalizationFilters(context).apply {
        text = "Filters"
        resetText = "Reset"
        applyText = "Apply"
        setSections((1..sections).map { index ->
            PersonalizationFilters.Section.Options(
                "facet$index",
                "Facet $index",
                (1..3).map { PersonalizationFilters.Option("$index-$it", "Value $it") }
            )
        })
    }

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun unspecified() = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    private fun button(root: View, text: String): PersonalizationButton =
        all(root).filterIsInstance<PersonalizationButton>().single { it.text == text }

    private fun all(root: View): List<View> = when (root) {
        is ViewGroup -> listOf(root) + (0 until root.childCount).flatMap { all(root.getChildAt(it)) }
        else -> listOf(root)
    }

    private fun bottomIn(root: View, view: View): Int {
        var top = 0
        var current: View = view
        while (current !== root) {
            top += current.top
            current = current.parent as View
        }
        return top + view.height
    }
}
