package com.personalization.api.models.tracking

import com.personalization.Params
import com.personalization.core.BridgeApi
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.sdk.domain.models.RecommendedBy

/**
 * One product line in a tracking event.
 *
 * [quantity] is the domain name for the line quantity; on the wire it is sent as `amount` —
 * the field the REES46 API has always consumed.
 */
data class TrackingItem(
    val id: String,
    val quantity: Int = 1,
    val price: Double? = null,
    val fashionSize: String? = null,
)

/**
 * Where a tracked action came from — a recommender block, a search result, a story.
 *
 * The Flutter and React Native bridges also make one from the wire value of the contract's source type with
 * [fromWire], which takes the contract's `slider` and `agent_search` before [TrackingSourceType] has cases for
 * them (its next major release): such a source goes as its wire value wherever a source goes — an event's
 * `source` on `tracking` and `tracking.queued`, and [com.personalization.api.managers.TrackingApi.setSource].
 */
data class TrackingSource(
    val type: TrackingSourceType,
    val code: String,
) {
    /** The wire value of a source [fromWire] made for a type [TrackingSourceType] has no case for; null otherwise. */
    private var rawType: String? = null

    /** What goes in `recommended_by` (an event) or `source.from` (`setSource`): the raw type, or [type]'s value. */
    internal val wire: String get() = rawType ?: type.value

    /**
     * The wire value of the source's type, as the request carries it: `slider` or `agent_search` for a source
     * [fromWire] made of them, [type]'s value otherwise. Hidden from Java.
     */
    @BridgeApi
    @get:JvmSynthetic
    val wireType: String get() = wire

    // Written out so that a source of a raw type is not equal to the placeholder [type] it carries.
    override fun equals(other: Any?): Boolean =
        this === other || other is TrackingSource && type == other.type && code == other.code && rawType == other.rawType

    override fun hashCode(): Int = 31 * (31 * type.hashCode() + code.hashCode()) + (rawType?.hashCode() ?: 0)

    override fun toString(): String = "TrackingSource(type=${rawType ?: type}, code=$code)"

    companion object {
        /** The contract's source types [TrackingSourceType] has no case for until its next major release. */
        private val RAW_TYPES = setOf("slider", "agent_search")

        /**
         * For the Flutter and React Native bridges: the source of the contract's type [type] — its wire value,
         * one of `dynamic`, `chain`, `bulk`, `transactional`, `instant_search`, `full_search`, `stories`,
         * `web_push_digest`, `slider`, `agent_search` (contract `tracking`, `TrackingSourceType`).
         *
         * A value [TrackingSourceType] has a case for gives the source of that case. `slider` and `agent_search`
         * give a source that goes as that value; its [type] is [TrackingSourceType.DYNAMIC], a placeholder until
         * the major adds their cases — read [wireType] — and `copy()` of it gives a `dynamic` one. Any other
         * value, an empty one among them, is refused: [SdkException] with [SdkError.InvalidArgument]. As an
         * event's own source the event refuses `transactional` and `web_push_digest` as for [TrackingSourceType].
         * Hidden from Java.
         */
        @BridgeApi
        @JvmSynthetic
        fun fromWire(type: String, code: String): TrackingSource {
            TrackingSourceType.entries.firstOrNull { it.value == type }?.let { return TrackingSource(it, code) }
            if (type !in RAW_TYPES) throw SdkException(SdkError.InvalidArgument("source type \"$type\" is not one of the contract's"))
            return TrackingSource(TrackingSourceType.DYNAMIC, code).also { it.rawType = type }
        }
    }
}

/**
 * The tool an event is attributed to (`recommended_by` on the wire).
 *
 * Mirrors iOS `TrackingSourceType` case for case, so the same source means the same thing on both
 * platforms. Kept separate from the released [Params.RecommendedBy.TYPE], which is part of the public
 * `PurchaseTrackingRequest` surface: adding a constant there would break any host with an exhaustive
 * `when` over it. The wire value travels as a raw string instead — see `Params.putRawRecommendedBy`.
 */
enum class TrackingSourceType(val value: String) {
    DYNAMIC("dynamic"),
    CHAIN("chain"),
    BULK("bulk"),

    /** Only for `tracking.setSource`: as an event's own source it is `invalidArgument` (see `TrackingApi`). */
    TRANSACTIONAL("transactional"),
    INSTANT_SEARCH("instant_search"),
    FULL_SEARCH("full_search"),
    STORIES("stories"),

    /** Only for `tracking.setSource`: as an event's own source it is `invalidArgument` (see `TrackingApi`). */
    WEB_PUSH_DIGEST("web_push_digest"),
    ;

    internal fun toDomainType(): RecommendedBy.TYPE = when (this) {
        DYNAMIC -> RecommendedBy.TYPE.RECOMMENDATION
        CHAIN -> RecommendedBy.TYPE.TRIGGER
        BULK -> RecommendedBy.TYPE.BULK
        TRANSACTIONAL -> RecommendedBy.TYPE.TRANSACTIONAL
        INSTANT_SEARCH -> RecommendedBy.TYPE.INSTANT_SEARCH
        FULL_SEARCH -> RecommendedBy.TYPE.FULL_SEARCH
        STORIES -> RecommendedBy.TYPE.STORIES
        WEB_PUSH_DIGEST -> RecommendedBy.TYPE.WEB_PUSH_DIGEST
    }
}

/** Adds this source to [params] as `recommended_by` plus its code field. */
internal fun Params.putSource(source: TrackingSource): Params =
    putRawRecommendedBy(source.wire, source.code)
