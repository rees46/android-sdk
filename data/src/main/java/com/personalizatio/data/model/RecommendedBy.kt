package com.personalizatio.data.model

class RecommendedBy {
    enum class TYPE(val value: String) {
        RECOMMENDATION("dynamic"),
        TRIGGER("chain"),
        BULK("bulk"),
        TRANSACTIONAL("transactional"),
        INSTANT_SEARCH("instant_search"),
        FULL_SEARCH("full_search"),
        STORIES("stories"),
    }

    val type: String
    var code: String? = null

    constructor(type: TYPE) {
        this.type = type.value
    }

    constructor(type: TYPE, code: String?) {
        this.type = type.value
        this.code = code
    }
}
