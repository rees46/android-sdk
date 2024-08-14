package com.personalization.sdk.domain.usecases.network

import com.personalization.sdk.domain.repositories.NetworkRepository
import javax.inject.Inject

class ExecuteQueueTasksUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

    fun invoke() {
        networkRepository.executeQueueTasks()
    }
}
