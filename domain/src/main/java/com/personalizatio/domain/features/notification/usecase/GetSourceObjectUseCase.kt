package com.personalizatio.domain.features.notification.usecase

import com.personalizatio.data.repository.notification.SourceRepository
import javax.inject.Inject

class GetSourceObjectUseCase @Inject constructor(
    private val sourceRepository: SourceRepository
) {

    operator fun invoke(
        timeDuration: Int
    ) = sourceRepository.getJsonObject(
        timeDuration = timeDuration
    )
}
