
# Personalisatio Android SDK

## Download

Add to `dependencies`:

```
implementation 'com.personalizatio:rees46-sdk:1.1.1'
```

## Configure

Append to your project `build.gradle`

```
buildscript {
	dependencies {
		...
		classpath 'com.google.gms:google-services:4.2.0'
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
		intent.putExtra(REES46.NOTIFICATION_URL, data.get("url"));

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
REES46.setEmail("EMAIL");

//For tracking notification opened
if( getIntent().getExtras() != null && getIntent().getExtras().getString(REES46.NOTIFICATION_URL, null) != null ) {
	REES46.notificationClicked(getIntent().getExtras().getString(REES46.NOTIFICATION_URL, null));
}
```

## Recommendation

```
REES46.recommend("RECOMMENDER_CODE", new Api.OnApiCallbackListener() {
    @Override
    public void onSuccess(JSONObject response) {
        Log.i(TAG, "Recommender response: " + response.toString());
    }
});
```

## Search

```
REES46.search("SEARCH_QUERY", Params.SEARCH_TYPE.INSTANT, new Api.OnApiCallbackListener() {
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

//Purchase
Params purchase = new Params();
purchase
	.put(new Params.Item("37"))
	.put(Params.Parameter.ORDER_ID, "100234")
	.put(Params.Parameter.ORDER_PRICE, 100500)
	.put(new Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"));
REES46.track(Params.TrackEvent.PURCHASE, purchase);

//Просмотр категории
REES46.track(Params.TrackEvent.CATEGORY, (new Params()).put(Params.Parameter.CATEGORY_ID, "100"));
```