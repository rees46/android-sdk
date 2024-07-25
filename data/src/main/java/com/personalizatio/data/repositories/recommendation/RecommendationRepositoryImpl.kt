package com.personalizatio.data.repositories.recommendation

import com.personalizatio.domain.models.RecommendedBy
import com.personalizatio.domain.repositories.RecommendationRepository
import javax.inject.Inject

class RecommendationRepositoryImpl @Inject constructor(
    private val recommendationDataSource: RecommendationDataSource
) : RecommendationRepository {

    override fun getRecommendedBy(): RecommendedBy? = recommendationDataSource.getRecommendedBy()

    override fun setRecommendedBy(recommendedBy: RecommendedBy?) {
        recommendationDataSource.setRecommendedBy(recommendedBy)
    }
}
