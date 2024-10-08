package com.personalization

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.personalization.SDK.Companion.TAG
import com.personalization.SDK.Companion.debug
import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import java.security.SecureRandom
import java.sql.Timestamp
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
    private val executeQueueTasksUseCase: ExecuteQueueTasksUseCase
) {
    private var autoSendPushToken: Boolean = false

    private lateinit var contentResolver: ContentResolver

    /**
     * Get did from properties or generate a new did
     */
    @SuppressLint("HardwareIds")
    internal fun initialize(contentResolver: ContentResolver, autoSendPushToken: Boolean) {
        this.contentResolver = contentResolver
        this.autoSendPushToken = autoSendPushToken

        var did = getUserSettingsValueUseCase.getDid()

        if (did.isEmpty()) {
            did = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            updateUserSettingsValueUseCase.updateDid(did)

            init()
        } else {
            initializeSdk(null)
        }
    }

    /**
     * Init device token
     */
    private fun initToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
            if (!task.isSuccessful) {
                SDK.error("Failed to retrieve Firebase token: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            if (task.result == null) {
                SDK.error("Firebase result is null")
                return@addOnCompleteListener
            }

            val token = task.result
            Log.i(TAG, "Retrieved Firebase token: $token")

            val tokenField = getPreferencesValueUseCase.getToken()
            val currentDate = Date()

            if (autoSendPushToken && (tokenField.isEmpty() || tokenField != token || (currentDate.time - getPreferencesValueUseCase.getLastPushTokenDate()) >= ONE_WEEK_MILLISECONDS)) {
                Log.i(TAG, "Calling setPushTokenNotification with token: $token")
                setPushTokenNotification(token, object : OnApiCallbackListener() {
                    override fun onSuccess(response: JSONObject?) {
                        savePreferencesValueUseCase.saveLastPushTokenDate(currentDate.time)
                        savePreferencesValueUseCase.saveToken(token)
                        Log.d(TAG, "Push token successfully sent and saved")
                    }

                    override fun onError(code: Int, msg: String?) {
                        Log.e(TAG, "Failed to send push token. Code: $code, Message: $msg")
                    }
                })
            }
        }
    }

    private fun init() {
        if (isTestDevice) {
            Log.w(TAG, "Disable working on Google Play Pre-Launch report devices")
            return
        }

        try {
            val params = JSONObject()
            params.put("tz", (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            Log.i(TAG,"Sending init request with params: $params")
            sendNetworkMethodUseCase.get("init", params, object : OnApiCallbackListener() {
                @Volatile
                private var attempt = 0

                override fun onSuccess(response: JSONObject?) {
                    if (response == null) {
                        SDK.error("Init response is not correct.")
                        Log.e(TAG, "Init response is null or incorrect.")
                        return
                    }

                    val did = response.optString("did")
                    if (did.isNullOrEmpty()) {
                        SDK.error("Init response does not contain the correct did field.")
                        return
                    }

                    updateUserSettingsValueUseCase.updateDid(did)

                    val seance = response.optString("seance")
                    if (seance.isNullOrEmpty()) {
                        SDK.error("Init response does not contain the correct seance field.")
                        return
                    }

                    initializeSdk(seance)
                }

                override fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "Init error: code: $code, message: $msg")
                        if (attempt < 5) {
                            attempt++
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000L * attempt)
                            Log.d(TAG, "Retrying init, attempt: $attempt")
                            init()
                        }
                    } else {
                        SDK.error("Init error: code: $code, message: $msg")
                    }
                }
            })
        } catch (e: Exception) {
            SDK.error(e.message, e)
        }
    }

    private fun initializeSdk(sid: String?) {
        updateUserSettingsValueUseCase.updateIsInitialized(true)
        var seance = sid

        //If there is no session, try to find it in the storage
        //We need to separate sessions by time.
        //To do this, it is enough to track the time of the last action for the session and, if it is more than N hours, then create a new session.

        if (seance == null) {
            val newSid = getUserSettingsValueUseCase.getSid()
            if (newSid.isNotEmpty() && getUserSettingsValueUseCase.getSidLastActTime() >= System.currentTimeMillis() - SESSION_CODE_EXPIRE * 3600 * 1000) {
                seance = newSid
            }
        }


        //If there is no session, generate a new one
        if (seance == null) {
            debug("Generate new seance")
            seance = alphanumeric(10)
        }

        updateUserSettingsValueUseCase.updateSid(seance)
        debug(
            "Device ID: " + getUserSettingsValueUseCase.getDid() + ", seance: " + seance + ", last act: " + Timestamp(
                getUserSettingsValueUseCase.getSidLastActTime()
            )
        )

        executeQueueTasksUseCase.invoke()

        initToken()
    }

    private val isTestDevice: Boolean
        get() = IS_TEST_DEVICE_FIELD == Settings.System.getString(
            contentResolver, FIREBASE_TEST_LAB
        )

    fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        val params = HashMap<String, String>()
        params[PLATFORM_FIELD] = PLATFORM_ANDROID_FIELD
        params[TOKEN_FIELD] = token
        Log.d(TAG, "Sending push token to server: $token")
        sendNetworkMethodUseCase.post(MOBILE_PUSH_TOKENS, JSONObject(params.toMap()), listener)
    }

    private fun alphanumeric(length: Int): String {
        val sb = StringBuilder(length)
        val secureRandom = SecureRandom()
        for (i in 0 until length) {
            sb.append(SOURCE[secureRandom.nextInt(SOURCE.length)])
        }
        return sb.toString()
    }

    companion object {

        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val PLATFORM_FIELD = "platform"
        private const val PLATFORM_ANDROID_FIELD = "android"
        private const val TOKEN_FIELD = "token"
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val SESSION_CODE_EXPIRE = 2
        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcefghijklmnopqrstuvwxyz"

        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60
    }
}
