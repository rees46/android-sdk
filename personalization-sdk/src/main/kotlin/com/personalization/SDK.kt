package com.personalization

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentManager
import com.google.firebase.messaging.RemoteMessage
import com.personalization.Params.InternalParameter
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CartManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.ProductsManager
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.params.ProfileParams
import com.personalization.di.AppModule
import com.personalization.di.DaggerSdkComponent
import com.personalization.features.notification.data.mapper.toNotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.handlers.notifications.NotificationHandler
import com.personalization.sdk.domain.usecases.network.AddTaskToQueueUseCase
import com.personalization.sdk.domain.usecases.network.InitNetworkUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.notification.GetAllNotificationsUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.InitPreferencesUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.InitUserSettingsUseCase
import com.personalization.sdk.domain.usecases.userSettings.InitializeAdvertisingIdUseCase
import com.personalization.stories.StoriesManager
import com.personalization.stories.views.StoriesView
import com.personalization.utils.DomainFormattingUtils.formatApiDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

open class SDK {

    internal lateinit var context: Context

    private var onMessageListener: OnMessageListener? = null
    private var search: Search = Search(JSONObject())

    @Inject
    internal lateinit var initializeAdvertisingIdUseCase: InitializeAdvertisingIdUseCase

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var registerManager: RegisterManager

    @Inject
    lateinit var storiesManager: StoriesManager

    @Inject
    lateinit var recommendationManager: RecommendationManager

    @Inject
    lateinit var productsManager: ProductsManager

    @Inject
    lateinit var cartManager: CartManager

    @Inject
    lateinit var trackEventManager: TrackEventManager

    @Inject
    lateinit var searchManager: SearchManager

    @Inject
    lateinit var inAppNotificationManager: InAppNotificationManager

    @Inject
    lateinit var initPreferencesUseCase: InitPreferencesUseCase

    @Inject
    lateinit var initUserSettingsUseCase: InitUserSettingsUseCase

    @Inject
    lateinit var initNetworkUseCase: InitNetworkUseCase

    @Inject
    lateinit var getPreferencesValueUseCase: GetPreferencesValueUseCase

    @Inject
    lateinit var getUserSettingsValueUseCase: GetUserSettingsValueUseCase

    @Inject
    lateinit var addTaskToQueueUseCase: AddTaskToQueueUseCase

    @Inject
    lateinit var sendNetworkMethodUseCase: SendNetworkMethodUseCase

    @Inject
    lateinit var setRecommendedByUseCase: SetRecommendedByUseCase

    @Inject
    lateinit var getAllNotificationsUseCase: GetAllNotificationsUseCase

    @Inject
    lateinit var notificationHelper: NotificationHelper

    /**
     * @param shopId Shop key
     */
    fun initialize(
        context: Context,
        shopId: String,
        apiDomain: String? = null,
        tag: String = TAG,
        preferencesKey: String = DEFAULT_STORAGE_KEY,
        stream: String = ANDROID,
        autoSendPushToken: Boolean = true,
        needReInitialization: Boolean = false,
        addTrailingSlash: Boolean = true
    ) {

        val sdkComponent = DaggerSdkComponent.factory().create(
            appModule = AppModule(applicationContext = context)
        )

        val baseUrl = apiDomain?.let {
            formatApiDomain(
                apiDomain = it,
                addTrailingSlash = addTrailingSlash
            )
        }

        sdkComponent.inject(sdk = this)
        this.context = context
        TAG = tag

        initPreferencesUseCase.invoke(
            context = context,
            preferencesKey = preferencesKey
        )

        notificationHandler.initialize(context = context)

        initUserSettingsUseCase.invoke(
            shopId = shopId,
            stream = stream
        )

        initNetworkUseCase(url = baseUrl)

        registerManager.initialize(
            contentResolver = context.contentResolver,
            autoSendPushToken = autoSendPushToken,
            needReInitialization = needReInitialization
        )

        CoroutineScope(Dispatchers.IO).launch {
            initializeAdvertisingIdUseCase.invoke()
        }
    }

    private fun initNetworkUseCase(url: String?) {
        if (url != null) {
            initNetworkUseCase.invoke(
                baseUrl = url
            )
        }
    }

