
# Personalisatio Android SDK

## Download

Add to `dependencies`:

```
implementation 'com.rees46:rees46-sdk:+'
implementation 'com.google.firebase:firebase-bom:29.0.3'
implementation 'com.google.firebase:firebase-messaging:23.0.0'
```

## Configure

Append to your project `build.gradle`

```
buildscript {
	dependencies {
		...
		classpath 'com.google.gms:google-services:4.3.10'
	}
}
```

Append to your app module `build.gradle` after line `apply plugin: 'com.android.application'`

```
apply plugin: 'com.google.gms.google-services'
```

Create your app in the [Firebase console](https://console.firebase.google.com/u/0/) and copy file `google-services.json` to your app root path. Sync gradle now.

## Initialize

Add code to your application:

```
public class SampleApplication extends Application {
	public void onCreate() {
		super.onCreate();
		
		//Initialize
		REES46.initialize(getApplicationContext(), SHOP_ID);
		
		//Notification callback
		REES46.setOnMessageListener(new MessagingService.OnMessageListener() {
			@Override
			public void onMessage(Map<String, String> data) {
				
				//----->
				//Show your custom notification
				//----->
				
			}
		});
	}
}
```

For On-Premise integration need use initialize with custom api domain:

```
REES46.initialize(getApplicationContext(), SHOP_ID, API_DOMAIN);
```

Check `AndroidManifest.xml` and add `android:name=".SampleApplication"` to application item.

```
<application
		...
		android:name=".SampleApplication"
```

## Notification data structure

```json
{
	"title" : "Message title",
	"body" : "Message body",
	"icon" : "Message large url image",
	"url" : "Url for open and tracking events"
}
```
## How use

Example show notification with download icon:

```
new AsyncTask<String, Void, Bitmap>() {

	@Override
	protected Bitmap doInBackground(String... params) {
		try {
			InputStream in = new URL(params[0]).openStream();
			return BitmapFactory.decodeStream(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		super.onPostExecute(result);

		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

		//REQUIRED! For tracking click notification
        intent.putExtra(REES46.NOTIFICATION_TYPE, data.get("type"));
        intent.putExtra(REES46.NOTIFICATION_ID, data.get("id"));

		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id))
				.setSmallIcon(R.mipmap.ic_launcher)
				.setLargeIcon(result)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(data.get("body")))
				.setContentTitle(data.get("title"))
				.setContentText(data.get("body"))
				.setAutoCancel(true)
				.setContentIntent(pendingIntent);

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if( notificationManager != null ) {
			notificationManager.notify(0, notificationBuilder.build());
		} else {
			Log.e(REES46.TAG, "NotificationManager not allowed");
		}
	}
}.execute(data.get("icon"));
```

Use to Activity:

```
//User data
HashMap<String, String> params = new HashMap<>();
params.put("email", "email@example.com");
REES46.profile(params);
//With callback
REES46.profile(params, new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(TAG, "Response: " + response.toString());
    }
});

//For tracking notification opened
if( getIntent().getExtras() != null ) {
	REES46.notificationClicked(getIntent().getExtras());
}
```

## Recommendation

```
Params params = new Params();
params.put(Params.Parameter.EXTENDED, true);
params.put(Params.Parameter.ITEM, "37");
REES46.recommend("RECOMMENDER_CODE", params, new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(TAG, "Recommender response: " + response.toString());
    }
});
```

## Search

```
//Instant search
SearchParams params = new SearchParams();
params.put(SearchParams.Parameter.LOCATIONS, "location");
REES46.search("SEARCH_QUERY", SearchParams.TYPE.INSTANT, params, new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(TAG, "Search response: " + response.toString());
    }
});

//Full search
SearchParams params = new SearchParams();
params.put(SearchParams.Parameter.LOCATIONS, "location");
//Additional filters
SearchParams.SearchFilters filters = new SearchParams.SearchFilters();
filters.put("voltage", new String[] {"11.1", "14.8"});
params.put(SearchParams.Parameter.FILTERS, filters);
//Disable clarification search
params.put(SearchParams.Parameter.NO_CLARIFICATION, true);
REES46.search("SEARCH_QUERY", SearchParams.TYPE.FULL, params, new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(TAG, "Search response: " + response.toString());
    }
});

//Search blank request
REES46.search_blank(new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(T.TAG, "Search response: " + response.toString());
    }
});
```

## Tracking

```
//Product view
REES46.track(Params.TrackEvent.VIEW, "37");

//Add to cart (simple)
REES46.track(Params.TrackEvent.CART, "37");

//Add to cart (extended)
Params cart = new Params();
cart
	.put(new Params.Item("37")
		.set(Params.Item.COLUMN.FASHION_SIZE, "M")
		.set(Params.Item.COLUMN.AMOUNT, 2)
	)
	.put(new Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"));
REES46.track(Params.TrackEvent.CART, cart);

//Tracking full cart
Params full_cart = new Params();
full_cart
    .put(Params.Parameter.FULL_CART, true)
    .put(new Params.Item("37")
        .set(Params.Item.COLUMN.AMOUNT, 2)
        .set(Params.Item.COLUMN.FASHION_SIZE, "M")
    )
    .put(new Params.Item("40")
        .set(Params.Item.COLUMN.AMOUNT, 1)
        .set(Params.Item.COLUMN.FASHION_SIZE, "M")
    );
REES46.track(Params.TrackEvent.CART, full_cart);

//Purchase
Params purchase = new Params();
purchase
	.put(new Params.Item("37").set(Params.Item.COLUMN.AMOUNT, 2).set(Params.Item.COLUMN.PRICE, 100))
	.put(Params.Parameter.ORDER_ID, "100234")
	.put(Params.Parameter.ORDER_PRICE, 100500)
	.put(new Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"));
REES46.track(Params.TrackEvent.PURCHASE, purchase);

//Category view
REES46.track(Params.TrackEvent.CATEGORY, (new Params()).put(Params.Parameter.CATEGORY_ID, "100"));

//Wish
REES46.track(Params.TrackEvent.WISH, "37");
REES46.track(Params.TrackEvent.REMOVE_FROM_WISH, "37");

//Custom events simple
REES46.track("my_event");

//Tracking with custom parameters
REES46.track("my_event", "event category", "event label", 100);

//Price drop
REES46.subscribeForPriceDrop("37", 100);
REES46.subscribeForPriceDrop("37", 100, "mail@example.com");
REES46.subscribeForPriceDrop("37", 100, null, "+19999999999");

//Back in Stock
REES46.subscribeForBackInStock("37");
REES46.subscribeForBackInStock("37", "mail@example.com");
REES46.subscribeForBackInStock("37", null, "+19999999999");
JSONObject properties = new JSONObject();
properties.put("fashion_size", "XL");
REES46.subscribeForBackInStock("PRODUCT_ID", properties, "mail@example.com", null, null);
```

## Stories

Add code to your layout:

```xml
<com.personalizatio.stories.StoriesView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:code="STORY BLOCK CODE" />
```