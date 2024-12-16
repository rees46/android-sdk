package com.personalization.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomainFormattingUtilsTest {


    @Test
    fun formatsDomainWithoutScheme() {
        assertEquals("https://example.com", DomainFormattingUtils.formatApiDomain("example.com"))
    }

    @Test
    fun returnsDomainUnchangedWithHttpsScheme() {
        assertEquals(
            "https://example.com",
            DomainFormattingUtils.formatApiDomain("https://example.com")
        )
    }

    @Test
    fun returnsDomainUnchangedWithHttpScheme() {
        assertEquals(
            "http://example.com",
            DomainFormattingUtils.formatApiDomain("http://example.com")
        )
    }

    @Test
    fun removesTrailingSlashesFromDomain() {
        val domain = "example.com////"
        val formattedDomain = DomainFormattingUtils.formatApiDomain(domain)
        assertEquals("https://example.com", formattedDomain)
    }

    @Test
    fun throwsExceptionForBlankDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("")
        }
        assertEquals("API domain cannot be blank", exception.message)
    }


    @Test
    fun throwsExceptionForInvalidDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("ftp://example.com")
        }
        assertEquals("Invalid domain: ftp://example.com", exception.message)
    }
}
