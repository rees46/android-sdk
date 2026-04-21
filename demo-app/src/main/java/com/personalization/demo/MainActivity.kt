package com.personalization.demo

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseItemRequest
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.params.ProductItemParams
import com.personalization.api.params.PurchasePredictParams
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

    private object DemoProductViewConstants {
        const val PRODUCT_ID = "demo-product-view-001"
        const val DEMO_PRICE = 2499.99
        const val DEMO_AMOUNT = 1
    }

    private object DemoPurchaseTrackingConstants {
        const val ORDER_ID_MINIMAL = "android-demo-order-minimal"
        const val ORDER_ID_FULL = "android-demo-order-full"
        const val ORDER_PRICE_MINIMAL = 199.0
        const val ORDER_PRICE_FULL = 999.0
        const val ITEM_ID = "android-demo-sku-001"
        const val ITEM_AMOUNT = 1
        const val ITEM_PRICE = 99.0
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

        findViewById<Button>(R.id.btnTrackViewNoParams).setOnClickListener {
            trackProductViewIdOnly()
        }

        findViewById<Button>(R.id.btnTrackViewWithParams).setOnClickListener {
            trackProductViewWithItemParams()
        }

        findViewById<Button>(R.id.btnPredictDidOnly).setOnClickListener {
            predictPurchase(PurchasePredictParams())
        }

        findViewById<Button>(R.id.btnPredictWithEmail).setOnClickListener {
            predictPurchase(
                PurchasePredictParams(email = getString(R.string.predict_demo_email))
            )
        }

        findViewById<Button>(R.id.btnTrackPurchaseMinimal).setOnClickListener {
            trackPurchaseMinimal()
        }

        findViewById<Button>(R.id.btnTrackPurchaseFull).setOnClickListener {
            trackPurchaseFull()
        }
    }

    private fun trackPurchaseMinimal() {
        val request = PurchaseTrackingRequest(
            orderId = DemoPurchaseTrackingConstants.ORDER_ID_MINIMAL,
            orderPrice = DemoPurchaseTrackingConstants.ORDER_PRICE_MINIMAL,
            items = listOf(
                PurchaseItemRequest(
                    id = DemoPurchaseTrackingConstants.ITEM_ID,
                    amount = DemoPurchaseTrackingConstants.ITEM_AMOUNT,
                    price = DemoPurchaseTrackingConstants.ITEM_PRICE,
                ),
            ),
        )
        sdk.trackPurchase(
            request,
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.track_purchase_ok),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "${getString(R.string.track_purchase_fail)}: $msg",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    private fun trackPurchaseFull() {
        val request = PurchaseTrackingRequest(
            orderId = DemoPurchaseTrackingConstants.ORDER_ID_FULL,
            orderPrice = DemoPurchaseTrackingConstants.ORDER_PRICE_FULL,
            items = listOf(
                PurchaseItemRequest(
                    id = DemoPurchaseTrackingConstants.ITEM_ID,
                    amount = 2,
                    price = 49.99,
                    quantity = 2,
                    lineId = "demo-line-1",
                    fashionSize = "L",
                ),
            ),
            deliveryType = "courier",
            deliveryAddress = "Demo address",
            paymentType = "card",
            isTaxFree = true,
            promocode = "DEMO10",
            orderCash = 100.0,
            orderBonuses = 10.0,
            orderDelivery = 5.0,
            orderDiscount = 15.0,
            channel = "mobile",
            custom = mapOf("demo_custom" to "android_demo"),
            recommendedBy = Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "demo-block"),
            recommendedSource = JSONObject().put("source_key", "source_value"),
            stream = "demo-stream",
            segment = "A",
        )
        sdk.trackPurchase(
            request,
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.track_purchase_ok),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "${getString(R.string.track_purchase_fail)}: $msg",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    private fun predictPurchase(params: PurchasePredictParams) {
        sdk.predictManager.getProbabilityToPurchase(
            params = params,
            onSuccess = { response ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.predict_ok, response.probability, response.clientId),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onError = { code, msg ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.predict_fail, code, msg ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun trackProductViewIdOnly() {
        sdk.trackEventManager.track(TrackEvent.VIEW, DemoProductViewConstants.PRODUCT_ID)
        Toast.makeText(this, getString(R.string.track_view_no_params_queued), Toast.LENGTH_SHORT).show()
    }

    private fun trackProductViewWithItemParams() {
        val item = ProductItemParams(DemoProductViewConstants.PRODUCT_ID)
            .set(ProductItemParams.PARAMETER.PRICE, DemoProductViewConstants.DEMO_PRICE)
            .set(ProductItemParams.PARAMETER.AMOUNT, DemoProductViewConstants.DEMO_AMOUNT)

        sdk.trackEventManager.track(
            TrackEvent.VIEW,
            Params().put(item),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.track_view_ok),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "${getString(R.string.track_view_fail)}: $msg",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
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

