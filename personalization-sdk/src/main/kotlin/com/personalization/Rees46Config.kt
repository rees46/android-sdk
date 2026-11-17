package com.personalization

import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs

/**
 * Configuration for one SDK instance (one shop).
 *
 * The unified initialization input shared by [Rees46.initialize] and [Rees46.register]. Mirrors
 * the parameters of the legacy `SDK.initialize`, minus the Android `Context` (passed separately); its
 * `preferencesKey` is [storageKey] here. Storage is partitioned per [shopId] automatically — see
 * [PreferencesPartition].
 *
 * @property shopId the shop key; also the partition key and the id used by [Rees46.instance].
 * @property apiDomain API host the instance talks to (a region points here). The SDK's manifest does not
 * allow cleartext traffic: a host given with `http://`, like images and videos of stories, popups and
 * pushes served over `http://`, needs the app's own `android:usesCleartextTraffic="true"` or network
 * security config.
 * @property stream traffic stream label sent with every request.
 * @property autoSendPushToken whether the SDK registers push tokens with the backend on its own.
 * @property needReInitialization forces a fresh `init` request instead of reusing stored session data.
 * @property addTrailingSlash whether a trailing slash is appended to [apiDomain] when building URLs.
 * @property tag log tag for this instance.
 * @property enableAutoPopupPresentation whether server popups are shown in the activity on screen
 * while no [SDK.popupPresentationListener] is set.
 * @property logLevel how much this shop's instance writes to the log; [LogLevel.WARNING] by default.
 * Messages that pass it also reach [Rees46.setLogHandler].
 * @property deviceId a `did` the app already keeps, from another SDK or an earlier version of it: taken
 * only while the shop has no `did` of its own, and sent with the next `/init`. Ignored with
 * [needReInitialization].
 * @property sendAdvertisingId sends the advertising id with `/init`; off by default, and then the new
 * core does not read it. The legacy core keeps sending it as before, whatever this says. The SDK's
 * manifest does not declare `com.google.android.gms.permission.AD_ID`: on Android 13 and later Google
 * Play services give an app without it a zeroed id, so an app that wants the advertising id sent
 * declares the permission in its own manifest.
 * @property storageKey a prefix for the shop's storage, when the app needs its own split of it: the
 * shop's `SharedPreferences` file becomes `<storageKey>_personalization_sdk_<shopId>` instead of
 * `personalization_sdk_<shopId>`. A file named exactly [storageKey] (the whole store of
 * `SDK.initialize(preferencesKey = …)` in earlier releases) is read into it once and left as it is,
 * so an app moving its `preferencesKey` here keeps its customers; only the SDK's own keys are taken
 * from that file, which may be the app's own preferences. Set on an install that already runs the SDK
 * without it, or changed later, it opens a new, empty partition: every customer becomes a new device
 * with a new `did`, unless the value equals the `preferencesKey` the app passed before. The
 * device-level data (A/B group, push token, advertising id) is the app's and has no prefix. Android's
 * Auto Backup restores these files on the user's new device, the `did` among them; see
 * [SDK.deleteUserCredentials] for the files an app excludes from its backup rules to avoid that.
 *
 * Nothing here picks the core: [Rees46.initialize] and [Rees46.register] ask for the legacy one, and
 * the new core is for the SDK's Flutter and React Native bridges, which start it with
 * [Rees46.initializeBridge]. The process runs one core: the first of them decides it.
 *
 * Cookies: the legacy core's requests, and those of the stories v3 and popups v3 clients, follow the
 * app's global `java.net.CookieHandler`: an app that installs one (`CookieHandler.setDefault`) has them
 * keep the cookies the server sets, the API's year-long `did` cookie among them, and send them back.
 *
 * Constructors are overloaded for Java: every trailing parameter with a default may be left out, so
 * Java code written against the seven-argument constructor keeps compiling as parameters are added.
 */
data class Rees46Config @JvmOverloads constructor(
    val shopId: String,
    val apiDomain: String = "api.rees46.ru",
    val stream: String = "android",
    val autoSendPushToken: Boolean = true,
    val needReInitialization: Boolean = false,
    val addTrailingSlash: Boolean = true,
    val tag: String = "SDK",
    val enableAutoPopupPresentation: Boolean = true,
    val logLevel: LogLevel = LogLevel.WARNING,
    val storageKey: String? = null,
    val deviceId: String? = null,
    val sendAdvertisingId: Boolean = false,
) {

    /**
     * The constructor of the releases with `coreV2`, for apps built against them: `coreV2` is ignored,
     * and `true` is logged as `invalid_config` (`bridge_only`). Source that passes it no longer compiles.
     */
    @Deprecated(message = BRIDGE_ONLY_CORE, level = DeprecationLevel.HIDDEN)
    constructor(
        shopId: String,
        apiDomain: String = "api.rees46.ru",
        stream: String = "android",
        autoSendPushToken: Boolean = true,
        needReInitialization: Boolean = false,
        addTrailingSlash: Boolean = true,
        tag: String = "SDK",
        enableAutoPopupPresentation: Boolean = true,
        logLevel: LogLevel = LogLevel.WARNING,
        coreV2: Boolean = false,
        storageKey: String? = null,
        deviceId: String? = null,
        sendAdvertisingId: Boolean = false,
    ) : this(
        shopId, apiDomain, stream, autoSendPushToken, needReInitialization, addTrailingSlash, tag,
        enableAutoPopupPresentation, logLevel, storageKey, deviceId, sendAdvertisingId,
    ) {
        if (coreV2) ignoredCoreV2(shopId)
    }

    /** The `copy` of the releases with `coreV2`, for apps built against them; `coreV2` is ignored as above. */
    @Deprecated(message = BRIDGE_ONLY_CORE, level = DeprecationLevel.HIDDEN)
    fun copy(
        shopId: String = this.shopId,
        apiDomain: String = this.apiDomain,
        stream: String = this.stream,
        autoSendPushToken: Boolean = this.autoSendPushToken,
        needReInitialization: Boolean = this.needReInitialization,
        addTrailingSlash: Boolean = this.addTrailingSlash,
        tag: String = this.tag,
        enableAutoPopupPresentation: Boolean = this.enableAutoPopupPresentation,
        logLevel: LogLevel = this.logLevel,
        coreV2: Boolean = false,
        storageKey: String? = this.storageKey,
        deviceId: String? = this.deviceId,
        sendAdvertisingId: Boolean = this.sendAdvertisingId,
    ): Rees46Config {
        if (coreV2) ignoredCoreV2(shopId)
        return copy(
            shopId, apiDomain, stream, autoSendPushToken, needReInitialization, addTrailingSlash, tag,
            enableAutoPopupPresentation, logLevel, storageKey, deviceId, sendAdvertisingId,
        )
    }

    private companion object {
        const val BRIDGE_ONLY_CORE = "Rees46Config.coreV2 is gone: the new core is for the SDK's Flutter and " +
            "React Native bridges, which start it with Rees46.initializeBridge."

        fun ignoredCoreV2(shopId: String) = SdkLogs.log(
            LogLevel.WARNING,
            LogCodes.INVALID_CONFIG,
            "Rees46Config.coreV2 is ignored: the new core is the SDK's bridges', and this config asks for the legacy one",
            shopId,
            mapOf("reason" to LogReasons.BRIDGE_ONLY),
        )
    }
}
