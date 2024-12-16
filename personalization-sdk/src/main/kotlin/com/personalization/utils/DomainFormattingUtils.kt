package com.personalization.utils

object DomainFormattingUtils {

    fun formatApiDomain(apiDomain: String): String {
        require(apiDomain.isNotBlank()) { "API domain cannot be blank" }

        if (apiDomain.startsWith("ftp://")) {
            throw IllegalArgumentException("Invalid domain: $apiDomain")
        }

        val domainWithoutTrailingSlashes = apiDomain.trimEnd('/')

        val domainWithScheme =
            if (domainWithoutTrailingSlashes.startsWith("http://") || domainWithoutTrailingSlashes.startsWith(
                    "https://"
                )
            ) {
                domainWithoutTrailingSlashes
        } else {
                "https://$domainWithoutTrailingSlashes"
            }

        if (!(domainWithScheme.startsWith("http://") || domainWithScheme.startsWith("https://"))) {
            throw IllegalArgumentException("Invalid domain: $domainWithScheme")
        }

        return domainWithScheme
    }
}
