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
		T.setOnMessageListener(new OnMessageListener() {
			@SuppressLint("StaticFieldLeak")
			@Override
			public void onMessage(final Map<String, String> data) {
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

						Intent intent = new Intent(getApplicationContext(), AbstractMainActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

						//REQUIRED! For tracking click notification
						intent.putExtra(T.NOTIFICATION_URL, data.get("url"));

						PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

						NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id))
								.setSmallIcon(R.mipmap.ic_launcher)
								.setLargeIcon(result)
								.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("body")))
								.setContentTitle(data.get("title"))
								.setContentText(data.get("body"))
								.setAutoCancel(true)
								.setContentIntent(pendingIntent);

						NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						if( notificationManager != null ) {
							notificationManager.notify(0, notificationBuilder.build());
						} else {
							Log.e(T.TAG, "NotificationManager not allowed");
						}
					}
				}.execute(data.get("icon"));
			}
		});
	}
}
