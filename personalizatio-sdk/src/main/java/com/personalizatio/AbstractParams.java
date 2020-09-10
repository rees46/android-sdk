package com.personalizatio;

import android.text.TextUtils;

import java.util.HashMap;

abstract class AbstractParams<P extends AbstractParams<P>> {
	protected final HashMap<String, String> params = new HashMap<>();

	public interface ParamInterface {
		String getValue();
	}

	/**
	 * Вставка строковых параметров
	 */
	public P put(P.ParamInterface param, String value) {
		params.put(param.getValue(), value);
		return (P) this;
	}
	public P put(P.ParamInterface param, int value) {
		return put(param, String.valueOf(value));
	}
	public P put(P.ParamInterface param, boolean value) {
		return put(param, value ? "1" : "0");
	}


	/**
	 * Вставка параметров с массивом
	 */
	public P put(P.ParamInterface param, String[] value) {
		params.put(param.getValue(), TextUtils.join(",", value));
		return (P) this;
	}

	HashMap<String, String> build() {
		return params;
	}
}
