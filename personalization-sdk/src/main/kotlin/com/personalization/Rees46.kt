package com.personalization

import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import android.content.Context
import android.os.Bundle
import com.personalization.core.BridgeApi
import com.personalization.core.BridgePushListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushPayload
import com.personalization.core.PushTokenListener
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.PushHub
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.StorageMigrations
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

    /** A shop registered and not yet made, with the core it asked for. */
    private data class PendingInit(val context: Context, val config: Rees46Config, val coreV2: Boolean)

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
     *
     * The instance asks for the legacy core. The process has one core, decided by the first
     * [initialize], [register], [initializeBridge] or [registerBridge]: an instance that asks for the other
     * one runs on the process's and logs `core_flag_mismatch`.
     */
    fun initialize(context: Context, config: Rees46Config): SDK = initialize(context, config, coreV2 = false)

    /**
     * For the bridges: [initialize] on the new core. The SDK's Flutter and React Native bridges start their
     * shops here; apps use [initialize]. The rule of one core per process stands: a process whose core is
     * the legacy one already (an [initialize] or [register] came first) runs this instance on it too and
     * logs `core_flag_mismatch`. So a bridge does not [register] its shops, which asks for the legacy core:
     * it starts each shop here, a lazy one on its first use, and registers with [registerBridge] the shops of
     * a process its own code may never reach.
     *
     * [segment] is the A/B group the bridge kept for this device before it ran on the native SDK (React
     * Native kept `A` or `B` for each shop). The start takes it while the device has no A/B group of its
     * own, so the device stays in the group the server knows it by; a device that has one keeps it, and a
     * different [segment] is logged as `invalid_config` (`segment_kept`). A value the SDK never gives, any
     * but `A` and `B`, is not taken and logged as `invalid_config` (`segment_invalid`). Neither throws.
     * The shop's group is [SDK.getSegment]: once that is the bridge's, the bridge may stop passing it.
     *
     * Hidden from Java, which knows no opt-in: the bridges are Kotlin.
     *
     * The new core differs from the legacy one in:
     * - its network client: requests go through OkHttp, a request without a network fails at once,
     *   `release()` cancels the instance's requests, and a failure without an answer is `onError(-1)` (no
     *   connection used to be `504`), as is a `2xx` whose body is not JSON (it used to succeed empty);
     * - its `/init`: repeated with growing pauses until it gets an answer; a shop that answers `402`,
     *   `403`, `404` or another `4xx` is refused ([com.personalization.core.SdkState.REFUSED]) until a
     *   later launch's `/init` succeeds, and every request of a refused shop fails with `onError(402)`;
     * - a request waits until the instance is ready, within its own read timeout, instead of going out
     *   without a `did`: a new device's first requests fail with `onError(-1)` if `/init` does not answer
     *   in time;
     * - [Rees46Config.needReInitialization] really gives the device a new `did`; [Rees46Config.stream]
     *   goes with `/init` too; the advertising id is read and sent only with
     *   [Rees46Config.sendAdvertisingId];
     * - its session: the `sid` is the server's, and a session two hours without events ends — the next
     *   request or event sends `/init` first; requests with a result no longer extend it;
     * - its event queue: tracking events (`/push`, `/push/custom`, `track/stories`, `popup/showed`) are
     *   kept on disk and sent in order, repeated after failures and after a restart, for up to 7 days;
     *   their listeners hear the server's answer once the event went, or the error that dropped it, and an
     *   empty id is refused at once; [SDK.flush] sends the queue now; the stored tracking source goes on
     *   `/push` only, and a popup in an event's answer is shown only within 10 s of the event and in the
     *   foreground;
     * - cookies: the new core's requests never send or keep a cookie. The legacy core's requests, and those
     *   of the stories v3 and popups v3 clients on either core, follow the app's global
     *   `java.net.CookieHandler`: an app that installs one (`CookieHandler.setDefault`) has them keep the
     *   cookies the server sets, the API's year-long `did` cookie among them, and send them back.
     */
    @BridgeApi
    @JvmSynthetic
    fun initializeBridge(context: Context, config: Rees46Config, segment: String? = null): SDK {
        segment?.let { offerSegment(context, config, it) }
        return initialize(context, config, coreV2 = true)
    }

    /**
     * For the bridges: [register] on the new core, without `eagerInit`. The shops are registered, not
     * made, and nothing goes to the network. A bridge calls it at a process start its own code (Dart, JS)
     * may never reach — a process FCM or HMS started only to deliver a push — so that the push can be
     * routed to its shop at no `/init`:
     * - a push for a registered shop brings up an instance only for the push: its event goes with the
     *   shop's stored `did` (or [Rees46Config.deviceId], or the saved `/init` answer's), with no `/init`,
     *   token, profile or popup; a shop without a `did` drops the push (`push_dropped`, `no_did`);
     * - the bridge's [initializeBridge] of the shop, or an [instance] of it, makes the full instance with its
     *   `/init`, in place of the push's, whose events in flight still go.
     *
     * It decides the process's core as [initializeBridge] does: in a process whose core is the legacy one
     * already, the shops run on it and `core_flag_mismatch` is logged. A shop that has a live instance keeps
     * it. Hidden from Java, as [initializeBridge].
     */
    @BridgeApi
    @JvmSynthetic
    fun registerBridge(context: Context, configs: List<Rees46Config>) =
        register(context, configs, eagerInit = false, coreV2 = true)

    /**
     * [initialize] asking for the core of [coreV2]: the new one for [initializeBridge], which tests use
     * through here too. The process's core decides ([CoreSelection]).
     */
    internal fun initialize(context: Context, config: Rees46Config, coreV2: Boolean): SDK = synchronized(lock) {
        val existing = SdkRegistry.byShopId(config.shopId)
        if (existing != null && !existing.pushOnly) {
            // The instance runs on the process's core: one that asks for the other hears it.
            CoreSelection.resolve(coreV2, config.shopId)
            existing.keepConfig(config)
            pending.remove(config.shopId)
            return existing.handedToApp()
        }
        val sdk = SDK()
        // A shop brought up only to track a push gives way to the full instance once it is registered; the
        // push's tracking in flight still gets through.
        sdk.initializeWith(context, config, coreV2)
        // Removed once the instance is registered: a lookup meanwhile still finds the shop.
        pending.remove(config.shopId)
        sdk
    }

    /**
     * Registers [configs] without initializing them. Initialization happens lazily on the first
     * [instance] for a shop — the region case, where only the current region is needed. Pass
     * [eagerInit] = true to initialize every shop up front — the super-shop case, where instances must
     * stay consistent.
     *
     * The shops ask for the legacy core, and a registration decides the process's core as [initialize]
     * does.
     */
    fun register(
        context: Context,
        configs: List<Rees46Config>,
        eagerInit: Boolean = false
    ) = register(context, configs, eagerInit, coreV2 = false)

    /** [register] asking for the core of [coreV2]: the new one for [registerBridge], which tests use through here too. */
    internal fun register(
        context: Context,
        configs: List<Rees46Config>,
        eagerInit: Boolean = false,
        coreV2: Boolean,
    ) {
        val appContext = context.applicationContext
        // A lazily registered shop initializes on first use, usually after the first activity
        // resumed. The startup initializer covers that; this covers hosts that turned it off.
        ForegroundActivity.install(appContext)
        configs.forEach { config ->
            // A registration decides the process's core as an initialize does.
            CoreSelection.resolve(coreV2, config.shopId)
            if (eagerInit) {
                initialize(appContext, config, coreV2)
            } else {
                pending[config.shopId] = PendingInit(appContext, config, coreV2)
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
                appInstance(resolution.shopId) ?: throw UnknownShopIdException(missingMessage(resolution.shopId))

            is InstanceResolver.Resolution.Pending ->
                appInstance(resolution.shopId) ?: throw UnknownShopIdException(missingMessage(resolution.shopId))

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
                appInstance(resolution.shopId)?.let(onReady)
                Cancellable.NOOP
            }

            is InstanceResolver.Resolution.Pending -> {
                appInstance(resolution.shopId)?.let(onReady)
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
     * Hands a push to the shop it belongs to and returns that shop's id, or null when it is not the SDK's
     * or no shop takes it. Never throws. The SDK's messaging services call it, and so does an app that has
     * its own `FirebaseMessagingService` or `HmsMessageService`, or draws and handles the notifications
     * itself.
     *
     * 1. A push of the SDK has a `type` and an `id`, the message's code (a tap's intent extras
     *    `NOTIFICATION_TYPE` and `NOTIFICATION_ID` are read too); without them it is not the SDK's
     *    (`push_dropped`, `not_sdk_push`).
     * 2. The shop is the push's `shop_id`; without one, the only registered shop. An unknown shop, or no
     *    `shop_id` while several shops are registered, drops the push (`push_dropped`) instead of handing it
     *    to the wrong shop. A shop registered without `eagerInit` is brought up just to send the event: the
     *    app may have been started by the push itself.
     * 3. The event goes to the shop. On the new core ([initializeBridge]) through its queue to
     *    `track/delivered`, `track/opened`, `track/clicked` or `track/closed`, once per message in a process:
     *    a repeat sends nothing and still returns the shop. A tap also sets the tracking source for 48 hours,
     *    which the `/push` events carry as `source`. The legacy core sends a delivery to `track/received`
     *    and a tap to `track/clicked`, as before.
     * 4. The shop's callback of the event runs on the main thread, on the new core once the queue accepted
     *    the event: the one of [SDK.setPushCallbacks], or else the one of [setPushCallbacks]. An event the
     *    queue refused (the instance released, the shop refused) calls none. For [PushEvent.DELIVERED] the
     *    old listeners ([setOnMessageListener], [SDK.setOnMessageListener]) hear the push too, as before.
     */
    fun handlePush(payload: Map<String, String>, event: PushEvent): String? = try {
        route(payload, event)
    } catch (e: Exception) {
        SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "handlePush: the push could not be handled", payload[PreferencesPartition.SHOP_ID_FIELD], throwable = e)
        null
    }

    private fun route(data: Map<String, String>, event: PushEvent): String? {
        val parsed = PushPayload.parse(data)
        if (parsed == null) {
            // Not the values: they carry the push's content.
            SdkLogs.log(
                LogLevel.DEBUG,
                LogCodes.PUSH_DROPPED,
                "handlePush: the push has no id or type, it is not a push of the SDK",
                data[PreferencesPartition.SHOP_ID_FIELD],
                mapOf("reason" to LogReasons.NOT_SDK_PUSH, "keys" to data.keys.sorted()),
            )
            return null
        }
        val (resolution, shopId) = resolvePushTarget(data[PreferencesPartition.SHOP_ID_FIELD]) ?: return null
        logHandedOver(shopId, event.name)
        // Kept before it is shown: a notification the SDK draws for it reports the whole push for its shop.
        val payload = parsed.withShop(shopId)
        PushHub.remember(payload)
        // Shown first, as before: the process-global listener needs no instance.
        if (event == PushEvent.DELIVERED) SdkRegistry.dispatchMessage(shopId = shopId, data = data.toNotificationData())
        val target = targetInstance(resolution, shopId)
        if (target == null) {
            logDropped(shopId, LogReasons.UNKNOWN_SHOP)
            return null
        }
        if (target.dropsPushWithoutDid()) return null
        // The callbacks hear the event once it is taken: on the new core, once a queue accepted it. Those of
        // the instance that took it: one that took a push-only instance's place takes its event too.
        val tell = { taker: SDK -> PushHub.event(event, payload, taker.pushCallbacks) }
        when {
            event == PushEvent.DELIVERED -> target.onPushReceived(data, tell)
            // The legacy core's tap, as it always went: from the extras of a tap's intent.
            event == PushEvent.CLICKED && target.core?.isV2 != true -> {
                target.notificationClicked(tapExtras(parsed.code, parsed.type))
                tell(target)
            }
            else -> target.pushEvent(event, parsed.code, parsed.type, tell)
        }
        return shopId
    }

    /**
     * The form of [handlePush] before [PushEvent]: [PushEventType.RECEIVED] is [PushEvent.DELIVERED] and
     * [PushEventType.CLICKED] is [PushEvent.CLICKED].
     *
     * A `RECEIVED` push is **shown first, with no initialization**: the parsed notification is dispatched
     * to the process-global listener ([setOnMessageListener]) so it appears even for a shop that is
     * registered but not yet initialized — the cold-process case, where FCM/HMS start the app just to
     * deliver a data-only message and only eagerly-initialized shops are live. As before, any push the
     * shop resolves reaches that listener, one without `id` and `type` too. Tracking the delivery then runs
     * on the shop's own network, bringing a pending shop up with a light init ([materializeForPush]); a
     * failure to track never undoes the shown notification. The legacy core sends it to `track/received`,
     * the new core to `track/delivered`.
     */
    @Deprecated(
        message = "Use handlePush(payload, PushEvent): it returns the shop that took the push, and knows OPENED and CLOSED.",
        replaceWith = ReplaceWith("Rees46.handlePush(payload, com.personalization.core.PushEvent.DELIVERED)", "com.personalization.Rees46"),
    )
    fun handlePush(payload: Map<String, String>, event: PushEventType) {
        when (event) {
            PushEventType.RECEIVED -> receivePush(payload)
            PushEventType.CLICKED -> clickPush(payload)
        }
    }

    /** A push the SDK's messaging services got: [handlePush] with [PushEventType.RECEIVED]. */
    internal fun receivePush(payload: Map<String, String>) {
        val (resolution, shopId) = resolvePushTarget(payload[PreferencesPartition.SHOP_ID_FIELD]) ?: return
        logHandedOver(shopId, PushEventType.RECEIVED.name)
        val parsed = PushPayload.parse(payload)?.withShop(shopId)
        // Kept before it is shown: a notification the SDK draws for it reports the whole push for its shop.
        parsed?.let(PushHub::remember)
        // Show first — the process-global listener needs no instance, so a pending shop's push still
        // appears. Then track the delivery on the shop's own network (light bring-up if it isn't live);
        // a tracking/init failure must never undo the shown notification.
        SdkRegistry.dispatchMessage(shopId = shopId, data = payload.toNotificationData())
        runCatching {
            val target = targetInstance(resolution, shopId) ?: return@runCatching
            if (target.dropsPushWithoutDid()) return@runCatching
            target.onPushReceived(payload) { taker -> parsed?.let { PushHub.event(PushEvent.DELIVERED, it, taker.pushCallbacks) } }
        }.onFailure { SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "handlePush: the delivery was not tracked", shopId, throwable = it) }
    }

    /**
     * A tap in the old form: the legacy core tracks it from the payload's `NOTIFICATION_TYPE` and
     * `NOTIFICATION_ID`, as before; the new core as [handlePush] with [PushEvent.CLICKED].
     */
    private fun clickPush(payload: Map<String, String>) {
        val (resolution, shopId) = resolvePushTarget(payload[PreferencesPartition.SHOP_ID_FIELD]) ?: return
        logHandedOver(shopId, PushEventType.CLICKED.name)
        runCatching {
            val target = targetInstance(resolution, shopId) ?: return@runCatching
            if (target.dropsPushWithoutDid()) return@runCatching
            val parsed = PushPayload.parse(payload)?.withShop(shopId)
            parsed?.let(PushHub::remember)
            if (parsed != null && target.core?.isV2 == true) {
                target.pushEvent(PushEvent.CLICKED, parsed.code, parsed.type) { taker -> PushHub.event(PushEvent.CLICKED, parsed, taker.pushCallbacks) }
            } else {
                target.notificationClicked(payload.toBundle())
                parsed?.let { PushHub.event(PushEvent.CLICKED, it, target.pushCallbacks) }
            }
        }.onFailure { SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "handlePush: the click was not tracked", shopId, throwable = it) }
    }

    /**
     * The app's callbacks on the pushes of every shop, the registered and not yet created among them:
     * [PushPayload.shopId] tells the shop. A shop's own callback of an event ([SDK.setPushCallbacks]) wins
     * over the one here. Replaces the callbacks set before; null clears them. [setOnMessageListener] keeps
     * working beside them.
     */
    fun setPushCallbacks(callbacks: PushCallbacks?) {
        PushHub.callbacks = callbacks
    }

    /**
     * For the bridges: [listener] hears every push event the SDK handles, of every shop, on the main
     * thread, beside the app's [PushCallbacks] and whatever they are. Once per event of a message, like
     * them, and on the new core once the queue accepted the event. Until the handle is cancelled.
     */
    @BridgeApi
    fun onPushEvent(listener: BridgePushListener): Cancellable = PushHub.addEventSink(listener)

    /**
     * For the bridges: [listener] hears every new push token the SDK gets from FCM or HMS, once per token
     * and provider in a process, on the main thread, beside the app's `onToken`. Until the handle is
     * cancelled.
     */
    @BridgeApi
    fun onPushToken(listener: PushTokenListener): Cancellable = PushHub.addTokenSink(listener)

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

    private fun logHandedOver(shopId: String, event: String) {
        SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_RECEIVED, "Push handed to its shop", shopId, mapOf("event" to event.lowercase()))
    }

    /** The live instance, or a pending shop brought up with a light push-context init. */
    private fun targetInstance(resolution: InstanceResolver.Resolution, shopId: String): SDK? =
        when (resolution) {
            is InstanceResolver.Resolution.Pending -> materializeForPush(shopId)
            else -> SdkRegistry.byShopId(shopId)
        }

    /**
     * Brings up a registered-but-pending shop just enough to send a push's event, via
     * [SDK.initializeForPush]: no `/init`, no push token, no profile, no popup; the event goes with the shop's
     * stored `did`. The app's first [instance] or [initialize] of the shop puts a full instance in its place.
     *
     * On the legacy core a shop without a `did` starts as 2.49.0 started it ([SDK.initializeForPushWithoutDid]):
     * its full instance, whose one `/init` gives the `did` the push's `track/received` waits for, without the
     * push token and the profile. The app then gets it as it is, and they go when it takes it.
     */
    private fun materializeForPush(shopId: String): SDK? = synchronized(lock) {
        SdkRegistry.byShopId(shopId)?.let { return it }
        val pendingInit = pending[shopId] ?: return null
        if (!CoreSelection.resolve(pendingInit.coreV2, shopId) && !legacyHasDid(pendingInit.context, pendingInit.config)) {
            return SDK().apply { initializeForPushWithoutDid(pendingInit.context, pendingInit.config) }.also { pending.remove(shopId) }
        }
        SDK().apply { initializeForPush(pendingInit.context, pendingInit.config, pendingInit.coreV2) }.also { pending.remove(shopId) }
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

    /** The extras of a tap's intent, as the SDK's notification helpers name them: what the legacy core reads. */
    private fun tapExtras(code: String, type: String): Bundle = Bundle().apply {
        putString(PushPayload.CLICK_TYPE, type)
        putString(PushPayload.CLICK_ID, code)
    }

    private fun Map<String, String>.toBundle(): Bundle =
        Bundle().also { bundle -> forEach { (key, value) -> bundle.putString(key, value) } }

    /**
     * The app's own instance of [shopId], never one brought up only for a push ([SDK.initializeForPush]):
     * the live full instance, or one made now with a full start (`/init`, the push token, everything) — from
     * the pending registration, or in place of the push-only instance, whose push events in flight still go.
     * A full instance a push started gets what it left for the app ([SDK.handedToApp]). Null when the shop has
     * neither (released meanwhile).
     */
    internal fun appInstance(shopId: String): SDK? {
        SdkRegistry.byShopId(shopId)?.takeUnless { it.pushOnly }?.let { return it.handedToApp() }
        return synchronized(lock) {
            // Another caller may have made it meanwhile, or a push brought the shop up for itself.
            val live = SdkRegistry.byShopId(shopId)
            if (live != null && !live.pushOnly) return live.handedToApp()
            val pendingInit = pending[shopId]
            when {
                pendingInit != null -> initialize(pendingInit.context, pendingInit.config, pendingInit.coreV2)
                // Its config is set before it registers: an instance found in the registry has one.
                live != null -> live.config?.let { initialize(live.context, it, live.coreV2Requested) }
                else -> null
            }
        }
    }

    private fun missingMessage(shopId: String): String =
        "No shop is registered for shopId=$shopId. " +
            "Call Rees46.initialize(...) or Rees46.register(...) first."

    /**
     * Whether the legacy core has a `did` for [config]'s shop without `/init`: stored — the data of older
     * releases read into its partition first — or brought in [Rees46Config.deviceId] and taken. Reads the disk.
     */
    private fun legacyHasDid(context: Context, config: Rees46Config): Boolean {
        val layout = StorageLayout(config.shopId, config.storageKey)
        StorageMigrations.run(context, layout)
        val storage = ShopStorage(context, layout)
        return storage.storedDid().isNotEmpty() || storage.takesConfiguredDid(config.deviceId)
    }

    /**
     * The A/B group a bridge brings to [initializeBridge]: offered to the shop's one-shot move of its data to
     * the device level ([StorageMigrations]), which takes it while neither the device nor the shop has a
     * group. Runs before the shop's start, whose own run of the migrations then finds them done. Reads and
     * writes the disk.
     */
    private fun offerSegment(context: Context, config: Rees46Config, segment: String) {
        if (segment !in DeviceStorage.SEGMENTS) {
            SdkLogs.log(
                LogLevel.WARNING,
                LogCodes.INVALID_CONFIG,
                "initializeBridge: the A/B group is not one the SDK gives; it is not taken",
                config.shopId,
                mapOf("reason" to LogReasons.SEGMENT_INVALID),
            )
            return
        }
        val layout = StorageLayout(config.shopId, config.storageKey)
        StorageMigrations.run(context, layout, segment = segment)
        val kept = ShopStorage(context, layout).segment() ?: return
        if (kept != segment) {
            SdkLogs.log(
                LogLevel.WARNING,
                LogCodes.INVALID_CONFIG,
                "initializeBridge: the A/B group is not taken: the device keeps the one it has",
                config.shopId,
                mapOf("reason" to LogReasons.SEGMENT_KEPT),
            )
        }
    }

    private fun registeredShopIds(): List<String> =
        (SdkRegistry.shopIds() + pending.keys).sorted()

    /** Test-only: drops pending registrations. Live instances live in [SdkRegistry]. */
    internal fun reset() = pending.clear()

    /** Test-only: shops registered lazily and not yet initialized. */
    internal fun pendingShopIds(): Set<String> = pending.keys.toSet()
}