    fun initializeStoriesView(storiesView: StoriesView) {
        storiesManager.initialize(storiesView, this)
    }

    fun initializeFragmentManager(fragmentManager: FragmentManager) {
        inAppNotificationManager.initFragmentManager(fragmentManager = fragmentManager)
    }

    /**
     * @param listener
     */
    fun stories(code: String, listener: OnApiCallbackListener) {
        storiesManager.requestStories(code, listener)
    }

    /**
     * Show stories block by code
     *
     * @param code Stories block code
     */
    fun showStories(code: String) {
        storiesManager.showStories(context.mainLooper, code)
    }


    /**
     * Triggers a story event
     *
     * @param event Event
     * @param code Stories block code
     * @param storyId Story ID
     * @param slideId Slide ID
     */
    fun trackStory(event: String, code: String, storyId: Int, slideId: String) {
        if (::storiesManager.isInitialized) {
            storiesManager.trackStory(
                event = event, code = code, storyId = storyId, slideId = slideId
            )
        } else {
            Log.i(TAG, "storiesManager is not initialized")
        }
    }

    /**
     * Update profile data
     *
     * @param data profile data
     */
    fun profile(data: ProfileParams, listener: OnApiCallbackListener? = null) {
        sendAsync(SET_PROFILE_FIELD, data.toJson(), listener)
    }

    /**
     * Return the session ID
     */
    fun getSid(): String = getUserSettingsValueUseCase.getSid()

    /**
     * Return the Advertising ID
     */
    fun getAdvertisingId(): String = getUserSettingsValueUseCase.getAdvertisingId()

