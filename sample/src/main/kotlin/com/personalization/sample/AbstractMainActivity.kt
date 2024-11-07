package com.personalization.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.SDK
import com.personalization.notification.core.NotificationHelper
import com.personalization.notification.helpers.NotificationImageHelper.loadBitmaps
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.notification.model.NotificationData
import com.personalization.stories.views.StoriesView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class AbstractMainActivity<out T : SDK> internal constructor(
    private val sdk: SDK
) : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handlePermissions()
        handleNotification()
        handleEmailSending()
        initializingFragmentManager()
        initializingStoriesView()
        handleInAppNotifications()
        handlePushNotification()
    }

    private fun handlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    /* activity = */ this,
                    /* permissions = */ arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    /* requestCode = */ 1
                )
            }
        }
    }

    private fun handleNotification() {
        if (intent.extras != null) {
            sdk.notificationClicked(intent.extras)
        }
        sdk.notificationClicked(intent.extras)
    }

    private fun handleEmailSending() {
        button = findViewById(R.id.button)
        emailEditText = findViewById(R.id.emailEditText)

        emailEditText.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                button.callOnClick()
            }
            false
        }

        button.setOnClickListener {
            if (emailEditText.text.toString().isNotEmpty()) {
                val params = HashMap<String, String>()
                params["email"] = emailEditText.text.toString()
                sdk.profile(params)
                Toast.makeText(applicationContext, "Email sent", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializingFragmentManager() = sdk.initializeFragmentManager(
        fragmentManager = supportFragmentManager
    )

    private fun initializingStoriesView() {
        val storiesView: StoriesView = findViewById(R.id.stories_view)
        sdk.initializeStoriesView(storiesView)

        storiesView.itemClickListener = object : OnClickListener {
            override fun onClick(url: String): Boolean = false

            override fun onClick(product: Product): Boolean = false
        }
    }

    private fun handleInAppNotifications() {
        val debugFullScreenMessage = resources.getString(R.string.alert_dialog_full_screen_message)
        val buttonNegative = resources.getString(R.string.alert_dialog_button_decline_title)
        val buttonPositive = resources.getString(R.string.alert_dialog_button_accept_title)
        val debugMessage = resources.getString(R.string.alert_dialog_message)
        val debugTitle = resources.getString(R.string.alert_dialog_title)

        val buttonPositiveColor = ContextCompat.getColor(this, R.color.buttonAcceptColor)
        val buttonNegativeColor = ContextCompat.getColor(this, R.color.colorGray)

        val debugImageUrl =
            "https://mir-s3-cdn-cf.behance.net/projects/404/01d316151239201.Y3JvcCwzMzA0LDI1ODUsMzQzLDA.png"

        findViewById<Button>(R.id.alertDialogButton).setOnClickListener {
            sdk.showAlertDialog(
                title = debugTitle,
                message = debugMessage,
                imageUrl = debugImageUrl,
                buttonNegativeText = buttonNegative,
                buttonPositiveText = buttonPositive,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveColor = buttonPositiveColor,
                onNegativeClick = {
                    Log.d(this.localClassName, ": onNegativeClick")
                },
                onPositiveClick = {
                    Log.d(this.localClassName, ": onPositiveClick")
                },
            )
        }

        findViewById<Button>(R.id.fullScreenDialogButton).setOnClickListener {
            sdk.showFullScreenDialog(
                title = debugTitle,
                message = debugFullScreenMessage,
                imageUrl = debugImageUrl,
                buttonNegativeText = buttonNegative,
                buttonPositiveText = buttonPositive,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveColor = buttonPositiveColor,
                onNegativeClick = {
                    Log.d(this.localClassName, ": onNegativeClick")
                },
                onPositiveClick = {
                    Log.d(this.localClassName, ": onPositiveClick")
                },
            )
        }

        findViewById<Button>(R.id.bottomSheetDialogButton).setOnClickListener {
            sdk.showBottomSheetDialog(
                title = debugTitle,
                message = debugMessage,
                imageUrl = debugImageUrl,
                buttonNegativeText = null,
                buttonPositiveText = buttonPositive,
                buttonNegativeColor = buttonNegativeColor,
                buttonPositiveColor = buttonPositiveColor,
                onNegativeClick = {
                    Log.d(this.localClassName, ": onNegativeClick")
                },
                onPositiveClick = {
                    Log.d(this.localClassName, ": onPositiveClick")
                },
            )
        }

        findViewById<Button>(R.id.snackBarButton).setOnClickListener {
            sdk.inAppNotificationManager.showSnackBar(
                view = findViewById(android.R.id.content),
                message = debugTitle,
                buttonNegativeText = buttonNegative,
                buttonPositiveText = buttonPositive,
                onNegativeClick = {
                    Log.d(this.localClassName, ": onNegativeClick")
                },
                onPositiveClick = {
                    Log.d(this.localClassName, ": onPositiveClick")
                },
            )
        }
    }

    private fun handlePushNotification() {
        findViewById<Button>(R.id.pushNotificationButton).setOnClickListener {
            val testData = mapOf(
                NOTIFICATION_TITLE to "Test Notification Title",
                NOTIFICATION_BODY to "This is a test notification body",
                NOTIFICATION_IMAGES to "https://img10.hotstar.com/image/upload/f_auto/sources/r1/cms/prod/1468/1727698041468-i,https://static1.srcdn.com/wordpress/wp-content/uploads/2024/07/futurana-season-12-poster-featuring-fry-bender-leela-and-nibbler-1.jpg,https://www.hollywoodreporter.com/wp-content/uploads/2022/02/TCDFUTU_FE007-H2-2022.jpg?w=1296&h=730&crop=1"
            )

            CoroutineScope(Dispatchers.Main).launch {
                val images = loadBitmaps(testData[NOTIFICATION_IMAGES])

                NotificationHelper.createNotification(
                    context = applicationContext,
                    notificationId = testData.hashCode(),
                    data = NotificationData(
                        title = testData[NOTIFICATION_TITLE],
                        body = testData[NOTIFICATION_BODY],
                        images = testData[NOTIFICATION_IMAGES]
                    ),
                    images = images
                )
            }
        }
    }
}
