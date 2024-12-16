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
            /* expected = */ HTTPS_SCHEME + TEST_EXAMPLE_DOMAIN,
            /* actual = */ DomainFormattingUtils.formatApiDomain(apiDomain = TEST_EXAMPLE_DOMAIN)
        )
    }

    @Test
    fun returnsDomainUnchangedWithHttpsScheme() {
        assertEquals(
            /* expected = */ HTTPS_TEST_URL,
            /* actual = */ DomainFormattingUtils.formatApiDomain(apiDomain = HTTPS_TEST_URL)
        )
    }

    @Test
    fun returnsDomainUnchangedWithHttpScheme() {
        assertEquals(
            /* expected = */ HTTP_TEST_URL,
            /* actual = */ DomainFormattingUtils.formatApiDomain(apiDomain = HTTP_TEST_URL)
        )
    }

    @Test
    fun removesTrailingSlashesFromDomain() {
        val domain = "$TEST_EXAMPLE_DOMAIN////"
        val formattedDomain = DomainFormattingUtils.formatApiDomain(apiDomain = domain)
        assertEquals(
            /* expected = */ HTTPS_SCHEME + TEST_EXAMPLE_DOMAIN,
            /* actual = */ formattedDomain
        )
    }

    @Test
    fun throwsExceptionForBlankDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("")
        }
        assertEquals(
            /* expected = */ BLANK_DOMAIN_MESSAGE,
            /* actual = */ exception.message
        )
    }

    @Test
    fun throwsExceptionForInvalidDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DomainFormattingUtils.formatApiDomain("ftp://example.com")
        }
        assertEquals(
            /* expected = */ INVALID_SCHEME_MESSAGE + "ftp://example.com",
            /* actual = */ exception.message
        )
    }

    companion object {
        private const val HTTPS_TEST_URL = "https://example.com"
        private const val HTTP_TEST_URL = "http://example.com"
        private const val TEST_EXAMPLE_DOMAIN = "example.com"
        private const val HTTPS_SCHEME = "https://"
        private const val INVALID_SCHEME_MESSAGE = "Invalid domain: "
        private const val BLANK_DOMAIN_MESSAGE = "API domain cannot be blank"
    }
}
