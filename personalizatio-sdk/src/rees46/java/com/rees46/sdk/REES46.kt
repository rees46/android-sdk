package com.rees46.sdk

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import androidx.core.app.NotificationCompat
import com.personalizatio.BuildConfig
import com.personalizatio.OnMessageListener
import com.personalizatio.SDK
import java.io.IOException
import java.net.URL

class REES46 private constructor() : SDK() {
    companion object {
        const val TAG: String = "REES46"
        const val NOTIFICATION_TYPE: String = "REES46_NOTIFICATION_TYPE"
        const val NOTIFICATION_ID: String = "REES46_NOTIFICATION_ID"
        protected const val PREFERENCES_KEY: String = "rees46.sdk"
        protected val API_URL: String = if (BuildConfig.DEBUG) "http://dev.api.rees46.com:8000/" else "https://api.rees46.ru/"

        fun getInstance() : SDK {
            return SDK.getInstance()
        }

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        fun initialize(context: Context, shopId: String, apiHost: String? = null) {
            val apiUrl = apiHost?.let { "https://$it/" } ?: API_URL

            val sdk = getInstance()
            sdk.initialize(context, shopId, apiUrl, TAG, PREFERENCES_KEY, "android")

            // Дефолтное отображение сообщения без кастомизации
            sdk.setOnMessageListener(object : OnMessageListener {
                @SuppressLint("StaticFieldLeak")
                override fun onMessage(data: Map<String, String>) {
                    object : AsyncTask<String?, Void?, Bitmap?>() {
                        override fun doInBackground(vararg params: String?): Bitmap? {
                            return try {
                                val inputStream = URL(params[0]).openStream()
                                BitmapFactory.decodeStream(inputStream)
                            } catch (e: IOException) {
                                e.printStackTrace()
                                null
                            }
                        }

                        override fun onPostExecute(result: Bitmap?) {
                            super.onPostExecute(result)

                            val intent = Intent(context, context::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                            // REQUIRED! For tracking click notification
                            intent.putExtra(NOTIFICATION_TYPE, data["type"])
                            intent.putExtra(NOTIFICATION_ID, data["id"])

                            val pendingIntent = PendingIntent.getActivity(context, 0, intent,
                                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

                            val notificationBuilder = NotificationCompat.Builder(context, "notification_channel")
                                .setLargeIcon(result)
                                .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
                                .setContentTitle(data["title"])
                                .setContentText(data["body"])
                                .setSmallIcon(android.R.drawable.stat_notify_chat)
                                .setAutoCancel(true)
                                .setContentIntent(pendingIntent)

                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(0, notificationBuilder.build())
                        }
                    }.execute(data["icon"])
                }
            })
        }
    }
}
