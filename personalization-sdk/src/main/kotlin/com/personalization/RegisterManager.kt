package com.personalization

import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.core.SdkState
import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.push.PushTokenManager
import com.personalization.sdk.data.mappers.SdkInitializationMapper.mapToSdkInitResponse
import com.personalization.sdk.domain.usecases.network.ExecuteQueueTasksUseCase
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class RegisterManager @Inject constructor(
    private val updateUserSettingsValueUseCase: UpdateUserSettingsValueUseCase,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val executeQueueTasksUseCase: ExecuteQueueTasksUseCase,
    private val inAppNotificationManager: InAppNotificationManager,
    private val pushTokenManager: PushTokenManager
) {
    @Volatile
    private var autoSendPushToken: Boolean = false

    /**
     * Called once `/init` has answered and the did and session are in place, with `popups_v3` of
     * the answer — popups v3 start from it. Also called with false when `/init` gave up for a
     * device that already has a did, so a host that does without the flag still gets its popups.
     */
    internal var onInitFinished: ((popupsV3: Boolean) -> Unit)? = null

    /** Extra query parameters of `/init` (popups v3 test mode). */
    internal var extraInitParams: (() -> Map<String, String>)? = null

    /**
     * The instance's state on the legacy path: [SdkState.READY] once the device has its did and session
     * (at once for a known device, after `/init` for a new one), [SdkState.INITIALIZING] before. A
     * failed `/init` leaves it where it is; the legacy path is never retrying or refused.
     */
    internal var onStateChange: ((SdkState) -> Unit)? = null

    /**
     * Called when an answer to `/init` replaced the `did` the device had (`needReInitialization`) with
     * another one, before the push tokens are asked for again: the token's week was the old `did`'s.
     */
    internal var onDidReplaced: (() -> Unit)? = null

    /** Set by [stop]: nothing more goes out and no answer is taken. */
    @Volatile
    private var stopped = false

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    /**
     * Which start the `/init` in flight belongs to. [beginRestart] begins a new one, and an answer to an
     * `/init` of an earlier start is dropped: it carries the forgotten customer's did. Each restart
     * gets a number of its own.
     */
    private val generation = AtomicInteger(0)

    /**
     * Held while an answer writes the did and the session, and while a restart begins: an answer
     * either lands before the restart (and the wipe after it removes what it wrote) or sees the new
     * start and writes nothing.
     */
    private val answerLock = Any()

    private val isTestDevice: Boolean
        get() = isPreLaunchDevice(contentResolver)

    fun initialize(
        context: Context,
        contentResolver: ContentResolver,
        autoSendPushToken: Boolean,
        needReInitialization: Boolean = false
    ) {
        this.context = context
        this.contentResolver = contentResolver
        this.autoSendPushToken = autoSendPushToken

        val did = getUserSettingsValueUseCase.getDid()
        when {
            did.isEmpty() || needReInitialization -> {
                state(SdkState.INITIALIZING)
                initializeNewDevice()
            }
            else -> {
                initializeSdk(null)
                state(SdkState.READY)
                // A known device asks the server too, on every launch as the iOS and React Native SDKs
                // do: the /init answer is where the server hands out the popup for the visit. Only the
                // popup is taken from it — the did and the session stay as stored, so nobody is
                // re-identified on upgrade.
                init(knownDevice = true)
            }
        }
    }

    /**
     * Begins a new start before `deleteUserCredentials()` wipes the did: from now on an answer to an
     * `/init` sent before is dropped. Returns the start for [restartAsNewDevice].
     */
    internal fun beginRestart(): Int = synchronized(answerLock) {
        // The did is about to go: requests and the state wait for the next one from now on.
        state(SdkState.INITIALIZING)
        generation.incrementAndGet()
    }

    /**
     * Starts over as a device the server has not seen, once the did is wiped: `/init` goes out without
     * a did and the server hands out a new one, which the requests waiting for a did then carry. The
     * push token waits for it too: sent before, it would be registered without a did. Nothing happens
     * before [initialize], or when a later [beginRestart] took over from [start].
     */
    internal fun restartAsNewDevice(start: Int) {
        if (stopped || !::context.isInitialized || start != generation.get()) return
        state(SdkState.INITIALIZING)
        initializeSdk(seance = null, releaseQueue = false, fetchPushTokens = false)
        init(start = start)
    }

    private fun initializeNewDevice() {
        // Do NOT seed the did with a hardware id (Settings.Secure.ANDROID_ID). Like the iOS, React
        // Native and web SDKs, send an empty did on the first /init and let the backend assign one —
        // handleInitSuccess persists the server's did. Keeps device identity consistent across
        // platforms and off Google's discouraged hardware-id APIs. Existing installs already hold a
        // non-empty did, so they keep it (this branch only runs when did is empty).
        // The requests waiting for a did stay queued: released now, they would go out with an empty
        // did. handleInitSuccess releases them once the server's did is stored.
        initializeSdk(seance = null, releaseQueue = false)
        init()
    }

    private fun init(knownDevice: Boolean = false, start: Int = generation.get()) {
        if (isTestDevice) {
            log(LogLevel.INFO, LogCodes.INIT_SKIPPED, "/init is not sent on a Google Play pre-launch report device", mapOf("reason" to LogReasons.PRE_LAUNCH_REPORT))
            return
        }

        sendInitRequestWithRetry(knownDevice = knownDevice, start = start)
    }

    /**
     * The app took an instance a push started without its tokens: the providers' tokens go from now on with
     * [autoSendPushToken], once the device has its `did` — at once when it has one, otherwise from the answer
     * to `/init`, which asks the providers with it.
     */
    internal fun sendTokensFromNow(autoSendPushToken: Boolean) {
        this.autoSendPushToken = autoSendPushToken
        if (stopped || !::context.isInitialized) return
        if (getUserSettingsValueUseCase.getDid().isNotEmpty()) {
            pushTokenManager.initialize(context = context, autoSendPushToken = autoSendPushToken)
        }
    }

    /** The instance was released: no `/init` goes out any more and no answer is taken. */
    internal fun stop() {
        synchronized(answerLock) {
            stopped = true
            generation.incrementAndGet()
        }
    }

    private fun state(state: SdkState) {
        if (!stopped) onStateChange?.invoke(state)
    }

    private fun sendInitRequestWithRetry(attempt: Int = 0, knownDevice: Boolean = false, start: Int) {
        if (stopped || start != generation.get()) return
        try {
            val params = buildInitParams()
            sendNetworkMethodUseCase.get(
                method = GET_INIT_METHOD,
                params = params,
                listener = object : OnApiCallbackListener() {
                    override fun onSuccess(response: JSONObject?) {
                        if (start == generation.get()) handleInitSuccess(response, knownDevice, start)
                    }

                    // The answer's body stays out of the log: a server often repeats the request's data in it.
                    override fun onError(code: Int, msg: String?) {
                        if (start == generation.get()) handleInitError(code, attempt, knownDevice, start)
                    }
                }
            )
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "/init could not be built", throwable = e)
        }
    }

    private fun buildInitParams(): JSONObject {
        return JSONObject().apply {
            put(PARAM_TZ, (TimeZone.getDefault().rawOffset / 3600000.0).toInt().toString())
            put(PARAM_STREAM, PARAM_ANDROID)
            extraInitParams?.invoke()?.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun handleInitSuccess(response: JSONObject?, knownDevice: Boolean, start: Int) {
        val errorHandler = JsonResponseErrorHandler(
            tag = TAG,
            response = response
        )

        if (!errorHandler.validateResponse()) {
            return
        }
        log(LogLevel.INFO, LogCodes.INIT_SUCCEEDED, "/init succeeded")
        val initResponse = response?.mapToSdkInitResponse()
        val popUpData = initResponse?.popupDto
        if (popUpData != null) {
            inAppNotificationManager.shopPopUp(popupDto = popUpData)
        }
        val popupsV3 = initResponse?.popupsV3 == true

        // A known device is already set up from storage: its did and session stay as they are.
        if (knownDevice) {
            notifyInitFinished(popupsV3)
            return
        }

        val did = errorHandler.getRequiredField(fieldName = "did") ?: return
        val seance = errorHandler.getRequiredField(fieldName = "seance") ?: return

        synchronized(answerLock) {
            // A restart that began while this answer was on its way: the did belongs to the forgotten customer.
            if (start != generation.get()) return
            val before = getUserSettingsValueUseCase.getDid()
            updateUserSettingsValueUseCase.updateDid(did)
            updateUserSettingsValueUseCase.updateSid(value = seance)
            // The token's week was the old did's: the token goes to this one when the providers are asked below.
            if (before.isNotEmpty() && before != did) {
                try {
                    onDidReplaced?.invoke()
                } catch (e: Exception) {
                    log(LogLevel.ERROR, LogCodes.SDK_LOG, "The push token's week could not be forgotten", throwable = e)
                }
            }
            // Under the lock: a restart beginning right after this cannot be overtaken by it.
            state(SdkState.READY)
        }
        sessionStarted(releaseQueue = true, fetchPushTokens = true)
        notifyInitFinished(popupsV3)
    }

    private fun notifyInitFinished(popupsV3: Boolean) {
        try {
            onInitFinished?.invoke(popupsV3)
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.POPUPS_V3, "Popups v3 failed to start", throwable = e)
        }
    }

    private fun handleInitError(code: Int, attempt: Int, knownDevice: Boolean, start: Int) {
        if (code in 500..Int.MAX_VALUE || code <= 0) {
            if (attempt < MAX_INIT_RETRIES) {
                log(
                    LogLevel.WARNING,
                    LogCodes.INIT_RETRY,
                    "/init failed, retrying",
                    mapOf("status" to code, "attempt" to attempt + 1)
                )
                CoroutineScope(Dispatchers.Main).launch {
                    delay(RETRY_DELAY_MILLISECONDS * (attempt + 1))
                    sendInitRequestWithRetry(attempt + 1, knownDevice, start)
                }
            } else {
                log(
                    LogCodes.failureLevel(code),
                    LogCodes.INIT_FAILED,
                    "/init failed after all retries",
                    mapOf("status" to code, "attempts" to attempt + 1)
                )
                if (knownDevice) notifyInitFinished(popupsV3 = false)
            }
        } else {
            if (knownDevice) notifyInitFinished(popupsV3 = false)
            // A 4xx is a failed /init, not a refused shop: the legacy path has no refused state.
            log(LogCodes.failureLevel(code), LogCodes.INIT_FAILED, "/init failed", mapOf("status" to code))
        }
    }

    /** The level is checked first, so a line no shop writes reads no preferences. */
    private fun log(
        level: LogLevel,
        code: String,
        message: String,
        context: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        if (!SdkLogs.isLoggable(level)) return
        val shopId = runCatching { getUserSettingsValueUseCase.getShopId() }.getOrNull()?.takeIf { it.isNotEmpty() }
        SdkLogs.log(level, code, message, shopId, context, throwable)
    }

    /**
     * Starts the session; [releaseQueue] sends the requests that waited for a did, which must be stored
     * by now, and [fetchPushTokens] asks the providers for their tokens.
     */
    private fun initializeSdk(seance: String?, releaseQueue: Boolean = true, fetchPushTokens: Boolean = true) {
        val finalSeance = seance ?: generateOrRetrieveSeance()
        updateUserSettingsValueUseCase.updateSid(value = finalSeance)
        sessionStarted(releaseQueue, fetchPushTokens)
    }

    private fun sessionStarted(releaseQueue: Boolean, fetchPushTokens: Boolean) {
        if (releaseQueue) executeQueueTasksUseCase.invoke()
        if (fetchPushTokens) pushTokenManager.initialize(context = context, autoSendPushToken = autoSendPushToken)
    }

    private fun generateOrRetrieveSeance(): String {
        val storedSeance = getUserSettingsValueUseCase.getSid()
        val lastActTime = getUserSettingsValueUseCase.getSidLastActTime()
        return if (storedSeance.isNotEmpty() && lastActTime >= System.currentTimeMillis() - SESSION_CODE_EXPIRE) {
            storedSeance
        } else {
            alphanumeric()
        }
    }

    private fun alphanumeric(): String = SecureRandom().let { random ->
        (1..ALPHANUMERIC_VALUE).map {
            SOURCE[random.nextInt(SOURCE.length)]
        }.joinToString("")
    }

    companion object {
        private const val TAG = "RegisterManager"

        /** A Google Play pre-launch report device (Firebase Test Lab): `/init` is not sent there. */
        internal fun isPreLaunchDevice(contentResolver: ContentResolver): Boolean = try {
            IS_TEST_DEVICE_FIELD == Settings.System.getString(contentResolver, FIREBASE_TEST_LAB)
        } catch (e: Exception) {
            false
        }

        private const val SOURCE = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val FIREBASE_TEST_LAB = "firebase.test.lab"
        private const val SESSION_CODE_EXPIRE = 2 * 3600 * 1000L
        private const val RETRY_DELAY_MILLISECONDS = 1000L
        private const val IS_TEST_DEVICE_FIELD = "true"
        private const val PARAM_ANDROID = "android"
        private const val GET_INIT_METHOD = "init"
        private const val ALPHANUMERIC_VALUE = 10
        private const val PARAM_STREAM = "stream"
        private const val MAX_INIT_RETRIES = 5
        private const val PARAM_TZ = "tz"
    }
}
