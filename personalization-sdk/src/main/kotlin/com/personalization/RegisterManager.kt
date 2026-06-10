@file:SuppressLint("HardwareIds")

package com.personalization

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.errors.BaseInfoError
import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.mappers.SdkInitializationMapper.mapToSdkInitResponse
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.util.TimeZone
import javax.inject.Inject

class RegisterManager @Inject constructor(
    private val updateUserSettingsValueUseCase: UpdateUserSettingsValueUseCase,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val executeQueueTasksUseCase: ExecuteQueueTasksUseCase,
    private val inAppNotificationManager: InAppNotificationManager,
    private val pushTokenManager: PushTokenManager
) {
    private var autoSendPushToken: Boolean = false

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    private val isTestDevice: Boolean
        get() = IS_TEST_DEVICE_FIELD == Settings.System.getString(
            contentResolver,
            FIREBASE_TEST_LAB
        )

    fun initialize(
        context: Context,
        contentResolver: ContentResolver,
        autoSendPushToken: Boolean,
        needReInitialization: Boolean = false
    ) {
        this.context = context
        this.contentResolver = contentResolver
        this.autoSendPushToken = autoSendPushToken

        val did = getUserSettingsValueUseCase.getDid()
        when {
            did.isEmpty() || needReInitialization -> initializeNewDevice()
            else -> initializeSdk(null)
        }
    }

    private fun initializeNewDevice() {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        updateUserSettingsValueUseCase.updateDid(value = androidId)
        initializeSdk(seance = null)
        init()
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
        val finalSeance = seance ?: generateOrRetrieveSeance()
        updateUserSettingsValueUseCase.updateSid(value = finalSeance)
        executeQueueTasksUseCase.invoke()
        pushTokenManager.initialize(context = context, autoSendPushToken = autoSendPushToken)
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

    private fun alphanumeric(): String = SecureRandom().let { random ->
        (1..ALPHANUMERIC_VALUE).map {
            SOURCE[random.nextInt(SOURCE.length)]
        }.joinToString("")
    }

    companion object {
        private const val TAG = "RegisterManager"

        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val SESSION_CODE_EXPIRE = 2 * 3600 * 1000L
        private const val RETRY_DELAY_MILLISECONDS = 1000L
        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val PARAM_ANDROID = "android"
        private const val GET_INIT_METHOD = "init"
        private const val ALPHANUMERIC_VALUE = 10
        private const val PARAM_STREAM = "stream"
        private const val MAX_INIT_RETRIES = 5
        private const val PARAM_TZ = "tz"
    }
}
