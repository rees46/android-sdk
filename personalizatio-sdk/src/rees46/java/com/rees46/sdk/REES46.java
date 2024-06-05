package com.rees46.sdk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.personalizatio.BuildConfig;
import com.personalizatio.SDK;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final public class REES46 extends SDK {

    public static final String TAG = "REES46";
    public static final String NOTIFICATION_TYPE = "type";
    public static final String NOTIFICATION_ID = "id";
    protected static final String PREFERENCES_KEY = "rees46.sdk";
    protected static final String API_URL = BuildConfig.DEBUG ? "http://dev.api.rees46.com:8000/" : "https://api.rees46.ru/";
    private static final String NOTIFICATION_CHANNEL = "notification_channel";

    private static final String IMAGE_FIELD = "image";
    private static final String BODY_FIELD = "body";
    private static final String TITLE_FIELD = "title";
    private static final String TYPE_FIELD = "type";
    private static final String ID_FIELD = "id";

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private REES46(Context context, String shop_id, String api_host) {
        super(context, shop_id, api_host == null ? API_URL : "https://".concat(api_host).concat("/"), TAG, PREFERENCES_KEY, "android");
    }

    public static void initialize(final Context context, String shop_id) {
        initialize(context, shop_id, null);
    }

    public static void initialize(final Context context, String shop_id, String api_host) {
        if (instance == null) {
            instance = new REES46(context, shop_id, api_host);
            REES46.setOnMessageListener(data -> {
                String imageUrl = data.get(IMAGE_FIELD);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    executorService.submit(() -> {
                        Bitmap bitmap = loadBitmap(imageUrl);
                        createNotification(context, data, bitmap);
                    });
                } else {
                    createNotification(context, data, null);
                }
            });
        }
    }

    private static Bitmap loadBitmap(String url) {
        if (url != null) {
            try {
                InputStream in = new URL(url).openStream();
                return BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static void createNotification(Context context, Map<String, String> data, Bitmap bitmap) {
        Intent intent = new Intent(context, context.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        intent.putExtra(NOTIFICATION_TYPE, data.get(TYPE_FIELD));
        intent.putExtra(NOTIFICATION_ID, data.get(ID_FIELD));

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setContentTitle(data.get(TITLE_FIELD))
                .setContentText(data.get(BODY_FIELD))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (bitmap != null) {
            notificationBuilder.setLargeIcon(bitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null));
        } else {
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get(BODY_FIELD)));
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(0, notificationBuilder.build());
        } else {
            Log.e(TAG, "NotificationManager not allowed");
        }
    }
}
