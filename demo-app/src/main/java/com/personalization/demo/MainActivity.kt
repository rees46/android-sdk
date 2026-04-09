package com.personalization.demo

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.demo.BuildConfig
import com.personalization.sdk.data.models.dto.popUp.Components
import org.json.JSONObject
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position

class MainActivity : AppCompatActivity() {

    private lateinit var sdk: SDK

    private object DemoTrackEventConstants {
        /** Same value as SDK client-side validation errors for custom field key collisions. */
        const val CLIENT_VALIDATION_ERROR_CODE = -1
        const val EVENT_NAME = "custom_event"
        const val SAMPLE_UNIX_TIME = 123_456
        const val CATEGORY = "demo_category"
        const val LABEL = "demo_label"
        const val SAMPLE_VALUE = 100
        const val SAFE_CUSTOM_KEY = "demo_custom_key"
        const val SAFE_CUSTOM_VALUE = "android_demo_app"
        const val COLLISION_RESERVED_KEY = "shop_id"
        const val COLLISION_PLACEHOLDER_VALUE = "collision_demo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase if not already initialized
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize SDK
        try {
            sdk = SDK()
            sdk.initialize(
                context = this,
                shopId = BuildConfig.SHOP_ID,
                apiDomain = "api.rees46.ru",
                autoSendPushToken = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue even if SDK initialization fails for demo purposes
        }

        // Initialize fragment manager for popups
        sdk.inAppNotificationManager.initFragmentManager(supportFragmentManager)

        findViewById<Button>(R.id.btnShowTestPopup).setOnClickListener {
            showTestPopup()
        }

        findViewById<Button>(R.id.btnTrackEventCustomFields).setOnClickListener {
            trackEventWithCustomFieldsSuccess()
        }

        findViewById<Button>(R.id.btnTrackEventCollision).setOnClickListener {
            trackEventWithReservedKeyCollision()
        }
    }

    private fun trackEventWithCustomFieldsSuccess() {
        val customFields = mapOf(DemoTrackEventConstants.SAFE_CUSTOM_KEY to DemoTrackEventConstants.SAFE_CUSTOM_VALUE)
        sdk.trackEvent(
            event = DemoTrackEventConstants.EVENT_NAME,
            time = DemoTrackEventConstants.SAMPLE_UNIX_TIME,
            category = DemoTrackEventConstants.CATEGORY,
            label = DemoTrackEventConstants.LABEL,
            value = DemoTrackEventConstants.SAMPLE_VALUE,
            customFields = customFields,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.track_event_ok),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "${getString(R.string.track_event_fail)}: $msg",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun trackEventWithReservedKeyCollision() {
        val customFields = mapOf(
            DemoTrackEventConstants.COLLISION_RESERVED_KEY to DemoTrackEventConstants.COLLISION_PLACEHOLDER_VALUE
        )
        sdk.trackEvent(
            event = DemoTrackEventConstants.EVENT_NAME,
            time = DemoTrackEventConstants.SAMPLE_UNIX_TIME,
            category = DemoTrackEventConstants.CATEGORY,
            label = DemoTrackEventConstants.LABEL,
            value = DemoTrackEventConstants.SAMPLE_VALUE,
            customFields = customFields,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.track_event_unexpected_success),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    runOnUiThread {
                        val isClientValidation = code == DemoTrackEventConstants.CLIENT_VALIDATION_ERROR_CODE
                            && msg?.contains("customFields contains reserved keys") == true
                        val text = if (isClientValidation) {
                            "${getString(R.string.track_event_collision_ok)}\n$msg"
                        } else {
                            "${getString(R.string.track_event_fail)}: $msg"
                        }
                        Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun showTestPopup() {
        val testPopup = PopupDto(
            id = 999,
            channels = listOf("email"),
            position = Position.CENTERED,
            delay = 0,
            html = """
                <div class="popup-title">Test Popup</div>
                <p class="popup-999__intro">This is a test popup for Android SDK</p>
            """.trimIndent(),
            components = Components(
                header = "Test Popup",
                text = "This is a test popup for Android SDK",
                image = "",
                button = "",
                textEnabled = "",
                imageEnabled = "",
                headerEnabled = ""
            ),
            webPushSystem = false,
            popupActions = PopupActions(
                link = null,
                close = null,
                pushSubscribe = null
            )
        )

        sdk.inAppNotificationManager.shopPopUp(testPopup)
    }
}

