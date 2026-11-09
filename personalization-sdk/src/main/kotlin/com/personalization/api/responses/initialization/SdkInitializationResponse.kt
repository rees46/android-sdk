package com.personalization.api.responses.initialization

import com.personalization.InternalPersonalizationV3Api
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
    val recone: Boolean,
    /** The shop has popups v3 for this channel (`popups_v3` of `/init`). Behind the v3 gate. */
    @InternalPersonalizationV3Api
    @get:JvmSynthetic
    val popupsV3: Boolean = false
)
