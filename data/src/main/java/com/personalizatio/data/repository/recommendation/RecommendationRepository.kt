package com.personalizatio.data.repository.recommendation

import com.personalizatio.data.model.RecommendedBy
import javax.inject.Inject

class RecommendationRepository @Inject constructor() {

    var recommendedBy: RecommendedBy? = null
}
