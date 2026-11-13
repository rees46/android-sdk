package com.personalization

import android.content.Context
import android.os.Bundle
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentManager
import com.google.firebase.messaging.RemoteMessage
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.Params.InternalParameter
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.managers.CartManager
import com.personalization.api.managers.CategoryManager
import com.personalization.api.managers.CollectionManager
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.LoyaltyManager
import com.personalization.api.managers.OrdersManager
import com.personalization.api.managers.ProductsManager
import com.personalization.api.managers.ProfileManager
import com.personalization.api.managers.PredictManager
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.params.ProfileParams
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.SdkState
import com.personalization.core.internal.AndroidConnectivityMonitor
import com.personalization.core.internal.ConnectivityMonitor
import com.personalization.core.internal.Core
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CoreHttpClient
import com.personalization.core.internal.CoreInit
import com.personalization.core.internal.CoreSelection
import com.personalization.core.internal.CoreV2
import com.personalization.core.internal.InitHooks
import com.personalization.core.internal.InitSetup
import com.personalization.core.internal.InstanceState
import com.personalization.core.internal.ProcessAppLifecycle
import com.personalization.core.internal.ShopStorage
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.StorageMigrations
import com.personalization.di.AppModule
import com.personalization.di.DaggerSdkComponent
import com.personalization.features.inAppNotification.impl.ForegroundActivity
import com.personalization.features.inAppNotification.impl.PopupPresentation
import com.personalization.features.notification.data.mapper.toNotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.handlers.notifications.NotificationHandler
import com.personalization.popups.PopupsV3
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.mappers.SdkInitializationMapper.mapToSdkInitResponse
import com.personalization.sdk.data.utils.AdvertisingUtils
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.NPSRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import com.personalization.sdk.domain.usecases.network.AddTaskToQueueUseCase
import com.personalization.sdk.domain.usecases.network.InitNetworkUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.domain.usecases.notification.GetAllNotificationsUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.InitPreferencesUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.InitUserSettingsUseCase
import com.personalization.sdk.domain.usecases.userSettings.InitializeAdvertisingIdUseCase
import com.personalization.stories.StoriesManager
import com.personalization.stories.views.StoriesView
import com.personalization.ui.widgets.RecentSearches
import com.personalization.ui.widgets.ResponseCache
import com.personalization.utils.DomainFormattingUtils.formatApiDomain
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

private const val GAID_KEY = "google_advertising_id"

@OptIn(InternalPersonalizationV3Api::class)
open class SDK {

    internal lateinit var context: Context

    private var onMessageListener: OnMessageListener? = null
    private var onPushTokenListener: OnPushTokenListener? = null
    private var search: Search = Search(JSONObject())

    /**
     * True once initialize() has wired the Dagger graph. A push can reach the SDK in a process
     * where initialize() never ran (FCM cold start, or the host integrated the SDK incorrectly),
     * so the push path checks this flag to avoid crashing the host app with
     * UninitializedPropertyAccessException.
     */
    @Volatile
    private var isSdkInitialized: Boolean = false

    /** Set by [release]: every call through the instance fails with `notInitialized` until it is initialized again. */
    @Volatile
    private var released: Boolean = false

    /** Serializes initializing this object: two calls never initialize it twice. */
    private val initLock = Any()

    /** The config the instance was initialized with. */
    @Volatile
    internal var config: Rees46Config? = null
        private set

    /** Brought up only to track a push ([initializeForPush]): [Rees46.initialize] replaces it. */
    @Volatile
    internal var pushOnly: Boolean = false
        private set

    /** The instance's [SdkState] and the app's listeners of it, for both cores. */
    internal val instanceState: InstanceState by lazy { InstanceState(shopId = { logShopId }, dispatchers = dispatchers) }

    @Inject
    internal lateinit var initializeAdvertisingIdUseCase: InitializeAdvertisingIdUseCase

    @Inject
    internal lateinit var userSettingsRepository: UserSettingsRepository

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var registerManager: RegisterManager

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    @Inject
    lateinit var storiesManager: StoriesManager

    @Inject
    lateinit var recommendationManager: RecommendationManager

    @Inject
    lateinit var productsManager: ProductsManager

    @Inject
    lateinit var cartManager: CartManager

    /**
     * Standard tracking events, grouped: `sdk.tracking.productView(itemId)`,
     * `sdk.tracking.addToCart(item)`, and the rest.
     */
    @Inject
    lateinit var tracking: TrackingApi

    @Deprecated(
        message = "Use the tracking namespace: sdk.tracking.",
        replaceWith = ReplaceWith("tracking"),
        level = DeprecationLevel.WARNING
    )
    @Inject
    lateinit var trackEventManager: TrackEventManager

    @Inject
    lateinit var searchManager: SearchManager

    @Inject
    lateinit var predictManager: PredictManager

    @Inject
    lateinit var ordersManager: OrdersManager

    @Inject
    lateinit var loyaltyManager: LoyaltyManager

    @Inject
    lateinit var profileManager: ProfileManager

    @Inject
    lateinit var categoryManager: CategoryManager

    @Inject
    lateinit var collectionManager: CollectionManager

    @Inject
    lateinit var inAppNotificationManager: InAppNotificationManager

    @Inject
    internal lateinit var popupPresentation: PopupPresentation

    /**
     * Decides where each server popup is shown, or keeps it from being shown; see
     * [PopupPresentationListener]. Once set, [enableAutoPopupPresentation] is not consulted. May be
     * set before or after initialization.
     */
    var popupPresentationListener: PopupPresentationListener? = null
        set(value) {
            field = value
            applyPopupPresentation()
        }

