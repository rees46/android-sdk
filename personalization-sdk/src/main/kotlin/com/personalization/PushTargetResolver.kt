package com.personalization

/**
 * Pure decision behind push routing (Release 4): which shop a push belongs to.
 *
 * The payload's `shop_id` names the target; with no `shop_id`, a single-instance app still works
 * (the one live shop is used). A `shop_id` that names no live instance, or an absent `shop_id` while
 * several shops are live, resolves to null — the push is dropped rather than delivered to the wrong
 * shop. Side-effect-free so the routing rules can be tested without dispatching a push.
 */
internal object PushTargetResolver {

    fun resolve(payloadShopId: String?, liveShopIds: Set<String>): String? {
        if (payloadShopId != null) {
            return payloadShopId.takeIf { it in liveShopIds }
        }
        return liveShopIds.singleOrNull()
    }
}
