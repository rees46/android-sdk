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
import com.personalizatio.Api.OnApiCallbackListener
import com.personalizatio.Params.InternalParameter
import com.personalizatio.Params.RecommendedBy
import com.personalizatio.Params.TrackEvent
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Collections
import java.util.Locale
import java.util.TimeZone

open class SDK {
    private lateinit var context: Context
    private var did: String? = null
    private var seance: String? = null
    private var onMessageListener: OnMessageListener? = null

    var NOTIFICATION_TYPE: String = "NOTIFICATION_TYPE"
    var NOTIFICATION_ID: String = "NOTIFICATION_ID"
    val tag: String
        get() = TAG

    @Volatile
    private var initialized = false

    @Volatile
    private var attempt = 0
    private val queue: MutableList<Thread> = Collections.synchronizedList(ArrayList())
    private var search: Search? = null
    private lateinit var segment: String
    private var sourceType: String? = null
    private var sourceId: String? = null
    private var sourceTime: Long = 0
    private lateinit var shopId: String
    private lateinit var stream: String
    private lateinit var preferencesKey: String

    /**
     * @param shopId Shop key
     */
    public fun initialize(context: Context, shopId: String, apiUrl: String, tag: String, preferencesKey: String, stream: String) {
        Api.initialize(apiUrl)

        this.context = context
        this.shopId = shopId
        this.stream = stream
        this.preferencesKey = preferencesKey
        TAG = tag
        //Инициализируем сегмент
        segment = prefs().getString("$preferencesKey.segment", arrayOf("A", "B")[Math.round(Math.random()).toInt()]).toString()
        sourceType = prefs().getString("source_type", null)
        sourceId = prefs().getString("source_id", null)
        sourceTime = prefs().getLong("source_time", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            val channelId = context.getString(R.string.notification_channel_id)
            val channelName = context.getString(R.string.notification_channel_name)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW))
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
            send("get", "init", params, object : OnApiCallbackListener() {
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
        debug("Device ID: " + did + ", seance: " + seance + ", last act: "
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
                    error("getInstanceId failed", task.exception)
                    return@addOnCompleteListener
                }
                if (task.result == null) {
                    error("Firebase result is null")
                    return@addOnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result
                debug("token: $token")

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
        instance?.sendAsync("profile/set", JSONObject(data.toMap()), listener)
    }

    /**
     * Возвращает идентификатор сессии
     */
    fun getSid(listener: Consumer<String?>) {
        instance?.apply {
            val thread = Thread {
                listener.accept(seance)
            }
            if (initialized) {
                thread.start()
            } else {
                queue.add(thread)
            }
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
                instance?.sendAsync("track/clicked", params)

                //Сохраняем источник
                setSource(type, code)
            } catch (e: JSONException) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    /**
     * Прямое выполенение запроса
     */
    private fun send(requestType: String, method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        updateSidActivity()
        try {
            params.put("shop_id", shopId)
            if (did != null) {
                params.put("did", did)
            }
            if (seance != null) {
                params.put("seance", seance)
                params.put("sid", seance)
            }
            params.put("segment", segment)
            params.put("stream", stream)

            //Добавляем источник к запросу, проверяем время действия 2 дня
            if (sourceType != null && sourceTime > 0 && sourceTime + 172800 * 1000 > System.currentTimeMillis()) {
                val source = JSONObject()
                source.put("from", sourceType)
                source.put("code", sourceId)
                params.put("source", source)
            }

            Api.send(requestType, method, params, listener)
        } catch (e: JSONException) {
            error(e.message, e)
        }
    }

    private fun sendAsync(method: String, params: JSONObject) {
        sendAsync(method, params, null)
    }

    /**
     * Асинхронное выполенение запросе, если did не указан и не выполнена инициализация
     */
    fun sendAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        val thread = Thread { send("post", method, params, listener) }
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
        val thread = Thread { send("get", method, params, listener) }
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
        instance?.onMessageListener = listener
    }

    fun initialize(context: Context?, shop_id: String?) {
        throw IllegalStateException("You need make static initialize method!")
    }

    fun initialize(context: Context?, shop_id: String?, stream: String?) {
        throw IllegalStateException("You need make static initialize method!")
    }

    /**
     * Сохраняет данные источника
     *
     * @param type тип источника: bulk, chain, transactional
     * @param id   идентификатор сообщения
     */
    protected fun setSource(type: String, id: String) {
        instance?.apply {
            sourceType = type
            sourceId = id
            sourceTime = System.currentTimeMillis()
            prefs().edit()
                .putString("source_type", type)
                .putString("source_id", id)
                .putLong("source_time", sourceTime)
                .apply()
        }
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
    fun search(query: String, type: SearchParams.TYPE, params: SearchParams, listener: OnApiCallbackListener) {
        if (instance?.search != null) {
            params.put(InternalParameter.SEARCH_TYPE, type.value)
                .put(InternalParameter.SEARCH_QUERY, query)
            instance?.getAsync("search", params.build(), listener)
        } else {
            warn("Search not initialized")
        }
    }

    /**
     * Пустой поиск
     *
     * @param listener v
     */
    @Deprecated(
        """This method is no longer acceptable to compute time between versions.
	  <p> Use {@link SDK#searchBlank(Api.OnApiCallbackListener)} instead."""
    )
    fun search_blank(listener: OnApiCallbackListener) {
        searchBlank(listener)
    }

    fun searchBlank(listener: OnApiCallbackListener) {
        instance?.apply {
            if (search != null) {
                if (search!!.blank == null) {
                    instance!!.getAsync("search/blank",
                        Params().build(), object : OnApiCallbackListener() {
                            override fun onSuccess(response: JSONObject?) {
                                search!!.blank = response
                                listener.onSuccess(response)
                            }

                            override fun onError(code: Int, msg: String?) {
                                listener.onError(code, msg)
                            }
                        })
                } else {
                    listener.onSuccess(search!!.blank)
                }
            } else {
                warn("Search not initialized")
            }
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
        instance!!.getAsync("recommend/$code", params.build(), listener)
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
        instance?.sendAsync("push", params.build(), listener)
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
    fun track(event: String, category: String? = null, label: String? = null, value: Int? = null, listener: OnApiCallbackListener? = null) {
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
        instance?.sendAsync("push/custom", params.build(), listener)
    }

    /**
     * Возвращает идентификатор устройства
     *
     * @return String
     */
    fun getDid(): String? {
        return instance?.did
    }

    /**
     * Подписывает на снижение цены
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param id            Идентификатор товара
     * @param current_price Текущая цена
     * @param email         Email, если есть
     * @param phone         Телефон, если есть
     */
    fun subscribeForPriceDrop(id: String, current_price: Double, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
        val params = Params()
        params.put(Params.Parameter.ITEM, id)
        params.put(Params.Parameter.PRICE, current_price.toString())
        if (email != null) {
            params.put(InternalParameter.EMAIL, email)
        }
        if (phone != null) {
            params.put(InternalParameter.PHONE, phone)
        }
        instance?.sendAsync("subscriptions/subscribe_for_product_price", params.build(), listener)
    }

    /**
     * Отписывает на снижение цены
     * https://reference.api.rees46.com/?shell#price-drop
     *
     * @param item_ids Идентификаторы товара
     * @param email    Email, если есть
     * @param phone    Телефон, если есть
     */
    fun unsubscribeForPriceDrop(item_ids: Array<String>, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
        val params = JSONObject()
        try {
            params.put("item_ids", java.lang.String.join(", ", *item_ids))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            instance?.sendAsync("subscriptions/unsubscribe_from_product_price", params, listener)
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
    fun subscribeForBackInStock(id: String, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
        subscribeForBackInStock(id, null, email, phone, listener)
    }

    fun subscribeForBackInStock(id: String, properties: JSONObject?, email: String?, phone: String?, listener: OnApiCallbackListener?) {
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
        instance?.sendAsync("subscriptions/subscribe_for_product_available", params.build(), listener)
    }

    /**
     * Отписывает на наличие товара
     * https://reference.api.rees46.com/?shell#back-in-stock
     *
     * @param item_ids Идентификатор товара
     * @param email    Email, если есть
     * @param phone    Телефон, если есть
     */
    @JvmOverloads
    fun unsubscribeForBackInStock(item_ids: Array<String>, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
        val params = JSONObject()
        try {
            params.put("item_ids", java.lang.String.join(", ", *item_ids))
            if (email != null) {
                params.put(InternalParameter.EMAIL.value, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE.value, phone)
            }
            instance?.sendAsync(
                "subscriptions/unsubscribe_from_product_available",
                params,
                listener
            )
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
    fun manageSubscription(email: String?, phone: String?, subscriptions: HashMap<String, Boolean>, listener: OnApiCallbackListener? = null) {
        manageSubscription(email, phone, null, null, null, subscriptions, listener)
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
    fun manageSubscription(email: String?, phone: String?, externalId: String?, loyaltyId: String?, telegramId: String?,
                           subscriptions: HashMap<String, Boolean>, listener: OnApiCallbackListener? = null) {
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
            instance?.sendAsync("subscriptions/manage", params, listener)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * Возвращает текущий сегмент для А/В тестирования
     */
    fun getSegment(): String {
        if (instance == null) {
            throw RuntimeException("You need initialize SDK before request segment")
        } else {
            return instance!!.segment
        }
    }

    /**
     * Add user to a segment
     * https://reference.api.rees46.com/?java#add-user-to-a-segment
     *
     * @param segment_id
     * @param email
     * @param phone
     */
    fun addToSegment(segment_id: String, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
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
    fun removeFromSegment(segment_id: String, email: String?, phone: String?, listener: OnApiCallbackListener? = null) {
        segmentMethod("remove", segment_id, email, phone, listener)
    }

    /**
     * Get user segments
     * https://reference.api.rees46.com/?swift#get-user-segments
     *
     * @param listener
     */
    fun getCurrentSegment(listener: OnApiCallbackListener) {
        instance?.getAsync("segments/get", JSONObject(), listener)
    }

    private fun segmentMethod(method: String, segmentId: String?, email: String?, phone: String?, listener: OnApiCallbackListener?) {
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
            instance?.sendAsync("segments/$method", params, listener)
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
        instance?.sendAsync("mobile_push_tokens", JSONObject(params.toMap()), listener)
    }

    companion object {
        lateinit var TAG: String
        private const val SID_FIELD = "sid"
        private const val SID_LAST_ACT_FIELD = "sid_last_act"
        private const val DID_FIELD = "did"
        private const val TOKEN_FIELD = "token"
        private const val SESSION_CODE_EXPIRE = 2
        private var lastRecommendedBy: RecommendedBy? = null

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: SDK? = null

        fun getInstance(): SDK {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = SDK()
                    }
                }
            }
            return instance!!
        }

        fun isInstanced() : Boolean {
            return instance != null
        }

        fun userAgent(): String {
            return "Personalizatio SDK " + BuildConfig.FLAVOR.uppercase(Locale.getDefault()) + ", v";// + BuildConfig.VERSION_NAME
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
                    instance?.sendAsync("track/received", params)
                }
            } catch (e: JSONException) {
                Log.e(TAG, e.message, e)
            }
        }


        //----------Private--------------->
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

        //-------------Методы------------>
        /**
         * @param remoteMessage
         */
        fun onMessage(remoteMessage: RemoteMessage) {
            notificationReceived(remoteMessage.data)
            instance?.onMessageListener?.onMessage(remoteMessage.data)
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
         * @param story_id Идентификатор сториса
         * @param slide_id Идентификатор слайда
         */
        fun track_story(event: String, code: String?, story_id: Int, slide_id: String) {
            try {
                val params = JSONObject()
                params.put("event", event)
                params.put("story_id", story_id)
                params.put("slide_id", slide_id)
                params.put("code", code)

                //Запоминаем последний клик в сторис, чтобы при вызове события просмотра товара добавить
                lastRecommendedBy = RecommendedBy(RecommendedBy.TYPE.STORIES, code)

                instance?.sendAsync("track/stories", params, null)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

    }
}
