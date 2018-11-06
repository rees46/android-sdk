package com.personalizatio.sdk;

import android.content.Context;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
final public class Personaclick extends SDK {

	public static final String TAG = "PERSONACLICK";
	private static final String PREFERENCES_KEY = "personaclick.sdk";
	public static final String NOTIFICATION_URL = "PERSONACLICK_NOTIFICATION_URL";

	/**
	 * @param context application context
	 * @param shop_id Shop key
	 */
	protected Personaclick(Context context, String shop_id) {
		super(context, shop_id, Api.PC.class);
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
		instance = new Personaclick(context, shop_id);
	}
}
