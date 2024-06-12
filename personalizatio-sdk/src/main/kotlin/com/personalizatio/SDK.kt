package com.personalizatio

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
import com.personalizatio.notifications.Source
import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
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
        preferencesKey: String,
        stream: String
    ) {
        this.api = Api.getApi(apiUrl)

        this.context = context
        this.shopId = shopId
        this.stream = stream
        this.preferencesKey = preferencesKey

        //Инициализируем сегмент
        segment = prefs().getString(
            "$preferencesKey.segment",
            arrayOf("A", "B")[Math.round(Math.random()).toInt()]
        ).toString()
        source = Source.createSource(prefs())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            val channelId = context.getString(R.string.notification_channel_id)
            val channelName = context.getString(R.string.notification_channel_name)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            } else {
                error("NotificationManager not allowed")
            }
        }
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

            //Добавляем запрос токена в очередь
            queue.add(Thread { this.token })

            //Если еще ни разу не вызывали init
            if (preferences.getString(DID_FIELD, null) == null) {
                init()
            } else {
                initialized(null)
            }
        }
    }

    private val isTestDevice: Boolean
        get() = "true" == Settings.System.getString(context.contentResolver, "firebase.test.lab")

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
            params.put("tz", (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            send(ApiMethod.GET("init"), params, object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    try {
                        // Сохраняем данные в память
                        val edit = prefs().edit()
                        did = response!!.getString("did")
                        edit.putString(DID_FIELD, did)
                        edit.apply()

                        // Выполняем таски из очереди
                        initialized(response.getString("seance"))
                    } catch (e: JSONException) {
                        error(e.message, e)
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "code: $code, $msg")
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

        //Если сеанса нет, пробуем найти в хранилище
        //Нужно разделять сеансы по времени.
        //Для этого достаточно отслеживать время последнего действия на сеанс и, если оно больше N часов, то создавать новый сеанс.
        if (seance == null && prefs().getString(SID_FIELD, null) != null
            && prefs().getLong(SID_LAST_ACT_FIELD, 0)
            >= System.currentTimeMillis() - SESSION_CODE_EXPIRE * 3600 * 1000
        ) {
            seance = prefs().getString(SID_FIELD, null)
        }

        //Если сеанса нет, генерируем новый
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

        // Выполняем таски из очереди
        for (thread in queue) {
            thread.start()
        }
        queue.clear()
    }

    /**
     * Обновляем время последней активности
     */
    private fun updateSidActivity() {
        val edit = prefs().edit()
        edit.putString(SID_FIELD, seance)
        edit.putLong(SID_LAST_ACT_FIELD, System.currentTimeMillis())
        edit.apply()
    }

    private fun alphanumeric(length: Int): String {
        val source = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcefghijklmnopqrstuvwxyz"
        val sb = StringBuilder(length)
        val secureRandom = SecureRandom()
        for (i in 0 until length) {
            sb.append(source[secureRandom.nextInt(source.length)])
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
        sendAsync("profile/set", JSONObject(data.toMap()), listener)
    }

    /**
     * Возвращает идентификатор сессии
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
    public fun notificationClicked(extras: Bundle?) {
        val type = extras?.getString(NOTIFICATION_TYPE, null)
        val code = extras?.getString(NOTIFICATION_ID, null)
        if (type != null && code != null) {
            val params = JSONObject()
            try {
                params.put("type", type)
                params.put("code", code)
                sendAsync("track/clicked", params)

                //Сохраняем источник
                source.update(type, code, prefs())
            } catch (e: JSONException) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    /**
     * Прямое выполенение запроса
     */
    private fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        updateSidActivity()

        api.send(apiMethod, params, listener, shopId, did, seance, segment, stream, source)
    }

    private fun sendAsync(method: String, params: JSONObject) {
        sendAsync(method, params, null)
    }

    /**
     * Асинхронное выполенение запросе, если did не указан и не выполнена инициализация
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
     * Асинхронное выполенение запросе, если did не указан и не выполнена инициализация
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
     * Быстрый поиск
     *
     * @param query    Поисковая фраза
     * @param type     Тип поиска
     * @param listener Колбек
     */
    fun search(query: String, type: SearchParams.TYPE, listener: OnApiCallbackListener) {
        search(query, type, SearchParams(), listener)
    }

    /**
     * Быстрый поиск
     *
     * @param query    Поисковая фраза
     * @param type     Тип поиска
     * @param params   Дополнительные параметры к запросу
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
            getAsync("search", params.build(), listener)
        } else {
            warn("Search not initialized")
        }
    }

    fun searchBlank(listener: OnApiCallbackListener) {
        if (search != null) {
            if (search?.blank == null) {
                getAsync("search/blank",
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
     * Запрос динамического блока рекомендаций
     *
     * @param recommender_code Код блока рекомендаций
     * @param listener         Колбек
     */
    fun recommend(recommender_code: String, listener: OnApiCallbackListener) {
        recommend(recommender_code, Params(), listener)
    }

    /**
     * Запрос динамического блока рекомендаций
     *
     * @param code     Код динамического блока рекомендаций
     * @param params   Параметры для запроса
     * @param listener Колбек
     */
    fun recommend(code: String, params: Params, listener: OnApiCallbackListener) {
        getAsync("recommend/$code", params.build(), listener)
    }

    /**
     * Трекинг события
     *
     * @param event   Тип события
     * @param item_id ID товара
     */
    fun track(event: TrackEvent, item_id: String) {
        track(event, Params().put(Params.Item(item_id)), null)
    }

    /**
     * Трекинг события
     *
     * @param event    Тип события
     * @param params   Параметры для запроса
     * @param listener Колбек
     */
    /**
     * Трекинг события
     *
     * @param event  Тип события
     * @param params Параметры
     */
    fun track(event: TrackEvent, params: Params, listener: OnApiCallbackListener? = null) {
        params.put(InternalParameter.EVENT, event.value)
        if (lastRecommendedBy != null) {
            params.put(lastRecommendedBy!!)
            lastRecommendedBy = null
        }
        sendAsync("push", params.build(), listener)
    }

    /**
     * Трекинг кастомных событий
     *
     * @param event    Ключ события
     * @param category Event category
     * @param label    Event label
     * @param value    Event value
     * @param listener Колбек
     */
    /**
     * Трекинг кастомных событий
     *
     * @param event Ключ события
     */
    /**
     * Трекинг кастомных событий
     *
     * @param event    Ключ события
     * @param category Event category
     * @param label    Event label
     * @param value    Event value
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
        sendAsync("push/custom", params.build(), listener)
    }

    /**
     * Возвращает идентификатор устройства
     *
     * @return String
     */
    fun getDid(): String? {
        return did
    }

    /**
     * Подписывает на снижение цены
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param id            Идентификатор товара
     * @param currentPrice Текущая цена
     * @param email         Email, если есть
     * @param phone         Телефон, если есть
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
        sendAsync("subscriptions/subscribe_for_product_price", params.build(), listener)
    }

    /**
     * Отписывает на снижение цены
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param itemIds Идентификаторы товара
     * @param email    Email, если есть
     * @param phone    Телефон, если есть
     */
    fun unsubscribeForPriceDrop(
        itemIds: Array<String>,
        email: String? = null,
        phone: String? = null,
        listener: OnApiCallbackListener? = null
    ) {
        val params = JSONObject()
        try {
            params.put("item_ids", java.lang.String.join(", ", *itemIds))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            sendAsync("subscriptions/unsubscribe_from_product_price", params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Подписывает на наличие товара
     * https://reference.api.rees46.com/?shell#back-in-stock
     *
     * @param id    Идентификатор товара
     * @param email Email, если есть
     * @param phone Телефон, если есть
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
        sendAsync("subscriptions/subscribe_for_product_available", params.build(), listener)
    }

    /**
     * Отписывает на наличие товара
     * https://reference.api.rees46.com/?shell#back-in-stock
     *
     * @param itemIds Идентификатор товара
     * @param email    Email, если есть
     * @param phone    Телефон, если есть
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
            params.put("item_ids", java.lang.String.join(", ", *itemIds))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            sendAsync("subscriptions/unsubscribe_from_product_available", params, listener)
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
            sendAsync("subscriptions/manage", params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Возвращает текущий сегмент для А/В тестирования
     */
    fun getSegment(): String = instance.segment

    /**
     * Add user to a segment
     * https://reference.api.rees46.com/?java#add-user-to-a-segment
     *
     * @param segment_id
     * @param email
     * @param phone
     */
    fun addToSegment(
        segment_id: String,
        email: String?,
        phone: String?,
        listener: OnApiCallbackListener? = null
    ) {
        segmentMethod("add", segment_id, email, phone, listener)
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
        segmentMethod("remove", segment_id, email, phone, listener)
    }

    /**
     * Get user segments
     * https://reference.api.rees46.com/?swift#get-user-segments
     *
     * @param listener
     */
    fun getCurrentSegment(listener: OnApiCallbackListener) {
        getAsync("segments/get", JSONObject(), listener)
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
                params.put("segment_id", segmentId)
            }
            if (email != null) {
                params.put("email", email)
            }
            if (phone != null) {
                params.put("phone", phone)
            }
            sendAsync("segments/$method", params, listener)
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
    fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) {
        val params = HashMap<String, String>()
        params["platform"] = "android"
        params["token"] = token
        sendAsync("mobile_push_tokens", JSONObject(params.toMap()), listener)
    }


    /**
     * @param listener
     */
    fun stories(code: String, listener: OnApiCallbackListener) {
        instance?.getAsync("stories/$code", JSONObject(), listener)
    }

    /**
     * Вызывает событие сторисов
     *
     * @param event    Событие
     * @param code     Код блока сторисов
     * @param storyId Идентификатор сториса
     * @param slideId Идентификатор слайда
     */
    fun trackStory(event: String, code: String?, storyId: Int, slideId: String) {
        try {
            val params = JSONObject()
            params.put("event", event)
            params.put("story_id", storyId)
            params.put("slide_id", slideId)
            params.put("code", code)

            //Запоминаем последний клик в сторис, чтобы при вызове события просмотра товара добавить
            lastRecommendedBy = RecommendedBy(RecommendedBy.TYPE.STORIES, code)

            sendAsync("track/stories", params, null)
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
            val type = data["type"]
            if (type != null) {
                params.put("type", type)
            }
            val id = data["id"]
            if (id != null) {
                params.put("code", id)
            }
            if (params.length() > 0) {
                sendAsync("track/received", params)
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    companion object {
        const val TAG = "SDK"
        private const val NOTIFICATION_TYPE = "NOTIFICATION_TYPE"
        private const val NOTIFICATION_ID = "NOTIFICATION_ID"
        private const val SID_FIELD = "sid"
        private const val SID_LAST_ACT_FIELD = "sid_last_act"
        private const val DID_FIELD = "did"
        private const val TOKEN_FIELD = "token"
        private const val SESSION_CODE_EXPIRE = 2

        private const val TITLE_FIELD = "title"
        private const val BODY_FIELD = "body"
        private const val IMAGE_FIELD = "image"
        private const val IMAGES_FIELD = "images"

        val instance: SDK by lazy {
            SDK()
        }

        fun userAgent(): String {
            return "Personalizatio SDK " + BuildConfig.FLAVOR.uppercase(Locale.getDefault()) + ", v" + BuildConfig.VERSION_NAME
        }

        /**
         * @param message Сообщение
         */
        fun debug(message: String) {
            Log.d(TAG, message)
        }

        /**
         * @param message Сообщение
         */
        fun warn(message: String?) {
            Log.w(TAG, message.toString())
        }

        /**
         * @param message Сообщение об ошибке
         */
        fun error(message: String?) {
            Log.e(TAG, message.toString())
        }

        /**
         * @param message Сообщение об ошибке
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
                val data = prepareData(remoteMessage)
                listener.onMessage(data)
            }
        }

        private fun prepareData(remoteMessage: RemoteMessage): MutableMap<String, String> {
            val data: MutableMap<String, String> = HashMap(remoteMessage.data)
            remoteMessage.notification?.let { notification ->
                addNotificationData(notification, data)
            }
            data[IMAGES_FIELD]?.takeIf { it.isNotEmpty() }?.let { data[IMAGES_FIELD] = it }
            return data
        }

        private fun addNotificationData(
            notification: RemoteMessage.Notification,
            data: MutableMap<String, String>
        ) {
            notification.title?.takeIf { it.isNotEmpty() }?.let { data[TITLE_FIELD] = it }
            notification.body?.takeIf { it.isNotEmpty() }?.let { data[BODY_FIELD] = it }
            notification.imageUrl?.let { data[IMAGE_FIELD] = it.toString() }
        }
    }
}
