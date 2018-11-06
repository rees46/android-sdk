package com.personalizatio.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.personalizatio.sdk.REES46;

public class MainActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		REES46.setEmail("nixx.dj@gmail.com");
		if( getIntent().getExtras() != null && getIntent().getExtras().getString(REES46.NOTIFICATION_URL, null) != null ) {
			REES46.notificationClicked(getIntent().getExtras().getString("REES46_NOTIFICATION_URL", null));
		}

	}
}
