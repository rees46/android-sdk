package com.personalization

import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import android.content.Context
import android.os.Bundle
import com.personalization.core.internal.CoreSelection
import com.personalization.features.inAppNotification.impl.ForegroundActivity
import com.personalization.features.notification.data.mapper.toNotificationData
import java.util.concurrent.ConcurrentHashMap

/**
 * Public entry point for the SDK — the unified, multi-instance API.
 *
 * A host no longer keeps its own reference to the SDK: initialize (or register) shops here and reach
 * them by `shopId` through [instance]. One instance per shop, each with isolated storage and state.
 *
 * ```kotlin
 * // Single shop:
 * Rees46.initialize(context, Rees46Config(shopId = "SHOP_ID"))
 * Rees46.instance().tracking.productView("37")
 *
 * // Several shops, initialized lazily on first use:
 * Rees46.register(context, listOf(Rees46Config("shop-a"), Rees46Config("shop-b")))
 * Rees46.instance("shop-a").tracking.productView("37")
 * ```
 */
object Rees46 {

    private data class PendingInit(val context: Context, val config: Rees46Config)

    private val pending = ConcurrentHashMap<String, PendingInit>()

    /** Making an instance takes it: two calls for a shop never make two instances. */
    private val lock = Any()

    /**
     * Initializes an SDK instance for [config] and returns it at once; `/init` goes in the background
     * and [SDK.state] follows it. The instance is registered, so it is also reachable via [instance].
     * Any pending registration for the same shop is cleared.
     *
     * A shop that has a live instance gets that instance back, not a second one; when [config] differs
     * from the one it was made with, `invalid_config` (`already_initialized`) is logged and the instance
     * keeps its own. After [SDK.release] a call makes a fresh instance. Calls from several threads at
     * once make one instance.
     */
    fun initialize(context: Context, config: Rees46Config): SDK = synchronized(lock) {
        val existing = SdkRegistry.byShopId(config.shopId)
        if (existing != null && !existing.pushOnly) {
            existing.keepConfig(config)
            pending.remove(config.shopId)
            return existing
        }
        val sdk = SDK()
        sdk.initializeWith(context, config)
        // A shop brought up only to track a push gives way to the full instance; the push's tracking
        // in flight still gets through.
        existing?.giveWay()
        // Removed once the instance is registered: a lookup meanwhile still finds the shop.
        pending.remove(config.shopId)
        sdk
    }

    /**
     * Registers [configs] without initializing them. Initialization happens lazily on the first
     * [instance] for a shop — the region case, where only the current region is needed. Pass
     * [eagerInit] = true to initialize every shop up front — the super-shop case, where instances must
     * stay consistent.
     */
    fun register(
        context: Context,
        configs: List<Rees46Config>,
        eagerInit: Boolean = false
    ) {
        val appContext = context.applicationContext
        // A lazily registered shop initializes on first use, usually after the first activity
        // resumed. The startup initializer covers that; this covers hosts that turned it off.
        ForegroundActivity.install(appContext)
        configs.forEach { config ->
            // A registration decides the process's core as an initialize does.
            CoreSelection.resolve(config.coreV2, config.shopId)
            if (eagerInit) {
                initialize(appContext, config)
            } else {
                pending[config.shopId] = PendingInit(appContext, config)
            }
        }
    }

    /** The former name of [register]. */
    @Deprecated(
        message = "Renamed to register.",
        replaceWith = ReplaceWith("Rees46.register(context, configs, eagerInit)", "com.personalization.Rees46")
    )
    fun registerShops(
        context: Context,
        configs: List<Rees46Config>,
        eagerInit: Boolean = false
    ) = register(context, configs, eagerInit)

    /** The former name of [instance]. */
    @Deprecated(
        message = "Renamed to instance.",
        replaceWith = ReplaceWith("Rees46.instance(shopId)", "com.personalization.Rees46")
    )
    fun getInstance(shopId: String? = null): SDK = instance(shopId)

