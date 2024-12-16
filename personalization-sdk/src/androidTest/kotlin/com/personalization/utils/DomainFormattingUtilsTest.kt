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
        assertEquals(
            HTTPS_SCHEME + TEST_EXAMPLE_DOMAIN,
            DomainFormattingUtils.formatApiDomain(TEST_EXAMPLE_DOMAIN)
        )
    }

    @Test
    fun returnsDomainUnchangedWithHttpsScheme() {
        assertEquals(
            TEST_EXAMPLE_HTTPS_URL,
            DomainFormattingUtils.formatApiDomain(TEST_EXAMPLE_HTTPS_URL)
        )
    }

    @Test
    fun returnsDomainUnchangedWithHttpScheme() {
        assertEquals(
            /* expected = */ TEST_EXAMPLE_HTTP_URL,
            /* actual = */ DomainFormattingUtils.formatApiDomain("http://example.com")
        )
    }

    @Test
    fun removesTrailingSlashesFromDomain() {
        val domain = "$TEST_EXAMPLE_DOMAIN////"
        val formattedDomain = DomainFormattingUtils.formatApiDomain(domain)
        assertEquals(HTTPS_SCHEME + TEST_EXAMPLE_DOMAIN, formattedDomain)
    }

    @Test
    fun throwsExceptionForBlankDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("")
        }
        assertEquals(BLANK_DOMAIN_MESSAGE, exception.message)
    }

    @Test
    fun throwsExceptionForInvalidDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("ftp://example.com")
        }
        assertEquals(INVALID_SCHEME_MESSAGE + "ftp://example.com", exception.message)
    }

    companion object {
        private const val TEST_EXAMPLE_HTTPS_URL = "https://example.com"
        private const val TEST_EXAMPLE_HTTP_URL = "http://example.com"
        private const val TEST_EXAMPLE_DOMAIN = "example.com"
        private const val HTTP_SCHEME = "http://"
        private const val HTTPS_SCHEME = "https://"
        private const val INVALID_SCHEME_MESSAGE = "Invalid domain: "
        private const val BLANK_DOMAIN_MESSAGE = "API domain cannot be blank"
    }
}
