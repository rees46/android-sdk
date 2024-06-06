package com.personalizatio.sample;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.personalizatio.SDK;
import com.personalizatio.utils.NotificationHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
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
            List<Bitmap> images = loadBitmaps(data.get(NotificationHelper.NOTIFICATION_IMAGES));
            NotificationHelper.createNotification(getApplicationContext(), data, images, 0);
        }));
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
