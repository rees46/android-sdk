package com.personaclick.sample;

import com.personaclick.sdk.Personaclick;
import com.personalizatio.sample.AbstractSampleApplication;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
public class SampleApplication extends AbstractSampleApplication<Personaclick> {

	@Override
	protected String getShopId() {
		return "cb0516af5da25b1b41490072e679bc";
	}

	@Override
	protected void initialize() {
		Personaclick.initialize(getApplicationContext(), getShopId());
	}
}
