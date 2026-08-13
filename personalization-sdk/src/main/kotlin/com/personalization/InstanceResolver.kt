package com.personalization

/**
 * Pure decision logic behind [Rees46.getInstance]: given the requested `shopId` (or none) and the
 * sets of live and pending shops, decides which instance to return, whether one must be lazily
 * materialized, or which error to raise. Kept side-effect-free so the resolution rules can be tested
 * without constructing or initializing an SDK.
 */
internal object InstanceResolver {

    sealed interface Resolution {
        /** An initialized instance exists for this shop — return it. */
        data class Existing(val shopId: String) : Resolution

        /** A registration exists but is not initialized yet — materialize it now. */
        data class Pending(val shopId: String) : Resolution

        /** No live instance and no registration matches — raise [UnknownShopIdException]. */
        object NotRegistered : Resolution

        /** No shopId given and more than one shop registered — raise [AmbiguousShopException]. */
        object Ambiguous : Resolution
    }

    fun resolve(
        requestedShopId: String?,
        liveShopIds: Set<String>,
        pendingShopIds: Set<String>
    ): Resolution {
        if (requestedShopId != null) {
            return when (requestedShopId) {
                in liveShopIds -> Resolution.Existing(requestedShopId)
                in pendingShopIds -> Resolution.Pending(requestedShopId)
                else -> Resolution.NotRegistered
            }
        }

        val allShopIds = liveShopIds + pendingShopIds
        return when (allShopIds.size) {
            0 -> Resolution.NotRegistered
            1 -> allShopIds.first().let { only ->
                if (only in liveShopIds) Resolution.Existing(only) else Resolution.Pending(only)
            }
            else -> Resolution.Ambiguous
        }
    }
}
