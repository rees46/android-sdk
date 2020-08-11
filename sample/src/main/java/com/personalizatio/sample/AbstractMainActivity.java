package com.personalizatio.sample;

import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.personalizatio.Api;
import com.personalizatio.Params;
import com.personalizatio.SDK;

import org.json.JSONObject;

public abstract class AbstractMainActivity<T extends SDK> extends AppCompatActivity {

	private EditText text;
	private Button button;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if( getIntent().getExtras() != null && getIntent().getExtras().getString(T.NOTIFICATION_URL, null) != null ) {
			T.notificationClicked(getIntent().getExtras().getString(T.NOTIFICATION_URL, null));
		}

		button = findViewById(R.id.button);
		text = findViewById(R.id.email);
		text.setOnEditorActionListener((v, actionId, event) -> {
			if( actionId == EditorInfo.IME_ACTION_DONE ) {
				button.callOnClick();
			}
			return false;
		});

		button.setOnClickListener(v -> {
			if( !text.getText().toString().isEmpty() ) {
				T.setEmail(text.getText().toString());
				Toast.makeText(getApplicationContext(), "Email sent", Toast.LENGTH_LONG).show();
			}
		});

		//Запрашиваем поиск
		Params params = new Params();
		params.put(Params.Parameter.LOCATIONS, "location");
		T.search("coats", Params.SEARCH_TYPE.INSTANT, params, new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(JSONObject response) {
				Log.i(T.TAG, "Search response: " + response.toString());
			}
		});

		//Запрашиваем поиск при клике на пустое поле
		T.search_blank(new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(JSONObject response) {
				Log.i(T.TAG, "Search response: " + response.toString());
			}
		});

		//Запрашиваем блок рекомендаций
		T.recommend("e9ddb9cdc66285fac40c7a897760582a", new Api.OnApiCallbackListener() {
			@Override
			public void onSuccess(JSONObject response) {
				Log.i(T.TAG, "Recommender response: " + response.toString());
			}
		});

		//Просмотр товара (простой)
		T.track(Params.TrackEvent.VIEW, "37");

		//Добавление в корзину (простое)
		T.track(Params.TrackEvent.CART, "37");

		//Добавление в корзину (расширенный)
		Params cart = new Params();
		cart
			.put(new Params.Item("37")
				.set(Params.Item.COLUMN.AMOUNT, 2)
				.set(Params.Item.COLUMN.FASHION_SIZE, "M")
			)
			.put(new Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"));
		T.track(Params.TrackEvent.CART, cart);

		//Покупка
		Params purchase = new Params();
		purchase
				.put(new Params.Item("37").set(Params.Item.COLUMN.AMOUNT, 2))
				.put(new Params.Item("38").set(Params.Item.COLUMN.AMOUNT, 2))
				.put(Params.Parameter.ORDER_ID, "100234")
				.put(Params.Parameter.ORDER_PRICE, 100500)
				.put(new Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"));
		T.track(Params.TrackEvent.PURCHASE, purchase);

		//Просмотр категории
		T.track(Params.TrackEvent.CATEGORY, new Params().put(Params.Parameter.CATEGORY_ID, "100"));

		//Трекинг поиска
		T.track(Params.TrackEvent.SEARCH, new Params().put(Params.Parameter.SEARCH_QUERY, "coats"));
	}
}
