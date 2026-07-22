package com.personalization

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide registry of [SDK] instances.
 *
 * Groundwork for multi-instance (one SDK per `shop_id`). For now it is `internal` and carries no
 * public surface: it simply owns the static routing state that used to live in [SDK]'s companion
 * (`currentInstance` + `activeInstances`), so the single-instance behaviour is unchanged while the
 * plumbing for resolving an instance by `shop_id` is in place and tested. The public
 * `Rees46.initialize/getInstance/registerShops` API will be layered on top later.
 *
 * Two pieces of state, matching what [SDK] needs:
 *  - an ordered list of initialized instances, for fanning a push token out to each ([all]);
 *  - a "current" pointer to the last initialized instance, the default target for push callbacks.
 *
 * Thread-safe: the registry is read from the process-global messaging threads and written from
 * whatever thread called `initialize`.
 */
internal object SdkRegistry {

    private val instances = CopyOnWriteArrayList<SDK>()
    private val byShop = ConcurrentHashMap<String, SDK>()

    @Volatile
    private var current: SDK? = null

    /**
     * Records an initialized [sdk] for [shopId]: it joins the push fan-out set and becomes the
     * current default. Re-registering the same instance keeps a single entry; a new instance for an
     * already-known [shopId] replaces the mapping (last writer wins).
     */
    fun register(shopId: String, sdk: SDK) {
        instances.remove(sdk)
        instances.add(sdk)
        byShop[shopId] = sdk
        current = sdk
    }

    /**
     * Drops [sdk] from the fan-out set and the shop mapping. `current` is intentionally left as-is,
     * matching the legacy `release()` semantics (it only removed the instance from the fan-out set);
     * multi-instance will revisit how the default is chosen after a release.
     */
    fun unregister(sdk: SDK) {
        instances.remove(sdk)
        byShop.entries.removeAll { it.value === sdk }
    }

    /** Snapshot of every initialized instance — the push-token fan-out set. */
    fun all(): List<SDK> = instances.toList()

    /** The current default instance, or null when nothing has been registered. */
    fun current(): SDK? = current

    /**
     * The current default, lazily creating an (uninitialized) instance if none exists. Preserves the
     * legacy `SDK.instance` fallback: a push delivered before `initialize()` ran still reaches a
     * guarded instance instead of crashing the host.
     */
    fun currentOrLazy(): SDK = current ?: SDK().also { current = it }

    /** Resolves the instance registered for [shopId], or null if none — the multi-instance hook. */
    fun byShopId(shopId: String): SDK? = byShop[shopId]

    /** Shop ids with a live, initialized instance. */
    fun shopIds(): Set<String> = byShop.keys.toSet()

    /** Number of initialized instances currently registered. */
    fun count(): Int = instances.size

    /** Clears all state. Test-only: the registry is a process singleton. */
    internal fun reset() {
        instances.clear()
        byShop.clear()
        current = null
    }
}
