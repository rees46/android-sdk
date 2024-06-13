# Personalisatio Android SDK


## Download

Add to `dependencies`:

```
implementation 'com.rees46:rees46-sdk:+'
implementation 'com.google.firebase:firebase-bom:32.7.0''
implementation 'com.google.firebase:firebase-messaging:23.4.1'
```

## Configure

Append to your project `build.gradle`

```
buildscript {
	dependencies {
		...
		classpath 'com.google.gms:google-services:4.4.1'
	}
}
```

Append to your app module `build.gradle` after line `id 'com.android.application'`

```
plugins {
	id 'com.google.gms.google-services'
	id 'org.jetbrains.kotlin.android'
}
```

Create your app in the [Firebase console](https://console.firebase.google.com/u/0/) and copy file `google-services.json` to your app root path. Sync gradle now.

## Initialize

Add code to your application:

```kotlin
class SampleApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        //Initialize
        val sdk = REES46.getInstance()
        REES46.initialize(applicationContext, SHOP_ID)

        //Notification callback
        sdk.setOnMessageListener(object : OnMessageListener {
            override fun onMessage(data: Map<String, String>) {

                //----->
                //Show your custom notification
                //----->

            }
        })
    }
}
```

For On-Premise integration need use initialize with custom api domain:

```kotlin
REES46.initialize(applicationContext, SHOP_ID, API_DOMAIN)
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

```kotlin
object : AsyncTask<String?, Void?, Bitmap?>() {
    override fun doInBackground(vararg params: String?): Bitmap? {
        try {
            val inputStream = URL(params[0]).openStream()
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    override fun onPostExecute(result: Bitmap?) {
        super.onPostExecute(result)

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)


        //REQUIRED! For tracking click notification
        intent.putExtra(REES46.NOTIFICATION_TYPE, data["type"])
        intent.putExtra(REES46.NOTIFICATION_ID, data["id"])

        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, getString(R.string.notification_channel_id))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(result)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
            .setContentTitle(data["title"])
            .setContentText(data["body"])
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notificationBuilder.build())
    }
}.execute(data["icon"])
```


Use to Activity:

```kotlin
//User data
val params = HashMap<String, String>()
params["email"] = "email@example.com"
sdk.profile(params)

//With callback
sdk.profile(params, object : OnApiCallbackListener() {
    fun onSuccess(response: JSONObject) {
        Log.i(TAG, "Response: $response")
    }
})

//For tracking notification opened
if (intent.extras != null) {
    sdk.notificationClicked(intent.extras)
}
```

## Recommendation

```kotlin
val params = Params()
params.put(Params.Parameter.EXTENDED, true)
params.put(Params.Parameter.ITEM, "37")
sdk.recommend("RECOMMENDER_CODE", params, object : OnApiCallbackListener() {
    fun onSuccess(response: JSONObject) {
        Log.i(TAG, "Recommender response: $response")
    }
})
```

## Search

```kotlin
//Instant search
val params = SearchParams()
params.put(SearchParams.Parameter.LOCATIONS, "location")
sdk.search("SEARCH_QUERY", SearchParams.TYPE.INSTANT, params, object : OnApiCallbackListener() {
    fun onSuccess(response: JSONObject) {
        Log.i(TAG, "Search response: $response")
    }
})

//Full search
val params = SearchParams()
params.put(SearchParams.Parameter.LOCATIONS, "location")
//Additional filters
val filters = SearchFilters()
filters.put("voltage", arrayOf("11.1", "14.8"))
params.put(SearchParams.Parameter.FILTERS, filters)
//Disable clarification search
params.put(SearchParams.Parameter.NO_CLARIFICATION, true)
sdk.search("SEARCH_QUERY", SearchParams.TYPE.FULL, params, object : OnApiCallbackListener() {
    fun onSuccess(response: JSONObject) {
        Log.i(TAG, "Search response: $response")
    }
})

//Search blank request
sdk.searchBlank(object : OnApiCallbackListener() {
    fun onSuccess(response: JSONObject) {
        Log.i(TAG, "Search response: $response")
    }
})
```

## Tracking

```kotlin
//Product view
sdk.track(Params.TrackEvent.VIEW, "37")

//Add to cart (simple)
sdk.track(Params.TrackEvent.CART, "37")

