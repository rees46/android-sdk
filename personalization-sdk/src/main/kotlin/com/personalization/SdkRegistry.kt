package com.personalization

import com.personalization.sdk.data.models.dto.notification.NotificationData
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
 * whatever thread called `initialize`. All routing-state mutations run under [lock], so a
 * registration and a concurrent [onNextRegister] cannot interleave and lose a notification. Callback
 * invocation is deliberately kept outside the lock — a host's `onReady` may run arbitrary code and
 * re-enter the registry.
 */
internal object SdkRegistry {

    private val instances = CopyOnWriteArrayList<SDK>()
    private val byShop = ConcurrentHashMap<String, SDK>()
    private val awaiting = CopyOnWriteArrayList<Awaiter>()

    /** Guards the compound routing-state updates (register / subscribe / unregister / reset). */
    private val lock = Any()

    @Volatile
    private var current: SDK? = null

    /**
     * Process-global push-message listener behind [Rees46.setOnMessageListener]. Read from the
     * messaging threads that route a push; a single registration receives every shop's pushes, so a
     * host does not have to set a listener on each instance. `@Volatile` — set on one thread, read on
     * the delivery threads.
     */
    @Volatile
    private var messageListener: OnShopMessageListener? = null

    private class Awaiter(val shopId: String?, val onReady: (SDK) -> Unit)

    /**
     * Records an initialized [sdk] for [shopId]: it joins the push fan-out set and becomes the
     * current default. Re-registering the same instance keeps a single entry; a new instance for an
     * already-known [shopId] replaces the mapping (last writer wins) and evicts the superseded
     * instance from the fan-out set, so a re-init does not leave the old object receiving push tokens.
     */
    fun register(shopId: String, sdk: SDK) {
        val toNotify: List<Awaiter>
        synchronized(lock) {
            instances.remove(sdk)
            instances.add(sdk)
            // A re-init builds a new SDK for the same shop: drop the one it supersedes, or it lingers
            // in the fan-out set forever — still fed push tokens and inflating all()/count().
            val previous = byShop.put(shopId, sdk)
            if (previous != null && previous !== sdk) {
                instances.remove(previous)
            }
            current = sdk
            toNotify = takeMatchingAwaiters(shopId)
        }
        toNotify.forEach { it.onReady(sdk) }
    }

    /** Must be called while holding [lock]. Removes and returns the awaiters matching [shopId]. */
    private fun takeMatchingAwaiters(shopId: String): List<Awaiter> {
        if (awaiting.isEmpty()) return emptyList()
        val matched = awaiting.filter { it.shopId == null || it.shopId == shopId }
        if (matched.isNotEmpty()) awaiting.removeAll(matched)
        return matched
    }

    /**
     * Subscribes to the next [register] matching [shopId] (null matches the first registration of any
     * shop). Re-checks the live state under [lock] first: if the instance already arrived — e.g. a
     * registration landed between the caller resolving state and reaching here — it fires [onReady]
     * immediately instead of waiting for a signal that has already passed. Returns a handle that
     * removes the subscription; call it when the waiter goes away (e.g. a view detaches) so the
     * callback is not leaked.
     */
    fun onNextRegister(shopId: String?, onReady: (SDK) -> Unit): Cancellable {
        val alreadyLive: SDK?
        val awaiter: Awaiter?
        synchronized(lock) {
            alreadyLive = if (shopId != null) byShop[shopId] else current
            if (alreadyLive == null) {
                awaiter = Awaiter(shopId, onReady)
                awaiting.add(awaiter)
            } else {
                awaiter = null
            }
        }
        return if (alreadyLive != null) {
            onReady(alreadyLive)
            Cancellable.NOOP
        } else {
            val added = awaiter!!
            Cancellable { synchronized(lock) { awaiting.remove(added) } }
        }
    }

    /**
     * Drops [sdk] from the fan-out set and the shop mapping. `current` is intentionally left as-is,
     * matching the legacy `release()` semantics (it only removed the instance from the fan-out set);
     * multi-instance will revisit how the default is chosen after a release.
     */
    fun unregister(sdk: SDK) = synchronized(lock) {
        instances.remove(sdk)
        byShop.entries.removeAll { it.value === sdk }
        Unit
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
    fun currentOrLazy(): SDK = synchronized(lock) { current ?: SDK().also { current = it } }

    /** Sets (or clears, with null) the process-global push-message listener. */
    fun setMessageListener(listener: OnShopMessageListener?) {
        messageListener = listener
    }

    /**
     * Delivers a routed push to the process-global listener, if one is set. Called from
     * [SDK.receiveMessage] once the push has been resolved to [shopId]. Invoked outside [lock] — the
     * host callback may run arbitrary code.
     */
    fun dispatchMessage(shopId: String, data: NotificationData) {
        messageListener?.onMessage(shopId, data)
    }

    /** Resolves the instance registered for [shopId], or null if none — the multi-instance hook. */
    fun byShopId(shopId: String): SDK? = byShop[shopId]

    /** Shop ids with a live, initialized instance. */
    fun shopIds(): Set<String> = byShop.keys.toSet()

    /** Number of initialized instances currently registered. */
    fun count(): Int = instances.size

    /** Clears all state. Test-only: the registry is a process singleton. */
    internal fun reset() = synchronized(lock) {
        instances.clear()
        byShop.clear()
        awaiting.clear()
        current = null
        messageListener = null
    }
}
