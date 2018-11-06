package com.personalizatio.sdk;

import android.content.Context;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
final public class REES46 extends SDK {

	public static final String TAG = "REES46";
	private static final String PREFERENCES_KEY = "rees46.sdk";
	public static final String NOTIFICATION_URL = "REES46_NOTIFICATION_URL";

	/**
	 * @param context application context
	 * @param shop_id Shop key
	 */
	protected REES46(Context context, String shop_id) {
		super(context, shop_id, Api.R46.class);
	}

	@Override
	protected String getPreferencesKey() {
		return PREFERENCES_KEY;
	}

	@Override
	public String getTag() {
		return TAG;
	}

	/**
	 * Initialize api
	 * @param shop_id Shop key
	 */
	public static void initialize(Context context, String shop_id) {
		instance = new REES46(context, shop_id);
	}

}
