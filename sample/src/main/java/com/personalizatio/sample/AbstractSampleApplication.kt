package com.personalizatio.sample

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personalizatio.OnMessageListener
import com.personalizatio.SDK
import java.io.IOException
import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
abstract class AbstractSampleApplication<out T : SDK> internal constructor(
    private val classT: KClass<T>
): Application() {
    protected abstract val shopId: String?
        get

    protected abstract fun initialize()

    override fun onCreate() {
        super.onCreate()

        val sdk = classT.safeCast(SDK)

        //Demo shop
        initialize()
        sdk?.getSid { sid -> Log.d("APP", "sid: $sid") }
        sdk?.setOnMessageListener(object : OnMessageListener {
            @SuppressLint("StaticFieldLeak")
            override fun onMessage(data: Map<String, String>) {
                object : AsyncTask<String?, Void?, Bitmap?>() {
                    protected override fun doInBackground(vararg params: String?): Bitmap? {
                        if (params[0] != null) {
                            try {
                                val `in` = URL(params[0]).openStream()
                                return BitmapFactory.decodeStream(`in`)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        return null
                    }

                    override fun onPostExecute(result: Bitmap?) {
                        super.onPostExecute(result)

                        val intent = Intent(applicationContext, AbstractMainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                        //REQUIRED! For tracking click notification
                        intent.putExtra(sdk.NOTIFICATION_TYPE, data["type"])
                        intent.putExtra(sdk.NOTIFICATION_ID, data["id"])

                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            0,
                            intent,
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val notificationBuilder = NotificationCompat.Builder(
                            applicationContext, getString(R.string.notification_channel_id)
                        )
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
                            .setContentTitle(data["title"])
                            .setContentText(data["body"])
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)

                        if (result != null) {
                            notificationBuilder.setLargeIcon(result)
                        }

                        val notificationManager =
                            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(0, notificationBuilder.build())
                            ?: Log.e(sdk.tag, "NotificationManager not allowed")
                    }
                }.execute(data["icon"])
            }
        })
    }
}