    /**
     * Returns the session ID
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("getSid(): String")
    )
    fun getSid(listener: Consumer<String?>) {
        listener.accept(getSid())
    }

    /**
     * @param extras from data notification
     */
    fun notificationClicked(extras: Bundle?) {
        notificationHandler.notificationClicked(
            extras = extras,
            sendAsync = { method, params ->
                sendNetworkMethodUseCase.postAsync(
                    method = method,
                    params = params
                )
            }
        )
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("networkManager.postAsync(method, params, listener)")
    )
    fun sendAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendNetworkMethodUseCase.postAsync(method, params, listener)
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("networkManager.getAsync(method, params, listener)")
    )
    fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendNetworkMethodUseCase.getAsync(method, params, listener)
    }

    /**
     * @param listener Event on message receive
     */
    fun setOnMessageListener(listener: OnMessageListener) {
        onMessageListener = listener
    }

    /**
     * Send notification token
     *
     * @param token Token
     * @param listener Listener
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("registerManager.setPushTokenNotification(token, listener)")
    )
    fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        registerManager.setPushTokenNotification(
            token = token, listener = listener
        )
    }

    /**
     * Quick search
     *
     * @param query Search phrase
     * @param type Search type
     * @param listener Callback
     */
    @Deprecated(
        "This class will be removed in future versions. Use searchManager.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "searchManager.searchInstant(...) or searchManager.searchFull(...)"
        )
    )
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
    @Deprecated(
        "This class will be removed in future versions. Use searchManager.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "searchManager.searchInstant(...) or searchManager.searchFull(...)"
        )
    )
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

    @Deprecated(
        "This class will be removed in future versions. Use searchManager.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "searchManager.searchBlank(...)"
        )
    )
    fun searchBlank(listener: OnApiCallbackListener) {
        if (search != null) {
            if (search?.blank == null) {
                getAsync(BLANK_SEARCH_FIELD, Params().build(), object : OnApiCallbackListener() {
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
    @Deprecated(
        "This method will be removed in future versions. Use recommendationManager.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "recommendationManager.getRecommendation(recommender_code, ...)"
        )
    )
    fun recommend(recommender_code: String, listener: OnApiCallbackListener) {
        recommendationManager.getRecommendation(recommender_code, Params(), listener)
    }

    /**
     * Request a dynamic block of recommendations
     *
     * @param code Code of the dynamic block of recommendations
     * @param params Parameters for the request
     * @param listener Callback
     */
    @Deprecated(
        "This method will be removed in future versions. Use recommendationManager.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "recommendationManager.getRecommendation(code, ...)"
        )
    )
    fun recommend(code: String, params: Params, listener: OnApiCallbackListener) {
        recommendationManager.getRecommendation(code, params, listener)
    }

    /**
     * Event tracking
     *
     * @param event Event type
     * @param itemId Product ID
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "trackEventManager.track(event, itemId)"
        )
    )
    fun track(event: TrackEvent, itemId: String) {
        trackEventManager.track(event, itemId)
    }

    /**
     * Event tracking
     *
     * @param event Event type
     * @param params Parameters for the request
     * @param listener Callback
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "trackEventManager.track(event, params, listener)"
        )
    )
    fun track(event: TrackEvent, params: Params, listener: OnApiCallbackListener? = null) {
        trackEventManager.track(event, params, listener)
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
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("trackEventManager.customTrack(event, category, label, value = value, listener = listener)")
    )
    fun track(
        event: String,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        listener: OnApiCallbackListener? = null
    ) {
        trackEventManager.customTrack(event, category, label, value = value, listener = listener)
    }

    /**
     * Returns the device ID
     *
     * @return String
     */
    fun getDid(): String? {
        return getUserSettingsValueUseCase.getDid()
    }

    /**
     * Signs up for price reduction
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
     *
     * @param email
     * @param phone
     * @param subscriptions
     * @param listener
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
     *
     * @param email
     * @param phone
     * @param externalId
     * @param loyaltyId
     * @param telegramId
     * @param subscriptions
     * @param listener
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
    fun getSegment(): String = getUserSettingsValueUseCase.getSegmentForABTesting()

    /**
     * Add user to a segment
     *
     * @param segmentId
     * @param email
     * @param phone
     */
    fun addToSegment(
        segmentId: String, email: String?, phone: String?, listener: OnApiCallbackListener? = null
    ) {
        segmentMethod(ADD_FIELD, segmentId, email, phone, listener)
    }

    /**
     * Remove user from a segment
     *
     * @param segment_id
     * @param email
     * @param phone
     */
    fun removeFromSegment(
        segment_id: String, email: String?, phone: String?, listener: OnApiCallbackListener? = null
    ) {
        segmentMethod(REMOVE_FIELD, segment_id, email, phone, listener)
    }

    /**
     * Get user segments
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
                sendNetworkMethodUseCase.postAsync(TRACK_RECEIVED, params)
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun receiveMessage(remoteMessage: RemoteMessage) {
        notificationReceived(data = remoteMessage.data)

        onMessageListener?.onMessage(
            data = remoteMessage.toNotificationData()
        )
    }

    companion object {

        var TAG = "SDK"

        private const val SUBSCRIPTION_UNSUBSCRIBE_PRICE =
            "subscriptions/unsubscribe_from_product_price"
        private const val SUBSCRIPTION_UNSUBSCRIBE =
            "subscriptions/unsubscribe_from_product_available"
        private const val SUBSCRIPTION_SUBSCRIBE_PRICE = "subscriptions/subscribe_for_product_price"
        private const val SUBSCRIPTION_SUBSCRIBE = "subscriptions/subscribe_for_product_available"
        private const val SUBSCRIPTION_MANAGE = "subscriptions/manage"
        private const val DEFAULT_STORAGE_KEY = "DEFAULT_STORAGE_KEY"
        private const val PERSONALIZATION_SDK = "Personalizatio SDK "
        private const val ANDROID: String = "android"
        private const val BLANK_SEARCH_FIELD = "search/blank"
        private const val SEGMENT_GET_FIELD = "segments/get"
        private const val TRACK_RECEIVED = "track/received"
        private const val SET_PROFILE_FIELD = "profile/set"
        private const val SEGMENT_ID_FIELD = "segment_id"
        private const val SEGMENT_EMAIL_FIELD = "email"
        private const val SEGMENT_PHONE_FIELD = "email"
        private const val ITEM_IDS_FIELD = "item_ids"
        private const val SEGMENTS_FIELD = "segments"
        private const val SEARCH_FIELD = "search"
        private const val REMOVE_FIELD = "remove"
        private const val CODE_FIELD = "code"
        private const val TYPE_FIELD = "type"
        private const val ADD_FIELD = "add"
        private const val ID_FIELD = "id"

        val instance: SDK by lazy {
            SDK()
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
            instance.receiveMessage(remoteMessage)
        }
    }
}
