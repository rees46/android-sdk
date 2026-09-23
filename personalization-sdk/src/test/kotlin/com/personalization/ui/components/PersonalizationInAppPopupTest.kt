package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import android.view.ViewGroup.MarginLayoutParams
import com.personalization.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Empty means absent: a text button with no label is not drawn, the cross always is. Property
 * setters do not run at construction, so a popup the host never gave a label to is the case that
 * slipped through before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationInAppPopupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a popup given no labels shows the cross and no text buttons`() {
        val popup = PersonalizationInAppPopup(context)

        assertEquals(emptyList<CharSequence?>(), visibleTextButtons(popup))
        assertTrue("the cross must stay", visibleCross(popup))
    }

    @Test
    fun `only the labelled button is shown`() {
        val popup = PersonalizationInAppPopup(context).apply { actionText = "Go" }

        assertEquals(listOf<CharSequence?>("Go"), visibleTextButtons(popup))
    }

    @Test
    fun `clearing a label hides its button again`() {
        val popup = PersonalizationInAppPopup(context).apply {
            actionText = "Go"
            closeText = "Later"
        }

        popup.closeText = ""

        assertEquals(listOf<CharSequence?>("Go"), visibleTextButtons(popup))
    }

    @Test
    fun `labels survive a change of view`() {
        val popup = PersonalizationInAppPopup(context).apply { closeText = "Later" }

        popup.contentView = PersonalizationInAppPopup.ContentView.ICON

        assertEquals(listOf<CharSequence?>("Later"), visibleTextButtons(popup))
        assertTrue(visibleCross(popup))
    }

    @Test
    fun `the cross is a round overlay in the corner in every view`() {
        for (view in PersonalizationInAppPopup.ContentView.values()) {
            val popup = PersonalizationInAppPopup(context).apply { contentView = view }

            val cross = buttons(popup).single { it.iconStart != null }

            assertSame("$view: the cross lies over the content", popup, cross.parent)
            assertTrue("$view: the cross is round", cross.rounded)
        }
    }

    @Test
    fun `buttons are 12 apart in a modal and 16 in a fullscreen popup`() {
        val popup = PersonalizationInAppPopup(context).apply {
            actionText = "Go"
            closeText = "Later"
        }
        assertEquals(dimen(R.dimen.personalization_spacing_lg), gapAboveClose(popup))

        popup.presentation = PersonalizationInAppPopup.Presentation.FULLSCREEN

        assertEquals(dimen(R.dimen.personalization_spacing_xl), gapAboveClose(popup))
    }

    private fun gapAboveClose(popup: PersonalizationInAppPopup): Int =
        (buttons(popup).single { it.text == "Later" }.layoutParams as MarginLayoutParams).topMargin

    private fun dimen(id: Int): Int = context.resources.getDimensionPixelSize(id)

    private fun buttons(root: View): List<PersonalizationButton> = when (root) {
        is PersonalizationButton -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { buttons(root.getChildAt(it)) }
        else -> emptyList()
    }

    // Visible up to the popup itself: the view and every parent in between.
    private fun visibleIn(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null && current !== root) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun visibleTextButtons(popup: PersonalizationInAppPopup): List<CharSequence?> =
        buttons(popup).filter { it.iconStart == null && visibleIn(it, popup) }.map { it.text }

    private fun visibleCross(popup: PersonalizationInAppPopup): Boolean =
        buttons(popup).any { it.iconStart != null && visibleIn(it, popup) }
}
