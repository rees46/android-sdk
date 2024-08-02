package com.personalization.data.repositories.recommendation

import com.personalization.domain.models.RecommendedBy
import javax.inject.Inject

class RecommendationDataSource @Inject constructor() {

    private var recommendedBy: RecommendedBy? = null

    fun getRecommendedBy(): RecommendedBy? = recommendedBy

    fun setRecommendedBy(recommendedBy: RecommendedBy?) {
        this.recommendedBy = recommendedBy
    }
}