    /**
     * Returns the SDK instance for [shopId], initializing a pending registration on first use. With no
     * [shopId], returns the single instance when exactly one shop is registered.
     *
     * @throws AmbiguousShopException when [shopId] is null and more than one shop is registered.
     * @throws UnknownShopIdException when the shop is unknown — nothing registered, or no such shop.
     */
    fun instance(shopId: String? = null): SDK {
        val resolution = InstanceResolver.resolve(
            requestedShopId = shopId,
            liveShopIds = SdkRegistry.shopIds(),
            pendingShopIds = pending.keys.toSet()
        )
        return when (resolution) {
            is InstanceResolver.Resolution.Existing ->
                SdkRegistry.byShopId(resolution.shopId)
                    ?: throw UnknownShopIdException(missingMessage(resolution.shopId))

            is InstanceResolver.Resolution.Pending -> materialize(resolution.shopId)

            InstanceResolver.Resolution.NotRegistered -> throw UnknownShopIdException(
                if (shopId != null) {
                    missingMessage(shopId)
                } else {
                    "No shop has been registered. Call Rees46.initialize(...) or Rees46.register(...) first."
                }
            )

            InstanceResolver.Resolution.Ambiguous -> throw AmbiguousShopException(
                "More than one shop is registered — call Rees46.instance(shopId) with an explicit id. " +
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
     * @throws AmbiguousShopException when [shopId] is null and more than one shop is already
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

            InstanceResolver.Resolution.Ambiguous -> throw AmbiguousShopException(
                "More than one shop is registered — call Rees46.awaitInstance(shopId) with an " +
                    "explicit id. Registered: ${registeredShopIds()}."
            )

            // Nothing matches yet: wake up on the first matching registration. A named shop that is
            // neither live nor pending is most likely a forgotten registration or a typo — the wait
            // is legitimate (it may register later) but silent, so leave a breadcrumb. A null shopId
            // waiting for the first-ever registration is the normal single-shop startup race and is
            // not warned about.
            InstanceResolver.Resolution.NotRegistered -> {
                if (shopId != null) {
                    SdkLogs.log(
                        LogLevel.WARNING,
                        LogCodes.INVALID_ARGUMENT,
                        "Rees46.awaitInstance: no shop with this id is initialized or registered. " +
                            "Waiting for it to register — if it never is (typo, or a missing " +
                            "Rees46.initialize/register), onReady will never fire.",
                        shopId
                    )
                }
                SdkRegistry.onNextRegister(shopId, onReady)
            }
        }
    }

    /**
     * Routes a push to the shop it belongs to. The target is the payload's `shop_id`; with no `shop_id`
     * a single registered shop still resolves, but an unknown shop — or an absent `shop_id` while
     * several shops are registered — drops the push instead of delivering it to the wrong one.
     *
     * A `RECEIVED` push is **shown first, with no initialization**: the parsed notification is dispatched
     * to the process-global listener ([setOnMessageListener]) so it appears even for a shop that is
     * registered but not yet initialized — the cold-process case, where FCM/HMS start the app just to
     * deliver a data-only message and only eagerly-initialized shops are live. Tracking the delivery
     * (`track/received`) then runs on the shop's own network, bringing a pending shop up with a light
     * init ([materializeForPush]): no `/init` round-trip, no push-token re-registration, no profile
     * call. A failure to track never undoes the shown notification.
     *
     * This is the router the SDK's own messaging services use; a host that owns its messaging service
     * calls it directly.
     */
    fun handlePush(payload: Map<String, String>, event: PushEventType) {
        when (event) {
            PushEventType.RECEIVED -> receivePush(payload)
            PushEventType.CLICKED -> clickPush(payload)
        }
    }

    private fun receivePush(payload: Map<String, String>) {
        val (resolution, shopId) = resolvePushTarget(payload[PreferencesPartition.SHOP_ID_FIELD]) ?: return
        logHandedOver(shopId, PushEventType.RECEIVED)
        // Show first — the process-global listener needs no instance, so a pending shop's push still
        // appears. Then track the delivery on the shop's own network (light bring-up if it isn't live);
        // a tracking/init failure must never undo the shown notification.
        SdkRegistry.dispatchMessage(shopId = shopId, data = payload.toNotificationData())
        runCatching { targetInstance(resolution, shopId)?.onPushReceived(payload) }
            .onFailure { SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "handlePush: the delivery was not tracked", shopId, throwable = it) }
    }

