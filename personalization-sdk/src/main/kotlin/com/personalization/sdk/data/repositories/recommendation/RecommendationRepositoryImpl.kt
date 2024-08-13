package com.personalization.sdk.data.repositories.recommendation

import com.personalization.sdk.domain.models.RecommendedBy
import com.personalization.sdk.domain.repositories.RecommendationRepository
import javax.inject.Inject

class RecommendationRepositoryImpl @Inject constructor(
    private val recommendationDataSource: RecommendationDataSource
) : RecommendationRepository {

    override fun getRecommendedBy(): RecommendedBy? = recommendationDataSource.getRecommendedBy()

    override fun setRecommendedBy(recommendedBy: RecommendedBy?) {
        recommendationDataSource.setRecommendedBy(recommendedBy)
    }
}
