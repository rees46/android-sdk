package com.personalization.core

import com.personalization.PushProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * A push of the SDK, parsed: what [PushCallbacks] receive. FCM and HMS send every value as a string, so
 * `actions`, `action_urls` and `event` come as JSON text and are parsed here.
 */
class PushPayload internal constructor(
    /** The shop the push was handed to; null when the push named none and was not routed. */
    val shopId: String?,
    /** The message's code: the push's `id`. */
    val code: String,
    /** The mailing's type: the push's `type` (`bulk`, `chain`, `transactional`, `digest`). */
    val type: String,
    val title: String?,
    val body: String?,
    /** A link to the icon. */
    val icon: String?,
    /** A link to the image; several are separated with commas. */
    val image: String?,
    /** The notification's buttons; empty when it has none. */
    val actions: List<PushAction>,
    /** What to open on a tap: the mailing's "additional data for the app"; null when it has none. */
    val target: PushTarget?,
    /** The mailing's `utm_source`. */
    val source: String?,
    /** The name of the sound file (`android_sound_file`). */
    val sound: String?,
    /** The whole push as it came. */
    val data: Map<String, String>,
) {

    override fun toString(): String = "PushPayload(shopId=$shopId, type=$type)"

    internal fun withShop(shopId: String): PushPayload =
        PushPayload(shopId, code, type, title, body, icon, image, actions, target, source, sound, data)

    internal companion object {
        /**
         * The push in [data], or null when it is not the SDK's: every SDK push has an `id` and a `type`.
         * A tap's intent extras (`NOTIFICATION_ID`, `NOTIFICATION_TYPE`) are read as well. Never throws:
         * a field that does not parse is left empty.
         */
        fun parse(data: Map<String, String>): PushPayload? {
            val code = (data[ID] ?: data[CLICK_ID])?.takeIf { it.isNotBlank() } ?: return null
            val type = (data[TYPE] ?: data[CLICK_TYPE])?.takeIf { it.isNotBlank() } ?: return null
            return PushPayload(
                shopId = data[SHOP_ID]?.takeIf { it.isNotEmpty() },
                code = code,
                type = type,
                title = data[TITLE],
                body = data[BODY],
                icon = data[ICON],
                image = data[IMAGE],
                actions = actions(data[ACTIONS], data[ACTION_URLS]),
                target = target(data[EVENT]),
                source = data[SOURCE],
                sound = data[SOUND],
                data = data.toMap(),
            )
        }

        private fun actions(actions: String?, urls: String?): List<PushAction> {
            val list = array(actions) ?: return emptyList()
            val links = array(urls)
            return (0 until list.length()).mapNotNull { index ->
                val action = list.optJSONObject(index) ?: return@mapNotNull null
                PushAction(
                    id = action.optString(ACTION),
                    title = action.optString(TITLE),
                    url = links?.optString(index)?.takeIf { it.isNotEmpty() },
                )
            }
        }

        private fun target(event: String?): PushTarget? {
            if (event.isNullOrBlank()) return null
            val json = try {
                JSONObject(event)
            } catch (e: Exception) {
                return null
            }
            val payload = json.optJSONObject(PAYLOAD)?.let { fields -> fields.keys().asSequence().associateWith { fields.opt(it) } }.orEmpty()
            return PushTarget(PushTargetType(json.optString(TYPE)), json.optString(URI), payload)
        }

        private fun array(text: String?): JSONArray? = if (text.isNullOrBlank()) null else try {
            JSONArray(text)
        } catch (e: Exception) {
            null
        }

        const val SHOP_ID = "shop_id"
        const val ID = "id"
        const val TYPE = "type"

        /** The extras a tap's intent carries, as the SDK's notification helpers name them. */
        const val CLICK_ID = "NOTIFICATION_ID"
        const val CLICK_TYPE = "NOTIFICATION_TYPE"

        private const val TITLE = "title"
        private const val BODY = "body"
        private const val ICON = "icon"
        private const val IMAGE = "image"
        private const val ACTIONS = "actions"
        private const val ACTION = "action"
        private const val ACTION_URLS = "action_urls"
        private const val EVENT = "event"
        private const val URI = "uri"
        private const val PAYLOAD = "payload"
        private const val SOURCE = "source"
        private const val SOUND = "android_sound_file"
    }
}

/** A button of a notification. */
class PushAction internal constructor(
    /** `actions[i].action`. */
    val id: String,
    val title: String,
    /** The link of the same index in `action_urls`; null when there is none. */
    val url: String?,
)

/** What a tap opens: the mailing's "additional data for the app" (`event`). */
class PushTarget internal constructor(
    val type: PushTargetType,
    /** A link, a product's id, a category's id or the app's own link. */
    val uri: String,
    /** A free field of the mailing. */
    val payload: Map<String, Any?>,
)

/**
 * What [PushTarget.uri] is. An open set: the server understands `web` and `carousel` itself, the others
 * are an agreement with the app, and a value the SDK does not know comes as it is.
 */
@JvmInline
value class PushTargetType(val value: String) {
    companion object {
        val WEB = PushTargetType("web")
        val PRODUCT = PushTargetType("product")
        val CATEGORY = PushTargetType("category")
        val CAROUSEL = PushTargetType("carousel")
        val CUSTOM = PushTargetType("custom")
    }
}

/** A push token and the provider that issued it. */
data class PushToken(val token: String, val provider: PushProvider) {
    override fun toString(): String = "PushToken(provider=$provider)"
}
