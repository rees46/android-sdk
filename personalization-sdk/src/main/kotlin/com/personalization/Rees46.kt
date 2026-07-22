package com.personalization

import android.content.Context
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

            is InstanceResolver.Resolution.Pending -> {
                val pendingInit = pending.remove(resolution.shopId)
                // Lost the race — another thread already materialized it.
                if (pendingInit != null) {
                    initialize(pendingInit.context, pendingInit.config)
                } else {
                    SdkRegistry.byShopId(resolution.shopId)
                        ?: throw SdkNotInitializedException(missingMessage(resolution.shopId))
                }
            }

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
