package com.personalizatio;

import java.util.Map;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
public interface OnMessageListener {
	void onMessage(Map<String, String> data);
}
