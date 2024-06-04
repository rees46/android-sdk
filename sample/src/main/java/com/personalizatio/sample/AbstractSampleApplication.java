package com.personalizatio.sample;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.personalizatio.OnMessageListener;
import com.personalizatio.SDK;
import com.rees46.sdk.REES46;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
public abstract class AbstractSampleApplication<T extends SDK> extends Application {

	protected abstract String getShopId();

	protected abstract void initialize();

	public void onCreate() {
		super.onCreate();

		//Demo shop
		initialize();
		T.getSid(sid -> Log.d("APP", "sid: " + sid));
		T.setOnMessageListener(new OnMessageListener() {
			@SuppressLint("StaticFieldLeak")
			@Override
			public void onMessage(final Map<String, String> data) {
				new AsyncTask<String, Void, Bitmap>() {

					@Override
					protected Bitmap doInBackground(String... params) {
						if (params[0] != null) {
							try {
								InputStream in = new URL(params[0]).openStream();
								return BitmapFactory.decodeStream(in);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						return null;
					}

					@Override
					protected void onPostExecute(Bitmap result) {
						super.onPostExecute(result);
						createNotification(getApplicationContext(), data);
					}
				}.execute(data.get("icon"));
			}
		});
	}

	private static void createNotification(Context context, Map<String, String> data) {
		Intent intent = new Intent(context, context.getClass());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

		intent.putExtra(REES46.NOTIFICATION_TYPE, data.get("type"));
		intent.putExtra(REES46.NOTIFICATION_ID, data.get("id"));

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, "notification_channel")
				.setContentTitle(data.get("title"))
				.setContentText(data.get("body"))
				.setSmallIcon(android.R.drawable.stat_notify_chat)
				.setAutoCancel(true)
				.setContentIntent(pendingIntent);

		// Check if there is an image URL in the data
		String imageUrl = data.get("image");
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
						notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("body")));
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
			notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("body")));

			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.notify(0, notificationBuilder.build());
			} else {
				Log.e(REES46.TAG, "NotificationManager not allowed");
			}
		}
	}
}