    private fun clickPush(payload: Map<String, String>) {
        val (resolution, shopId) = resolvePushTarget(payload[PreferencesPartition.SHOP_ID_FIELD]) ?: return
        logHandedOver(shopId, PushEventType.CLICKED)
        runCatching { targetInstance(resolution, shopId)?.notificationClicked(payload.toBundle()) }
            .onFailure { SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "handlePush: the click was not tracked", shopId, throwable = it) }
    }

    /**
     * Resolves the push target as ([InstanceResolver.Resolution] to shopId), or null when the push
     * should be dropped. Mirrors [instance] resolution (live wins over pending; a single registered
     * shop resolves with no `shop_id`) but drops instead of throwing on an unknown or ambiguous target.
     */
    private fun resolvePushTarget(payloadShopId: String?): Pair<InstanceResolver.Resolution, String>? {
        val resolution = InstanceResolver.resolve(
            requestedShopId = payloadShopId,
            liveShopIds = SdkRegistry.shopIds(),
            pendingShopIds = pending.keys.toSet()
        )
        return when (resolution) {
            is InstanceResolver.Resolution.Existing -> resolution to resolution.shopId
            is InstanceResolver.Resolution.Pending -> resolution to resolution.shopId
            InstanceResolver.Resolution.Ambiguous -> {
                logDropped(payloadShopId, LogReasons.AMBIGUOUS_SHOP)
                null
            }
            InstanceResolver.Resolution.NotRegistered -> {
                logDropped(payloadShopId, LogReasons.UNKNOWN_SHOP)
                null
            }
        }
    }

    private fun logDropped(payloadShopId: String?, reason: String) {
        SdkLogs.log(
            LogLevel.WARNING,
            LogCodes.PUSH_DROPPED,
            "handlePush: push dropped — no shop resolves it",
            payloadShopId,
            mapOf("reason" to reason, "live" to SdkRegistry.shopIds().size, "pending" to pending.size)
        )
    }

    private fun logHandedOver(shopId: String, event: PushEventType) {
        SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_RECEIVED, "Push handed to its shop", shopId, mapOf("event" to event.name.lowercase()))
    }

    /** The live instance, or a pending shop brought up with a light push-context init. */
    private fun targetInstance(resolution: InstanceResolver.Resolution, shopId: String): SDK? =
        when (resolution) {
            is InstanceResolver.Resolution.Pending -> materializeForPush(shopId)
            else -> SdkRegistry.byShopId(shopId)
        }

    /**
     * Brings up a registered-but-pending shop just enough to track a push, via [SDK.initializeForPush]:
     * no `/init` round-trip, no push-token re-registration, no profile call. A shop that was never
     * initialized has no did and the backend never sent it a push, so this is not reached for real
     * deliveries.
     */
    private fun materializeForPush(shopId: String): SDK? = synchronized(lock) {
        SdkRegistry.byShopId(shopId)?.let { return it }
        val pendingInit = pending[shopId] ?: return null
        SDK().apply { initializeForPush(pendingInit.context, pendingInit.config) }.also { pending.remove(shopId) }
    }

    /**
     * Sets a single process-global listener invoked for every received push, on whatever shop it
     * routes to, with that shop's id. Set it once — typically in `Application.onCreate` — instead of
     * wiring a per-instance [SDK.setOnMessageListener] on every shop. Replaces any previously set
     * global listener; pass null to clear.
     *
     * Displaying the notification stays the host's job: build and post it from the delivered
     * `NotificationData` (the SDK tracks `received` on its own). Fires for both push entry points — the
     * SDK's own messaging service ([SDK.onMessage]) and a host that owns its service ([handlePush]).
     */
    fun setOnMessageListener(listener: OnShopMessageListener?) {
        SdkRegistry.setMessageListener(listener)
    }

    /**
     * Sets the process-global receiver of the SDK's log messages: each message of every shop that
     * passes that shop's [Rees46Config.logLevel], for example to forward it to the app's crash
     * reporter. The handler runs on a background thread; an exception it throws is caught. Messages
     * carry no personal data. Replaces any previously set handler; pass null to clear.
     */
    fun setLogHandler(handler: SdkLogHandler?) {
        SdkLogs.handler = handler
    }

    private fun Map<String, String>.toBundle(): Bundle =
        Bundle().also { bundle -> forEach { (key, value) -> bundle.putString(key, value) } }

    /** Initializes a pending registration for [shopId]; one that another caller initialized meanwhile is returned. */
    private fun materialize(shopId: String): SDK = synchronized(lock) {
        SdkRegistry.byShopId(shopId)?.let { return it }
        val pendingInit = pending[shopId] ?: throw UnknownShopIdException(missingMessage(shopId))
        initialize(pendingInit.context, pendingInit.config)
    }

    private fun missingMessage(shopId: String): String =
        "No shop is registered for shopId=$shopId. " +
            "Call Rees46.initialize(...) or Rees46.register(...) first."

    private fun registeredShopIds(): List<String> =
        (SdkRegistry.shopIds() + pending.keys).sorted()

    /** Test-only: drops pending registrations. Live instances live in [SdkRegistry]. */
    internal fun reset() = pending.clear()

    /** Test-only: shops registered lazily and not yet initialized. */
    internal fun pendingShopIds(): Set<String> = pending.keys.toSet()
}
