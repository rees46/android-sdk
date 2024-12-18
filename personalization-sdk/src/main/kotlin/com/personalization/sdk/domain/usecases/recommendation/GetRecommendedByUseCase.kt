package com.personalization.sdk.domain.usecases.recommendation

import com.personalization.sdk.domain.models.RecommendedBy
import com.personalization.sdk.domain.repositories.RecommendationRepository
import javax.inject.Inject

class GetRecommendedByUseCase @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) {

    operator fun invoke(): RecommendedBy? {
        return recommendationRepository.getRecommendedBy()
    }
}
