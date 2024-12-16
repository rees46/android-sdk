package com.personalization.utils

/**
 * Utility class for formatting API domain names by ensuring they follow a specific format:
 * - If the domain does not contain a scheme (http:// or https://), it will default to https://
 * - If the domain contains a valid scheme (http:// or https://), it will be returned as-is
 * - If the domain contains any other scheme (e.g., ftp://), an exception is thrown
 * - Any trailing slashes at the end of the domain are removed
 *
 * This utility helps ensure that API domain names are always returned in a standardized format
 */
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
     * @param apiDomain The input domain to be formatted
     * @return The formatted domain
     * @throws IllegalArgumentException If the domain is blank or contains an unsupported scheme
     */
    fun formatApiDomain(apiDomain: String): String {

        // Ensure the domain is not blank, else throw an exception with an appropriate message
        require(value = apiDomain.isNotBlank()) { BLANK_DOMAIN_MESSAGE }

        // Check if the domain contains a scheme (e.g., http://, https://)
        val hasScheme = apiDomain.contains("://")

        // Check if the scheme is valid (http:// or https://)
        val isValidScheme = apiDomain.startsWith(HTTP_SCHEME) || apiDomain.startsWith(HTTPS_SCHEME)

        // If the domain has a scheme, but it's not http:// or https://, throw an exception
        if (hasScheme && !isValidScheme) {
            throw IllegalArgumentException(s = "$INVALID_SCHEME_MESSAGE$apiDomain")
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
