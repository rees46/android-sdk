package com.personalization.utils

object DomainFormattingUtils {

    private val URL_REGEX = Regex("^(https?://)?.+\\..+")

    fun formatApiDomain(apiDomain: String): String {
        require(apiDomain.isNotBlank()) { "API domain cannot be blank" }

        if (!URL_REGEX.matches(apiDomain)) {
            throw IllegalArgumentException("Invalid domain: $apiDomain")
        }

        val formattedDomain = if (!apiDomain.startsWith("http://") && !apiDomain.startsWith("https://")) {
            "https://$apiDomain"
        } else {
            apiDomain
        }

        return formattedDomain.trimEnd('/')
    }
}
