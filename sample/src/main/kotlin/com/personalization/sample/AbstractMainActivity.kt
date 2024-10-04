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
import com.personalization.OnLinkClickListener
import com.personalization.Product
import com.personalization.SDK
import com.personalization.stories.views.StoriesView

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
        initializingStoriesView()
        handleInAppNotifications()
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

        storiesView.itemClickListener = object : OnLinkClickListener {
            override fun onClick(url: String): Boolean {
                // return true if need to opening using the SDK
                return false
            }

            override fun onClick(product: Product): Boolean {
                // return true if need to opening using the SDK
                return false
            }
        }
    }

    private fun handleInAppNotifications() {
        //TODO remove
        val debugTitle = "Привет,мы на связи"
        val debugMessage =
            "И мы к вам с хорошими новостями. Совсем скоро мы проведем вебинар по поиску на сайте — там будет масса полезной информации, которая поможет бустануть конверсию и повысить лояльность аудитории. Приходите!"
        val debugImageUrl =
            "https://blog-frontend.envato.com/cdn-cgi/image/width=2560,quality=75,format=auto/uploads/sites/2/2022/04/E-commerce-App-JPG-File-scaled.jpg"
        val buttonNegative = "Cancel"
        val buttonPositive = "OK"

        findViewById<Button>(R.id.alertDialogButton).setOnClickListener {
            sdk.showAlertDialog(
                title = debugTitle,
                message = debugMessage,
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

        findViewById<Button>(R.id.fullScreenDialogButton).setOnClickListener {
            sdk.showFullScreenDialog(
                title = debugTitle,
                message = debugMessage,
                imageUrl = debugImageUrl,
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

        findViewById<Button>(R.id.bottomSheetDialogButton).setOnClickListener {
            sdk.showBottomSheetDialog(
                title = debugTitle,
                message = debugMessage,
                imageUrl = debugImageUrl,
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
}
