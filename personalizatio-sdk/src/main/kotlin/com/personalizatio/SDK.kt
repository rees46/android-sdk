package com.personalizatio

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.util.Consumer
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.personalizatio.Params.InternalParameter
import com.personalizatio.Params.RecommendedBy
import com.personalizatio.Params.TrackEvent
import com.personalizatio.api.Api
import com.personalizatio.api.ApiMethod
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.notification.NotificationHandler
import com.personalizatio.notifications.Source
import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import org.json.JSONException
import org.json.JSONObject

open class SDK {

    private lateinit var preferencesKey: String
    private lateinit var context: Context
    private lateinit var segment: String
    private lateinit var source: Source
    private lateinit var shopId: String
    private lateinit var stream: String
    private lateinit var api: Api

    private val queue: MutableList<Thread> = Collections.synchronizedList(ArrayList())
    private lateinit var notificationHandler: NotificationHandler
    private var onMessageListener: OnMessageListener? = null
    private var lastRecommendedBy: RecommendedBy? = null
    private var seance: String? = null
    private var search: Search? = null
    private var did: String? = null
    private var initialized = false
    private var attempt = 0

    /**
     * @param shopId Shop key
     */
    fun initialize(
        context: Context,
        shopId: String,
        apiUrl: String,
        tag: String,
        preferencesKey: String,
        stream: String
    ) {
        this.api = Api.getApi(apiUrl)

        this.context = context
        this.shopId = shopId
        this.stream = stream
        this.preferencesKey = preferencesKey
        TAG = tag

        segment = prefs().getString(
            "$preferencesKey.segment",
            arrayOf("A", "B")[Math.random().roundToInt()]
        ).toString()
        source = Source.createSource(prefs())

        notificationHandler = NotificationHandler(context) { prefs() }
        notificationHandler.createNotificationChannel()

        did()
    }

