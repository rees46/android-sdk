
# Personalisatio Android SDK

## Download

Add to `dependencies`:

```
implementation 'com.personalizatio:personalizatio-sdk:1.0.0'
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