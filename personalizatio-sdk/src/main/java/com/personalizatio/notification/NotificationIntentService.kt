package com.personalizatio.notification

import android.app.IntentService
import android.content.Intent
import com.personalizatio.notification.NotificationHelper.createNotification

class NotificationIntentService : IntentService("NotificationIntentService") {

    override fun onHandleIntent(intent: Intent?) {

        if (intent != null) {

            val action = intent.action
            val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
            val imageUrls = intent.getStringExtra(NOTIFICATION_IMAGES)
            val images = NotificationHelper.loadBitmaps(imageUrls)

            if (action != null) {
                when (action) {
                    ACTION_NEXT_IMAGE -> {
                        if (currentIndex + 1 < images.size) {
                            val data: MutableMap<String?, String?> = HashMap()

                            data[NOTIFICATION_TYPE] = intent.getStringExtra(NOTIFICATION_TYPE)
                            data[NOTIFICATION_ID] = intent.getStringExtra(NOTIFICATION_ID)
                            data[NOTIFICATION_IMAGES] = intent.getStringExtra(NOTIFICATION_IMAGES)
                            data[NOTIFICATION_TITLE] = intent.getStringExtra(NOTIFICATION_TITLE)
                            data[NOTIFICATION_BODY] = intent.getStringExtra(NOTIFICATION_BODY)

                            createNotification(
                                context = this,
                                data = data,
                                images = images,
                                currentIndex = currentIndex + 1
                            )
                        }
                    }

                    ACTION_PREVIOUS_IMAGE -> {
                        if (currentIndex - 1 >= 0) {
                            val data: MutableMap<String?, String?> = HashMap()

                            data[NOTIFICATION_TYPE] = intent.getStringExtra(NOTIFICATION_TYPE)
                            data[NOTIFICATION_ID] = intent.getStringExtra(NOTIFICATION_ID)
                            data[NOTIFICATION_IMAGES] = intent.getStringExtra(NOTIFICATION_IMAGES)
                            data[NOTIFICATION_TITLE] = intent.getStringExtra(NOTIFICATION_TITLE)
                            data[NOTIFICATION_BODY] = intent.getStringExtra(NOTIFICATION_BODY)

                            createNotification(
                                context = this,
                                data = data,
                                images = images,
                                currentIndex = currentIndex - 1
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_TYPE = "type"
        private const val NOTIFICATION_ID = "id"
        private const val NOTIFICATION_TITLE = "title"
        private const val NOTIFICATION_BODY = "body"
        private const val NOTIFICATION_IMAGES = "images"
        private const val CURRENT_IMAGE_INDEX = "current_image_index"
        private const val ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE"
        private const val ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE"
    }
}
