package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

/**
 * Which core the process runs: decided by the first `initialize` or `register`, the same for every
 * instance after it. An instance that asks for the other one gets the process's and a
 * `core_flag_mismatch` warning: switching takes a new launch.
 */
internal object CoreSelection {

    private var coreV2: Boolean? = null

    /** The process's choice; the first call makes it from [requested]. */
    fun resolve(requested: Boolean, shopId: String?): Boolean {
        val decided = synchronized(this) { coreV2 ?: requested.also { coreV2 = it } }
        if (decided != requested) {
            SdkLogs.log(
                LogLevel.WARNING,
                LogCodes.CORE_FLAG_MISMATCH,
                "Rees46Config.coreV2 differs from the process's core; the instance runs on the process's",
                shopId,
                mapOf("requested" to requested, "process" to decided)
            )
        }
        return decided
    }

    /** Test-only: the next call decides again. */
    internal fun reset() = synchronized(this) { coreV2 = null }
}
