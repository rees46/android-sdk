package com.personalization.demo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.personalization.Params
import com.personalization.SDK
import com.personalization.demo.BuildConfig
import com.personalization.sdk.data.models.dto.popUp.Components
import com.personalization.sdk.data.models.dto.popUp.PopupActions
import com.personalization.sdk.data.models.dto.popUp.PopupDto
import com.personalization.sdk.data.models.dto.popUp.Position

class MainActivity : AppCompatActivity() {

    private lateinit var sdk: SDK
    private var secondSdk: SDK? = null

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

        // Initialize primary SDK instance
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
        }

        // Multi-instance example: initialize a second SDK for a different shop
        try {
            secondSdk = SDK()
            secondSdk?.initialize(
                context = this,
                shopId = SECOND_SHOP_ID,
                apiDomain = "api.rees46.ru",
                autoSendPushToken = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Retrieve instances later by shopId
        val primarySdk = SDK.getInstance(BuildConfig.SHOP_ID)
        val secondarySdk = SDK.getInstance(SECOND_SHOP_ID)

        SDK.debug("Registered SDK instances: ${SDK.getAllInstances().keys}")

        // Initialize fragment manager for popups
        sdk.inAppNotificationManager.initFragmentManager(supportFragmentManager)

        // Setup buttons
        findViewById<android.widget.Button>(R.id.btnShowTestPopup).setOnClickListener {
            showTestPopup()
        }
        findViewById<android.widget.Button>(R.id.btnTestApiCalls).setOnClickListener {
            if (::sdk.isInitialized) {
                runSampleApiCalls(sdk)
            } else {
                Log.w(TAG, "SDK not initialized, skip API test")
            }
        }
    }

    /**
     * Demo: several network calls via public managers (search, recommendations, tracking).
     * Replace recommender code / product id with values valid for your shop if needed.
     */
    private fun runSampleApiCalls(sdk: SDK) {
        val search = sdk.searchManager

        search.searchBlank(
            onSearchBlank = { response ->
                Log.i(TAG, "searchBlank OK: ${response.javaClass.simpleName}")
            },
            onError = { code, msg ->
                Log.w(TAG, "searchBlank error: code=$code msg=$msg")
            }
        )

        search.searchInstant(
            query = "test",
            onSearchInstant = { response ->
                Log.i(TAG, "searchInstant OK: ${response.javaClass.simpleName}")
            },
            onError = { code, msg ->
                Log.w(TAG, "searchInstant error: code=$code msg=$msg")
            }
        )

        search.searchFull(
            query = "test",
            onSearchFull = { response ->
                Log.i(TAG, "searchFull OK: ${response.javaClass.simpleName}")
            },
            onError = { code, msg ->
                Log.w(TAG, "searchFull error: code=$code msg=$msg")
            }
        )

        sdk.recommendationManager.getRecommendation(
            recommenderCode = DEMO_RECOMMENDER_CODE,
            onGetRecommendation = { response ->
                Log.i(TAG, "getRecommendation OK: ${response.javaClass.simpleName}")
            },
            onError = { code, msg ->
                Log.w(TAG, "getRecommendation error: code=$code msg=$msg")
            }
        )

        sdk.trackEventManager.track(
            event = Params.TrackEvent.VIEW,
            productId = DEMO_PRODUCT_ID
        )
        Log.i(TAG, "track(view) sent for productId=$DEMO_PRODUCT_ID")
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

    companion object {
        private const val TAG = "DemoMainActivity"
        private const val SECOND_SHOP_ID = "second_shop_demo"
        /** Change to a recommender code configured in your REES46/PersonaClick project */
        private const val DEMO_RECOMMENDER_CODE = "popular"
        private const val DEMO_PRODUCT_ID = "demo_sku_1"
    }
}