    /**
     * @return preferences
     */
    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
    }

    /**
     * Get did from properties or generate a new did
     */
    @SuppressLint("HardwareIds")
    private fun did() {
        if (did == null) {
            val preferences = prefs()
            did = preferences.getString(DID_FIELD, null)
            if (did == null) {
                //get unique device id
                did = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }

            //Add the token request to the queue
            queue.add(Thread { this.token })

            //If you have never called init before
            if (preferences.getString(DID_FIELD, null) == null) {
                init()
            } else {
                initialized(null)
            }
        }
    }

    private val isTestDevice: Boolean
        get() = IS_TEST_DEVICE_FIELD == Settings.System.getString(
            context.contentResolver,
            FIREBASE_TEST_LAB
        )

    /**
     * Connect to init script
     */
    private fun init() {
        //Disable working Google Play Pre-Launch report devices

        if (isTestDevice) {
            Log.w(TAG, "Disable working Google Play Pre-Launch report devices")
            return
        }

        try {
            val params = JSONObject()

            params.put(TZ_FIELD, (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            send(ApiMethod.GET(INIT_FIELD), params, object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    try {
                        // Saving data to memory
                        val edit = prefs().edit()
                        did = response?.getString(DID_FIELD).orEmpty()
                        edit.putString(DID_FIELD, did)
                        edit.apply()

                        // Execute tasks from the queue
                        initialized(response?.getString(SEANCE_FIELD))
                    } catch (e: JSONException) {
                        error(e.message, e)
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "$CODE_FIELD: $code, $msg")
                        if (attempt < 5) {
                            attempt++
                        }
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({ init() }, 1000L * attempt)
                    }
                }
            })
        } catch (e: JSONException) {
            error(e.message, e)
        }
    }

    /**
     * Инициализация SDK
     *
     * @param sid String
     */
    private fun initialized(sid: String?) {
        initialized = true
        seance = sid

        //If there is no session, try to find it in the storage
        //We need to separate sessions by time.
        //To do this, it is enough to track the time of the last action for the session and, if it is more than N hours, then create a new session.

        if (seance == null && prefs().getString(SID_FIELD, null) != null
            && prefs().getLong(SID_LAST_ACT_FIELD, 0)
            >= System.currentTimeMillis() - SESSION_CODE_EXPIRE * 3600 * 1000
        ) {
            seance = prefs().getString(SID_FIELD, null)
        }

        //If there is no session, generate a new one
        if (seance == null) {
            debug("Generate new seance")
            seance = alphanumeric(10)
        }
        updateSidActivity()
        debug(
            "Device ID: " + did + ", seance: " + seance + ", last act: "
                    + Timestamp(prefs().getLong(SID_LAST_ACT_FIELD, 0))
        )

        //Seach
        search = Search(JSONObject())

        // Execute tasks from the queue
        for (thread in queue) {
            thread.start()
        }
        queue.clear()
    }

    /**
     * Update last activity time
     */
    private fun updateSidActivity() {
        val edit = prefs().edit()
        edit.putString(SID_FIELD, seance)
        edit.putLong(SID_LAST_ACT_FIELD, System.currentTimeMillis())
        edit.apply()
    }

    private fun alphanumeric(length: Int): String {
        val sb = StringBuilder(length)
        val secureRandom = SecureRandom()
        for (i in 0 until length) {
            sb.append(SOURCE[secureRandom.nextInt(SOURCE.length)])
        }
        return sb.toString()
    }

    private val token: Unit
        /**
         * Get device token
         */
        get() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
                if (!task.isSuccessful) {
                    error("Firebase: getInstanceId failed", task.exception)
                    return@addOnCompleteListener
                }
                if (task.result == null) {
                    error("Firebase: result is null")
                    return@addOnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result
                debug("Firebase token: $token")

                //Check send token
                val tokenField = prefs().getString(TOKEN_FIELD, null)
                if (tokenField == null || tokenField != token) {
                    //Send token

                    setPushTokenNotification(token, object : OnApiCallbackListener() {
                        override fun onSuccess(response: JSONObject?) {
                            val edit = prefs().edit()
                            edit.putString(TOKEN_FIELD, token)
                            edit.apply()
                        }
                    })
                }
            }
        }

    /**
     * Update profile data
     * https://reference.api.rees46.com/#save-profile-settings
     *
     * @param data profile data
     */
    fun profile(data: HashMap<String, String>, listener: OnApiCallbackListener? = null) {
        sendAsync(SET_PROFILE_FIELD, JSONObject(data.toMap()), listener)
    }

    /**
     * Returns the session ID
     */
    fun getSid(listener: Consumer<String?>) {
        val thread = Thread {
            listener.accept(seance)
        }
        if (initialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    /**
     * @param extras from data notification
     */
    fun notificationClicked(extras: Bundle?) {
        notificationHandler.notificationClicked(
            extras = extras,
            sendAsync = { method, params -> sendAsync(method, params) },
            source = source
        )
    }

    /**
     * Direct query execution
     */
    private fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        updateSidActivity()

        api.send(apiMethod, params, listener, shopId, did, seance, segment, stream, source)
    }

    private fun sendAsync(method: String, params: JSONObject) {
        sendAsync(method, params, null)
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun sendAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        val thread = Thread { send(ApiMethod.POST(method), params, listener) }
        if (did != null && initialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        val thread = Thread { send(ApiMethod.GET(method), params, listener) }
        if (did != null && initialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    /**
     * @param listener Event on message receive
     */
    fun setOnMessageListener(listener: OnMessageListener) {
        onMessageListener = listener
    }

    /**
     * Quick search
     *
     * @param query Search phrase
     * @param type Search type
     * @param listener Callback
     */
    fun search(query: String, type: SearchParams.TYPE, listener: OnApiCallbackListener) {
        search(query, type, SearchParams(), listener)
    }

    /**
     * Quick search
     *
     * @param query Search phrase
     * @param type Search type
     * @param params Additional parameters for the request
     * @param listener v
     */
    fun search(
        query: String,
        type: SearchParams.TYPE,
        params: SearchParams,
        listener: OnApiCallbackListener
    ) {
        if (search != null) {
            params.put(InternalParameter.SEARCH_TYPE, type.value)
                .put(InternalParameter.SEARCH_QUERY, query)
            getAsync(SEARCH_FIELD, params.build(), listener)
        } else {
            warn("Search not initialized")
        }
    }

    fun searchBlank(listener: OnApiCallbackListener) {
        if (search != null) {
            if (search?.blank == null) {
                getAsync(
                    BLANK_SEARCH_FIELD,
                    Params().build(), object : OnApiCallbackListener() {
                        override fun onSuccess(response: JSONObject?) {
                            search?.blank = response
                            listener.onSuccess(response)
                        }

                        override fun onError(code: Int, msg: String?) {
                            listener.onError(code, msg)
                        }
                    })
            } else {
                listener.onSuccess(search?.blank)
            }
        } else {
            warn("Search not initialized")
        }
    }

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommender_code Recommendation block code
     * @param listener Callback
     */
    fun recommend(recommender_code: String, listener: OnApiCallbackListener) {
        recommend(recommender_code, Params(), listener)
    }

    /**
     * Request a dynamic block of recommendations
     *
     * @param code Code of the dynamic block of recommendations
     * @param params Parameters for the request
     * @param listener Callback
     */
    fun recommend(code: String, params: Params, listener: OnApiCallbackListener) {
        getAsync("recommend/$code", params.build(), listener)
    }

    /**
     * Event tracking
     *
     * @param event Event type
     * @param itemId Product ID
     */
    fun track(event: TrackEvent, itemId: String) {
        track(event, Params().put(Params.Item(itemId)), null)
    }

    /**
     * Event tracking
     *
     * @param event Event type
     * @param params Parameters for the request
     * @param listener Callback
     */
    /**
     * Event tracking
     *
     * @param event Event type
     * @param params Parameters
     */
    fun track(event: TrackEvent, params: Params, listener: OnApiCallbackListener? = null) {
        params.put(InternalParameter.EVENT, event.value)
        if (lastRecommendedBy != null) {
            params.put(lastRecommendedBy!!)
            lastRecommendedBy = null
        }
        sendAsync(PUSH_FIELD, params.build(), listener)
    }

    /**
     * Tracking custom events
     *
     * @param event Event key
     * @param category Event category
     * @param label Event label
     * @param value Event value
     * @param listener Callback
     */
    /**
     * Tracking custom events
     *
     * @param event Event key
     */
    /**
     * Tracking custom events
     *
     * @param event Event key
     * @param category Event category
     * @param label Event label
     * @param value Event value
     */
    @JvmOverloads
    fun track(
        event: String,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = Params()
        params.put(InternalParameter.EVENT, event)
        if (category != null) {
            params.put(InternalParameter.CATEGORY, category)
        }
        if (label != null) {
            params.put(InternalParameter.LABEL, label)
        }
        if (value != null) {
            params.put(InternalParameter.VALUE, value)
        }
        sendAsync(CUSTOM_PUSH_FIELD, params.build(), listener)
    }

    /**
     * Returns the device ID
     *
     * @return String
     */
    fun getDid(): String? {
        return did
    }

    /**
     * Signs up for price reduction
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param id Product ID
     * @param currentPrice Current price
     * @param email Email, if available
     * @param phone Phone, if available
     */
    fun subscribeForPriceDrop(
        id: String,
        currentPrice: Double,
        email: String? = null,
        phone: String? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = Params()
        params.put(Params.Parameter.ITEM, id)
        params.put(Params.Parameter.PRICE, currentPrice.toString())
        if (email != null) {
            params.put(InternalParameter.EMAIL, email)
        }
        if (phone != null) {
            params.put(InternalParameter.PHONE, phone)
        }
        sendAsync(SUBSCRIPTION_SUBSCRIBE_PRICE, params.build(), listener)
    }

    /**
     * Subscribes for price reduction
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param itemIds Product identifiers
     * @param email Email, if available
     * @param phone Phone, if available
     */
    fun unsubscribeForPriceDrop(
        itemIds: Array<String>,
        email: String? = null,
        phone: String? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = JSONObject()
        try {
            params.put(ITEM_IDS_FIELD, java.lang.String.join(", ", *itemIds))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            sendAsync(SUBSCRIPTION_UNSUBSCRIBE_PRICE, params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Signs for product availability
     * https://reference.api.rees46.com/?shell#back-in-stock
     *
     * @param id Product ID
     * @param email Email, if available
     * @param phone Phone, if available
     */
    fun subscribeForBackInStock(
        id: String,
        email: String? = null,
        phone: String? = null,
        properties: JSONObject? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = Params()
        params.put(Params.Parameter.ITEM, id)
        if (properties != null) {
            params.put(InternalParameter.PROPERTIES, properties)
        }
        if (email != null) {
            params.put(InternalParameter.EMAIL, email)
        }
        if (phone != null) {
            params.put(InternalParameter.PHONE, phone)
        }
        sendAsync(SUBSCRIPTION_SUBSCRIBE, params.build(), listener)
    }

    /**
     * Subscribes to product availability
     * https://reference.api.rees46.com/?shell#back-in-stock
     *
     * @param itemIds Product ID
     * @param email Email, if available
     * @param phone Phone, if available
     */
    @JvmOverloads
    fun unsubscribeForBackInStock(
        itemIds: Array<String>,
        email: String? = null,
        phone: String? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = JSONObject()
        try {
            params.put(ITEM_IDS_FIELD, java.lang.String.join(", ", *itemIds))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            sendAsync(SUBSCRIPTION_UNSUBSCRIBE, params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Manage subscriptions
     * https://reference.api.rees46.com/?java#manage-subscriptions
     *
     * @param email
     * @param phone
     * @param subscriptions
     * @param listener
     */
    /**
     * Manage subscriptions
     * https://reference.api.rees46.com/?java#manage-subscriptions
     *
     * @param email
     * @param phone
     * @param subscriptions
     */
    fun manageSubscription(
        email: String?,
        phone: String?,
        subscriptions: HashMap<String, Boolean>,
        listener: OnApiCallbackListener? = null
    ) {
        manageSubscription(
            email = email,
            phone = phone,
            externalId = null,
            loyaltyId = null,
            telegramId = null,
            subscriptions = subscriptions,
            listener = listener
        )
    }

    /**
     * Manage subscriptions
     * https://reference.api.rees46.com/?java#manage-subscriptions
     *
     * @param email
     * @param phone
     * @param externalId
     * @param loyaltyId
     * @param telegramId
     * @param subscriptions
     * @param listener
     */
    /**
     * Manage subscriptions
     * https://reference.api.rees46.com/?java#manage-subscriptions
     *
     * @param email
     * @param phone
     * @param externalId
     * @param loyaltyId
     * @param telegramId
     * @param subscriptions
     */
    @JvmOverloads
    fun manageSubscription(
        email: String?,
        phone: String?,
        externalId: String?,
        loyaltyId: String?,
        telegramId: String?,
        subscriptions: HashMap<String, Boolean>,
        listener: OnApiCallbackListener? = null
    ) {
        try {
            val params = JSONObject()
            for ((key, value) in subscriptions) {
                params.put(key, value)
            }
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            if (externalId != null) {
                params.put(InternalParameter.EXTERNAL_ID.value, externalId)
            }
            if (loyaltyId != null) {
                params.put(InternalParameter.LOYALTY_ID.value, loyaltyId)
            }
            if (telegramId != null) {
                params.put(InternalParameter.TELEGRAM_ID.value, telegramId)
            }
            sendAsync(SUBSCRIPTION_MANAGE, params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Returns the current segment for A/B testing
     */
    fun getSegment(): String = instance.segment

    /**
     * Add user to a segment
     * https://reference.api.rees46.com/?java#add-user-to-a-segment
     *
     * @param segmentId
     * @param email
     * @param phone
     */
    fun addToSegment(
        segmentId: String,
        email: String?,
        phone: String?,
        listener: OnApiCallbackListener? = null
    ) {
        segmentMethod(ADD_FIELD, segmentId, email, phone, listener)
    }

    /**
     * Remove user from a segment
     * https://reference.api.rees46.com/?swift#remove-user-from-a-segment
     *
     * @param segment_id
     * @param email
     * @param phone
     */
    fun removeFromSegment(
        segment_id: String,
        email: String?,
        phone: String?,
        listener: OnApiCallbackListener? = null
    ) {
        segmentMethod(REMOVE_FIELD, segment_id, email, phone, listener)
    }

    /**
     * Get user segments
     * https://reference.api.rees46.com/?swift#get-user-segments
     *
     * @param listener
     */
    fun getCurrentSegment(listener: OnApiCallbackListener) {
        getAsync(SEGMENT_GET_FIELD, JSONObject(), listener)
    }

    private fun segmentMethod(
        method: String,
        segmentId: String?,
        email: String?,
        phone: String?,
        listener: OnApiCallbackListener?
    ) {
        try {
            val params = JSONObject()
            if (segmentId != null) {
                params.put(SEGMENT_ID_FIELD, segmentId)
            }
            if (email != null) {
                params.put(SEGMENT_EMAIL_FIELD, email)
            }
            if (phone != null) {
                params.put(SEGMENT_PHONE_FIELD, phone)
            }
            sendAsync("$SEGMENTS_FIELD/$method", params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Send notification token
     * https://reference.api.rees46.com/?java#create-new-token
     *
     * @param token
     * @param listener
     */
    private fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        val params = HashMap<String, String>()
        params[PLATFORM_FIELD] = PLATFORM_ANDROID_FIELD
        params[TOKEN_FIELD] = token
        sendAsync(MOBILE_PUSH_TOKENS, JSONObject(params.toMap()), listener)
    }


    /**
     * @param listener
     */
    fun stories(code: String, listener: OnApiCallbackListener) {
        instance.getAsync("$STORIES_FIELD/$code", JSONObject(), listener)
    }

    /**
     * Triggers a story event
     *
     * @param event Event
     * @param code Stories block code
     * @param storyId Story ID
     * @param slideId Slide ID
     */
    fun trackStory(event: String, code: String?, storyId: Int, slideId: String) {
        try {
            val params = JSONObject()
            params.put(TRACK_EVENT_FIELD, event)
            params.put(TRACK_STORY_ID_FIELD, storyId)
            params.put(TRACK_SLIDE_ID_FIELD, slideId)
            params.put(TRACK_CODE_FIELD, code)

            //Remember the last click in stories so that when the product viewing event is called, add
            lastRecommendedBy = RecommendedBy(RecommendedBy.TYPE.STORIES, code)

            sendAsync(TRACK_STORIES_FIELD, params, null)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * @param data from data notification
     */
    fun notificationReceived(data: Map<String, String>) {
        val params = JSONObject()
        try {
            val type = data[TYPE_FIELD]
            if (type != null) {
                params.put(TYPE_FIELD, type)
            }
            val id = data[ID_FIELD]
            if (id != null) {
                params.put(CODE_FIELD, id)
            }
            if (params.length() > 0) {
                sendAsync(TRACK_RECEIVED, params)
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    companion object {

        var TAG = "SDK"

        private const val SUBSCRIPTION_UNSUBSCRIBE_PRICE = "subscriptions/unsubscribe_from_product_price"
        private const val SUBSCRIPTION_UNSUBSCRIBE = "subscriptions/unsubscribe_from_product_available"
        private const val SUBSCRIPTION_SUBSCRIBE_PRICE = "subscriptions/subscribe_for_product_price"
        private const val SUBSCRIPTION_SUBSCRIBE = "subscriptions/subscribe_for_product_available"
        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcefghijklmnopqrstuvwxyz"
        private const val SUBSCRIPTION_MANAGE = "subscriptions/manage"
        private const val PERSONALIZATION_SDK = "Personalizatio SDK "
        private const val MOBILE_PUSH_TOKENS = "mobile_push_tokens"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val TRACK_STORIES_FIELD = "track/stories"
        private const val SID_LAST_ACT_FIELD = "sid_last_act"
        private const val BLANK_SEARCH_FIELD = "search/blank"
        private const val PLATFORM_ANDROID_FIELD = "android"
        private const val SEGMENT_GET_FIELD = "segments/get"
        private const val TRACK_STORY_ID_FIELD = "story_id"
        private const val TRACK_SLIDE_ID_FIELD = "slide_id"
        private const val TRACK_RECEIVED = "track/received"
        private const val CUSTOM_PUSH_FIELD = "push/custom"
        private const val SET_PROFILE_FIELD = "profile/set"
        private const val SEGMENT_ID_FIELD = "segment_id"
        private const val SEGMENT_EMAIL_FIELD = "email"
        private const val SEGMENT_PHONE_FIELD = "email"
        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val ITEM_IDS_FIELD = "item_ids"
        private const val SEGMENTS_FIELD = "segments"
        private const val PLATFORM_FIELD = "platform"
        private const val TRACK_EVENT_FIELD = "event"
        private const val TRACK_CODE_FIELD = "code"
        private const val STORIES_FIELD = "stories"
        private const val SESSION_CODE_EXPIRE = 2
        private const val SEANCE_FIELD = "seance"
        private const val SEARCH_FIELD = "search"
        private const val REMOVE_FIELD = "remove"
        private const val TOKEN_FIELD = "token"
        private const val CODE_FIELD = "code"
        private const val INIT_FIELD = "init"
        private const val TYPE_FIELD = "type"
        private const val PUSH_FIELD = "push"
        private const val ADD_FIELD = "add"
        private const val DID_FIELD = "did"
        private const val SID_FIELD = "sid"
        private const val ID_FIELD = "id"
        private const val TZ_FIELD = "tz"

        val instance: SDK by lazy {
            SDK()
        }

        private val notificationHandler: NotificationHandler by lazy {
            NotificationHandler(instance.context) { instance.prefs() }
        }

        fun userAgent(): String {
            return PERSONALIZATION_SDK + BuildConfig.FLAVOR.uppercase(Locale.getDefault()) + ", v" + BuildConfig.VERSION_NAME
        }

        /**
         * @param message Message
         */
        fun debug(message: String) {
            Log.d(TAG, message)
        }

        /**
         * @param message Message
         */
        fun warn(message: String?) {
            Log.w(TAG, message.toString())
        }

        /**
         * @param message Error message
         */
        fun error(message: String?) {
            Log.e(TAG, message.toString())
        }

        /**
         * @param message Error message
         */
        fun error(message: String?, e: Throwable?) {
            Log.e(TAG, message, e)
        }

        /**
         * @param remoteMessage
         */
        fun onMessage(remoteMessage: RemoteMessage) {
            instance.notificationReceived(remoteMessage.data)

            instance.onMessageListener?.let { listener ->
                val data = notificationHandler.prepareData(remoteMessage)
                listener.onMessage(data)
            }
        }
    }
}
