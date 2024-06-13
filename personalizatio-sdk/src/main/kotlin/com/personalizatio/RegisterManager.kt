package com.personalizatio

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.personalizatio.SDK.Companion.TAG
import com.personalizatio.SDK.Companion.debug
import com.personalizatio.api.ApiMethod
import com.personalizatio.api.OnApiCallbackListener
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import java.util.TimeZone

class RegisterManager(val sdk: SDK) {
    private var autoSendPushToken: Boolean = false

    internal var did: String? = null
        private set

    /**
     * Get did from properties or generate a new did
     */
    @SuppressLint("HardwareIds")
    internal fun initialize(autoSendPushToken: Boolean) {
        this.autoSendPushToken = autoSendPushToken

        if(did != null) return

        sdk.addToQueue(Thread { this.token })

        did = getDid()

        if (did == null) {
            //get unique device id
            did = Settings.Secure.getString(sdk.context.contentResolver, Settings.Secure.ANDROID_ID)

            init()
        }
        else{
            sdk.initialized(null)
        }
    }

    /**
     * Get device token
     */
    private val token: Unit
        get() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
                if (!task.isSuccessful) {
                    SDK.error("getInstanceId failed", task.exception)
                    return@addOnCompleteListener
                }
                if (task.result == null) {
                    SDK.error("Firebase result is null")
                    return@addOnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result
                debug("token: $token")

                //Check send token
                val tokenField = getToken()

                val currentDate = Date()

                if (tokenField == null
                    || tokenField != token
                    || (currentDate.time - getLastPushTokenMilliseconds()) >= ONE_WEEK_MILLISECONDS) {
                    //Send token
                    sdk.setPushTokenNotification(token, object : OnApiCallbackListener() {
                        override fun onSuccess(response: JSONObject?) {
                            saveLastPushTokenDate(currentDate)
                            saveToken(token)
                        }
                    })
                }
            }
        }

    private fun init() {
        //Disable working Google Play Pre-Launch report devices

        if (isTestDevice) {
            Log.w(TAG, "Disable working Google Play Pre-Launch report devices")
            return
        }

        try {
            val params = JSONObject()
            params.put("tz", (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            sdk.send(ApiMethod.GET("init"), params, object : OnApiCallbackListener() {
                @Volatile
                private var attempt = 0

                override fun onSuccess(response: JSONObject?) {
                    try {
                        did = response!!.getString("did")
                        saveDid()

                        // Выполняем таски из очереди
                        sdk.initialized(response.getString("seance"))
                    } catch (e: JSONException) {
                        SDK.error(e.message, e)
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "code: $code, $msg")
                        if (attempt < 5) {
                            attempt++
                        }
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({ init() }, 1000L * attempt)
                    }
                }
            })
        } catch (e: JSONException) {
            SDK.error(e.message, e)
        }
    }

    private val isTestDevice: Boolean
        get() = "true" == Settings.System.getString(sdk.context.contentResolver, "firebase.test.lab")

    private fun getDid() : String? {
        return sdk.prefs().getString(DID_PREFS_KEY, null)
    }

    private fun saveDid() {
        val edit = sdk.prefs().edit()
        edit.putString(DID_PREFS_KEY, did)
        edit.apply()
    }

    private fun getToken() : String? {
        return sdk.prefs().getString(TOKEN_PREFS_KEY, null)
    }

    private fun saveToken(token: String) {
        val edit = sdk.prefs().edit()
        edit.putString(TOKEN_PREFS_KEY, token)
        edit.apply()
    }

    private fun getLastPushTokenMilliseconds() : Long {
        return sdk.prefs().getLong(LAST_PUSH_TOKEN_DATE_PREFS_KEY, 0)
    }

    private fun saveLastPushTokenDate(date: Date) {
        val edit = sdk.prefs().edit()
        edit.putLong(LAST_PUSH_TOKEN_DATE_PREFS_KEY, date.time)
        edit.apply()
    }

    companion object {
        private const val DID_PREFS_KEY = "did"
        private const val TOKEN_PREFS_KEY = "token"
        private const val LAST_PUSH_TOKEN_DATE_PREFS_KEY = "last_push_token_date"

        private const val ONE_WEEK_MILLISECONDS = 7 * 24 * 60 * 60
    }
}