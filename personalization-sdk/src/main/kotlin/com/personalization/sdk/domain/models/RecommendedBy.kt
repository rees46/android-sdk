package com.personalization.sdk.domain.models

data class RecommendedBy(
    val type: TYPE,
    var code: String? = null
) {
    enum class TYPE(val value: String) {
        RECOMMENDATION("dynamic"),
        TRIGGER("chain"),
        BULK("bulk"),
        TRANSACTIONAL("transactional"),
        INSTANT_SEARCH("instant_search"),
        FULL_SEARCH("full_search"),
        STORIES("stories"),
    }
}
