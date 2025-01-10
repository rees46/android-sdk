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
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.SDK
import com.personalization.api.params.ProfileParams
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
        initializingFragmentManager()
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
                val params = ProfileParams.Builder()
                    .put("email", emailEditText.toString())
                    .build()
                sdk.profile(params)
                Toast.makeText(applicationContext, "Email sent", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializingStoriesView() {
        val storiesView: StoriesView = findViewById(R.id.stories_view)
        sdk.initializeStoriesView(storiesView)

        storiesView.itemClickListener = object : OnClickListener {
            override fun onClick(url: String): Boolean = false

            override fun onClick(product: Product): Boolean = false
        }
    }

    private fun initializingFragmentManager() = sdk.initializeFragmentManager(
        fragmentManager = supportFragmentManager
    )

    private fun handleInAppNotifications() {
        val buttonNegative = resources.getString(R.string.alert_dialog_button_decline_title)
        val buttonPositive = resources.getString(R.string.alert_dialog_button_accept_title)
        val debugTitle = resources.getString(R.string.alert_dialog_title)

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