    /**
     * Whether server popups are shown in the activity on screen while no [popupPresentationListener]
     * is set. Turn it off and, without a listener, popups are not shown at all. Defaults to true;
     * [Rees46Config.enableAutoPopupPresentation] sets it for instances made through [Rees46].
     */
    var enableAutoPopupPresentation: Boolean = true
        set(value) {
            field = value
            applyPopupPresentation()
        }

    /**
     * Where the instance's initialization stands; [SdkState.NOT_INITIALIZED] before it is initialized
     * and after [release]. On the legacy core it only goes from [SdkState.INITIALIZING] to
     * [SdkState.READY].
     */
    val state: SdkState
        get() = instanceState.current

    /**
     * Calls [listener] on the main thread with every later change of [state], once per change, until
     * the returned handle is cancelled. May be set before the instance is initialized. An exception
     * the listener throws is logged as `callback_failed`. [release] tells it [SdkState.NOT_INITIALIZED]
     * and drops it.
     */
    fun onStateChange(listener: (SdkState) -> Unit): Cancellable = instanceState.addListener(listener)

    /** The shop of this instance; empty before it is initialized. */
    val shopId: String
        get() = config?.shopId ?: logShopId.orEmpty()

    /** The device's `did` at this shop; null before the server hands one out, or before initialization. */
    val deviceId: String?
        get() = if (::userSettingsRepository.isInitialized) userSettingsRepository.getDid().ifEmpty { null } else null

    /** The `sid` of the current session; null while there is none, or before initialization. */
    val sessionId: String?
        get() = if (::userSettingsRepository.isInitialized) userSettingsRepository.getSid().ifEmpty { null } else null

    /** The level of this instance's log; [Rees46Config.logLevel] sets it for instances made through [Rees46]. */
    internal var logLevel: LogLevel = LogLevel.WARNING

    /** [Rees46Config.coreV2] of the instance; the process's core decides ([CoreSelection]). */
    internal var coreV2Requested: Boolean = false

    /** The instance's core, once it is initialized: the legacy one or [CoreV2]. */
    @Volatile
    internal var core: Core? = null
        private set

    /** The shop's partition, once the instance is initialized. */
    @Volatile
    internal var storage: ShopStorage? = null
        private set

    /** Where the instance's own work runs: tests pass test dispatchers before the first call. */
    internal var dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT

