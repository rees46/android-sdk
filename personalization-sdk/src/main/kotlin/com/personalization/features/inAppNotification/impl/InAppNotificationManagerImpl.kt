package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.DefaultAlertDialog
import com.personalization.inAppNotification.view.FullScreenDialog
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    //TODO remove
    private val debugTitle = "Привет,мы на связи"
    private val debugMessage =
        "И мы к вам с хорошими новостями. Совсем скоро мы проведем вебинар по поиску на сайте — там будет масса полезной информации, которая поможет бустануть конверсию и повысить лояльность аудитории. Приходите!"
    private val debugImageUrl = "https://blog-frontend.envato.com/cdn-cgi/image/width=2560,quality=75,format=auto/uploads/sites/2/2022/04/E-commerce-App-JPG-File-scaled.jpg"

    private lateinit var supportFragmentManager: FragmentManager

    override fun initialize(fragmentManager: FragmentManager) {
        supportFragmentManager = fragmentManager
        showFullScreenAlertDialog(
            title = debugTitle,
            message = debugMessage,
            imageUrl = debugImageUrl
        )
    }

    override fun showAlertDialog(title: String, message: String) {
        DefaultAlertDialog.newInstance(
            title = title,
            message = message
        ).show(
            /* manager = */ supportFragmentManager,
            /* tag = */ DefaultAlertDialog.TAG
        )
    }

    override fun showFullScreenAlertDialog(
        title: String,
        message: String,
        imageUrl: String?
    ) {
        FullScreenDialog.newInstance(
            title = title,
            message = message,
            imageUrl = imageUrl
        ).show(
            /* manager = */ supportFragmentManager,
            /* tag = */ FullScreenDialog.TAG
        )
    }
}
