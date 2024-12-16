package com.personalization.utils

object DomainFormattingUtils {

    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
    private const val INVALID_SCHEME_MESSAGE = "Invalid domain: "
    private const val BLANK_DOMAIN_MESSAGE = "API domain cannot be blank"

    fun formatApiDomain(apiDomain: String): String {
        require(apiDomain.isNotBlank()) { BLANK_DOMAIN_MESSAGE }

        if (apiDomain.contains("://") && !apiDomain.startsWith(HTTP_SCHEME) && !apiDomain.startsWith(
                HTTPS_SCHEME
            )
        ) {
            throw IllegalArgumentException("$INVALID_SCHEME_MESSAGE$apiDomain")
        }

        val domainWithoutTrailingSlashes = apiDomain.trimEnd('/')

        return if (domainWithoutTrailingSlashes.startsWith(HTTP_SCHEME) || domainWithoutTrailingSlashes.startsWith(
                HTTPS_SCHEME
            )
        ) {
            domainWithoutTrailingSlashes
        } else {
            "$HTTPS_SCHEME$domainWithoutTrailingSlashes"
        }
    }
}
