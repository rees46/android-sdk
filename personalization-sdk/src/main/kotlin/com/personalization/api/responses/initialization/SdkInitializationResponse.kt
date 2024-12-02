package com.personalization.api.responses.initialization

import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.WebPushSettings
import com.personalization.sdk.data.models.dto.search.Search

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
    val popupDto: PopupDto?,
    val search: Search?,
    val webPushSettings: WebPushSettings?,
    val recone: Boolean
)
