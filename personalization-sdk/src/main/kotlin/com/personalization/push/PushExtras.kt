package com.personalization.push

import android.content.Intent
import android.os.Bundle

/**
 * The push an intent of a notification the SDK drew carries — its dismissal, the buttons of its carousel —
 * under one extra: the push's fields as they came, its `shop_id` among them. A later process, started by
 * the intent itself, reports the dismissal for the push's own shop and hands the callbacks the whole push.
 *
 * A push of FCM or HMS holds at most 4 KB, so it fits as it is. One larger than [MAX_CHARS] (built by the
 * app) keeps only what routes it and what a callback shows first: `id`, `type`, `shop_id`, `title` and
 * `body`.
 */
internal object PushExtras {

    const val EXTRA = "com.personalization.push.PUSH"

    /** The most characters of keys and values an intent carries for a push. */
    const val MAX_CHARS = 4_096

    private val ESSENTIAL = setOf("id", "type", "shop_id", "title", "body")

    fun put(intent: Intent, push: Map<String, String>) {
        val bundle = Bundle()
        fit(push).forEach { (key, value) -> bundle.putString(key, value) }
        intent.putExtra(EXTRA, bundle)
    }

    /** The push [intent] carries; null when it carries none. */
    fun read(intent: Intent?): Map<String, String>? {
        val bundle = try {
            intent?.getBundleExtra(EXTRA)
        } catch (e: RuntimeException) {
            // An extra that does not unparcel: not one the SDK wrote.
            null
        } ?: return null
        val push = bundle.keySet().mapNotNull { key -> bundle.getString(key)?.let { key to it } }.toMap()
        return push.takeIf { it.isNotEmpty() }
    }

    /** [push] as an intent may carry it. */
    fun fit(push: Map<String, String>): Map<String, String> {
        val size = push.entries.sumOf { (key, value) -> key.length + value.length }
        return if (size <= MAX_CHARS) push else push.filterKeys { it in ESSENTIAL }
    }
}
