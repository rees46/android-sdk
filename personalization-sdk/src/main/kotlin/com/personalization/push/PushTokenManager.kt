package com.personalization.push

import android.content.Context
import android.util.Log
import com.personalization.OnPushTokenListener
import com.personalization.PushProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.errors.BaseInfoError
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single pipeline for push tokens across all providers.
 *
 * Every token — proactively fetched at init, delivered asynchronously by a messaging service
 * ([onTokenReceived]), or pushed manually by the host ([sendToken]) — funnels through here:
 * it is deduplicated, persisted per provider, sent to the rees46 backend, and reported to the
 * host via [OnPushTokenListener]. Adding a provider only requires a new [PushTokenSource].
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

    private val sources: List<PushTokenSource> = listOf(FcmTokenSource(), HmsTokenSource())

    fun setOnPushTokenListener(listener: OnPushTokenListener) {
        this.listener = listener
    }

    /** Last token known for [provider] from the local cache, or null if none. */
    fun getToken(provider: PushProvider): String? =
        getPreferencesValueUseCase.getPushToken(provider).takeIf { it.isNotEmpty() }

    /**
     * Detects available providers and proactively fetches their tokens. Tokens that are only
     * delivered asynchronously (HMS) arrive later via [onTokenReceived] from the messaging service.
     */
    fun initialize(context: Context, autoSendPushToken: Boolean) {
        this.context = context
        this.autoSendPushToken = autoSendPushToken

        sources.filter { it.isAvailable(context) }.forEach { source ->
            source.fetchToken(context) { result ->
                result.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { token -> onTokenReceived(token, source.provider) }
                result.exceptionOrNull()?.let { error ->
                    BaseInfoError(
                        tag = TAG,
                        message = "${source.provider.id} token fetch error: ${error.message}"
                    ).logError()
                }
            }
        }
    }

    /**
     * Entry point for tokens received from a provider (proactive fetch or messaging service
     * `onNewToken`). Sends to the backend when changed, then always notifies the host listener.
     */
    fun onTokenReceived(token: String, provider: PushProvider) {
        if (token.isEmpty() || !::context.isInitialized) return

        val currentDate = Date().time
        if (shouldSendToken(provider, token, currentDate)) {
            sendTokenToServer(token, provider, currentDate, external = null)
        }
        listener?.onPushToken(token, provider)
    }

    /**
     * Manual registration entry point ([com.personalization.SDK.setPushToken]). Always sends to
     * the backend regardless of [autoSendPushToken], because the host asked for it explicitly.
     */
    fun sendToken(token: String, provider: PushProvider, listener: OnApiCallbackListener?) {
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
        sendNetworkMethodUseCase.post(
            MOBILE_PUSH_TOKENS,
            JSONObject(params),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    savePreferencesValueUseCase.saveLastPushTokenDate(provider, currentDate)
                    savePreferencesValueUseCase.savePushToken(provider, token)
                    Log.d(TAG, "${provider.id} push token successfully sent and saved")
                    external?.onSuccess(response)
                }

                override fun onError(code: Int, msg: String?) {
                    BaseInfoError(
                        tag = TAG,
                        message = "${provider.id} push token failed. Code: $code, Message: $msg"
                    ).logError()
                    external?.onError(code, msg)
                }
            }
        )
    }

    companion object {
        private const val TAG = "PushTokenManager"
        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60 * 1000L
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val PUSH_PROVIDER_FIELD = "push_provider"
        private const val PLATFORM_FIELD = "platform"
        private const val PARAM_ANDROID = "android"
        private const val TOKEN_FIELD = "token"
    }
}
