package com.personalizatio.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.personalizatio.NotificationIntentService;
import com.rees46.sdk.REES46;

import java.util.List;
import java.util.Map;

public class NotificationHelper {

    public static final String ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE";
    public static final String ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE";
    public static final String CURRENT_IMAGE_INDEX = "current_image_index";
    public static final String NOTIFICATION_IMAGES = "images";
    public static final String NOTIFICATION_TITLE = "title";
    public static final String NOTIFICATION_BODY = "body";
    private static final String NOTIFICATION_CHANNEL = "notification_channel";

    public static void createNotification(Context context, Map<String, String> data, List<Bitmap> images, int currentIndex) {
        Intent intent = new Intent(context, context.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(NOTIFICATION_TITLE, data.get(NOTIFICATION_TITLE));
        intent.putExtra(NOTIFICATION_BODY, data.get(NOTIFICATION_BODY));
        intent.putExtra(NOTIFICATION_IMAGES, data.get(NOTIFICATION_IMAGES));
        intent.putExtra(CURRENT_IMAGE_INDEX, currentIndex);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setContentTitle(data.get(NOTIFICATION_TITLE))
                .setContentText(data.get(NOTIFICATION_BODY))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (images != null && !images.isEmpty()) {
            Bitmap currentImage = images.get(currentIndex);
            notificationBuilder.setLargeIcon(currentImage)
                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(currentImage).bigLargeIcon(null));

            if (currentIndex > 0) {
                PendingIntent prevPendingIntent = createPendingIntent(context, ACTION_PREVIOUS_IMAGE, currentIndex, data);
                notificationBuilder.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_previous, "Назад", prevPendingIntent).build());
            }

            if (currentIndex < images.size() - 1) {
                PendingIntent nextPendingIntent = createPendingIntent(context, ACTION_NEXT_IMAGE, currentIndex, data);
                notificationBuilder.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_next, "Вперед", nextPendingIntent).build());
            }
        } else {
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get(NOTIFICATION_BODY)));
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(0, notificationBuilder.build());
            Log.d("Notification", "Notification posted with currentIndex: " + currentIndex);
        } else {
            Log.e(REES46.TAG, "NotificationManager not allowed");
        }
    }

    private static PendingIntent createPendingIntent(Context context, String action, int currentIndex, Map<String, String> data) {
        Intent intent = new Intent(context, NotificationIntentService.class);
        intent.setAction(action);
        intent.putExtra(CURRENT_IMAGE_INDEX, currentIndex);
        intent.putExtra(NOTIFICATION_IMAGES, data.get(NOTIFICATION_IMAGES));
        intent.putExtra(NOTIFICATION_TITLE, data.get(NOTIFICATION_TITLE));
        intent.putExtra(NOTIFICATION_BODY, data.get(NOTIFICATION_BODY));
        return PendingIntent.getService(context, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

