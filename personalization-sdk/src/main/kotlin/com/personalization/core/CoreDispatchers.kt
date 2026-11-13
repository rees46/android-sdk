package com.personalization.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where the core runs its work: network and disk on [io], app callbacks on [main]. Tests pass test
 * dispatchers to run the core step by step.
 */
internal class CoreDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val main: CoroutineDispatcher = Dispatchers.Main,
    val default: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        val DEFAULT = CoreDispatchers()
    }
}
