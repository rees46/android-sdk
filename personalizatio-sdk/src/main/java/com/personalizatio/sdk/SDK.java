package com.personalizatio.sdk;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
abstract class SDK {

	protected static final String SSID_FIELD = "ssid";
	protected static final String TOKEN_FIELD = "token";

	protected Context context;
	protected String shop_id;
	protected String ssid;
	public OnMessageListener onMessageListener;
	@SuppressLint("StaticFieldLeak")
	protected static SDK instance;

	protected ArrayList<Thread> queue = new ArrayList<>();
	private Class<?> apiClass;

	/**
	 * @param email user email
	 */
	public static void setEmail(String email) {
		HashMap<String, String> params = new HashMap<>();
		params.put("attributes[email]", email);
		instance.sendAsync("push_attributes", params);
	}

	/**
	 * @param url from data notification
	 */
	public static void notificationReceived(String url) {
		HashMap<String, String> params = new HashMap<>();
		params.put("url", url);
		instance.sendAsync("web_push_subscriptions/received", params);
	}

	/**
	 * @param url from data notification
	 */
	public static void notificationClicked(String url) {
		HashMap<String, String> params = new HashMap<>();
		params.put("url", url);
		instance.sendAsync("web_push_subscriptions/clicked", params);
	}

	/**
	 * @param listener Event on message receive
	 */
	public static void setOnMessageListener(OnMessageListener listener) {
		instance.onMessageListener = listener;
	}

	protected static void onMessage(Map<String, String> data) {
		if( instance.onMessageListener != null ) {
			instance.onMessageListener.onMessage(data);
		}
	}

	//----------Private--------------->

	/**
	 * @param message
	 */
	protected static void debug(String message) {
		Log.d(instance.getTag(), message);
	}

	/**
	 * @param message
	 */
	protected static void error(String message) {
		Log.e(instance.getTag(), message);
	}

	/**
	 * @param message
	 */
	protected static void error(String message, Throwable e) {
		Log.e(instance.getTag(), message, e);
	}

	/**
	 * @param shop_id Shop key
	 */
	protected SDK(Context context, String shop_id, Class<?> apiClass) {
		this.shop_id = shop_id;
		this.context = context;
		this.apiClass = apiClass;
		try {
			apiClass.getMethod("initialize", Class.class).invoke(null, apiClass);
		} catch(IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			e.printStackTrace();
			Log.e(getTag(), e.getMessage(), e);
		}

		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Create channel to show notifications.
			String channelId  = context.getString(R.string.notification_channel_id);
			String channelName = context.getString(R.string.notification_channel_name);
			NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
			if( notificationManager != null ) {
				notificationManager.createNotificationChannel(new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW));
			} else {
				Log.e(getTag(), "NotificationManager not allowed");
			}
		}
		ssid();
	}

	/**
	 * @return preferences key for service
	 */
	abstract protected String getPreferencesKey();

	/**
	 * @return tag for service
	 */
	abstract public String getTag();

	/**
	 * @return preferences
	 */
	final protected SharedPreferences prefs() {
		return context.getSharedPreferences(getPreferencesKey(), Context.MODE_PRIVATE);
	}

	/**
	 * Get ssid from properties or generate a new ssid
	 */
	final protected void ssid() {
		if( ssid == null ) {
			SharedPreferences preferences = prefs();
			ssid = preferences.getString(SSID_FIELD, null);
			if( ssid == null ) {
				generateSSID();
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						getToken();
					}
				});
				queue.add(thread);
			} else {
				getToken();
			}
		}
	}

	/**
	 * Generate a new ssid
	 */
	final protected void generateSSID() {
		HashMap<String, String> params = new HashMap<>();
		send("get","generate_ssid", params, new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(String msg) {
				if( msg != null && msg.length() > 0 ) {
					ssid = msg;
					SharedPreferences.Editor edit = prefs().edit();
					edit.putString(SSID_FIELD, ssid);
					edit.apply();
					for( Thread thread : queue ) {
						thread.start();
					}
					queue.clear();
				}
			}
		});
	}

	/**
	 * Get device token
	 */
	final protected void getToken() {
		FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
			@Override
			public void onComplete(@NonNull Task<InstanceIdResult> task) {
				if (!task.isSuccessful()) {
					Log.w(getTag(), "getInstanceId failed", task.getException());
					return;
				}

				// Get new Instance ID token
				final String token = task.getResult().getToken();
				Log.d(getTag(), "token: " + token);

				//Check send token
				if( prefs().getString(TOKEN_FIELD, null) == null || !prefs().getString(TOKEN_FIELD, null).equals(token) ) {

					//Send token
					try {
						HashMap<String, String> params = new HashMap<>();
						params.put("mobile", "true");
						JSONObject t = new JSONObject();
						t.put("endpoint", token);
						params.put("token", t.toString());
						send("post", "web_push_subscriptions", params, new Api.OnApiCallbackListener() {
							@Override
							public void onSuccess(String msg) {
								SharedPreferences.Editor edit = prefs().edit();
								edit.putString(TOKEN_FIELD, token);
								edit.apply();
							}
						});
					} catch(JSONException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	/**
	 * @param method
	 * @param params
	 * @param listener
	 */
	final protected void send(String request_type, String method, Map<String, String> params, @Nullable Api.OnApiCallbackListener listener) {
		params.put("shop_id", shop_id);
		if( ssid != null ){
			params.put("ssid", ssid);
		}
		try {
			apiClass.getMethod("send", request_type.getClass(), method.getClass(), Map.class, Api.OnApiCallbackListener.class)
					.invoke(null, new Object[] {request_type, method, params, listener});
		} catch(IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			SDK.error(e.getMessage(), e);
			e.printStackTrace();
		}
	}

	/**
	 * @param method
	 * @param params
	 */
	final protected void sendAsync(final String method, final HashMap<String, String> params) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					send("post", method, params, null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		if( ssid != null ) {
			thread.start();
		} else {
			queue.add(thread);
		}
	}
}
