package com.personalization

import android.content.Context
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

/**
 * Public entry point for the SDK — the unified, multi-instance API.
 *
 * A host no longer keeps its own reference to the SDK: initialize (or register) shops here and reach
 * them by `shopId` through [getInstance]. One instance per shop, each with isolated storage and state.
 *
 * ```kotlin
 * // Single shop:
 * Rees46.initialize(context, Rees46Config(shopId = "SHOP_ID"))
 * Rees46.getInstance().track(...)
 *
 * // Several shops, initialized lazily on first use:
 * Rees46.registerShops(context, listOf(Rees46Config("shop-a"), Rees46Config("shop-b")))
 * Rees46.getInstance("shop-a").track(...)
 * ```
 */
object Rees46 {

    private data class PendingInit(val context: Context, val config: Rees46Config)

    private val pending = ConcurrentHashMap<String, PendingInit>()

    /**
     * Initializes an SDK instance for [config] immediately and returns it. The instance is registered,
     * so it is also reachable via [getInstance]. Any pending registration for the same shop is cleared.
     */
    @Suppress("DEPRECATION")
    fun initialize(context: Context, config: Rees46Config): SDK {
        val sdk = SDK()
        sdk.initialize(
            context = context,
            shopId = config.shopId,
            apiDomain = config.apiDomain,
            tag = config.tag,
            stream = config.stream,
            autoSendPushToken = config.autoSendPushToken,
            needReInitialization = config.needReInitialization,
            addTrailingSlash = config.addTrailingSlash
        )
        pending.remove(config.shopId)
        return sdk
    }

    /**
     * Registers [configs] without initializing them. Initialization happens lazily on the first
     * [getInstance] for a shop — the region case, where only the current region is needed. Pass
     * [eagerInit] = true to initialize every shop up front — the super-shop case, where instances must
     * stay consistent.
     */
    fun registerShops(
        context: Context,
        configs: List<Rees46Config>,
        eagerInit: Boolean = false
    ) {
        val appContext = context.applicationContext
        configs.forEach { config ->
            if (eagerInit) {
                initialize(appContext, config)
            } else {
                pending[config.shopId] = PendingInit(appContext, config)
            }
        }
    }

    /**
     * Returns the SDK instance for [shopId], initializing a pending registration on first use. With no
     * [shopId], returns the single instance when exactly one shop is registered.
     *
     * @throws AmbiguousSdkInstanceException when [shopId] is null and more than one shop is registered.
     * @throws SdkNotInitializedException when nothing matches — nothing registered, or no such shop.
     */
    fun getInstance(shopId: String? = null): SDK {
        val resolution = InstanceResolver.resolve(
            requestedShopId = shopId,
            liveShopIds = SdkRegistry.shopIds(),
            pendingShopIds = pending.keys.toSet()
        )
        return when (resolution) {
            is InstanceResolver.Resolution.Existing ->
                SdkRegistry.byShopId(resolution.shopId)
                    ?: throw SdkNotInitializedException(missingMessage(resolution.shopId))

            is InstanceResolver.Resolution.Pending -> materialize(resolution.shopId)

            InstanceResolver.Resolution.NotInitialized -> throw SdkNotInitializedException(
                if (shopId != null) {
                    missingMessage(shopId)
                } else {
                    "No SDK has been initialized. Call Rees46.initialize(...) or Rees46.registerShops(...) first."
                }
            )

            InstanceResolver.Resolution.Ambiguous -> throw AmbiguousSdkInstanceException(
                "More than one shop is registered — call Rees46.getInstance(shopId) with an explicit id. " +
                    "Registered: ${registeredShopIds()}."
            )
        }
    }

    /**
     * True when an instance is available for [shopId] — or, with no [shopId], when exactly one shop
     * is initialized so the default is unambiguous. A pending (registered-but-not-initialized) shop
     * is not counted as initialized.
     */
    fun isInitialized(shopId: String? = null): Boolean =
        if (shopId != null) SdkRegistry.byShopId(shopId) != null else SdkRegistry.shopIds().size == 1

