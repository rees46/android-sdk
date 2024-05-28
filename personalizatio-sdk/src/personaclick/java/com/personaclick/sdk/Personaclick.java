package com.personaclick.sdk;

import android.content.Context;

import com.personalizatio.SDK;
import com.personalizatio.BuildConfig;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
final public class Personaclick extends SDK {

	public static final String TAG = "PERSONACLICK";
	public static final String NOTIFICATION_TYPE = "PERSONACLICK_NOTIFICATION_TYPE";
	public static final String NOTIFICATION_ID = "PERSONACLICK_NOTIFICATION_ID";
	protected static final String PREFERENCES_KEY = "personaclick.sdk";
	protected static final String API_URL = BuildConfig.DEBUG ? "https://api.rees46.ru/" : "https://api.personaclick.com/";
	/**
	 * @param context application context
	 * @param shop_id Shop key
	 */
	private Personaclick(Context context, String shop_id) {
		super(context, shop_id, API_URL, TAG, PREFERENCES_KEY, "android");
	}

	/**
	 * Initialize api
	 * @param shop_id Shop key
	 */
	public static void initialize(Context context, String shop_id) {
		if( instance == null ) {
			instance = new Personaclick(context, shop_id);
		}
	}
}
