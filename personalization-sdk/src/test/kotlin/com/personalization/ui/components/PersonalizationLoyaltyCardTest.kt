package com.personalization.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.personalization.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The card is a Wallet pass: never shorter than 370×560, and every part the host gave nothing for
 * is gone rather than drawn empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationLoyaltyCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `keeps the pass proportions`() {
        val card = PersonalizationLoyaltyCard(context).apply {
            balanceLabel = "Bonuses"
            balance = "50 550"
            code = "2000012345678"
        }
        val width = 1110

        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        assertEquals(width * 560 / 370, card.measuredHeight)
    }

    @Test
    fun `an empty card shows none of its parts`() {
        val card = PersonalizationLoyaltyCard(context)

        assertEquals(emptyList<String>(), visibleTexts(card))
        assertEquals(0, stampIcons(card).size)
        assertFalse(barcodeShown(card))
    }

    @Test
    fun `collected stamps are capped by the total`() {
        val card = PersonalizationLoyaltyCard(context).apply {
            stampsTotal = 5
            stamps = 7
        }

        assertEquals(5, stampIcons(card).size)
        assertEquals(5, stampIcons(card).count { it == R.drawable.personalization_ic_check_rosette_fill })

        card.stamps = 4

        assertEquals(
            listOf(4, 1),
            listOf(
                stampIcons(card).count { it == R.drawable.personalization_ic_check_rosette_fill },
                stampIcons(card).count { it == R.drawable.personalization_ic_rosette }
            )
        )
    }

    @Test
    fun `fields and balance show what was given`() {
        val card = PersonalizationLoyaltyCard(context).apply {
            balance = "50 550"
            fields = listOf(
                PersonalizationLoyaltyCard.Field("Owner", "Oleg"),
                PersonalizationLoyaltyCard.Field("Level", "Basic")
            )
        }

        assertEquals(listOf("50 550", "Owner", "Oleg", "Level", "Basic"), visibleTexts(card))
    }

    @Test
    fun `a code Code 128 cannot carry leaves no barcode`() {
        val card = PersonalizationLoyaltyCard(context).apply { code = "2000012345678" }
        assertTrue(barcodeShown(card))

        card.code = "номер"

        assertFalse(barcodeShown(card))
    }

    private fun visibleIn(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null && current !== root) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private fun all(root: View): List<View> = when (root) {
        is ViewGroup -> listOf(root) + (0 until root.childCount).flatMap { all(root.getChildAt(it)) }
        else -> listOf(root)
    }

    private fun visibleTexts(card: PersonalizationLoyaltyCard): List<String> =
        all(card).filterIsInstance<TextView>().filter { visibleIn(it, card) }.map { it.text.toString() }

    private fun stampIcons(card: PersonalizationLoyaltyCard): List<Int> =
        all(card).filterIsInstance<ImageView>()
            .filter { visibleIn(it, card) && it.drawable != null }
            .map { shadowOf(it.drawable).createdFromResId }

    private fun barcodeShown(card: PersonalizationLoyaltyCard): Boolean =
        all(card).filterIsInstance<PersonalizationBarcode>().single().let { visibleIn(it, card) }
}
