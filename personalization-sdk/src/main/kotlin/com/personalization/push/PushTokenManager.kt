package com.personalization.push

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.LogLevel
import android.content.Context
import com.personalization.OnPushTokenListener
import com.personalization.PushProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushToken
import com.personalization.core.internal.CorePushTokens
import com.personalization.core.internal.PushHub
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import org.json.JSONObject
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single pipeline for push tokens across all providers.
 *
 * Every token — proactively fetched at init, delivered asynchronously by a messaging service
 * ([onTokenReceived]), or pushed manually by the host ([sendToken]) — funnels through here:
 * it is deduplicated, persisted per provider, sent to the rees46 backend, and reported to the
 * host via [OnPushTokenListener]. Adding a provider only requires a new [PushTokenSource].
 *
 * A token never goes to the backend without a `did`: registered without one it would belong to no
 * device. Until the device has a `did` (a new install before `/init` answers, or the window after
 * `deleteUserCredentials()`) the request waits in the instance's queue and goes once the `did` is
 * stored; the host's listener hears of the token at once all the same.
 *
 * On the new core the rules of `core/push.md` are [CorePushTokens]'s: the token is the device's, each shop
 * sends a new one at once and the same one at most once a week, after the instance is ready. Either way
 * the app's `PushCallbacks.onToken` hears each new token once ([PushHub]).
 */