    /** Where the calls made for Java and for code outside coroutines run; nothing they do reaches the app as an exception. */
    private val calls: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.io + CoroutineExceptionHandler { _, throwable ->
            log(LogLevel.ERROR, LogCodes.SDK_LOG, "A call of the instance failed unexpectedly", throwable = throwable)
        })
    }

    /** The shop this instance logs for, once it is initialized: its lines pass that shop's level. */
    @Volatile
    internal var logShopId: String? = null
        private set

    /** `popups_v3` of the last `/init` answer; null while `/init` has not answered. */
    @Volatile
    internal var popupsV3InitFlag: Boolean? = null
        private set

    /** Popups v3 of this instance, once the host has touched [popups]. */
    @Volatile
    internal var popupsV3: PopupsV3? = null
        private set

    /**
     * Popups v3 — native rendering of the new popup builder's documents. Asleep until the host turns
     * them on (`popups.enabled`) and `/init` reports `popups_v3`; see [PopupsV3]. Behind the v3 gate
     * ([InternalPersonalizationV3Api]) and hidden from Java.
     */
    @InternalPersonalizationV3Api
    @get:JvmSynthetic
    val popups: PopupsV3
        get() = popupsV3 ?: synchronized(this) { popupsV3 ?: PopupsV3(this).also { popupsV3 = it } }

    @Inject
    lateinit var initPreferencesUseCase: InitPreferencesUseCase

    @Inject
    lateinit var initUserSettingsUseCase: InitUserSettingsUseCase

    @Inject
    lateinit var initNetworkUseCase: InitNetworkUseCase

    /** The request funnel of this instance; its core is chosen at initialization. */
    @Inject
    internal lateinit var networkRepository: NetworkRepositoryImpl

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

    @Inject
    lateinit var NPSRepository: NPSRepository

    /**
     * @param shopId Shop key
     * @param preferencesKey the prefix of the shop's storage, as [Rees46Config.storageKey]. Earlier
     * releases kept everything in one file of this name; it is read once into the shop's partition and
     * left as it is.
     *
     * On an object already initialized for [shopId] it does nothing (and logs `invalid_config` when the
     * parameters differ); after [release] it initializes the object again.
     */
    @Deprecated(
        message = "Use Rees46.initialize(context, Rees46Config(...)) — the unified, multi-instance " +
            "entry point. Reach instances with Rees46.instance(shopId).",
        replaceWith = ReplaceWith(
            "Rees46.initialize(context, Rees46Config(shopId = shopId))",
            "com.personalization.Rees46",
            "com.personalization.Rees46Config"
        )
    )
    fun initialize(
        context: Context,
        shopId: String,
        apiDomain: String = "api.rees46.ru",
        tag: String = TAG,
        preferencesKey: String = PreferencesPartition.LEGACY_KEY,
        stream: String = ANDROID,
        autoSendPushToken: Boolean = true,
        needReInitialization: Boolean = false,
        addTrailingSlash: Boolean = true
    ) = initializeWith(
        context,
        Rees46Config(
            shopId = shopId,
            apiDomain = apiDomain,
            stream = stream,
            autoSendPushToken = autoSendPushToken,
            needReInitialization = needReInitialization,
            addTrailingSlash = addTrailingSlash,
            tag = tag,
            enableAutoPopupPresentation = enableAutoPopupPresentation,
            logLevel = logLevel,
            coreV2 = coreV2Requested,
            storageKey = preferencesKey.takeUnless { it == PreferencesPartition.LEGACY_KEY },
        )
    )

    /**
     * Initializes this object for [config]: the entry of [Rees46.initialize] and of the legacy
     * [initialize]. An object live for the same shop is left as it is: it keeps the config it was made
     * with, and a different [config] is logged as `invalid_config` (`already_initialized`).
     */
    internal fun initializeWith(context: Context, config: Rees46Config) = start(
        context,
        config,
        autoSendPushToken = config.autoSendPushToken,
        needReInitialization = config.needReInitialization,
        pushOnly = false,
    )

    /**
     * Light bring-up for handling a push in a (possibly cold) background process: does not re-register
     * the push token (it is already on the server — that is why the push arrived) and skips the GAID +
     * profile/set call. Just enough to track the delivery and reach the message listener. Used by
     * [Rees46.handlePush] to materialize a lazily-registered shop without the full startup work.
     */
    internal fun initializeForPush(context: Context, config: Rees46Config) = start(
        context,
        config,
        autoSendPushToken = false,
        needReInitialization = false,
        pushOnly = true,
    )

    private fun start(
        context: Context,
        config: Rees46Config,
        autoSendPushToken: Boolean,
        needReInitialization: Boolean,
        pushOnly: Boolean,
    ): Unit = synchronized(initLock) {
        val current = this.config
        if (current != null && isLive && current.shopId == config.shopId) {
            keepConfig(config)
            return
        }
        enableAutoPopupPresentation = config.enableAutoPopupPresentation
        logLevel = config.logLevel
        coreV2Requested = config.coreV2
        initializeInternal(context, config, autoSendPushToken, needReInitialization, sendProfileOnInit = !pushOnly)
        this.pushOnly = pushOnly
        this.config = config
    }

    /** Initialized and not released. */
    private val isLive: Boolean get() = isSdkInitialized && !released

    /** A later initialization of a live shop: the instance stays as it was made, a different config is logged. */
    internal fun keepConfig(config: Rees46Config) {
        if (this.config == config) return
        SdkLogs.log(
            LogLevel.WARNING,
            LogCodes.INVALID_CONFIG,
            "The shop is initialized already; its instance keeps the config it was made with",
            config.shopId,
            mapOf("reason" to LogReasons.ALREADY_INITIALIZED)
        )
    }

    private fun initializeInternal(
        context: Context,
        config: Rees46Config,
        autoSendPushToken: Boolean,
        needReInitialization: Boolean,
        // When false, skip the background GAID + profile/set call (the push-context bring-up).
        sendProfileOnInit: Boolean
    ) {
        val shopId = config.shopId
        // Initialized before (another shop, or released): the old start sends and takes nothing more.
        stopCurrent()

        val sdkComponent = DaggerSdkComponent.factory().create(
            appModule = AppModule(applicationContext = context)
        )

        val baseUrl = formatApiDomain(
            apiDomain = config.apiDomain,
            addTrailingSlash = config.addTrailingSlash
        )

        sdkComponent.inject(sdk = this)
        this.context = context
        TAG = config.tag
        SdkLogs.register(shopId = shopId, level = logLevel, tag = config.tag, owner = this)
        logShopId = shopId
        pushTokenManager.logShopId = shopId
        storiesManager.logShopId = shopId

        // Before the init request goes out: its response may already carry a popup.
        ForegroundActivity.install(context)
        applyPopupPresentation()

        onPushTokenListener?.let { pushTokenManager.setOnPushTokenListener(it) }

        // The shop's own partition, prefixed by the app's storageKey if it has one. Before /init, the
        // data of older releases is read into it once (the file of a custom preferencesKey, or the file
        // all shops shared before partitions), and the device-level store gets the A/B group, push
        // tokens and advertising id. Nothing older is deleted.
        val layout = StorageLayout(shopId, config.storageKey)
        StorageMigrations.run(context, layout)
        val storage = ShopStorage(context, layout).also { this.storage = it }

        initPreferencesUseCase.invoke(
            context = context,
            preferencesKey = layout.partitionName,
            shopId = shopId
        )

        notificationHandler.initialize(context = context)

        initUserSettingsUseCase.invoke(
            shopId = shopId,
            stream = config.stream
        )

        initNetworkUseCase(url = baseUrl)
        released = false
        val coreV2 = CoreSelection.resolve(coreV2Requested, shopId)
        if (coreV2) {
            val connectivity = AndroidConnectivityMonitor(context)
            val core = networkRepository.useCoreV2(shopId, connectivity, dispatchers)
            this.core = core
            startCoreV2(core, connectivity, context, config, storage, autoSendPushToken, needReInitialization)
        } else {
            core = networkRepository.core
            startLegacy(context, config, autoSendPushToken, needReInitialization)
        }

        // Register this instance so pushes delivered to the (process-global) messaging services are
        // routed back to every initialized SDK instance, not just one, and so the documented
        // `SDK().initialize()` usage and hosts reading SDK.instance get a working instance. register
        // both adds this to the fan-out set and makes it the current default.
        SdkRegistry.register(shopId = shopId, sdk = this)
        isSdkInitialized = true

        // The new core sends the advertising id with /init, and only with sendAdvertisingId.
        if (sendProfileOnInit && !coreV2) {
            CoroutineScope(Dispatchers.IO).launch {
                val advertisingId = initializeAdvertisingIdUseCase.invoke()

                // Not through profile(): the advertising id is not a change of the visitor that
                // should make popups v3 ask for their list again.
                sendNetworkMethodUseCase.postAsync(
                    method = SET_PROFILE_FIELD,
                    params = ProfileParams
                        .Builder()
                        .put(GAID_KEY, advertisingId)
                        .build()
                        .toJson(),
                    listener = object : OnApiCallbackListener() {
                        override fun onSuccess(response: JSONObject?) {
                            SdkLogs.log(LogLevel.DEBUG, LogCodes.ADVERTISING_ID_SENT, "Advertising id sent", shopId)
                        }

                        override fun onError(code: Int, msg: String?) {
                            // Without the answer's body: an error answer often repeats the request's data.
                            SdkLogs.log(
                                LogLevel.WARNING,
                                LogCodes.ADVERTISING_ID_FAILED,
                                "Advertising id was not sent",
                                shopId,
                                mapOf("status" to code)
                            )
                        }
                    }
                )
            }
        }

    }

    /** Stops what an earlier initialization of this object started. */
    private fun stopCurrent() {
        if (::registerManager.isInitialized) registerManager.stop()
        if (::networkRepository.isInitialized) networkRepository.release()
    }

    /** `/init` and the session of the legacy path, as before. */
    private fun startLegacy(context: Context, config: Rees46Config, autoSendPushToken: Boolean, needReInitialization: Boolean) {
        // A did the app brings is taken while the shop has none, as on the new core.
        val configured = config.deviceId?.trim().orEmpty()
        if (!needReInitialization && configured.isNotEmpty() && userSettingsRepository.getDid().isEmpty()) {
            userSettingsRepository.updateDid(configured)
        }

        // Popups v3 start from the /init answer; in their test mode /init counts test popups too.
        registerManager.onInitFinished = { flag ->
            popupsV3InitFlag = flag
            popupsV3?.onInit(flag)
        }
        registerManager.extraInitParams = ::popupsV3InitParams
        registerManager.onStateChange = { state -> instanceState.set(state) }

        registerManager.initialize(
            context = context,
            contentResolver = context.contentResolver,
            autoSendPushToken = autoSendPushToken,
            needReInitialization = needReInitialization
        )
    }

    /** The new core's `/init` and state ([CoreInit]); requests wait for it in [CoreV2]. */
    private fun startCoreV2(
        core: CoreV2,
        connectivity: ConnectivityMonitor,
        context: Context,
        config: Rees46Config,
        storage: ShopStorage,
        autoSendPushToken: Boolean,
        needReInitialization: Boolean,
    ) {
        val contentResolver = context.contentResolver
        val init = CoreInit(
            setup = InitSetup(
                shopId = config.shopId,
                needReInitialization = needReInitialization,
                deviceId = config.deviceId,
                advertisingId = if (config.sendAdvertisingId) ::readAdvertisingId else null,
                extraParams = ::popupsV3InitParams,
                skipInit = { RegisterManager.isPreLaunchDevice(contentResolver) },
            ),
            send = { params -> core.client.send(NetworkMethod.GET(CoreHttpClient.INIT_PATH), params) },
            settings = userSettingsRepository,
            storage = storage,
            state = instanceState,
            connectivity = connectivity,
            lifecycle = ProcessAppLifecycle(),
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) {
                    val response = answer.mapToSdkInitResponse()
                    // A popup is shown from a fresh answer only, never again from the saved one.
                    if (fresh) response?.popupDto?.let { inAppNotificationManager.shopPopUp(popupDto = it) }
                    val flag = response?.popupsV3 == true
                    popupsV3InitFlag = flag
                    popupsV3?.onInit(flag)
                }

                override fun onUsable() {
                    // What waited for a did on the legacy path (the stories v3 requests) goes now.
                    networkRepository.executeQueueTasks()
                    pushTokenManager.initialize(context = context, autoSendPushToken = autoSendPushToken)
                }
            },
            dispatchers = dispatchers,
        )
        core.init = init
        init.start()
    }

    /** The advertising id for `/init`, or null when it cannot be read. */
    private suspend fun readAdvertisingId(): String? =
        initializeAdvertisingIdUseCase.invoke().takeUnless { it.isBlank() || it == AdvertisingUtils.DEFAULT_ADVERTISING_ID }

    /** The test mode of popups v3 makes `/init` count test popups too. */
    private fun popupsV3InitParams(): Map<String, String> =
        if (popupsV3?.testMode == true) mapOf(POPUPS_V3_TEST_MODE to "true") else emptyMap()

    private fun initNetworkUseCase(url: String?) {
        if (url != null) {
            initNetworkUseCase.invoke(
                baseUrl = url
            )
        }
    }

    @Deprecated(
        message = "The view loads itself: declare app:shop_id (or none for the default instance) on " +
            "the XML StoriesView, or use the Compose StoriesWidget. Kept working for existing hosts.",
        replaceWith = ReplaceWith("")
    )
    fun initializeStoriesView(storiesView: StoriesView) {
        storiesView.attach(this)
    }

    @Deprecated(
        "Not needed any more: popups are shown in the activity on screen. To pick the activity, " +
            "or keep a popup from being shown, set SDK.popupPresentationListener."
    )
    @Suppress("DEPRECATION")
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
    /**
     * No `replaceWith`: which method replaces this one depends on [event], and a quick-fix that always
     * picked `storyView` would silently turn a tracked click into a view.
     */
    @Deprecated(
        message = "Use the tracking namespace: sdk.tracking.storyView(storyId, slideId, code) for " +
            "\"view\" and sdk.tracking.storyClick(storyId, slideId, code) for \"click\".",
        level = DeprecationLevel.WARNING
    )
    @Suppress("DEPRECATION")
    fun trackStory(event: String, code: String, storyId: Int, slideId: String) {
        if (::storiesManager.isInitialized) {
            storiesManager.trackStory(
                event = event, code = code, storyId = storyId, slideId = slideId
            )
        } else {
            log(LogLevel.WARNING, LogCodes.STORIES, "storiesManager is not initialized")
        }
    }

    /**
     * Update profile data
     *
     * @param data profile data
     */
    fun profile(data: ProfileParams, listener: OnApiCallbackListener? = null) {
        val params = data.toJson()
        sendAsync(SET_PROFILE_FIELD, params, listener)
        popupsV3?.onContextChange(SET_PROFILE_FIELD, params.toString())
    }

    /**
     * Return the session ID
     */
    fun getSid(): String = sessionId.orEmpty()

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
     * @deprecated Per-instance and must be set on every shop; a shop it was never set on silently
     * shows nothing. Use the process-global [Rees46.setOnMessageListener], which fires for every shop
     * with its `shopId`. This one still works and fires alongside the global listener.
     */
    @Deprecated(
        "Use Rees46.setOnMessageListener { shopId, data -> ... } — one process-global listener covers " +
            "every shop instead of wiring one per instance."
    )
    fun setOnMessageListener(listener: OnMessageListener) {
        onMessageListener = listener
    }

    /**
     * Registers a callback invoked when a push token is received or refreshed for a provider
     * (FCM and/or HMS).
     *
     * This is the recommended way for the host app to obtain push tokens: the SDK captures
     * token issuance and refresh from its own messaging services automatically, including
     * tokens that a provider delivers only asynchronously (e.g. HMS via `onNewToken`, which
     * cannot be retrieved synchronously). May be called before or after [initialize].
     */
    fun setOnPushTokenListener(listener: OnPushTokenListener) {
        onPushTokenListener = listener
        if (::pushTokenManager.isInitialized) {
            pushTokenManager.setOnPushTokenListener(listener)
        }
    }

    private fun applyPopupPresentation() {
        if (!::popupPresentation.isInitialized) return
        popupPresentation.listener = popupPresentationListener?.let { listener ->
            { popup -> listener.shouldPresentPopup(this, popup) }
        }
        popupPresentation.autoPresentation = enableAutoPopupPresentation
    }

    /**
     * Returns the last known push token for [provider] from the local cache, or null if no
     * token has been received yet. For live updates use [setOnPushTokenListener].
     */
    fun getPushToken(provider: PushProvider): String? = pushTokenManager.getToken(provider)

    /**
     * Registers a push [token] for the given [provider] with the rees46 backend.
     *
     * In the default setup you normally do NOT need to call this. The SDK ships its own
     * messaging services for FCM and HMS, captures token issuance and refresh automatically,
     * sends the token to the backend, and reports it to the host via [setOnPushTokenListener].
     *
     * Call this only if your app owns the messaging service (you declare your own
     * FirebaseMessagingService / HmsMessageService) and want to forward the token manually.
     * Always pass the [PushProvider] that issued the token: the backend stores it per provider,
     * and FCM and HMS tokens are not interchangeable.
     *
     * @param token    the push token issued by [provider]
     * @param provider the provider that issued the token (FCM or HMS)
     * @param listener optional callback with the backend registration result
     */
    fun setPushToken(
        token: String,
        provider: PushProvider,
        listener: OnApiCallbackListener? = null
    ) {
        pushTokenManager.sendToken(token = token, provider = provider, listener = listener)
    }

    /**
     * Registers an FCM push token with the rees46 backend.
     *
     * @deprecated This method predates multi-provider support. It has no [PushProvider]
     * parameter and therefore always assumes the token is an FCM token. Now that both FCM and
     * HMS are supported the provider must be explicit, otherwise an HMS token would be
     * registered as FCM (the backend keeps them separate and they are not interchangeable).
     * Use [setPushToken] with an explicit [PushProvider] instead. Equivalent to
     * `setPushToken(token, PushProvider.FCM, listener)`.
     *
     * @param token    an FCM push token
     * @param listener optional callback with the backend registration result
     */
    @Deprecated(
        message = "Assumes an FCM token; the provider cannot be specified. Use " +
            "setPushToken(token, provider, listener) with an explicit PushProvider — " +
            "FCM and HMS tokens are stored separately and are not interchangeable.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("setPushToken(token, PushProvider.FCM, listener)")
    )
    fun setPushTokenNotification(token: String, listener: OnApiCallbackListener?) =
        setPushToken(token = token, provider = PushProvider.FCM, listener = listener)

    /**
     * Stops this instance and takes it out of [Rees46]'s registry and the push fan-out. Call it for
     * short-lived instances; an app that keeps one instance for the whole process does not need it.
     *
     * Afterwards every call through the instance fails with `notInitialized`, on both cores: a
     * request's old callback gets `onError(-1)` (a request still waiting for a `did` too), and
     * [deleteUserCredentials] throws [SdkException] without deleting anything or asking `/init`.
     * [state] is [SdkState.NOT_INITIALIZED]: the state listeners hear it and are dropped. On the new
     * core ([Rees46Config.coreV2]) the requests in flight are cancelled (`onError(-1)`) and `/init`
     * stops; the legacy one cannot cancel a request in flight. The shop's data stays on the device, and
     * a new [Rees46.initialize] for the shop makes a fresh instance.
     */
    fun release() {
        released = true
        SdkRegistry.unregister(this)
        // Nothing more goes out: no /init, and requests fail with notInitialized.
        stopCurrent()
        instanceState.set(SdkState.NOT_INITIALIZED)
        instanceState.dropListeners()
        // Only this instance's level goes: another live instance of the shop keeps logging at its own.
        logShopId?.let { SdkLogs.unregister(it, owner = this) }
    }

    /**
     * Forgets this shop's customer on this device: the `did`, the session (`sid` and the time of the last
     * activity), the saved `/init` answer, the source [tracking] `setSource` set, the dates the push
     * token was sent (so the token goes to the next customer), the shop's recent searches, the answers
     * the widgets keep in memory and, on the new core, the queue of events not sent yet. The `did` and
     * the session also go from the files older releases kept them in, so a later start cannot read them
     * back. The stories' state, the device's push token, its A/B group and its advertising id stay: they
     * belong to the device, not to the customer. Logs `device_id_reset`.
     *
     * The instance stays registered and asks `/init` again without a `did`; the server hands out a new
     * one, and requests made meanwhile wait for it, the push token among them. An answer to an `/init`
     * sent before is dropped. Other shops of the app keep their customers.
     *
     * When part of the data does not reach the disk, the call still ends normally: there is no error for
     * storage. The failure is logged as `storage_failed` (and not as `device_id_reset`), and the next
     * call tries again.
     *
     * Android's Auto Backup (on by default for apps targeting Android 6.0 and later) copies the app's
     * `SharedPreferences`, the SDK's among them, and restores them on the user's new device: the `did`
     * of the backed-up customer comes back there. A library cannot change the app's backup rules; an
     * app that wants every device to start without one excludes the SDK's files in its own
     * `fullBackupContent` / `dataExtractionRules`, all in the `sharedpref` domain:
     * - `personalization_sdk_<shopId>.xml`, the shop's partition (with a [Rees46Config.storageKey],
     *   `<storageKey>_personalization_sdk_<shopId>.xml`);
     * - `personalization_sdk.device.xml`, the device's A/B group, push token and advertising id;
     * - `DEFAULT_STORAGE_KEY.xml`, the store of releases before per-shop partitions, and `<storageKey>.xml`,
     *   the store an app named with `preferencesKey`: read once into the partition;
     * - `personalization_sdk_<shopId>_stories.xml`, the stories seen;
     * - `personalization_sdk_<shopId>_popups_v3.xml`, when popups v3 were shown;
     * - `personalization_ui_recent_searches.xml`, the search widget's recent searches.
     *
     * The saved `/init` answer and the queue are kept in `noBackupFilesDir` and never leave the device.
     *
     * @throws SdkException with [SdkError.NotInitialized] when the instance was never initialized or
     * was released; nothing is deleted then.
     */
    suspend fun deleteUserCredentials() {
        val core = core
        val storage = storage
        if (core == null || storage == null || released) throw SdkException(SdkError.NotInitialized)
        // From here on an answer to an /init sent before is dropped: one landing during the wipe would
        // write the forgotten did back. The new core also holds requests until the new did.
        val init = (core as? CoreV2)?.init
        init?.holdForRestart()
        val restart = if (init == null) registerManager.beginRestart() else 0
        val complete = withContext(dispatchers.io) {
            val stored = core.deleteUserCredentials(storage)
            val searches = RecentSearches.forget(context, storage.layout.shopId)
            ResponseCache.shared.clear()
            stored && searches
        }
        if (complete) {
            log(
                LogLevel.INFO,
                LogCodes.DEVICE_ID_RESET,
                "The customer's data was deleted; /init asks for a new did",
                mapOf("reason" to LogReasons.DELETE_USER_CREDENTIALS)
            )
        } else {
            log(
                LogLevel.ERROR,
                LogCodes.STORAGE_FAILED,
                "The customer's data was not all deleted",
                mapOf("op" to ShopStorage.OP_DELETE_CREDENTIALS)
            )
        }
        try {
            if (init != null) init.restart() else registerManager.restartAsNewDevice(restart)
        } catch (e: Exception) {
            // The customer is forgotten either way; the next launch asks /init.
            log(LogLevel.ERROR, LogCodes.SDK_LOG, "/init could not be restarted after the deletion", throwable = e)
        }
    }

    /**
     * [deleteUserCredentials] for Java and for code outside coroutines: [listener] gets
     * [SdkResult.Success] once the customer is forgotten, or [SdkResult.Failure] with the error
     * ([SdkError.NotInitialized]), once, on the main thread.
     */
    fun deleteUserCredentials(listener: CompletionListener<Unit>) {
        calls.launch {
            val result: SdkResult<Unit> = try {
                deleteUserCredentials()
                SdkResult.Success(Unit)
            } catch (e: SdkException) {
                SdkResult.Failure(e.error)
            }
            complete(listener, result)
        }
    }

    /** Hands [result] to the app's [listener] on the main thread; an exception it throws is logged. */
    private suspend fun <T> complete(listener: CompletionListener<T>, result: SdkResult<T>) {
        withContext(dispatchers.main) {
            try {
                listener.onComplete(result)
            } catch (e: Exception) {
                log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's CompletionListener threw", throwable = e)
            }
        }
    }

    /** Writes to the SDK's log under this instance's shop, so the shop's [Rees46Config.logLevel] applies. */
    internal fun log(
        level: LogLevel,
        code: String,
        message: String,
        context: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = SdkLogs.log(level, code, message, logShopId, context, throwable)

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
            log(LogLevel.WARNING, LogCodes.SDK_LOG, "Search not initialized")
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
            log(LogLevel.WARNING, LogCodes.SDK_LOG, "Search not initialized")
        }
    }

    fun review(
        rate: Int,
        channel: String,
        category: String,
        orderId: String? = null,
        comment: String? = null,
        listener: OnApiCallbackListener? = null
    ) = NPSRepository.review(
        rate = rate,
        channel = channel,
        category = category,
        orderId = orderId,
        comment = comment,
        listener = listener
    )

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
    @Suppress("DEPRECATION")
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
    @Suppress("DEPRECATION")
    fun track(event: TrackEvent, params: Params, listener: OnApiCallbackListener? = null) {
        trackEventManager.track(event, params, listener)
    }

    /**
     * Custom event tracking.
     *
     * @param event Event key
     * @param time Optional UNIX time in seconds
     * @param category Event category
     * @param label Event label
     * @param value Event value (sent as string in JSON)
     * @param customFields Optional map merged at top level and under `payload`
     * @param listener Callback
     */
    @Deprecated(
        message = "Use the tracking namespace: sdk.tracking.custom(...).",
        replaceWith = ReplaceWith(
            "tracking.custom(event = event, time = time, category = category, label = label, value = value, customFields = customFields, listener = listener)"
        ),
        level = DeprecationLevel.WARNING
    )
    @Suppress("DEPRECATION")
    fun trackEvent(
        event: String,
        time: Int? = null,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        customFields: Map<String, Any?>? = null,
        listener: OnApiCallbackListener? = null
    ) {
        trackEventManager.trackEvent(
            event = event,
            time = time,
            category = category,
            label = label,
            value = value,
            customFields = customFields,
            listener = listener
        )
    }

    /**
     * Strict purchase tracking (`push`, event = `purchase`).
     *
     * Prefer this over [track] with [TrackEvent.PURCHASE] and manual [Params] assembly.
     */
    @Deprecated(
        message = "Use the tracking namespace: sdk.tracking.purchase(request, listener = listener).",
        replaceWith = ReplaceWith("tracking.purchase(request = request, listener = listener)"),
        level = DeprecationLevel.WARNING
    )
    @Suppress("DEPRECATION")
    fun trackPurchase(
        request: PurchaseTrackingRequest,
        listener: OnApiCallbackListener? = null,
    ) {
        trackEventManager.trackPurchase(request, listener)
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
        "This method will be removed in future versions. Use trackEvent.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "trackEvent(event = event, category = category, label = label, value = value, listener = listener)",
            imports = []
        )
    )
    fun track(
        event: String,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        listener: OnApiCallbackListener? = null
    ) {
        trackEventManager.trackEvent(
            event = event,
            time = null,
            category = category,
            label = label,
            value = value,
            customFields = null,
            listener = listener
        )
    }

    /**
     * Returns the device ID
     *
     * @return String
     */
    fun getDid(): String? = deviceId.orEmpty()

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
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
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
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
    }

    /**
     * Manage subscriptions
     *
     * @param email
     * @param phone
     * @param subscriptions Flags by request key, `true` subscribes and `false` unsubscribes; keys left out
     * keep their current value. Keys: `email_*`, `sms_*`, `web_push_*`, `mobile_push_*`, `telegram_*`,
     * `whatsapp_*`, `max_*` with the `bulk`, `chain` or `transactional` suffix, and `wallet_bulk`.
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
     * @param subscriptions Flags by request key, `true` subscribes and `false` unsubscribes; keys left out
     * keep their current value. Keys: `email_*`, `sms_*`, `web_push_*`, `mobile_push_*`, `telegram_*`,
     * `whatsapp_*`, `max_*` with the `bulk`, `chain` or `transactional` suffix, and `wallet_bulk`.
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
            popupsV3?.onContextChange(SUBSCRIPTION_MANAGE, params.toString())
        } catch (e: JSONException) {
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
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
            popupsV3?.onContextChange(SEGMENTS_FIELD, "$method $params")
        } catch (e: JSONException) {
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
    }

    /**
     * @param data from data notification
     */
    fun notificationReceived(data: Map<String, String>) {
        if (!isSdkInitialized) {
            log(
                LogLevel.WARNING,
                LogCodes.PUSH,
                "notificationReceived() called before SDK.initialize(); ignoring. " +
                    "Initialize SDK.instance in Application.onCreate so push delivery works in a cold process."
            )
            return
        }
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
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
    }

    private fun receiveMessage(data: Map<String, String>) {
        // Track the delivery on this instance's network and fire the deprecated per-instance listener.
        // The process-global listener (Rees46.setOnMessageListener) is dispatched by the router
        // ([Rees46.handlePush] / the [onMessage] companion) instead, so the notification shows even for a
        // shop that has no live instance yet.
        notificationReceived(data = data)
        onMessageListener?.onMessage(data = data.toNotificationData())
    }

    /** Entry point for [Rees46.handlePush]: track a received push on the already-resolved instance. */
    internal fun onPushReceived(data: Map<String, String>) = receiveMessage(data = data)

    companion object {

        var TAG = "SDK"

        /**
         * Optional debug hook for observing all SDK HTTP traffic (requests and responses).
         * Process-global; set it from a debug/QA build to mirror the SDK's network calls.
         * See [NetworkLogger]. Invoked on network threads — keep implementations fast and thread-safe.
         */
        @Volatile
        @JvmStatic
        var networkLogger: NetworkLogger? = null

        private const val SUBSCRIPTION_UNSUBSCRIBE_PRICE =
            "subscriptions/unsubscribe_from_product_price"
        private const val SUBSCRIPTION_UNSUBSCRIBE =
            "subscriptions/unsubscribe_from_product_available"
        private const val SUBSCRIPTION_SUBSCRIBE_PRICE = "subscriptions/subscribe_for_product_price"
        private const val SUBSCRIPTION_SUBSCRIBE = "subscriptions/subscribe_for_product_available"
        private const val SUBSCRIPTION_MANAGE = "subscriptions/manage"
        private const val PERSONALIZATION_SDK = "Personalizatio SDK "
        private const val ANDROID: String = "android"
        private const val BLANK_SEARCH_FIELD = "search/blank"
        private const val SEGMENT_GET_FIELD = "segments/get"
        private const val TRACK_RECEIVED = "track/received"
        private const val SET_PROFILE_FIELD = "profile/set"
        private const val SEGMENT_ID_FIELD = "segment_id"
        private const val SEGMENT_EMAIL_FIELD = "email"
        private const val SEGMENT_PHONE_FIELD = "phone"
        private const val ITEM_IDS_FIELD = "item_ids"
        private const val SEGMENTS_FIELD = "segments"
        private const val SEARCH_FIELD = "search"
        private const val REMOVE_FIELD = "remove"
        private const val CODE_FIELD = "code"
        private const val TYPE_FIELD = "type"
        private const val ADD_FIELD = "add"
        private const val ID_FIELD = "id"
        private const val POPUPS_V3_TEST_MODE = "testmode"

        /**
         * The SDK that receives push callbacks (see [onMessage]): whichever SDK was last passed to
         * [initialize], so the documented `SDK().initialize()` usage routes incoming pushes to that
         * initialized instance (otherwise the push hit a different, lazily-created object that was
         * never initialized). Falls back to a lazily-created instance if a push is delivered before
         * initialize() ran — that instance is guarded and won't crash the host. Backed by
         * [SdkRegistry], which owns the routing state for the coming multi-instance support.
         */
        val instance: SDK
            get() = SdkRegistry.currentOrLazy()

        fun userAgent(): String = userAgent(BuildConfig.FLAVOR, BuildConfig.VERSION_NAME)

        /**
         * The flavor is upper-cased without the device's locale: under Turkish or Azerbaijani the `i`
         * of `personaclick` would become `İ`, which no HTTP header may carry, and every request of the
         * new client would fail.
         */
        internal fun userAgent(flavor: String, version: String): String =
            PERSONALIZATION_SDK + flavor.uppercase(Locale.ROOT) + ", v" + version

        /**
         * Writes [message] to the SDK's log at [LogLevel.DEBUG] (see [Rees46Config.logLevel]).
         *
         * @param message Message
         */
        fun debug(message: String) {
            SdkLogs.log(LogLevel.DEBUG, LogCodes.SDK_LOG, message)
        }

        /**
         * Writes [message] to the SDK's log at [LogLevel.WARNING].
         *
         * @param message Message
         */
        fun warn(message: String?) {
            SdkLogs.log(LogLevel.WARNING, LogCodes.SDK_LOG, message.toString())
        }

        /**
         * Writes [message] to the SDK's log at [LogLevel.ERROR].
         *
         * @param message Error message
         */
        fun error(message: String?) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, message.toString())
        }

        /**
         * Writes [message] and [e] to the SDK's log at [LogLevel.ERROR].
         *
         * @param message Error message
         */
        fun error(message: String?, e: Throwable?) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, message.toString(), throwable = e)
        }

        /**
         * Routes a push payload to the SDK so it is tracked as received and forwarded to the host's
         * [OnMessageListener] for display. Called by the SDK's messaging services.
         *
         * Both providers deliver the payload as a `data` map (title/body/icon/…): this FCM overload
         * unwraps [RemoteMessage.getData], while the map overload is used by [HmsMessagingService]
         * so Huawei data-messages are shown the same way as FCM ones.
         *
         * @param remoteMessage an FCM message
         */
        fun onMessage(remoteMessage: RemoteMessage) {
            onMessage(remoteMessage.data)
        }

        /**
         * Routes a push `data` payload among the currently-live shops. See the [RemoteMessage] overload
         * for the FCM entry point.
         *
         * The payload's `shop_id` names the target, and a single-shop app resolves with no `shop_id`. An
         * unknown `shop_id` — or none while several shops are live — is dropped, not delivered (and
         * tracked as received) against the wrong shop. A push arriving before any `initialize()` has
         * nowhere to route and is likewise dropped.
         *
         * This resolves against live shops only. The SDK's messaging services route through
         * [Rees46.handlePush] instead, which additionally materializes a registered-but-pending shop the
         * push targets — needed for a data-only push to reach a lazily-registered shop in a cold process.
         *
         * @param data the push data payload (title/body/icon/…)
         */
        fun onMessage(data: Map<String, String>) {
            val payloadShopId = data[PreferencesPartition.SHOP_ID_FIELD]
            val liveShopIds = SdkRegistry.shopIds()
            val shopId = PushTargetResolver.resolve(payloadShopId = payloadShopId, liveShopIds = liveShopIds)
            val target = shopId?.let { SdkRegistry.byShopId(it) }
            if (target == null || shopId == null) {
                val reason = if (payloadShopId == null && liveShopIds.size > 1) LogReasons.AMBIGUOUS_SHOP else LogReasons.UNKNOWN_SHOP
                SdkLogs.log(
                    LogLevel.WARNING,
                    LogCodes.PUSH_DROPPED,
                    "onMessage: push dropped — no shop resolves it",
                    payloadShopId,
                    mapOf("reason" to reason, "live" to liveShopIds.size)
                )
                return
            }
            SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_RECEIVED, "Push handed to its shop", shopId, mapOf("event" to "received"))
            // Show via the process-global listener, then track received on the instance.
            SdkRegistry.dispatchMessage(shopId = shopId, data = data.toNotificationData())
            target.receiveMessage(data = data)
        }

        /**
         * Entry point used by the SDK's messaging services to report a token delivered via
         * their `onNewToken` callback. The public manual entry point for hosts that own their
         * messaging service is [setPushToken].
         */
        internal fun onPushTokenReceived(token: String, provider: PushProvider) {
            SdkRegistry.all().forEach { sdk ->
                if (sdk::pushTokenManager.isInitialized) {
                    sdk.pushTokenManager.onTokenReceived(token, provider)
                }
            }
        }
    }
}
