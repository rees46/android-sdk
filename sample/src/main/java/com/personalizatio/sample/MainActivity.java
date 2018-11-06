package com.personalizatio.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.personalizatio.sdk.REES46;

public class MainActivity extends AppCompatActivity {

	private EditText text;
	private Button button;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if( getIntent().getExtras() != null && getIntent().getExtras().getString(REES46.NOTIFICATION_URL, null) != null ) {
			REES46.notificationClicked(getIntent().getExtras().getString(REES46.NOTIFICATION_URL, null));
		}

		button = findViewById(R.id.button);
		text = findViewById(R.id.email);
		text.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if( actionId == EditorInfo.IME_ACTION_DONE ) {
					button.callOnClick();
				}
				return false;
			}
		});

		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if( !text.getText().toString().isEmpty() ) {
					REES46.setEmail(text.getText().toString());
					Toast.makeText(getApplicationContext(), "Email sent", Toast.LENGTH_LONG).show();
				}
			}
		});

	}
}