@Singleton
class PushTokenManager @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val getPreferencesValueUseCase: GetPreferencesValueUseCase,
    private val savePreferencesValueUseCase: SavePreferencesValueUseCase
) {

    private lateinit var context: Context
    private var autoSendPushToken: Boolean = false
    private var listener: OnPushTokenListener? = null

    /** The shop of the instance, so the token's log lines pass that shop's level. */
    @Volatile
    internal var logShopId: String? = null

    /** The new core's rules for this shop's token; null on the legacy core. */
    @Volatile
    internal var core: CorePushTokens? = null

    /** The instance's own push callbacks, for `onToken`. */
    @Volatile
    internal var callbacks: () -> PushCallbacks? = { null }

    /** The tokens the instance's own `onToken` heard of, by provider. */
    private val reported = ConcurrentHashMap<PushProvider, String>()

    /**
     * Tokens whose request is already on the wire, as `provider:token`.
     *
     * [shouldSendToken] compares the incoming token against the persisted one, but persistence
     * only happens in the response callback. On a fresh install the same token reaches
     * [onTokenReceived] twice almost simultaneously — once from the proactive fetch in
     * [initialize], once from the messaging service's `onNewToken` — and both see an empty cache,
     * so both send. Claiming the pair before the request leaves closes that window; the backing
     * map is concurrent, so exactly one caller wins the claim.
     */
    private val tokensInFlight: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Register only the providers whose SDK is actually on the classpath. HMS is an optional
     * (compileOnly) dependency, so a FCM-only app has no Huawei classes — referencing
     * [HmsTokenSource] there would throw NoClassDefFoundError. The guard keeps a provider off
     * unless the host opted in by adding its artifacts.
     */
    private val sources: List<PushTokenSource> = sourceFactory()

    fun setOnPushTokenListener(listener: OnPushTokenListener) {
        this.listener = listener
    }

    /** Last token known for [provider] from the local cache, or null if none: on the new core the device's. */
    fun getToken(provider: PushProvider): String? =
        core?.token(provider) ?: getPreferencesValueUseCase.getPushToken(provider).takeIf { it.isNotEmpty() }

    /**
     * Detects available providers and proactively fetches their tokens. Tokens that are only
     * delivered asynchronously (HMS) arrive later via [onTokenReceived] from the messaging service.
     */
    fun initialize(context: Context, autoSendPushToken: Boolean) {
        this.context = context
        this.autoSendPushToken = autoSendPushToken
        // The new core: what the device has goes once it is due, before a fetch brings it again.
        core?.sendStored()

        sources.filter { it.isAvailable(context) }.forEach { source ->
            source.fetchToken(context) { result ->
                result.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { token -> onTokenReceived(token, source.provider) }
                result.exceptionOrNull()?.let { error ->
                    SdkLogs.log(
                        LogLevel.WARNING,
                        LogCodes.PUSH_TOKEN_FAILED,
                        "Push token could not be fetched",
                        logShopId,
                        mapOf("provider" to source.provider.id),
                        error
                    )
                }
            }
        }
    }

    /**
     * Entry point for tokens received from a provider (proactive fetch or messaging service
     * `onNewToken`). Sends to the backend when changed, then always notifies the host listener.
     */
    fun onTokenReceived(token: String, provider: PushProvider) {
        val v2 = core
        if (token.isEmpty() || (v2 == null && !::context.isInitialized)) return
        // Never the token itself.
        SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_TOKEN_RECEIVED, "Push token received", logShopId, mapOf("provider" to provider.id))

        if (v2 != null) {
            // Kept for the device and sent once the instance is ready, even before it is.
            v2.received(token, provider)
        } else if (!autoSendPushToken) {
            // As before, not sent: the app passes the tokens it wants sent through setPushToken.
            SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_TOKEN_SKIPPED, "Push token not sent", logShopId,
                mapOf("provider" to provider.id, "reason" to LogReasons.AUTO_SEND_OFF))
        } else {
            val currentDate = Date().time
            if (shouldSendToken(provider, token, currentDate) && claimInFlight(provider, token)) {
                sendTokenToServer(token, provider, currentDate, external = null)
            }
        }
        listener?.onPushToken(token, provider)
        PushHub.token(PushToken(token, provider), logShopId, callbacks(), reported)
    }

    /**
     * Manual registration entry point ([com.personalization.SDK.setPushToken]). Always sends to
     * the backend regardless of [autoSendPushToken], because the host asked for it explicitly. On the
     * new core it waits for the instance and keeps the week like any token ([CorePushTokens.set]).
     */
    fun sendToken(token: String, provider: PushProvider, listener: OnApiCallbackListener?) {
        core?.let {
            it.set(token, provider, listener)
            return
        }
        if (token.isEmpty()) return
        sendTokenToServer(token, provider, Date().time, external = listener)
    }

    private fun shouldSendToken(
        provider: PushProvider,
        newToken: String,
        currentDate: Long
    ): Boolean {
        val savedToken = getPreferencesValueUseCase.getPushToken(provider)
        val lastUpdate = getPreferencesValueUseCase.getLastPushTokenDate(provider)
        return autoSendPushToken &&
            (savedToken.isEmpty() ||
                savedToken != newToken ||
                currentDate - lastUpdate >= ONE_WEEK_MILLISECONDS)
    }

    /** True for the first caller to claim this token; false while its request is still running. */
    private fun claimInFlight(provider: PushProvider, token: String): Boolean =
        tokensInFlight.add(inFlightKey(provider, token))

    private fun inFlightKey(provider: PushProvider, token: String): String =
        "${provider.id}:$token"

    private fun sendTokenToServer(
        token: String,
        provider: PushProvider,
        currentDate: Long,
        external: OnApiCallbackListener?
    ) {
        val params = mapOf(
            PLATFORM_FIELD to PARAM_ANDROID,
            TOKEN_FIELD to token,
            PUSH_PROVIDER_FIELD to provider.serverValue
        )
        // Through the queue of requests that wait for a did, never straight to the network.
        sendNetworkMethodUseCase.postAsync(
            MOBILE_PUSH_TOKENS,
            JSONObject(params),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    savePreferencesValueUseCase.saveLastPushTokenDate(provider, currentDate)
                    savePreferencesValueUseCase.savePushToken(provider, token)
                    // Released only after the token is persisted, so a delivery arriving in
                    // between is still deduplicated — by the cache rather than by the claim.
                    tokensInFlight.remove(inFlightKey(provider, token))
                    SdkLogs.log(LogLevel.INFO, LogCodes.PUSH_TOKEN_SENT, "Push token sent", logShopId, mapOf("provider" to provider.id))
                    external?.onSuccess(response)
                }

                override fun onError(code: Int, msg: String?) {
                    // Nothing was persisted, so free the claim and let a later delivery retry.
                    tokensInFlight.remove(inFlightKey(provider, token))
                    // Without the answer's body: an error answer often repeats the request's data.
                    SdkLogs.log(
                        LogLevel.WARNING,
                        LogCodes.PUSH_TOKEN_FAILED,
                        "Push token was not sent",
                        logShopId,
                        mapOf("provider" to provider.id, "status" to code)
                    )
                    external?.onError(code, msg)
                }
            }
        )
    }

    companion object {
        /** The providers whose SDK is on the classpath; a test hands its own before the instance is made. */
        @Volatile
        internal var sourceFactory: () -> List<PushTokenSource> = ::classpathSources

        internal fun classpathSources(): List<PushTokenSource> = buildList {
            if (isOnClasspath(FCM_MARKER_CLASS)) add(FcmTokenSource())
            if (isOnClasspath(HMS_MARKER_CLASS)) add(HmsTokenSource())
        }

        private fun isOnClasspath(className: String): Boolean = try {
            Class.forName(className, false, PushTokenManager::class.java.classLoader)
            true
        } catch (_: Throwable) {
            false
        }

        private const val TAG = "PushTokenManager"
        // Marker classes used to detect whether each provider's SDK is present at runtime.
        private const val FCM_MARKER_CLASS = "com.google.firebase.messaging.FirebaseMessaging"
        private const val HMS_MARKER_CLASS = "com.huawei.hms.push.HmsMessaging"
        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60 * 1000L
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val PUSH_PROVIDER_FIELD = "push_provider"
        private const val PLATFORM_FIELD = "platform"
        private const val PARAM_ANDROID = "android"
        private const val TOKEN_FIELD = "token"
    }
}
