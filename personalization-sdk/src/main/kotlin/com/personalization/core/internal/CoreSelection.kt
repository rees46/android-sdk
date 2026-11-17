package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

/**
 * Which core the process runs: decided by the first `initialize`, `register` or `initializeBridge`
 * (the new core), the same for every instance after it. An instance that asks for the other one gets
 * the process's and a `core_flag_mismatch` warning, once per shop (a registration and the instance it
 * makes later ask twice): switching takes a new launch.
 */
internal object CoreSelection {

    private var coreV2: Boolean? = null
    private val warned = HashSet<String?>()

    /** The process's choice; the first call makes it from [requested]. */
    fun resolve(requested: Boolean, shopId: String?): Boolean {
        val (decided, warn) = synchronized(this) {
            val decided = coreV2 ?: requested.also { coreV2 = it }
            decided to (decided != requested && warned.add(shopId))
        }
        if (warn) {
            SdkLogs.log(
                LogLevel.WARNING,
                LogCodes.CORE_FLAG_MISMATCH,
                "The instance asks for a core other than the process's; it runs on the process's",
                shopId,
                mapOf("requested" to requested, "process" to decided)
            )
        }
        return decided
    }

    /** The process's choice, without making it: true for the new core, null before the first call. */
    fun decided(): Boolean? = synchronized(this) { coreV2 }

    /** Test-only: the next call decides again. */
    internal fun reset() = synchronized(this) {
        coreV2 = null
        warned.clear()
    }
}
