package com.personalizatio;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.personalizatio.utils.NotificationHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationIntentService extends IntentService {

    private static final String NOTIFICATION_TYPE = "type";
    private static final String NOTIFICATION_ID = "id";
    private static final String NOTIFICATION_TITLE = "title";
    private static final String NOTIFICATION_BODY = "body";
    private static final String NOTIFICATION_IMAGES = "images";
    private static final String CURRENT_IMAGE_INDEX = "current_image_index";

    private static final String ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE";
    private static final String ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE";

    public NotificationIntentService() {
        super("NotificationIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            int currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0);
            String imageUrls = intent.getStringExtra(NOTIFICATION_IMAGES);
            List<Bitmap> images = loadBitmaps(imageUrls);

            if (action != null) {
                switch (action) {
                    case ACTION_NEXT_IMAGE -> {
                        if (currentIndex + 1 < images.size()) {
                            Map<String, String> data = new HashMap<>();
                            data.put(NOTIFICATION_TYPE, intent.getStringExtra(NOTIFICATION_TYPE));
                            data.put(NOTIFICATION_ID, intent.getStringExtra(NOTIFICATION_ID));
                            data.put(NOTIFICATION_IMAGES, intent.getStringExtra(NOTIFICATION_IMAGES));
                            data.put(NOTIFICATION_TITLE, intent.getStringExtra(NOTIFICATION_TITLE));
                            data.put(NOTIFICATION_BODY, intent.getStringExtra(NOTIFICATION_BODY));
                            NotificationHelper.createNotification(this, data, images, currentIndex + 1);
                        }
                    }
                    case ACTION_PREVIOUS_IMAGE -> {
                        if (currentIndex - 1 >= 0) {
                            Map<String, String> data = new HashMap<>();
                            data.put(NOTIFICATION_TYPE, intent.getStringExtra(NOTIFICATION_TYPE));
                            data.put(NOTIFICATION_ID, intent.getStringExtra(NOTIFICATION_ID));
                            data.put(NOTIFICATION_IMAGES, intent.getStringExtra(NOTIFICATION_IMAGES));
                            data.put(NOTIFICATION_TITLE, intent.getStringExtra(NOTIFICATION_TITLE));
                            data.put(NOTIFICATION_BODY, intent.getStringExtra(NOTIFICATION_BODY));
                            NotificationHelper.createNotification(this, data, images, currentIndex - 1);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private List<Bitmap> loadBitmaps(String urls) {
        List<Bitmap> bitmaps = new ArrayList<>();
        if (urls != null) {
            String[] urlArray = urls.split(",");
            for (String url : urlArray) {
                try {
                    InputStream in = new URL(url).openStream();
                    bitmaps.add(BitmapFactory.decodeStream(in));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmaps;
    }
}
