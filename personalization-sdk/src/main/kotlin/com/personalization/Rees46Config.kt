package com.personalization

/**
 * Configuration for one SDK instance (one shop).
 *
 * The unified initialization input shared by [Rees46.initialize] and [Rees46.register]. Mirrors
 * the parameters of the legacy `SDK.initialize`, minus the Android `Context` (passed separately); its
 * `preferencesKey` is [storageKey] here. Storage is partitioned per [shopId] automatically — see
 * [PreferencesPartition].
 *
 * @property shopId the shop key; also the partition key and the id used by [Rees46.instance].
 * @property apiDomain API host the instance talks to (a region points here).
 * @property stream traffic stream label sent with every request.
 * @property autoSendPushToken whether the SDK registers push tokens with the backend on its own.
 * @property needReInitialization forces a fresh `init` request instead of reusing stored session data.
 * @property addTrailingSlash whether a trailing slash is appended to [apiDomain] when building URLs.
 * @property tag log tag for this instance.
 * @property enableAutoPopupPresentation whether server popups are shown in the activity on screen
 * while no [SDK.popupPresentationListener] is set.
 * @property logLevel how much this shop's instance writes to the log; [LogLevel.WARNING] by default.
 * Messages that pass it also reach [Rees46.setLogHandler].
 * @property coreV2 runs the instance on the new core; off by default. The process has one core: the
 * first [Rees46.initialize] or [Rees46.register] decides it, and an instance that asks for the other
 * one runs on the process's and logs `core_flag_mismatch`. The new core differs in:
 * - its network client: requests go through OkHttp, a request without a network fails at once,
 *   `release()` cancels the instance's requests, and a failure without an answer is `onError(-1)` (no
 *   connection used to be `504`), as is a `2xx` whose body is not JSON (it used to succeed empty);
 * - its `/init`: repeated with growing pauses until it gets an answer; a shop that answers `402`,
 *   `403`, `404` or another `4xx` is refused ([com.personalization.core.SdkState.REFUSED]) until a
 *   later launch's `/init` succeeds, and every request of a refused shop fails with `onError(402)`;
 * - a request waits until the instance is ready, within its own read timeout, instead of going out
 *   without a `did`: a new device's first requests fail with `onError(-1)` if `/init` does not answer
 *   in time;
 * - [needReInitialization] really gives the device a new `did`; [stream] goes with `/init` too; the
 *   advertising id is read and sent only with [sendAdvertisingId];
 * - its session: the `sid` is the server's, and a session two hours without events ends — the next
 *   request or event sends `/init` first; requests with a result no longer extend it;
 * - its event queue: tracking events (`/push`, `/push/custom`, `track/stories`, `popup/showed`) are
 *   kept on disk and sent in order, repeated after failures and after a restart, for up to 7 days;
 *   their listeners hear the server's answer once the event went, or the error that dropped it, and an
 *   empty id is refused at once; [SDK.flush] sends the queue now; the stored tracking source goes on
 *   `/push` only, and a popup in an event's answer is shown only within 10 s of the event and in the
 *   foreground;
 * - cookies: the new core's requests never send or keep a cookie. The legacy core's requests, and those
 *   of the stories v3 and popups v3 clients on either core, follow the app's global
 *   `java.net.CookieHandler`: an app that installs one (`CookieHandler.setDefault`) has them keep the
 *   cookies the server sets, the API's year-long `did` cookie among them, and send them back.
 * @property deviceId a `did` the app already keeps, from another SDK or an earlier version of it: taken
 * only while the shop has no `did` of its own, and sent with the next `/init`. Ignored with
 * [needReInitialization].
 * @property sendAdvertisingId sends the advertising id with `/init`; off by default, and then the new
 * core does not read it. The legacy core keeps sending it as before, whatever this says.
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
    val coreV2: Boolean = false,
    val storageKey: String? = null,
    val deviceId: String? = null,
    val sendAdvertisingId: Boolean = false,
)
