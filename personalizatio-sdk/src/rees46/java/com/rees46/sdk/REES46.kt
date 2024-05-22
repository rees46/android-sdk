package com.rees46.sdk

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personalizatio.BuildConfig
import com.personalizatio.OnMessageListener
import com.personalizatio.SDK
import java.io.IOException
import java.io.InputStream
import java.net.URL

class REES46 private constructor(context: Context, shop_id: String?, api_host: String?) : SDK(
    context,
    shop_id,
    api_host?.let { "https://$it/" } ?: API_URL,
    TAG,
    PREFERENCES_KEY,
    "android"
) {
    companion object {
        const val TAG: String = "REES46"
        const val NOTIFICATION_TYPE: String = "REES46_NOTIFICATION_TYPE"
        const val NOTIFICATION_ID: String = "REES46_NOTIFICATION_ID"
        protected const val PREFERENCES_KEY: String = "rees46.sdk"
        protected val API_URL: String =
            if (BuildConfig.DEBUG) "http://dev.api.rees46.com:8000/" else "https://api.rees46.ru/"

        private var instance: REES46? = null

        /**
         * Initialize api
         * @param shop_id Shop key
         */
        @JvmStatic
        fun initialize(context: Context, shop_id: String?) {
            initialize(context, shop_id, null)
        }

        /**
         * Initialize api
         * @param shop_id Shop key
         */
        @JvmStatic
        fun initialize(context: Context, shop_id: String?, api_host: String?) {
            if (instance == null) {
                instance = REES46(context, shop_id, api_host)
                // Дефолтное отображение сообщения без кастомизации
                setOnMessageListener(object : OnMessageListener {
                    @SuppressLint("StaticFieldLeak")
                    override fun onMessage(data: Map<String?, String?>) {
                        object : AsyncTask<String?, Void?, Bitmap?>() {
                            override fun doInBackground(vararg params: String?): Bitmap? {
                                return try {
                                    val `in`: InputStream = URL(params[0]).openStream()
                                    BitmapFactory.decodeStream(`in`)
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

                                val pendingIntent: PendingIntent =
                                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)

                                val notificationBuilder: NotificationCompat.Builder =
                                    NotificationCompat.Builder(context, "notification_channel")
                                        .setLargeIcon(result)
                                        .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
                                        .setContentTitle(data["title"])
                                        .setContentText(data["body"])
                                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                                        .setAutoCancel(true)
                                        .setContentIntent(pendingIntent)

                                val notificationManager: NotificationManager =
                                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                if (notificationManager != null) {
                                    notificationManager.notify(0, notificationBuilder.build())
                                } else {
                                    Log.e(TAG, "NotificationManager not allowed")
                                }
                            }
                        }.execute(data["icon"])
                    }
                })
            }
        }
    }
}
