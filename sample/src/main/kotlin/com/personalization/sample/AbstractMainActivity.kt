package com.personalization.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.personalization.SDK

abstract class AbstractMainActivity<out T : SDK> internal constructor(
    private val sdk: SDK
) : AppCompatActivity() {

    private lateinit var text: EditText
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
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
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
        text = findViewById(R.id.email)

        text.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                button.callOnClick()
            }
            false
        }

        button.setOnClickListener {
            if (text.text.toString().isNotEmpty()) {
                val params = HashMap<String, String>()
                params["email"] = text.text.toString()
                sdk.profile(params)
                Toast.makeText(applicationContext, "Email sent", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializingStoriesView() {
        sdk.initializeStoriesView(findViewById(R.id.stories_view))
    }

    private fun handleInAppNotifications() {
        //TODO remove
        val debugTitle = "Привет,мы на связи"
        val debugMessage =
            "И мы к вам с хорошими новостями. Совсем скоро мы проведем вебинар по поиску на сайте — там будет масса полезной информации, которая поможет бустануть конверсию и повысить лояльность аудитории. Приходите!"
        val debugImageUrl =
            "https://blog-frontend.envato.com/cdn-cgi/image/width=2560,quality=75,format=auto/uploads/sites/2/2022/04/E-commerce-App-JPG-File-scaled.jpg"


        findViewById<Button>(R.id.alertDialogButton).setOnClickListener {
            sdk.inAppNotificationManager.showAlertDialog(
                fragmentManager = supportFragmentManager,
                title = debugTitle,
                message = debugMessage,
            )
        }
        findViewById<Button>(R.id.fullScreenDialogButton).setOnClickListener {
            sdk.inAppNotificationManager.showFullScreenAlertDialog(
                fragmentManager = supportFragmentManager,
                title = debugTitle,
                message = debugMessage,
                imageUrl = debugImageUrl
            )
        }
    }
}
