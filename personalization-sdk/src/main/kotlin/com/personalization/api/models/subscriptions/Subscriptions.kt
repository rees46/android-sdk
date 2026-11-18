package com.personalization.api.models.subscriptions

/**
 * What `sdk.subscriptions.manage` changes: `true` subscribes, `false` unsubscribes, null leaves a flag as
 * it is and is not sent. A request without a single flag fails with `invalidArgument`, as does a contact
 * that is an empty string. The kinds of mailings: `bulk` — mass mailings, `chain` — triggered ones,
 * `transactional` — service ones.
 *
 * A contact in the request applies the change to the contact's owner. Pass a contact only when it is the
 * customer's own.
 *
 * @property externalId the customer's id in the shop's own system.
 * @property loyaltyId the customer's id in an outside loyalty program.
 */
data class SubscriptionsUpdateRequest(
    val email: String? = null,
    val phone: String? = null,
    val externalId: String? = null,
    val loyaltyId: String? = null,
    val telegramId: String? = null,
    val emailBulk: Boolean? = null,
    val emailChain: Boolean? = null,
    val emailTransactional: Boolean? = null,
    val smsBulk: Boolean? = null,
    val smsChain: Boolean? = null,
    val smsTransactional: Boolean? = null,
    val webPushBulk: Boolean? = null,
    val webPushChain: Boolean? = null,
    val webPushTransactional: Boolean? = null,
    val mobilePushBulk: Boolean? = null,
    val mobilePushChain: Boolean? = null,
    val mobilePushTransactional: Boolean? = null,
    val telegramBulk: Boolean? = null,
    val telegramChain: Boolean? = null,
    val telegramTransactional: Boolean? = null,
    val whatsappBulk: Boolean? = null,
    val whatsappChain: Boolean? = null,
    val whatsappTransactional: Boolean? = null,
    val maxBulk: Boolean? = null,
    val maxChain: Boolean? = null,
    val maxTransactional: Boolean? = null,
    val walletBulk: Boolean? = null,
    val walletChain: Boolean? = null,
    val walletTransactional: Boolean? = null,
)

/**
 * The customer's subscriptions on every channel (`sdk.subscriptions.check`). A flag the answer leaves out is
 * false.
 *
 * @property exists whether the customer is in the shop's customer base.
 * @property emailBulk and the other 23 flags: whether the customer has the channel's contact and is
 * subscribed to the kind of mailings.
 * @property hasWebPush whether the customer has a working web push token (`web_push`).
 * @property hasMobilePush whether the customer has a working mobile push token (`mobile_push`).
 * @property emailSuppressed the email is blocked: its subscriptions cannot change.
 */
data class SubscriptionStatus(
    val exists: Boolean = false,
    val emailBulk: Boolean = false,
    val emailChain: Boolean = false,
    val emailTransactional: Boolean = false,
    val smsBulk: Boolean = false,
    val smsChain: Boolean = false,
    val smsTransactional: Boolean = false,
    val webPushBulk: Boolean = false,
    val webPushChain: Boolean = false,
    val webPushTransactional: Boolean = false,
    val mobilePushBulk: Boolean = false,
    val mobilePushChain: Boolean = false,
    val mobilePushTransactional: Boolean = false,
    val telegramBulk: Boolean = false,
    val telegramChain: Boolean = false,
    val telegramTransactional: Boolean = false,
    val whatsappBulk: Boolean = false,
    val whatsappChain: Boolean = false,
    val whatsappTransactional: Boolean = false,
    val maxBulk: Boolean = false,
    val maxChain: Boolean = false,
    val maxTransactional: Boolean = false,
    val walletBulk: Boolean = false,
    val walletChain: Boolean = false,
    val walletTransactional: Boolean = false,
    val hasWebPush: Boolean = false,
    val hasMobilePush: Boolean = false,
    val emailInvalid: Boolean = false,
    val emailBlacklisted: Boolean = false,
    val emailBounced: Boolean = false,
    val emailSuppressed: Boolean = false,
    val emailConfirmed: Boolean = false,
)

/**
 * The variant of a product a back-in-stock subscription waits for. Properties with no field given are not
 * sent.
 *
 * @property fashionSize the size; the server reads it in lower case.
 */
data class BackInStockProperties(
    val fashionSize: String? = null,
    val fashionColor: String? = null,
    val barcode: String? = null,
)
