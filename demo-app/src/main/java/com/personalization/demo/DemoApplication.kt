package com.personalization.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDexApplication
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.google.firebase.FirebaseApp
import com.personalization.SDK
import com.personalization.demo.httplogger.HttpLogStore
import com.personalization.sdk.data.models.dto.notification.NotificationData
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

class DemoApplication : MultiDexApplication() {

    lateinit var sdk: SDK

    override fun onCreate() {
        // Capture any uncaught crash (including ones in MessagingService while a push is
        // handled — it runs in this process) so MainActivity can show it for the tester.
        installCrashHandler()
        super.onCreate()
        // Capture all SDK HTTP traffic for the in-app HTTP log screen (debug aid).
        SDK.networkLogger = HttpLogStore
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        initSdk()
    }

    private fun initSdk() {
        // Official initialization (per docs): create the SDK once in Application.onCreate so it is
        // ready in every process start — including the cold process FCM starts just to deliver a
        // push.
        try {
            sdk = SDK()
            sdk.initialize(
                context = applicationContext,
                shopId = BuildConfig.SHOP_ID,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        createPushChannel()

        // Displaying a push is the host app's responsibility: the SDK delivers the parsed
        // NotificationData via setOnMessageListener, and the host posts the notification. We build a
        // standard BigPicture notification — the same approach the React Native demo uses (notifee
        // AndroidStyle.BIGPICTURE): title and body are visible without expanding, the image is shown
        // as the big picture, and tapping opens the app. Set in Application so it also works when FCM
        // delivers a push to a cold process. Image download runs off the main thread.
        sdk.setOnMessageListener { data ->
            Thread { showPushNotification(data) }.start()
        }
    }

    private fun createPushChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH so the push appears as a heads-up pop-up with sound.
            val channel = NotificationChannel(
                PUSH_CHANNEL_ID,
                getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds and posts a standard BigPicture notification from the SDK push data — the native
     * equivalent of the React Native demo's notifee BIGPICTURE notification.
     */
    private fun showPushNotification(data: NotificationData) {
        val bigPicture = data.image?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { url -> loadBitmap(url, BIG_PICTURE_MAX_WIDTH_PX, BIG_PICTURE_MAX_HEIGHT_PX) }
        val largeIcon = data.icon?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { url -> loadBitmap(url, LARGE_ICON_SIZE_PX, LARGE_ICON_SIZE_PX) }

        val builder = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            // Host owns the push icon — the demo uses its own notification glyph, not an SDK asset.
            .setSmallIcon(R.drawable.ic_demo_notification)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(buildContentIntent(data))

        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        if (bigPicture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .bigLargeIcon(null as Bitmap?),
            )
        }

        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify((data.title.orEmpty() + data.body.orEmpty()).hashCode(), builder.build())
    }

    /** Tapping opens MainActivity, carrying the push type/id so it can report the click to the SDK. */
    private fun buildContentIntent(data: NotificationData): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("NOTIFICATION_TYPE", data.type)
            putExtra("NOTIFICATION_ID", data.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            (data.id ?: (data.title.orEmpty() + data.body.orEmpty())).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Push images go through Glide, not through `URL.openStream()` + `BitmapFactory`. Google Play
     * flags a manual download-and-decode ("Improve your app's performance with bitmap image
     * optimisation"): the bitmap is allocated at the source resolution, so a large product photo
     * can cost tens of megabytes for a notification that is a few hundred pixels tall. Glide
     * downsamples into the target box, caches the download and pools the bitmaps.
     *
     * Runs on the background thread that [showPushNotification] is called on — `get()` blocks.
     */
    private fun loadBitmap(url: String, width: Int, height: Int): Bitmap? {
        val target = Glide.with(this)
            .asBitmap()
            // Fit inside the box, keeping the aspect ratio — the notification shows the whole image.
            .downsample(DownsampleStrategy.AT_MOST)
            .centerInside()
            // RemoteViews cannot take a Config.HARDWARE bitmap, and it cannot be copied out of.
            .disallowHardwareConfig()
            .load(url)
            .submit(width, height)
        return try {
            target.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // clear() below may hand the pooled bitmap to the next decode while the posted
                // notification still draws it, so keep an independent copy.
                ?.let { bitmap -> bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            Glide.with(this).clear(target)
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
                val text = "Thread: ${thread.name}\n\n$stack"
                // commit() is synchronous — it must persist before the process is killed.
                getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, text)
                    .commit()
            } catch (_: Throwable) {
                // Never let the crash handler itself throw.
            }
            // Let the default handler run so the OS still reports/kills as usual.
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_PREFS = "demo_crash"
        const val KEY_LAST_CRASH = "last_crash"
        const val PUSH_CHANNEL_ID = "demo_push"

        // The platform itself scales a big picture down to about this box before showing it in the
        // shade, so decoding anything larger is wasted memory. The large icon is 64dp on xxhdpi.
        private const val BIG_PICTURE_MAX_WIDTH_PX = 1024
        private const val BIG_PICTURE_MAX_HEIGHT_PX = 512
        private const val LARGE_ICON_SIZE_PX = 192
        private const val LOAD_TIMEOUT_SECONDS = 15L
    }
}
