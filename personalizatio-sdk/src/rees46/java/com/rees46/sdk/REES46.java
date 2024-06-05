package com.rees46.sdk;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.personalizatio.BuildConfig;
import com.personalizatio.OnMessageListener;
import com.personalizatio.SDK;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

final public class REES46 extends SDK {

    public static final String TAG = "REES46";
    public static final String NOTIFICATION_TYPE = "REES46_NOTIFICATION_TYPE";
    public static final String NOTIFICATION_ID = "REES46_NOTIFICATION_ID";
    protected static final String PREFERENCES_KEY = "rees46.sdk";
    protected static final String API_URL = BuildConfig.DEBUG ? "http://dev.api.rees46.com:8000/" : "https://api.rees46.ru/";
    private static final String NOTIFICATION_CHANNEL = "notification_channel";

    private static final String IMAGE_FIELD = "image";
    private static final String BODY_FIELD = "body";
    private static final String TITLE_FIELD = "title";
    private static final String TYPE_FIELD = "type";
    private static final String ID_FIELD = "id";

    private REES46(Context context, String shop_id, String api_host) {
        super(context, shop_id, api_host == null ? API_URL : "https://".concat(api_host).concat("/"), TAG, PREFERENCES_KEY, "android");
    }

    public static void initialize(final Context context, String shop_id) {
        initialize(context, shop_id, null);
    }

    public static void initialize(final Context context, String shop_id, String api_host) {
        if (instance == null) {
            instance = new REES46(context, shop_id, api_host);
            REES46.setOnMessageListener(new OnMessageListener() {
                @SuppressLint("StaticFieldLeak")
                @Override
                public void onMessage(Map<String, String> data) {
                    new AsyncTask<String, Void, Bitmap>() {
                        @Override
                        protected Bitmap doInBackground(String... params) {
                            try {
                                InputStream in = new URL(params[0]).openStream();
                                return BitmapFactory.decodeStream(in);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Bitmap result) {
                            super.onPostExecute(result);
                            createNotification(context, data);
                        }
                    }.execute(data.get(IMAGE_FIELD));
                }
            });
        }
    }

    private static void createNotification(Context context, Map<String, String> data) {
        Intent intent = new Intent(context, context.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        intent.putExtra(REES46.NOTIFICATION_TYPE, data.get(TYPE_FIELD));
        intent.putExtra(REES46.NOTIFICATION_ID, data.get(ID_FIELD));

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setContentTitle(data.get(TITLE_FIELD))
                .setContentText(data.get(BODY_FIELD))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Check if there is an image URL in the data
        String imageUrl = data.get(IMAGE_FIELD);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            // Load the image asynchronously
            new AsyncTask<String, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(String... params) {
                    try {
                        InputStream in = new URL(params[0]).openStream();
                        return BitmapFactory.decodeStream(in);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(Bitmap result) {
                    super.onPostExecute(result);
                    if (result != null) {
                        // If the image is loaded successfully, set it as the large icon
                        notificationBuilder.setLargeIcon(result)
                                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(result).bigLargeIcon(null));
                    } else {
                        // If the image failed to load, use BigTextStyle
                        notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get(BODY_FIELD)));
                    }

                    // Show the notification
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (notificationManager != null) {
                        notificationManager.notify(0, notificationBuilder.build());
                    } else {
                        Log.e(REES46.TAG, "NotificationManager not allowed");
                    }
                }
            }.execute(imageUrl);
        } else {
            // If there is no image URL, use BigTextStyle
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get(BODY_FIELD)));

            // Show the notification
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(0, notificationBuilder.build());
            } else {
                Log.e(REES46.TAG, "NotificationManager not allowed");
            }
        }
    }
}
