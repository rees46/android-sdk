@file:SuppressLint("HardwareIds")

package com.personalization

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.errors.BaseInfoError
import com.personalization.errors.FirebaseError
import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.sdk.data.mappers.SdkInitializationMapper.mapToSdkInitResponse
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import java.security.SecureRandom
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class RegisterManager @Inject constructor(
    private val getPreferencesValueUseCase: GetPreferencesValueUseCase,
    private val savePreferencesValueUseCase: SavePreferencesValueUseCase,
    private val updateUserSettingsValueUseCase: UpdateUserSettingsValueUseCase,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val executeQueueTasksUseCase: ExecuteQueueTasksUseCase,
    private val inAppNotificationManager: InAppNotificationManager
) {

    private var autoSendPushToken = false
    private lateinit var contentResolver: ContentResolver

    private val isTestDevice: Boolean
        get() = IS_TEST_DEVICE_FIELD == Settings.System.getString(
            contentResolver,
            FIREBASE_TEST_LAB
        )

    fun initialize(contentResolver: ContentResolver, autoSendPushToken: Boolean) {
        this.contentResolver = contentResolver
        this.autoSendPushToken = autoSendPushToken

        val did = getUserSettingsValueUseCase.getDid()
        when {
            did.isEmpty() -> initializeNewDevice()
            else -> initializeSdk(null)
        }
    }

    private fun initializeNewDevice() {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        updateUserSettingsValueUseCase.updateDid(value = androidId)
        initializeSdk(seance = null)
        init()
    }

    private fun initToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            handleFirebaseTokenResult(task = task)
        }
    }

    private fun handleFirebaseTokenResult(task: Task<String>) {
        if (!task.isSuccessful || task.result.isNullOrEmpty()) {
            logFirebaseTokenError(task)
            return
        }
        processFirebaseToken(token = task.result)
    }

    private fun logFirebaseTokenError(task: Task<String>) {
        FirebaseError(
            tag = TAG,
            exception = task.exception
        ).logError()
    }

    private fun processFirebaseToken(token: String) {
        val savedToken = getPreferencesValueUseCase.getToken()
        val lastUpdate = getPreferencesValueUseCase.getLastPushTokenDate()
        val currentDate = Date().time
        val isShouldSendToken = shouldSendToken(
            savedToken = savedToken,
            newToken = token,
            currentDate = currentDate,
            lastUpdate = lastUpdate
        )

        when {
            isShouldSendToken -> sendPushTokenToServer(
                token = token,
                currentDate = currentDate
            )

            else -> Log.i(TAG, "Token was send")
        }
    }

    private fun shouldSendToken(
        savedToken: String,
        newToken: String,
        currentDate: Long,
        lastUpdate: Long
    ): Boolean {
        return autoSendPushToken &&
            (savedToken.isEmpty() || savedToken != newToken || (currentDate - lastUpdate >= ONE_WEEK_MILLISECONDS))
    }

    private fun sendPushTokenToServer(token: String, currentDate: Long) {
        setPushTokenNotification(
            token = token,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    savePreferencesValueUseCase.saveLastPushTokenDate(currentDate)
                    savePreferencesValueUseCase.saveToken(token)
                    Log.d(TAG, "Push token successfully sent and saved")
                }

                override fun onError(code: Int, msg: String?) {
                    BaseInfoError(
                        tag = TAG,
                        message = "Failed to send push token. Code: $code, Message: $msg"
                    ).logError()
                }
            }
        )
    }

    private fun init() {
        if (isTestDevice) {
            BaseInfoError(
                tag = TAG,
                message = "Disable working on Google Play Pre-Launch report devices"
            ).logError()
            return
        }

        sendInitRequestWithRetry()
    }

    private fun sendInitRequestWithRetry(attempt: Int = 0) {
        try {
            val params = buildInitParams()
            sendNetworkMethodUseCase.get(
                method = GET_INIT_METHOD,
                params = params,
                listener = object : OnApiCallbackListener() {
                    override fun onSuccess(response: JSONObject?) {
                        handleInitSuccess(response)
                    }

                    override fun onError(code: Int, msg: String?) {
                        handleInitError(code, msg, attempt)
                    }
                }
            )
        } catch (e: Exception) {
            SDK.error(e.message, e)
        }
    }

    private fun buildInitParams(): JSONObject {
        return JSONObject().apply {
            put(PARAM_TZ, (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            put(PARAM_STREAM, PARAM_ANDROID)
        }
    }

    private fun handleInitSuccess(response: JSONObject?) {
        val errorHandler = JsonResponseErrorHandler(
            tag = TAG,
            response = response
        )

        if (!errorHandler.validateResponse()) {
            return
        } else {
            val popUpData = response?.mapToSdkInitResponse()?.popupDto
            if (popUpData != null) {
                inAppNotificationManager.shopPopUp(popupDto = popUpData)
            }
        }

        val did = errorHandler.getRequiredField(fieldName = "did") ?: return
        val seance = errorHandler.getRequiredField(fieldName = "seance") ?: return

        updateUserSettingsValueUseCase.updateDid(did)
        initializeSdk(seance)
    }

    private fun handleInitError(code: Int, msg: String?, attempt: Int) {
        if (code in 500..Int.MAX_VALUE || code <= 0) {
            if (attempt < MAX_INIT_RETRIES) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(RETRY_DELAY_MILLISECONDS * (attempt + 1))
                    sendInitRequestWithRetry(attempt + 1)
                }
            }
        } else {
            BaseInfoError(
                TAG,
                message = "Init error: code: $code, message: $msg"
            ).logError()
        }
    }

    private fun initializeSdk(seance: String?) {
        updateUserSettingsValueUseCase.updateIsInitialized(value = true)
        val finalSeance = seance ?: generateOrRetrieveSeance()
        updateUserSettingsValueUseCase.updateSid(value = finalSeance)
        executeQueueTasksUseCase.invoke()
        initToken()
    }

    private fun generateOrRetrieveSeance(): String {
        val storedSeance = getUserSettingsValueUseCase.getSid()
        val lastActTime = getUserSettingsValueUseCase.getSidLastActTime()
        return if (storedSeance.isNotEmpty() && lastActTime >= System.currentTimeMillis() - SESSION_CODE_EXPIRE) {
            storedSeance
        } else {
            alphanumeric()
        }
    }

    fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        val params = mapOf(PLATFORM_FIELD to PARAM_ANDROID, TOKEN_FIELD to token)
        sendNetworkMethodUseCase.post(MOBILE_PUSH_TOKENS, JSONObject(params), listener)
    }

    private fun alphanumeric(): String = SecureRandom().let { random ->
        (1..ALPHANUMERIC_VALUE).map {
            SOURCE[random.nextInt(SOURCE.length)]
        }.joinToString("")
    }

    companion object {
        private const val TAG = "RegisterManager"

        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60 * 1000L
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val SESSION_CODE_EXPIRE = 2 * 3600 * 1000L
        private const val RETRY_DELAY_MILLISECONDS = 1000L
        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val PLATFORM_FIELD = "platform"
        private const val PARAM_ANDROID = "android"
        private const val GET_INIT_METHOD = "init"
        private const val ALPHANUMERIC_VALUE = 10
        private const val PARAM_STREAM = "stream"
        private const val TOKEN_FIELD = "token"
        private const val MAX_INIT_RETRIES = 5
        private const val PARAM_TZ = "tz"
    }
}
