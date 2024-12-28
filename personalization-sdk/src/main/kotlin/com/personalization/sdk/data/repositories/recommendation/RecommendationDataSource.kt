package com.personalization.sdk.data.repositories.recommendation

import com.personalization.sdk.domain.models.RecommendedBy

interface RecommendationDataSource {

    fun getRecommendedBy(): RecommendedBy?

    fun setRecommendedBy(recommendedBy: RecommendedBy?)
}
