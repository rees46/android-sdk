package com.personalizatio.data.repositories.recommendation

import com.personalizatio.domain.models.RecommendedBy
import com.personalizatio.domain.repositories.RecommendationRepository
import javax.inject.Inject

class RecommendationRepositoryImpl @Inject constructor()
    : RecommendationRepository {

    private var recommendedBy: RecommendedBy? = null

    override fun getRecommendedBy(): RecommendedBy? =
        recommendedBy

    override fun setRecommendedBy(recommendedBy: RecommendedBy?) {
        this.recommendedBy = recommendedBy
    }
}
