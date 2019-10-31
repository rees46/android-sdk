package com.rees46.sdk;

import android.content.Context;

import com.personalizatio.SDK;
import com.personalizatio.BuildConfig;

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
	public static void initialize(Context context, String shop_id) {
		instance = new REES46(context, shop_id);
	}

}
