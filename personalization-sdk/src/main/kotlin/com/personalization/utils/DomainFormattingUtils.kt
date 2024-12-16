package com.personalization.utils

object DomainFormattingUtils {

    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
    private const val INVALID_SCHEME_MESSAGE = "Invalid domain: "
    private const val BLANK_DOMAIN_MESSAGE = "API domain cannot be blank"

    fun formatApiDomain(apiDomain: String): String {
        require(apiDomain.isNotBlank()) { BLANK_DOMAIN_MESSAGE }

        val hasScheme = apiDomain.contains("://")

        val isValidScheme = apiDomain.startsWith(HTTP_SCHEME) || apiDomain.startsWith(HTTPS_SCHEME)

        if (hasScheme && !isValidScheme) {
            throw IllegalArgumentException("$INVALID_SCHEME_MESSAGE$apiDomain")
        }

        val domainWithoutTrailingSlashes = apiDomain.trimEnd('/')

        return if (isValidScheme) {
            domainWithoutTrailingSlashes
        } else {
            "$HTTPS_SCHEME$domainWithoutTrailingSlashes"
        }
    }
}