//Add to cart (extended)
val cart = Params()
cart.put(Params.Item("37")
    .set(Params.Item.COLUMN.FASHION_SIZE, "M")
    .set(Params.Item.COLUMN.AMOUNT, 2)
    )
    .put(Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"))
sdk.track(Params.TrackEvent.CART, cart)

//Tracking full cart
val fullCart = Params()
fullCart
    .put(Params.Parameter.FULL_CART, true)
    .put(Params.Item("37")
        .set(Params.Item.COLUMN.AMOUNT, 2)
        .set(Params.Item.COLUMN.FASHION_SIZE, "M")
    )
    .put(Params.Item("40")
        .set(Params.Item.COLUMN.AMOUNT, 1)
        .set(Params.Item.COLUMN.FASHION_SIZE, "M")
    )
sdk.track(Params.TrackEvent.CART, fullCart)

//Purchase
val purchase = Params()
purchase
    .put(Params.Item("37")
        .set(Params.Item.COLUMN.AMOUNT, 2)
        .set(Params.Item.COLUMN.PRICE, 100)
    )
    .put(Params.Parameter.ORDER_ID, "100234")
    .put(Params.Parameter.ORDER_PRICE, 100500)
    .put(Params.RecommendedBy(Params.RecommendedBy.TYPE.RECOMMENDATION, "e9ddb9cdc66285fac40c7a897760582a"))
sdk.track(Params.TrackEvent.PURCHASE, purchase)

//Category view
sdk.track(Params.TrackEvent.CATEGORY, Params().put(Params.Parameter.CATEGORY_ID, "100"))

//Wish
sdk.track(Params.TrackEvent.WISH, "37")
sdk.track(Params.TrackEvent.REMOVE_FROM_WISH, "37")

//Custom events simple
sdk.track("my_event")

//Tracking with custom parameters
sdk.track("my_event", "event category", "event label", 100)

//Price drop
sdk.subscribeForPriceDrop("37", 100.0)
sdk.subscribeForPriceDrop("37", 100.0, "mail@example.com")
sdk.subscribeForPriceDrop("37", 100.0, null, "+19999999999")

//Back in Stock
sdk.subscribeForBackInStock("37")
sdk.subscribeForBackInStock("37", "mail@example.com")
sdk.subscribeForBackInStock("37", null, "+19999999999")
val properties = JSONObject()
properties.put("fashion_size", "XL")
sdk.subscribeForBackInStock("PRODUCT_ID", "mail@example.com", null, properties, null)
```

## Stories

Add code to your layout:

```xml
<com.personalizatio.stories.views.StoriesView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:code="STORY BLOCK CODE" />
```
```kotlin
val storiesView = findViewById<StoriesView>(R.id.stories_view)
sdk.initializeStoriesView(storiesView)
```

Or programmatically:

```kotlin
val storiesView = StoriesView(this, "STORY BLOCK CODE")
findViewById<ViewGroup>(R.id.stories).addView(storiesView)
sdk.initializeStoriesView(storiesView)
```

Set item click listener:

```kotlin
val storiesView = findViewById<StoriesView>(R.id.stories_view)
storiesView.itemClickListener = object : OnLinkClickListener {
    override fun onClick(url: String): Boolean {
        // return true if need to opening using the SDK
        return false
    }

    override fun onClick(product: Product): Boolean {
        // return true if need to opening using the SDK
        return false
    }
}
```

Show story by id:

```kotlin
sdk.showStory(STORY_ID)
```

Customize story settings:

```kotlin
val stories = findViewById<StoriesView>(R.id.story_view)
stories.settings.failed_load_text = "Failed"
stories.settings.failed_load_color = "#ff0000"
stories.settings.failed_load_size = 16
stories.settings.failed_load_font_family = Typeface.MONOSPACE
stories.settings.icon_size = 60
stories.settings.label_width = 120
stories.settings.icon_padding_x = 20
stories.settings.icon_padding_top = 20
stories.settings.icon_padding_bottom = 20
stories.settings.font_family = Typeface.MONOSPACE
stories.setBackgroundColor(Color.parseColor("#00ff00"))
stories.settings.label_font_color = "#ff0000"
stories.settings.visited_campaign_transparency = 0.1f
stories.settings.new_campaign_border_color = "#ff0000"
stories.settings.visited_campaign_border_color = "#00ff00"
stories.settings.background_pin = "#FD7C50"
stories.settings.pin_symbol = "📌"
stories.settings.close_color = "#FD7C50"
stories.settings.icon_display_format = Settings.ICON_DISPLAY_FORMAT.RECTANGLE
```
