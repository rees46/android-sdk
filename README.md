![r46_android_sdk_cover](https://github.com/user-attachments/assets/83efd12d-9420-430a-ae6a-b7deae56d29c)
![Maven Central Version](https://img.shields.io/maven-central/v/com.rees46/rees46-sdk?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/kotlin-2.0.0-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=%237F52FF)
![Java](https://img.shields.io/badge/JAVA-20-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=%23ED8B00)
![Gradle](https://img.shields.io/badge/Gradle-8.8-grey.svg?style=for-the-badge&logo=Gradle&logoColor=white)
![Android-Gradle](https://img.shields.io/badge/Android_Gradle_Plugin-8.5.2-grey.svg?style=for-the-badge&logo=Gradle&logoColor=white)

## Description

REES46 SDK for Android platform - the wide toolset for eCommerce apps.
This SDK can be used to integrate in your own app for Android in few steps.

## Documentation

For detailed information on methods, please refer to the documentation available at the following
link:

[Official API references](https://reference.api.rees46.com/?kotlin#introduction)

## Stories share links (API v3)

> Behind the v3 gate: opt in with `@OptIn(InternalPersonalizationV3Api::class)`. This API may change in any release.

The "Share" button of a stories slide hands out a link to the shop's site:
`https://<shop domain>/<path>?r46_stories=<block code>&r46_story=<story id>[&r46_slide=<slide id>]`.
To open such a link in the app instead of the browser:

1. Verify App Links for the shop's domain: an intent filter with `android:autoVerify="true"` and
   `https://<shop domain>/.well-known/assetlinks.json` with the app's package and its signing certificate's SHA-256.

   ```xml
   <activity android:name=".MainActivity" android:exported="true">
       <intent-filter android:autoVerify="true">
           <action android:name="android.intent.action.VIEW" />
           <category android:name="android.intent.category.DEFAULT" />
           <category android:name="android.intent.category.BROWSABLE" />
           <data android:scheme="https" android:host="shop.example" />
       </intent-filter>
   </activity>
   ```

2. Pass the opened URL to `handleLink`. It opens the story at the slide of the link and returns `true`; any other link
   gives `false` and is yours to handle. It needs the API v3 host: `PersonalizationStories.apiV3Domain` or its own
   `apiV3Domain` argument.

   ```kotlin
   override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)
       intent?.data?.let { PersonalizationStories.handleLink(this, it.toString()) }
   }

   override fun onNewIntent(intent: Intent) {
       super.onNewIntent(intent)
       intent.data?.let { PersonalizationStories.handleLink(this, it.toString()) }
   }
   ```

## Author

REES46, desk@rees46.com
