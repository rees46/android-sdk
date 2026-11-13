package com.personalization.core.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where the core runs its work: network and disk on [io], app callbacks on [main]. Tests pass test
 * dispatchers to run the core step by step.
 *
 * [main] is looked up on first use: [Dispatchers.Main] loads the platform's main dispatcher, which a
 * process that never delivers a callback (a push in a cold start, a unit test) should not pay for.
 */
internal class CoreDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    main: () -> CoroutineDispatcher = { Dispatchers.Main },
    val default: CoroutineDispatcher = Dispatchers.Default,
) {
    val main: CoroutineDispatcher by lazy(main)

    companion object {
        val DEFAULT = CoreDispatchers()
    }
}
