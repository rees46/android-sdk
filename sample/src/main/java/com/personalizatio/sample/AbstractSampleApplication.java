package com.personalizatio.sample;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.personalizatio.SDK;
import com.rees46.sdk.REES46;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractSampleApplication<T extends SDK> extends Application {

	protected abstract String getShopId();

	protected abstract void initialize();

	private static final String NOTIFICATION_TYPE = "type";
	private static final String NOTIFICATION_ID = "id";
	private static final String NOTIFICATION_TITLE = "title";
	private static final String NOTIFICATION_BODY = "body";
	private static final String NOTIFICATION_IMAGE = "image";
	private static final String NOTIFICATION_CHANNEL = "notification_channel";

	private final ExecutorService executorService = Executors.newFixedThreadPool(4);

	public void onCreate() {
		super.onCreate();

		// Demo shop
		initialize();
		T.getSid(sid -> Log.d("APP", "sid: " + sid));
		T.setOnMessageListener(data -> executorService.submit(() -> {
			Bitmap result = loadBitmap(data.get(NOTIFICATION_IMAGE));
			createNotification(getApplicationContext(), data, result);
		}));
	}

	private Bitmap loadBitmap(String url) {
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

	private static void createNotification(Context context, Map<String, String> data, Bitmap result) {
		Intent intent = new Intent(context, context.getClass());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

		intent.putExtra(NOTIFICATION_TYPE, data.get(NOTIFICATION_TYPE));
		intent.putExtra(NOTIFICATION_ID, data.get(NOTIFICATION_ID));

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
				.setContentTitle(data.get(NOTIFICATION_TITLE))
				.setContentText(data.get(NOTIFICATION_BODY))
				.setSmallIcon(android.R.drawable.stat_notify_chat)
				.setAutoCancel(true)
				.setContentIntent(pendingIntent);

		if (result != null) {
			notificationBuilder.setLargeIcon(result)
					.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(result).bigLargeIcon(null));
		} else {
			notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get(NOTIFICATION_BODY)));
		}

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (notificationManager != null) {
			notificationManager.notify(0, notificationBuilder.build());
		} else {
			Log.e(REES46.TAG, "NotificationManager not allowed");
		}
	}
}
