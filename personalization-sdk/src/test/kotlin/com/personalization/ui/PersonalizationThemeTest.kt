package com.personalization.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.personalization.R

/**
 * Pins the two branches token resolution actually takes. Compiling proves neither:
 * whether `resolveAttribute` finds the attribute, and whether it hands back a
 * resource id or a packed value, is a runtime property of the theme.
 *
 * A host that never opts in gets the plain resource; one that applies
 * `Theme.Personalization` goes down the attribute branch and must land on the
 * same default. An override is that same branch with a different value, so the
 * branch is what matters here, not the particular colour.
 */
@RunWith(RobolectricTestRunner::class)
class PersonalizationThemeTest {

    private val application: Context = ApplicationProvider.getApplicationContext()

    private val themed: Context
        get() = ContextThemeWrapper(application, R.style.Theme_Personalization)

    @Test
    fun `colour falls back to the resource when the theme declares nothing`() {
        assertEquals(
            ContextCompat.getColor(application, R.color.personalization_brand_primary),
            PersonalizationTheme.color(application, R.color.personalization_brand_primary)
        )
    }

    @Test
    fun `colour comes through the theme attribute and keeps the default value`() {
        assertEquals(
            ContextCompat.getColor(application, R.color.personalization_brand_primary),
            PersonalizationTheme.color(themed, R.color.personalization_brand_primary)
        )
    }

    @Test
    fun `a colour carrying alpha survives the attribute branch`() {
        // button_secondary is black at 5%; a branch that dropped alpha would still
        // match on an opaque colour, so this is the one worth asserting.
        assertEquals(
            ContextCompat.getColor(application, R.color.personalization_button_secondary),
            PersonalizationTheme.color(themed, R.color.personalization_button_secondary)
        )
    }

    @Test
    fun `radius falls back to the resource when the theme declares nothing`() {
        assertEquals(
            application.resources.getDimension(R.dimen.personalization_radius_xl),
            PersonalizationTheme.radius(application, R.dimen.personalization_radius_xl),
            0.01f
        )
    }

    @Test
    fun `radius comes through the theme attribute and keeps the default value`() {
        assertEquals(
            application.resources.getDimension(R.dimen.personalization_radius_xl),
            PersonalizationTheme.radius(themed, R.dimen.personalization_radius_xl),
            0.01f
        )
    }

    @Test
    fun `typeface is the system font until a host supplies one`() {
        // Never null: components set it unconditionally, so a null here would
        // silently reset the label to the platform default instead of ours.
        assertEquals(true, PersonalizationTheme.typeface(themed, emphasized = true) != null)
        assertEquals(true, PersonalizationTheme.typeface(application, emphasized = false) != null)
    }
}
