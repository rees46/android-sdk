package com.personalization.ui.widgets

import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GIF loop length (rules G1, G2) on the fixture shared by all four SDKs. */
class StoriesGifTest {

    /** 2x2, 3 frames, delays 10 cs / 0 cs / 50 cs, loops forever. */
    private val fixture = Base64.getDecoder().decode(
        "R0lGODlhAgACAIEAAP8AAAD/AAAA/////yH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAgACAAACAoRRACH5BAAAAAAALAAA" +
            "AAACAAIAAAICjFMAIfkEADIAAAAsAAAAAAIAAgAAAgKUVQA7"
    )

    @Test
    fun `a gif is recognised by its signature`() {
        assertTrue(StoriesGif.isGif(ByteBuffer.wrap(fixture)))
        assertTrue(StoriesGif.isGif(ByteBuffer.wrap("GIF87a....".toByteArray())))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertFalse(StoriesGif.isGif(ByteBuffer.wrap(png)))
        assertFalse(StoriesGif.isGif(ByteBuffer.wrap(ByteArray(3))))
    }

    @Test
    fun `frame delays below 20 ms count as 100 ms`() {
        assertEquals(listOf(100L, 100L, 500L), StoriesGif.frameDelaysMs(ByteBuffer.wrap(fixture)))
        assertEquals(700L, StoriesGif.loopMs(ByteBuffer.wrap(fixture)))
    }

    @Test
    fun `the buffer position does not matter and is left alone`() {
        val buffer = ByteBuffer.wrap(fixture)
        buffer.position(20)
        assertEquals(700L, StoriesGif.loopMs(buffer))
        assertEquals(20, buffer.position())
    }

    @Test
    fun `not a gif or a broken gif has no loop`() {
        assertNull(StoriesGif.loopMs(ByteBuffer.wrap("not a gif".toByteArray())))
        assertNull(StoriesGif.loopMs(ByteBuffer.wrap(fixture.copyOf(30))))
    }

    @Test
    fun `the slide length is the longer of the loop and the admin duration`() {
        assertEquals(8000L, StoriesMapping.animatedDurationMs(5000, 8000))
        assertEquals(5000L, StoriesMapping.animatedDurationMs(5000, 700))
        assertEquals(5000L, StoriesMapping.animatedDurationMs(5000, null))
        assertEquals(5000L, StoriesMapping.animatedDurationMs(5000, 0))
    }
}
