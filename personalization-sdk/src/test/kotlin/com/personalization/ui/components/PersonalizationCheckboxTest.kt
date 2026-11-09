package com.personalization.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.personalization.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Цвета чекбокса: без своих — токены кита и тёмная тема системы; свои заменяют токены в любой теме. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizationCheckboxTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Что чекбокс нарисовал: заливки, рамки и галки по порядку. */
    private class Drawn : Canvas() {
        val fills = mutableListOf<Int>()
        val borders = mutableListOf<Int>()
        val glyphs = mutableListOf<Int>()

        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL) fills += paint.color else borders += paint.color
        }

        override fun drawPath(path: Path, paint: Paint) {
            glyphs += paint.color
        }
    }

    private fun draw(checkbox: PersonalizationCheckbox, state: PersonalizationCheckbox.CheckState): Drawn {
        checkbox.checkState = state
        checkbox.layout(0, 0, 20, 20)
        return Drawn().also { checkbox.draw(it) }
    }

    private fun token(id: Int) = ContextCompat.getColor(context, id)

    @Test
    fun `without own colors it draws the kit tokens`() {
        val checkbox = PersonalizationCheckbox(context)

        val off = draw(checkbox, PersonalizationCheckbox.CheckState.UNCHECKED)
        assertEquals(listOf(token(R.color.personalization_background_input)), off.fills)
        assertEquals(listOf(token(R.color.personalization_line_input)), off.borders)
        assertEquals(emptyList<Int>(), off.glyphs)

        val on = draw(checkbox, PersonalizationCheckbox.CheckState.CHECKED)
        assertEquals(listOf(token(R.color.personalization_button_primary)), on.fills)
        assertEquals(emptyList<Int>(), on.borders)
        assertEquals(listOf(token(R.color.personalization_text_light_primary)), on.glyphs)

        checkbox.isEnabled = false
        assertEquals(listOf(token(R.color.personalization_button_primary_disabled)),
            draw(checkbox, PersonalizationCheckbox.CheckState.CHECKED).fills)
        assertEquals(listOf(token(R.color.personalization_background_input_disabled)),
            draw(checkbox, PersonalizationCheckbox.CheckState.UNCHECKED).fills)
    }

    @Test
    @Config(qualifiers = "night")
    fun `without own colors it follows the dark theme`() {
        val off = draw(PersonalizationCheckbox(context), PersonalizationCheckbox.CheckState.UNCHECKED)
        assertEquals(listOf(0xFF0D0D0D.toInt()), off.fills)
        assertEquals(listOf(0x33FFFFFF), off.borders)
    }

    @Test
    @Config(qualifiers = "night")
    fun `own colors replace the tokens in the dark theme too`() {
        val checkbox = PersonalizationCheckbox(context).apply {
            checkedColor = 0xFF16A34A.toInt()
            checkmarkColor = 0xFF111827.toInt()
            uncheckedColor = 0xFFFEF3C7.toInt()
            uncheckedBorderColor = 0xFFD97706.toInt()
        }

        val off = draw(checkbox, PersonalizationCheckbox.CheckState.UNCHECKED)
        assertEquals(listOf(0xFFFEF3C7.toInt()), off.fills)
        assertEquals(listOf(0xFFD97706.toInt()), off.borders)

        val on = draw(checkbox, PersonalizationCheckbox.CheckState.CHECKED)
        assertEquals(listOf(0xFF16A34A.toInt()), on.fills)
        assertEquals(listOf(0xFF111827.toInt()), on.glyphs)

        val dash = draw(checkbox, PersonalizationCheckbox.CheckState.INDETERMINATE)
        assertEquals(listOf(0xFF16A34A.toInt()), dash.fills)
        assertEquals(listOf(0xFF111827.toInt()), dash.glyphs)
    }

    @Test
    fun `a disabled checkbox keeps the disabled fills of the kit over own colors`() {
        val checkbox = PersonalizationCheckbox(context).apply {
            checkedColor = 0xFF16A34A.toInt()
            uncheckedColor = 0xFFFEF3C7.toInt()
            isEnabled = false
        }
        assertEquals(listOf(token(R.color.personalization_button_primary_disabled)),
            draw(checkbox, PersonalizationCheckbox.CheckState.CHECKED).fills)
        assertEquals(listOf(token(R.color.personalization_background_input_disabled)),
            draw(checkbox, PersonalizationCheckbox.CheckState.UNCHECKED).fills)
    }

    @Test
    fun `colors can be set back to the tokens`() {
        val checkbox = PersonalizationCheckbox(context).apply { uncheckedColor = 0xFFFEF3C7.toInt() }
        checkbox.uncheckedColor = null
        assertEquals(listOf(token(R.color.personalization_background_input)),
            draw(checkbox, PersonalizationCheckbox.CheckState.UNCHECKED).fills)
    }
}
