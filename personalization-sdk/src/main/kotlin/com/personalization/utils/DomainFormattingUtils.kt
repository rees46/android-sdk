package com.personalization.utils

object DomainFormattingUtils {

    fun formatApiDomain(apiDomain: String): String = when {
        apiDomain.endsWith("/") -> "https://$apiDomain"
        else -> "https://$apiDomain/"
    }
}
