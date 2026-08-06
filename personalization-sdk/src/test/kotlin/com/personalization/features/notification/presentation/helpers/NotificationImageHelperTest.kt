package com.personalization.features.notification.presentation.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the URL parsing of the push image loader. The download itself is Glide's job and is not
 * exercised here — these cases must never reach it.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationImageHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `a push without an image reports no images and an error`() = runBlocking {
        val (images, hasError) = NotificationImageHelper.loadBitmaps(
            context = context,
            urls = null
        )

        assertTrue(images.isEmpty())
        assertTrue(hasError)
    }

    @Test
    fun `a blank image field is treated as no image, not as a URL to download`() = runBlocking {
        val (images, hasError) = NotificationImageHelper.loadBitmaps(
            context = context,
            urls = " , "
        )

        assertTrue(images.isEmpty())
        assertTrue(hasError)
    }

    @Test
    fun `padded and empty entries are dropped from the comma-separated list`() {
        val parsed = NotificationImageHelper.parseUrls(
            urls = " https://example.com/a.jpg , ,https://example.com/b.jpg,"
        )

        assertEquals(
            listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
            parsed
        )
    }
}
