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

import com.personalizatio.OnMessageListener;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.BuildConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
final public class REES46 extends SDK {

	public static final String TAG = "REES46";
	public static final String NOTIFICATION_URL = "REES46_NOTIFICATION_URL";
	protected static final String PREFERENCES_KEY = "rees46.sdk";
	protected static final String API_URL = BuildConfig.DEBUG ? "http://dev.api.rees46.com:8080/" : "https://api.rees46.com/";

	/**
	 * @param context application context
	 * @param shop_id Shop key
	 */
	private REES46(Context context, String shop_id) {
		super(context, shop_id, API_URL, TAG, PREFERENCES_KEY);
	}

	/**
	 * Initialize api
	 * @param shop_id Shop key
	 */
	public static void initialize(final Context context, String shop_id) {
		instance = new REES46(context, shop_id);
		//Дефолтное отображение сообщения без кастомизации
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

						Intent intent = new Intent(context, context.getClass());
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

						//REQUIRED! For tracking click notification
						intent.putExtra(REES46.NOTIFICATION_URL, data.get("url"));

						PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

						NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, "notification_channel")
								.setLargeIcon(result)
								.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("body")))
								.setContentTitle(data.get("title"))
								.setContentText(data.get("body"))
								.setAutoCancel(true)
								.setContentIntent(pendingIntent);

						NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
						if( notificationManager != null ) {
							notificationManager.notify(0, notificationBuilder.build());
						} else {
							Log.e(REES46.TAG, "NotificationManager not allowed");
						}
					}
				}.execute(data.get("icon"));
			}
		});
	}

}
