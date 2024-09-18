package com.personalization.features.inAppNotification.impl

import androidx.fragment.app.FragmentManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.inAppNotification.view.AlertDialog
import javax.inject.Inject

class InAppNotificationManagerImpl @Inject constructor() : InAppNotificationManager {

    //TODO remove
    private val debugTitle = "Привет,мы на связи"
    private val debugMessage =
        "И мы к вам с хорошими новостями. Совсем скоро мы проведем вебинар по поиску на сайте — там будет масса полезной информации, которая поможет бустануть конверсию и повысить лояльность аудитории. Приходите!"

    private lateinit var supportFragmentManager: FragmentManager

    override fun initialize(fragmentManager: FragmentManager) {
        supportFragmentManager = fragmentManager
        showAlertDialog(
            title = debugTitle,
            message = debugMessage
        )
    }

    override fun showAlertDialog(title: String, message: String) {
        AlertDialog.newInstance(
            title = title,
            message = message
        ).show(
            /* manager = */ supportFragmentManager,
            /* tag = */ AlertDialog.TAG
        )
    }
}
