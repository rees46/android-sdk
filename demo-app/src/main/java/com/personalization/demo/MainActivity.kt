package com.personalization.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.personalization.SDK
import com.personalization.demo.BuildConfig
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position

class MainActivity : AppCompatActivity() {

    private lateinit var sdk: SDK

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

        // Setup button
        findViewById<android.widget.Button>(R.id.btnShowTestPopup).setOnClickListener {
            showTestPopup()
        }
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

