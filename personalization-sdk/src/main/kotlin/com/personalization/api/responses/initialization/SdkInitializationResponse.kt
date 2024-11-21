package com.personalization.api.responses.initialization

data class SdkInitializationResponse(
    val did: String,
    val seance: String,
    val currency: String,
    val emailCollector: Boolean,
    val hasEmail: Boolean,
    val recommendations: Boolean,
    val lazyLoad: Boolean,
    val autoCssRecommender: Boolean,
    val cms: String,
    val snippets: List<String>,
    val popup: Popup?,
    val search: Search?,
    val webPushSettings: WebPushSettings?,
    val recone: Boolean
)
