package com.personalization.core.internal

import com.personalization.core.SdkError
import org.junit.Assert.assertEquals
import org.junit.Test

/** The old `onError(code, msg)` of every [SdkError]: the codes apps already handle. */
class LegacyErrorsTest {

    @Test
    fun `every error has the old code`() {
        val expected = mapOf(
            SdkError.Http(404) to 404,
            SdkError.Http(500, serverCode = "internal") to 500,
            SdkError.Network to -1,
            SdkError.Timeout to -1,
            SdkError.Decoding to -1,
            SdkError.ShopBlocked to 402,
            SdkError.NotInitialized to -1,
            SdkError.Cancelled to -1,
            SdkError.InvalidArgument("itemId is empty") to -1,
        )

        expected.forEach { (error, code) -> assertEquals(error.toString(), code, LegacyErrors.of(error).code) }
    }

    @Test
    fun `an http error gives the old callback the answer's body, the others their message`() {
        assertEquals("""{"message":"no such card"}""", LegacyErrors.of(SdkError.Http(404), """{"message":"no such card"}""").message)
        assertEquals(SdkError.Http(404).message, LegacyErrors.of(SdkError.Http(404)).message)
        assertEquals(SdkError.Timeout.message, LegacyErrors.of(SdkError.Timeout).message)
        assertEquals("itemId is empty", LegacyErrors.of(SdkError.InvalidArgument("itemId is empty")).message)
    }
}
