package com.personalization.api.models.profile

/**
 * The customer's profile as the API gives it to the device (`sdk.profile.get`): without the shop's secret
 * key it has no name, email or phone. Which fields come depends on the shop's settings; a field the answer
 * leaves out is null.
 *
 * @property id the customer's id on the server, not the shop's own id ([ProfileUpdateRequest.externalId]).
 * @property hasEmail whether the customer has an email.
 * @property emailRegisteredAt when the shop first got the customer's email: `YYYY-MM-DD HH:MM:SS`, without
 * a time zone.
 * @property computedGender the gender the server worked out.
 * @property boughtSomething whether the customer has bought anything.
 * @property customProperties the profile's own properties the shop set up: a property's name → its value (a
 * string, a number, a boolean, null, a list or a map of these).
 */
data class Profile(
    val id: String? = null,
    val hasEmail: Boolean? = null,
    val emailRegisteredAt: String? = null,
    val gender: Gender? = null,
    val computedGender: Gender? = null,
    val boughtSomething: Boolean? = null,
    val customProperties: Map<String, Any?>? = null,
)

/**
 * The properties `sdk.profile.set` writes: only the fields given change, and a null field is not sent, so
 * its value on the server stays. An empty string erases [externalId], [telegramId], [birthday], [fbId] and
 * [vkId], and leaves [location], [firstName] and [lastName] empty; [age] 0 erases the age. The server does
 * not erase [email], [phone] or [loyaltyId] with an empty string, so an empty one is refused. [gender] has
 * no empty value: `set` cannot erase it. A customer's id of another customer ([email], [phone],
 * [externalId], [loyaltyId], [telegramId]) moves the device to that customer's profile: that is how a
 * sign-in and a change of user on one device work.
 *
 * The call fails with `invalidArgument`, before any request, when nothing is given; when [email], [phone]
 * or [loyaltyId] is empty; when [birthday] (a kid's too) is not an existing day `YYYY-MM-DD` of the years
 * 1000 to 2999 — the server would ignore it in silence —, the empty [birthday] that erases it aside (a
 * kid's may not be empty); when [age] is negative; when a key of [customProperties] is not of `a-z`, `0-9`
 * and `_`, or is a field of this request or of the SDK (`email`, `first_name`, `auto`, `did`, …); or when
 * one of its values is not a string, a number, a boolean, null, or a list or a map of these.
 *
 * @property externalId the customer's id in the shop's own system; sent as both `id` and `external_id`.
 * @property loyaltyId the customer's id in an outside loyalty program.
 * @property birthday `YYYY-MM-DD`: a date, not a moment, so it does not move between time zones.
 * @property location the customer's city: a location's id from the product feed.
 * @property cars sent as `auto`.
 * @property customProperties the profile's own properties the shop set up in its account; each goes as a
 * field of its own. A date goes as a string `YYYY-MM-DD`.
 */
data class ProfileUpdateRequest(
    val email: String? = null,
    val phone: String? = null,
    val externalId: String? = null,
    val loyaltyId: String? = null,
    val telegramId: String? = null,
    val fbId: String? = null,
    val vkId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val gender: Gender? = null,
    val birthday: String? = null,
    val age: Int? = null,
    val location: String? = null,
    val kids: List<ProfileKid>? = null,
    val cars: List<ProfileCar>? = null,
    val customProperties: Map<String, Any?>? = null,
)

/** A customer's kid; [birthday] is `YYYY-MM-DD`. */
data class ProfileKid(
    val gender: Gender? = null,
    val birthday: String? = null,
)

/** A customer's car; [vds], as the API's documentation says, is the VIN. */
data class ProfileCar(
    val brand: String? = null,
    val model: String? = null,
    val vds: String? = null,
)
