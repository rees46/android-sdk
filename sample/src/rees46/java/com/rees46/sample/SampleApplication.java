package com.rees46.sample;


import com.personalizatio.BuildConfig;
import com.personalizatio.sample.AbstractSampleApplication;
import com.rees46.sdk.REES46;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
public class SampleApplication extends AbstractSampleApplication<REES46> {

	@Override
	protected String getShopId() {
		return BuildConfig.DEBUG ? "cb0516af5da25b1b41490072e679bc" : "357382bf66ac0ce2f1722677c59511";
	}

	@Override
	protected void initialize() {
		REES46.initialize(getApplicationContext(), getShopId());
	}
}
