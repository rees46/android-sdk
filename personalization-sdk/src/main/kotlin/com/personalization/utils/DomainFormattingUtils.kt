package com.personalization.utils

import com.personalization.errors.InvalidDomainError

object DomainFormattingUtils {

    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
    private const val INVALID_SCHEME_MESSAGE = "Invalid domain: "
    private const val BLANK_DOMAIN_MESSAGE = "API domain cannot be blank"

    /**
     * Formats the given API domain according to the following rules:
     *
     * 1. If the domain is blank, an [IllegalArgumentException] is thrown with the message "API domain cannot be blank"
     * 2. If the domain contains an unsupported scheme (e.g., "ftp://"), an [IllegalArgumentException] is thrown
     *    with the message "Invalid domain: <apiDomain>"
     * 3. If the domain contains a valid scheme (http:// or https://), it is returned unchanged with any trailing slashes removed
     * 4. If the domain does not contain a scheme, "https://" is added as the default scheme, and trailing slashes are removed
     *
     */

    fun formatApiDomain(apiDomain: String): String {

        // Initialize the error logger
        val errorLogger = InvalidDomainError(
            tag = "DomainFormatting",
            functionName = "formatApiDomain"
        )

        // Ensure the domain is not blank, else throw an exception with an appropriate message
        require(value = apiDomain.isNotBlank()) {
            errorLogger.logBlankDomainError()
            BLANK_DOMAIN_MESSAGE
        }

        // Check if the domain contains a scheme (e.g., http://, https://)
        val hasScheme = apiDomain.contains("://")

        // Check if the scheme is valid (http:// or https://)
        val isValidScheme = apiDomain.startsWith(HTTP_SCHEME) || apiDomain.startsWith(HTTPS_SCHEME)

        // If the domain has a scheme, but it's not http:// or https://, throw an exception
        if (hasScheme && !isValidScheme) {
            errorLogger.logInvalidDomainError(apiDomain = apiDomain)
            throw IllegalArgumentException("$INVALID_SCHEME_MESSAGE$apiDomain")
        }

        // Remove trailing slashes from the domain
        val domainWithoutTrailingSlashes = apiDomain.trimEnd('/')

        // If the domain already has a valid scheme, return it as-is
        // If not, add the "https://" scheme by default
        return when {
            isValidScheme -> domainWithoutTrailingSlashes
            else -> "$HTTPS_SCHEME$domainWithoutTrailingSlashes"
        }
    }
}
