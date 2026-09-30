package com.personalization

/**
 * Configuration for one SDK instance (one shop).
 *
 * The unified initialization input shared by [Rees46.initialize] and [Rees46.registerShops]. Mirrors
 * the parameters of the legacy `SDK.initialize`, minus the Android `Context` (passed separately) and
 * the preferences key (storage is partitioned per [shopId] automatically — see [PreferencesPartition]).
 *
 * @property shopId the shop key; also the partition key and the id used by [Rees46.getInstance].
 * @property apiDomain API host the instance talks to (a region points here).
 * @property stream traffic stream label sent with every request.
 * @property autoSendPushToken whether the SDK registers push tokens with the backend on its own.
 * @property needReInitialization forces a fresh `init` request instead of reusing stored session data.
 * @property addTrailingSlash whether a trailing slash is appended to [apiDomain] when building URLs.
 * @property tag log tag for this instance.
 * @property enableAutoPopupPresentation whether server popups are shown in the activity on screen
 * while no [SDK.popupPresentationListener] is set.
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
    val enableAutoPopupPresentation: Boolean = true
)
