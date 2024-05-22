package com.personalizatio

import android.annotation.SuppressLint

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
class SDK protected constructor(
    context: Context?,
    private val shop_id: String?,
    api_url: String?,
    tag: String?,
    prefs_key: String?,
    private val stream: String?
) {
    private val PREFERENCES_KEY: String?
    private val context: Context? = context
    private var did: String? = null
    private var seance: String? = null
    private var onMessageListener: OnMessageListener? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var attempt = 0
    private val queue: List<Thread?>? = Collections.synchronizedList(ArrayList())
    private var search: Search? = null
    private val segment: String?
    private var source_type: String?
    private var source_id: String?
    private var source_time: Long = 0

    /**
     * @param shop_id Shop key
     */
    init {
        TAG = tag
        PREFERENCES_KEY = prefs_key
        Api.initialize(api_url)

        //Инициализируем сегмент
        segment = prefs().getString(
            PREFERENCES_KEY.toString() + ".segment",
            arrayOf<String?>("A", "B")[Math.round(Math.random()) as Int]
        )
        source_type = prefs().getString("source_type", null)
        source_id = prefs().getString("source_id", null)
        source_time = prefs().getLong("source_time", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            val channelId: String = context.getString(R.string.notification_channel_id)
            val channelName: String = context.getString(R.string.notification_channel_name)
            val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
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
    private fun prefs(): SharedPreferences? {
        return context.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    /**
     * Get did from properties or generate a new did
     */
    @SuppressLint("HardwareIds")
    private fun did() {
        if (did == null) {
            val preferences: SharedPreferences? = prefs()
            did = preferences.getString(DID_FIELD, null)
            if (did == null) {
                //get unique device id
                did = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID)
            }

            //Добавляем запрос токена в очередь
            queue.add(Thread { this.getToken() })

            //Если еще ни разу не вызывали init
            if (preferences.getString(DID_FIELD, null) == null) {
                init()
            } else {
                initialized(null)
            }
        }
    }

    private fun isTestDevice(): Boolean {
        return "true".equals(Settings.System.getString(context.getContentResolver(), "firebase.test.lab"))
    }

    /**
     * Connect to init script
     */
    private fun init() {
        //Disable working Google Play Pre-Launch report devices

        if (isTestDevice()) {
            Log.w(TAG, "Disable working Google Play Pre-Launch report devices")
            return
        }

        try {
            val params: JSONObject = JSONObject()
            params.put("tz", String.valueOf((TimeZone.getDefault().getRawOffset() / 3600000.0) as Int))
            send("get", "init", params, object : OnApiCallbackListener() {
                @Override
                fun onSuccess(response: JSONObject?) {
                    try {
                        // Сохраняем данные в память
                        val edit: SharedPreferences.Editor = prefs().edit()
                        did = response.getString("did")
                        edit.putString(DID_FIELD, did)
                        edit.apply()

                        // Выполняем таски из очереди
                        initialized(response.getString("seance"))
                    } catch (e: JSONException) {
                        error(e.getMessage(), e)
                    }
                }

                @Override
                fun onError(code: Int, msg: String?) {
                    if (code >= 500 || code <= 0) {
                        Log.e(TAG, "code: $code, $msg")
                        if (attempt < 5) {
                            attempt++
                        }
                        val handler: Handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({ init() }, 1000L * attempt)
                    }
                }
            })
        } catch (e: JSONException) {
            error(e.getMessage(), e)
        }
    }

    /**
     * Инициализация SDK
     *
     * @param sid String
     */
    private fun initialized(@Nullable sid: String?) {
        initialized = true
        seance = sid

        //Если сеанса нет, пробуем найти в хранилище
        //Нужно разделять сеансы по времени.
        //Для этого достаточно отслеживать время последнего действия на сеанс и, если оно больше N часов, то создавать новый сеанс.
        if (seance == null && prefs().getString(SID_FIELD, null) != null && prefs().getLong(
                SID_LAST_ACT_FIELD,
                0
            ) >= System.currentTimeMillis() - SESSION_CODE_EXPIRE * 3600 * 1000
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
            "Device ID: " + did + ", seance: " + seance + ", last act: " + Timestamp(
                prefs().getLong(
                    SID_LAST_ACT_FIELD, 0
                )
            )
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
        val edit: SharedPreferences.Editor = prefs().edit()
        edit.putString(SID_FIELD, seance)
        edit.putLong(SID_LAST_ACT_FIELD, System.currentTimeMillis())
        edit.apply()
    }

    private fun alphanumeric(length: Int): String? {
        val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcefghijklmnopqrstuvwxyz"
        val sb: StringBuilder = StringBuilder(length)
        val secureRandom: SecureRandom = SecureRandom()
        for (i in 0 until length) {
            sb.append(SOURCE.charAt(secureRandom.nextInt(SOURCE.length())))
        }
        return sb.toString()
    }

    /**
     * Get device token
     */
    private fun getToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener { task ->
            if (!task.isSuccessful()) {
                error("getInstanceId failed", task.getException())
                return@addOnCompleteListener
            }
            if (task.getResult() == null) {
                error("Firebase result is null")
                return@addOnCompleteListener
            }

            // Get new Instance ID token
            val token: String = task.getResult()
            debug("token: $token")

            //Check send token
            if (prefs().getString(TOKEN_FIELD, null) == null || !Objects.equals(
                    prefs().getString(TOKEN_FIELD, null),
                    token
                )
            ) {
                //Send token

                setPushTokenNotification(token, object : OnApiCallbackListener() {
                    @Override
                    fun onSuccess(msg: JSONObject?) {
                        val edit: SharedPreferences.Editor = prefs().edit()
                        edit.putString(TOKEN_FIELD, token)
                        edit.apply()
                    }
                })
            }
        }
    }

    /**
     * Прямое выполенение запроса
     */
    private fun send(
        request_type: String?,
        method: String?,
        params: JSONObject?,
        @Nullable listener: Api.OnApiCallbackListener?
    ) {
        updateSidActivity()
        try {
            params.put("shop_id", shop_id)
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
            if (source_type != null && source_time > 0 && source_time + 172800 * 1000 > System.currentTimeMillis()) {
                val source: JSONObject = JSONObject()
                source.put("from", source_type)
                source.put("code", source_id)
                params.put("source", source)
            }

            Api.send(request_type, method, params, listener)
        } catch (e: JSONException) {
            error(e.getMessage(), e)
        }
    }

    private fun sendAsync(method: String?, params: JSONObject?) {
        sendAsync(method, params, null)
    }

    /**
     * Асинхронное выполенение запросе, если did не указан и не выполнена инициализация
     */
    fun sendAsync(method: String?, params: JSONObject?, @Nullable listener: Api.OnApiCallbackListener?) {
        val thread: Thread = Thread { send("post", method, params, listener) }
        if (did != null && initialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    /**
     * Асинхронное выполенение запросе, если did не указан и не выполнена инициализация
     */
    fun getAsync(method: String?, params: JSONObject?, @Nullable listener: Api.OnApiCallbackListener?) {
        val thread: Thread = Thread { send("get", method, params, listener) }
        if (did != null && initialized) {
            thread.start()
        } else {
            queue.add(thread)
        }
    }

    companion object {
        var TAG: String?
        var NOTIFICATION_TYPE: String? = "NOTIFICATION_TYPE"
        var NOTIFICATION_ID: String? = "NOTIFICATION_ID"
        private val SID_FIELD: String? = "sid"
        private val SID_LAST_ACT_FIELD: String? = "sid_last_act"
        private val DID_FIELD: String? = "did"
        private val TOKEN_FIELD: String? = "token"
        private const val SESSION_CODE_EXPIRE = 2

        @SuppressLint("StaticFieldLeak")
        protected var instance: SDK? = null

        private var last_recommended_by: Params.RecommendedBy? = null

        fun initialize(context: Context?, shop_id: String?) {
            throw IllegalStateException("You need make static initialize method!")
        }

        fun initialize(context: Context?, shop_id: String?, stream: String?) {
            throw IllegalStateException("You need make static initialize method!")
        }

        fun userAgent(): String? {
            return ("Personalizatio SDK " + BuildConfig.FLAVOR.toUpperCase()).toString() + ", v" + BuildConfig.VERSION_NAME
        }

        /**
         * Update profile data
         * https://reference.api.rees46.com/#save-profile-settings
         *
         * @param data profile data
         */
        fun profile(data: HashMap<String?, String?>?) {
            profile(data, null)
        }

        fun profile(data: HashMap<String?, String?>?, listener: Api.OnApiCallbackListener?) {
            instance.sendAsync("profile/set", JSONObject(data), listener)
        }

        /**
         * @param data from data notification
         */
        fun notificationReceived(data: Map<String?, String?>?) {
            val params: JSONObject = JSONObject()
            try {
                if (data.get("type") != null) {
                    params.put("type", data.get("type"))
                }
                if (data.get("id") != null) {
                    params.put("code", data.get("id"))
                }
                if (params.length() > 0) {
                    instance.sendAsync("track/received", params)
                }
            } catch (e: JSONException) {
                Log.e(TAG, e.getMessage(), e)
            }
        }

        /**
         * @param extras from data notification
         */
        fun notificationClicked(extras: Bundle?) {
            if (extras != null && extras.getString(NOTIFICATION_TYPE, null) != null && extras.getString(
                    NOTIFICATION_ID,
                    null
                ) != null
            ) {
                val params: JSONObject = JSONObject()
                try {
                    params.put("type", extras.getString(NOTIFICATION_TYPE))
                    params.put("code", extras.getString(NOTIFICATION_ID))
                    instance.sendAsync("track/clicked", params)

                    //Сохраняем источник
                    setSource(extras.getString(NOTIFICATION_TYPE), extras.getString(NOTIFICATION_ID))
                } catch (e: JSONException) {
                    Log.e(TAG, e.getMessage(), e)
                }
            }
        }

        /**
         * Сохраняет данные источника
         *
         * @param type тип источника: bulk, chain, transactional
         * @param id   идентификатор сообщения
         */
        protected fun setSource(type: String?, id: String?) {
            instance.source_type = type
            instance.source_id = id
            instance.source_time = System.currentTimeMillis()
            instance.prefs().edit()
                .putString("source_type", type)
                .putString("source_id", id)
                .putLong("source_time", instance.source_time)
                .apply()
        }

        /**
         * @param listener Event on message receive
         */
        fun setOnMessageListener(listener: OnMessageListener?) {
            instance.onMessageListener = listener
        }

        /**
         * Быстрый поиск
         *
         * @param query    Поисковая фраза
         * @param type     Тип поиска
         * @param listener Колбек
         */
        fun search(query: String?, type: SearchParams.TYPE?, listener: Api.OnApiCallbackListener?) {
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
            query: String?,
            type: SearchParams.TYPE?,
            params: SearchParams?,
            listener: Api.OnApiCallbackListener?
        ) {
            if (instance.search != null) {
                params
                    .put(InternalParameter.SEARCH_TYPE, type.getValue())
                    .put(InternalParameter.SEARCH_QUERY, query)
                instance.getAsync("search", params.build(), listener)
            } else {
                warn("Search not initialized")
            }
        }

        /**
         * Пустой поиск
         *
         * @param listener v
         */
        @Deprecated
        @Deprecated(
            """This method is no longer acceptable to compute time between versions.
	  <p> Use {@link SDK#searchBlank(Api.OnApiCallbackListener)} instead."""
        )
        fun search_blank(listener: Api.OnApiCallbackListener?) {
            searchBlank(listener)
        }

        fun searchBlank(listener: Api.OnApiCallbackListener?) {
            if (instance.search != null) {
                if (instance.search.blank == null) {
                    instance.getAsync("search/blank",
                        Params().build(), object : OnApiCallbackListener() {
                            @Override
                            fun onSuccess(response: JSONObject?) {
                                instance.search.blank = response
                                listener.onSuccess(response)
                            }

                            @Override
                            fun onError(code: Int, msg: String?) {
                                listener.onError(code, msg)
                            }
                        })
                } else {
                    listener.onSuccess(instance.search.blank)
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
        fun recommend(recommender_code: String?, listener: Api.OnApiCallbackListener?) {
            recommend(recommender_code, Params(), listener)
        }

        /**
         * Запрос динамического блока рекомендаций
         *
         * @param code     Код динамического блока рекомендаций
         * @param params   Параметры для запроса
         * @param listener Колбек
         */
        fun recommend(code: String?, params: Params?, listener: Api.OnApiCallbackListener?) {
            instance.getAsync("recommend/$code", params.build(), listener)
        }

        /**
         * Трекинг события
         *
         * @param event   Тип события
         * @param item_id ID товара
         */
        fun track(event: Params.TrackEvent?, item_id: String?) {
            track(event, Params().put(Item(item_id)), null)
        }

        /**
         * Трекинг события
         *
         * @param event  Тип события
         * @param params Параметры
         */
        fun track(event: Params.TrackEvent?, @NonNull params: Params?) {
            track(event, params, null)
        }

        /**
         * Трекинг события
         *
         * @param event    Тип события
         * @param params   Параметры для запроса
         * @param listener Колбек
         */
        fun track(event: Params.TrackEvent?, @NonNull params: Params?, @Nullable listener: Api.OnApiCallbackListener?) {
            params.put(InternalParameter.EVENT, event.value)
            if (last_recommended_by != null) {
                params.put(last_recommended_by)
                last_recommended_by = null
            }
            instance.sendAsync("push", params.build(), listener)
        }

        /**
         * Трекинг кастомных событий
         *
         * @param event Ключ события
         */
        fun track(event: String?) {
            track(event, null, null, null, null)
        }

        /**
         * Трекинг кастомных событий
         *
         * @param event    Ключ события
         * @param category Event category
         * @param label    Event label
         * @param value    Event value
         */
        fun track(event: String?, @Nullable category: String?, @Nullable label: String?, @Nullable value: Integer?) {
            track(event, category, label, value, null)
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
        fun track(
            event: String?,
            @Nullable category: String?,
            @Nullable label: String?,
            @Nullable value: Integer?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            val params: Params = Params()
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
            instance.sendAsync("push/custom", params.build(), listener)
        }

        /**
         * @param listener
         */
        fun stories(code: String?, listener: Api.OnApiCallbackListener?) {
            if (instance != null) {
                instance.getAsync("stories/$code", JSONObject(), listener)
            }
        }

        /**
         * Вызывает событие сторисов
         *
         * @param event    Событие
         * @param code     Код блока сторисов
         * @param story_id Идентификатор сториса
         * @param slide_id Идентификатор слайда
         */
        fun track_story(event: String?, code: String?, story_id: Int, slide_id: String?) {
            if (instance != null) {
                try {
                    val params: JSONObject = JSONObject()
                    params.put("event", event)
                    params.put("story_id", story_id)
                    params.put("slide_id", slide_id)
                    params.put("code", code)

                    //Запоминаем последний клик в сторис, чтобы при вызове события просмотра товара добавить
                    last_recommended_by = RecommendedBy(Params.RecommendedBy.TYPE.STORIES, code)

                    instance.sendAsync("track/stories", params, null)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * Возвращает идентификатор устройства
         *
         * @return String
         */
        fun getDid(): String? {
            return instance.did
        }

        /**
         * Возвращает идентификатор сессии
         */
        fun getSid(listener: Consumer<String?>?) {
            val thread: Thread = Thread { listener.accept(instance.seance) }
            if (instance.initialized) {
                thread.start()
            } else {
                instance.queue.add(thread)
            }
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
        fun subscribeForPriceDrop(
            id: String?,
            current_price: Double,
            @Nullable email: String?,
            @Nullable phone: String?
        ) {
            subscribeForPriceDrop(id, current_price, email, phone, null)
        }

        fun subscribeForPriceDrop(
            id: String?,
            current_price: Double,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            val params: Params = Params()
            params.put(Params.Parameter.ITEM, id)
            params.put(Params.Parameter.PRICE, String.valueOf(current_price))
            if (email != null) {
                params.put(InternalParameter.EMAIL, email)
            }
            if (phone != null) {
                params.put(InternalParameter.PHONE, phone)
            }
            instance.sendAsync("subscriptions/subscribe_for_product_price", params.build(), listener)
        }

        /**
         * Отписывает на снижение цены
         * https://reference.api.rees46.com/?shell#price-drop
         *
         * @param item_ids Идентификаторы товара
         * @param email    Email, если есть
         * @param phone    Телефон, если есть
         */
        fun unsubscribeForPriceDrop(item_ids: Array<String?>?, @Nullable email: String?, @Nullable phone: String?) {
            unsubscribeForPriceDrop(item_ids, email, phone, null)
        }

        fun unsubscribeForPriceDrop(
            item_ids: Array<String?>?,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            val params: JSONObject = JSONObject()
            try {
                params.put("item_ids", String.join(", ", item_ids))
                if (email != null) {
                    params.put(InternalParameter.EMAIL.value, email)
                }
                if (phone != null) {
                    params.put(InternalParameter.PHONE.value, phone)
                }
                instance.sendAsync("subscriptions/unsubscribe_from_product_price", params, listener)
            } catch (e: JSONException) {
                Log.e(TAG, e.getMessage(), e)
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
        fun subscribeForBackInStock(id: String?, @Nullable email: String?, @Nullable phone: String?) {
            subscribeForBackInStock(id, null, email, phone, null)
        }

        fun subscribeForBackInStock(
            id: String?,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            subscribeForBackInStock(id, null, email, phone, listener)
        }

        fun subscribeForBackInStock(
            id: String?,
            @Nullable properties: JSONObject?,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            val params: Params = Params()
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
            instance.sendAsync("subscriptions/subscribe_for_product_available", params.build(), listener)
        }

        /**
         * Отписывает на наличие товара
         * https://reference.api.rees46.com/?shell#back-in-stock
         *
         * @param item_ids Идентификатор товара
         * @param email    Email, если есть
         * @param phone    Телефон, если есть
         */
        fun unsubscribeForBackInStock(item_ids: Array<String?>?, @Nullable email: String?, @Nullable phone: String?) {
            unsubscribeForBackInStock(item_ids, email, phone, null)
        }

        fun unsubscribeForBackInStock(
            item_ids: Array<String?>?,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            val params: JSONObject = JSONObject()
            try {
                params.put("item_ids", String.join(", ", item_ids))
                if (email != null) {
                    params.put(InternalParameter.EMAIL.value, email)
                }
                if (phone != null) {
                    params.put(InternalParameter.PHONE.value, phone)
                }
                instance.sendAsync("subscriptions/unsubscribe_from_product_available", params, listener)
            } catch (e: JSONException) {
                Log.e(TAG, e.getMessage(), e)
            }
        }

        /**
         * Manage subscriptions
         * https://reference.api.rees46.com/?swift#manage-subscriptions
         *
         * @param email
         * @param phone
         * @param subscriptions
         */
        fun manageSubscription(
            @Nullable email: String?,
            @Nullable phone: String?,
            @NonNull subscriptions: HashMap<String?, Boolean?>?
        ) {
            manageSubscription(email, phone, subscriptions, null)
        }

        fun manageSubscription(
            @Nullable email: String?,
            @Nullable phone: String?,
            @NonNull subscriptions: HashMap<String?, Boolean?>?,
            listener: Api.OnApiCallbackListener?
        ) {
            try {
                val params: JSONObject = JSONObject()
                for (entry in subscriptions.entrySet()) {
                    params.put(entry.getKey(), entry.getValue())
                }
                if (email != null) {
                    params.put(InternalParameter.EMAIL.value, email)
                }
                if (phone != null) {
                    params.put(InternalParameter.PHONE.value, phone)
                }
                instance.sendAsync("subscriptions/manage", params, listener)
            } catch (e: JSONException) {
                Log.e(TAG, e.getMessage(), e)
            }
        }

        /**
         * Возвращает текущий сегмент для А/В тестирования
         */
        fun getSegment(): String? {
            if (instance == null) {
                throw RuntimeException("You need initialize SDK before request segment")
            } else {
                return instance.segment
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
        fun addToSegment(@NonNull segment_id: String?, @Nullable email: String?, @Nullable phone: String?) {
            segmentMethod("add", segment_id, email, phone, null)
        }

        fun addToSegment(
            @NonNull segment_id: String?,
            @Nullable email: String?,
            @Nullable phone: String?,
            listener: Api.OnApiCallbackListener?
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
        fun removeFromSegment(@NonNull segment_id: String?, @Nullable email: String?, @Nullable phone: String?) {
            segmentMethod("remove", segment_id, email, phone, null)
        }

        fun removeFromSegment(
            @NonNull segment_id: String?,
            @Nullable email: String?,
            @Nullable phone: String?,
            listener: Api.OnApiCallbackListener?
        ) {
            segmentMethod("remove", segment_id, email, phone, listener)
        }

        /**
         * Get user segments
         * https://reference.api.rees46.com/?swift#get-user-segments
         *
         * @param listener
         */
        fun getCurrentSegment(@NonNull listener: Api.OnApiCallbackListener?) {
            instance.getAsync("segments/get", JSONObject(), listener)
        }

        private fun segmentMethod(
            method: String?,
            @Nullable segment_id: String?,
            @Nullable email: String?,
            @Nullable phone: String?,
            @Nullable listener: Api.OnApiCallbackListener?
        ) {
            try {
                val params: JSONObject = JSONObject()
                if (segment_id != null) {
                    params.put("segment_id", segment_id)
                }
                if (email != null) {
                    params.put("email", email)
                }
                if (phone != null) {
                    params.put("phone", phone)
                }
                instance.sendAsync("segments/$method", params, listener)
            } catch (e: JSONException) {
                Log.e(TAG, e.getMessage(), e)
            }
        }

        /**
         * Send notification token
         * https://reference.api.rees46.com/?java#create-new-token
         *
         * @param token
         * @param listener
         */
        fun setPushTokenNotification(@NonNull token: String?, listener: Api.OnApiCallbackListener?) {
            val params: HashMap<String?, String?> = HashMap()
            params.put("platform", "android")
            params.put("token", token)
            instance.sendAsync("mobile_push_tokens", JSONObject(params), listener)
        }

        //----------Private--------------->
        /**
         * @param message Сообщение
         */
        fun debug(message: String?) {
            Log.d(TAG, message)
        }

        /**
         * @param message Сообщение
         */
        fun warn(message: String?) {
            Log.w(TAG, message)
        }

        /**
         * @param message Сообщение об ошибке
         */
        fun error(message: String?) {
            Log.e(TAG, message)
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
        fun onMessage(remoteMessage: RemoteMessage?) {
            notificationReceived(remoteMessage.getData())
            if (instance.onMessageListener != null) {
                instance.onMessageListener.onMessage(remoteMessage.getData())
            }
        }
    }
}
