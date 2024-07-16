package com.personalizatio

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.personalizatio.SDK.Companion.TAG
import com.personalizatio.SDK.Companion.debug
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.domain.features.preferences.usecase.GetPreferencesValueUseCase
import com.personalizatio.domain.features.preferences.usecase.SavePreferencesValueUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject

class RegisterManager {
    private var autoSendPushToken: Boolean = false

    @Inject
    lateinit var getPreferencesValueUseCase: GetPreferencesValueUseCase
    @Inject
    lateinit var savePreferencesValueUseCase: SavePreferencesValueUseCase
    @Inject
    lateinit var networkManager: Lazy<NetworkManager>

    private lateinit var contentResolver: ContentResolver

    internal var did: String? = null
        private set

    internal var seance: String? = null
        private set

    internal var isInitialized: Boolean = false
        private set

    /**
     * Get did from properties or generate a new did
     */
    @SuppressLint("HardwareIds")
    internal fun initialize(contentResolver: ContentResolver, autoSendPushToken: Boolean) {
        this.contentResolver = contentResolver
        this.autoSendPushToken = autoSendPushToken

        if (did != null) return

        did = getDid()

        if (did == null) {
            did = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

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
                SDK.error("getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }
            if (task.result == null) {
                SDK.error("Firebase result is null")
                return@addOnCompleteListener
            }

            val token = task.result
            debug("token: $token")

            val tokenField = getToken()

            val currentDate = Date()

            if (tokenField == null
                || tokenField != token
                || (currentDate.time - getLastPushTokenMilliseconds()) >= ONE_WEEK_MILLISECONDS
            ) {

                setPushTokenNotification(token, object : OnApiCallbackListener() {
                    override fun onSuccess(response: JSONObject?) {
                        saveLastPushTokenDate(currentDate)
                        saveToken(token)
                    }
                })
            }
        }
    }

    private fun init() {
        if (isTestDevice) {
            Log.w(TAG, "Disable working Google Play Pre-Launch report devices")
            return
        }

        try {
            val params = JSONObject()
            params.put("tz", (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            networkManager.get().get("init", params, object : OnApiCallbackListener() {
                @Volatile
                private var attempt = 0

                override fun onSuccess(response: JSONObject?) {
                    if (response == null) {
                        SDK.error("Init response is not correct.")
                        return
                    }

                    did = response.optString("did")
                    if (did.isNullOrEmpty()) {
                        SDK.error("Init response does not contain the correct did field.")
                        return
                    }

                    saveDid()

                    val seance = response.optString("seance")
                    if (seance.isNullOrEmpty()) {
                        SDK.error("Init response does not contain the correct seance field.")
                        return
                    }

                    initializeSdk(seance)
                }

                override fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "code: $code, $msg")
                        if (attempt < 5) {
                            attempt++
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000L * attempt)
                            init()
                        }
                    } else {
                        SDK.error("Init error: code: $code, $msg")
                    }
                }
            })
        } catch (e: Exception) {
            SDK.error(e.message, e)
        }
    }

    private fun initializeSdk(sid: String?) {
        isInitialized = true
        seance = sid

        //If there is no session, try to find it in the storage
        //We need to separate sessions by time.
        //To do this, it is enough to track the time of the last action for the session and, if it is more than N hours, then create a new session.

        if (seance == null && getPreferencesValueUseCase.invoke(SID_FIELD, null) != null
            && getPreferencesValueUseCase.invoke(SID_LAST_ACT_FIELD, 0)
            >= System.currentTimeMillis() - SESSION_CODE_EXPIRE * 3600 * 1000
        ) {
            seance = getPreferencesValueUseCase.invoke(SID_FIELD, null)
        }

        //If there is no session, generate a new one
        if (seance == null) {
            debug("Generate new seance")
            seance = alphanumeric(10)
        }

        updateSidActivity()

        debug(
            "Device ID: " + did + ", seance: " + seance + ", last act: "
                    + Timestamp(getPreferencesValueUseCase.invoke(SID_LAST_ACT_FIELD, 0))
        )

        networkManager.get().executeQueueTasks()

        initToken()
    }

    internal fun updateSidActivity() {
        seance?.let {  seance ->
            savePreferencesValueUseCase(SID_FIELD, seance)
        }
        savePreferencesValueUseCase(SID_LAST_ACT_FIELD, System.currentTimeMillis())
    }


    private val isTestDevice: Boolean
        get() = IS_TEST_DEVICE_FIELD == Settings.System.getString(contentResolver, FIREBASE_TEST_LAB)

    private fun getDid(): String? {
        return getPreferencesValueUseCase(DID_PREFS_KEY, null)
    }

    private fun saveDid() {
        did?.let { did ->
            savePreferencesValueUseCase(DID_PREFS_KEY, did)
        }
    }

    private fun getToken(): String? {
        return getPreferencesValueUseCase(TOKEN_PREFS_KEY, null)
    }

    private fun saveToken(token: String) {
        savePreferencesValueUseCase(TOKEN_PREFS_KEY, token)
    }

    private fun getLastPushTokenMilliseconds(): Long {
        return getPreferencesValueUseCase(LAST_PUSH_TOKEN_DATE_PREFS_KEY, 0)
    }

    private fun saveLastPushTokenDate(date: Date) {
        savePreferencesValueUseCase(LAST_PUSH_TOKEN_DATE_PREFS_KEY, date.time)
    }

    internal fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        val params = HashMap<String, String>()
        params[PLATFORM_FIELD] = PLATFORM_ANDROID_FIELD
        params[TOKEN_FIELD] = token
        networkManager.get().post(MOBILE_PUSH_TOKENS, JSONObject(params.toMap()), listener)
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
        private const val DID_PREFS_KEY = "did"
        private const val TOKEN_PREFS_KEY = "token"
        private const val LAST_PUSH_TOKEN_DATE_PREFS_KEY = "last_push_token_date"
        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val PLATFORM_FIELD = "platform"
        private const val PLATFORM_ANDROID_FIELD = "android"
        private const val TOKEN_FIELD = "token"
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val SID_FIELD = "sid"
        private const val SID_LAST_ACT_FIELD = "sid_last_act"
        private const val SESSION_CODE_EXPIRE = 2
        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcefghijklmnopqrstuvwxyz"

        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60
    }
}