    /**
     * Delivers the instance for [shopId] to [onReady] as soon as it is available — immediately if it
     * is already initialized, otherwise once it is. A pending registration is initialized on the spot.
     * With no [shopId] the single default instance is used, waiting for the first one when nothing is
     * registered yet.
     *
     * Returns a [Cancellable]; call it when the waiter goes away (e.g. a view detaches) so [onReady]
     * is not held. Lets a UI element resolve its SDK reactively instead of the host wiring it in.
     *
     * @throws AmbiguousSdkInstanceException when [shopId] is null and more than one shop is already
     *   registered — the default is ambiguous, so pass an explicit shopId.
     */
    fun awaitInstance(shopId: String? = null, onReady: (SDK) -> Unit): Cancellable {
        val resolution = InstanceResolver.resolve(
            requestedShopId = shopId,
            liveShopIds = SdkRegistry.shopIds(),
            pendingShopIds = pending.keys.toSet()
        )
        return when (resolution) {
            is InstanceResolver.Resolution.Existing -> {
                SdkRegistry.byShopId(resolution.shopId)?.let(onReady)
                Cancellable.NOOP
            }

            is InstanceResolver.Resolution.Pending -> {
                onReady(materialize(resolution.shopId))
                Cancellable.NOOP
            }

            InstanceResolver.Resolution.Ambiguous -> throw AmbiguousSdkInstanceException(
                "More than one shop is registered — call Rees46.awaitInstance(shopId) with an " +
                    "explicit id. Registered: ${registeredShopIds()}."
            )

            // Nothing registered at all: wake up on the first matching registration.
            InstanceResolver.Resolution.NotInitialized -> SdkRegistry.onNextRegister(shopId, onReady)
        }
    }

    /**
     * Routes a push to the instance it belongs to and dispatches [event]. The target is the payload's
     * `shop_id`; with no `shop_id` a single-instance app still resolves, but an unknown shop — or an
     * absent `shop_id` while several shops are live — drops the push instead of delivering it to the
     * wrong one. Call this from a host that owns its messaging service.
     */
    fun handlePush(payload: Map<String, String>, event: PushEventType) {
        val shopId = PushTargetResolver.resolve(
            payloadShopId = payload[PreferencesPartition.SHOP_ID_FIELD],
            liveShopIds = SdkRegistry.shopIds()
        ) ?: return
        val sdk = SdkRegistry.byShopId(shopId) ?: return
        when (event) {
            PushEventType.RECEIVED -> sdk.onPushReceived(payload)
            PushEventType.CLICKED -> sdk.notificationClicked(payload.toBundle())
        }
    }

    private fun Map<String, String>.toBundle(): Bundle =
        Bundle().also { bundle -> forEach { (key, value) -> bundle.putString(key, value) } }

    /** Initializes a pending registration for [shopId], tolerating a lost race with another caller. */
    private fun materialize(shopId: String): SDK {
        val pendingInit = pending.remove(shopId)
        return if (pendingInit != null) {
            initialize(pendingInit.context, pendingInit.config)
        } else {
            SdkRegistry.byShopId(shopId)
                ?: throw SdkNotInitializedException(missingMessage(shopId))
        }
    }

    private fun missingMessage(shopId: String): String =
        "No SDK is initialized or registered for shopId=$shopId. " +
            "Call Rees46.initialize(...) or Rees46.registerShops(...) first."

    private fun registeredShopIds(): List<String> =
        (SdkRegistry.shopIds() + pending.keys).sorted()

    /** Test-only: drops pending registrations. Live instances live in [SdkRegistry]. */
    internal fun reset() = pending.clear()

    /** Test-only: shops registered lazily and not yet initialized. */
    internal fun pendingShopIds(): Set<String> = pending.keys.toSet()
}
