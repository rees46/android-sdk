package com.personalization

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the default return values of [OnClickListener], which drive what the stories carousel
 * does when a host does not override a callback.
 *
 * [onCloseDialogClick] is the one that matters most here: [ProductsAdapter] closes the story viewer
 * when it returns true and opens the product url when it returns false. The default is false so a
 * tap on a product card opens it; a true default (as it once was) closed the story and opened
 * nothing, which read as "the story just closes on tap".
 */
class OnClickListenerDefaultsTest {

    // Uses every default — no method overridden.
    private val listener = object : OnClickListener {}

    @Test
    fun `onCloseDialogClick defaults to false so a product tap opens the url instead of closing`() {
        assertFalse(listener.onCloseDialogClick(mockk(relaxed = true), "https://example.com/p/1"))
    }

    @Test
    fun `onClick(url) defaults to true so the SDK opens links itself`() {
        assertTrue(listener.onClick("https://example.com"))
    }

    @Test
    fun `onClick(product) defaults to true so the SDK handles the product`() {
        assertTrue(listener.onClick(mockk<Product>(relaxed = true)))
    }
}
