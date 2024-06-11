package com.personalizatio.notification

import android.app.IntentService
import android.content.Intent
import com.personalizatio.notification.NotificationHelper.createNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationIntentService : IntentService(TAG) {

    override fun onHandleIntent(intent: Intent?) {
        intent ?: return

        val action = intent.action
        val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
        val imageUrls = intent.getStringExtra(NOTIFICATION_IMAGES)

        CoroutineScope(Dispatchers.IO).launch {
            val images = NotificationHelper.loadBitmaps(imageUrls)

            withContext(Dispatchers.Main) {
                when (action) {
                    ACTION_NEXT_IMAGE -> {
                        if (currentIndex + 1 < images.size) {
                            val data = intentToMap(intent)
                            createNotification(
                                context = this@NotificationIntentService,
                                data = data,
                                images = images,
                                currentIndex = currentIndex + 1
                            )
                        }
                    }

                    ACTION_PREVIOUS_IMAGE -> {
                        if (currentIndex - 1 >= 0) {
                            val data = intentToMap(intent)
                            createNotification(
                                context = this@NotificationIntentService,
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

    private fun intentToMap(intent: Intent): MutableMap<String, String> {
        val data: MutableMap<String, String> = HashMap()
        data[NOTIFICATION_TYPE] = intent.getStringExtra(NOTIFICATION_TYPE).orEmpty()
        data[NOTIFICATION_ID] = intent.getStringExtra(NOTIFICATION_ID).orEmpty()
        data[NOTIFICATION_IMAGES] = intent.getStringExtra(NOTIFICATION_IMAGES).orEmpty()
        data[NOTIFICATION_TITLE] = intent.getStringExtra(NOTIFICATION_TITLE).orEmpty()
        data[NOTIFICATION_BODY] = intent.getStringExtra(NOTIFICATION_BODY).orEmpty()
        return data
    }

    companion object {
        private const val TAG = "NotificationIntentService"
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