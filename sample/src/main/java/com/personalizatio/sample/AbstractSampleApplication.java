package com.personalizatio.sample;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import com.personalizatio.SDK;
import com.personalizatio.notification.NotificationHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractSampleApplication<T extends SDK> extends Application {

    protected abstract String getShopId();

    protected abstract void initialize();

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void onCreate() {
        super.onCreate();

        // Demo shop
        initialize();
        T.getSid(sid -> Log.d("APP", "sid: " + sid));
        T.setOnMessageListener(data -> executorService.submit(() -> {
            List<Bitmap> images = NotificationHelper.loadBitmaps(data.get(NotificationHelper.NOTIFICATION_IMAGES));
            NotificationHelper.createNotification(getApplicationContext(), data, images, 0);
        }));
    }

}
