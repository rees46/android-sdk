package com.personalization.sdk.data.repositories.recommendation

import com.personalization.sdk.domain.models.RecommendedBy
import javax.inject.Inject

class RecommendationDataSourceImpl @Inject constructor() : RecommendationDataSource {

    private var recommendedBy: RecommendedBy? = null

    override fun getRecommendedBy(): RecommendedBy? = recommendedBy

    override fun setRecommendedBy(recommendedBy: RecommendedBy?) {
        this.recommendedBy = recommendedBy
    }
}
