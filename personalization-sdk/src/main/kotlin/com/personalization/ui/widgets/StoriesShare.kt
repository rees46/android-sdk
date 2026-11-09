package com.personalization.ui.widgets

import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import java.net.URLDecoder

/**
 * «Поделиться» сторис (API v3), как в веб-SDK: ссылка слайда или кампании и разбор ссылки
 * `https://<сайт>/<путь>?r46_stories=<code>&r46_story=<id>[&r46_slide=<id>]`.
 */
internal object StoriesShare {

    const val PARAM_BLOCK = "r46_stories"
    const val PARAM_STORY = "r46_story"
    const val PARAM_SLIDE = "r46_slide"

    private val STORY_ID = Regex("^\\d{1,10}$")

    /** Число или виртуальный слайд товарной кампании `{story_id}_{index}`. */
    private val SLIDE_ID = Regex("^\\d{1,10}(_\\d{1,4})?$")
    private val LINK_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** Ссылка на кампанию блока; неверный слайд — `null`, кампания всё равно открывается. */
    data class Link(val code: String, val storyId: Long, val slideId: String?)

    /** Ссылка сторис из адреса; любая другая — `null`. Повтор параметра — берётся первый. */
    fun parseLink(url: String): Link? {
        val params = query(url)
        val code = params[PARAM_BLOCK]?.takeIf { it.isNotEmpty() } ?: return null
        val storyId = params[PARAM_STORY]?.takeIf { STORY_ID.matches(it) } ?: return null
        val slideId = params[PARAM_SLIDE]?.takeIf { SLIDE_ID.matches(it) }
        return Link(code, storyId.toLong(), slideId)
    }

    /**
     * Ссылка показанного слайда: своя у слайда, иначе кампании с id слайда в `r46_slide`. У
     * слайдов товарных кампаний (`39_2`) своих ссылок нет, и без id слайда ссылка открыла бы
     * кампанию с начала, а не с того слайда, которым поделились. Кампания с `shareable: false`
     * и ссылки не http(s) — без кнопки.
     */
    fun slideShareUrl(story: Story, slide: Slide): String? =
        slideShareUrl(story.isShareable, story.shareUrl, slide.shareUrl, slide.id)

    fun slideShareUrl(shareable: Boolean, storyUrl: String, slideUrl: String, slideId: String): String? {
        if (!shareable) return null
        val url = when {
            slideUrl.isNotEmpty() -> slideUrl
            storyUrl.isNotEmpty() -> campaignSlideUrl(storyUrl, slideId)
            else -> return null
        }
        return url.takeIf { LINK_SCHEME.containsMatchIn(it) }
    }

    /**
     * Ссылка кампании с `r46_slide=<slideId>` в конце запроса, перед `#`. Уже есть `r46_slide`
     * или id не годится для ссылки — как есть. Остальной адрес не перекодируется.
     */
    fun campaignSlideUrl(campaign: String, slideId: String): String {
        if (!SLIDE_ID.matches(slideId)) return campaign
        val hash = campaign.indexOf('#').takeIf { it >= 0 } ?: campaign.length
        val address = campaign.substring(0, hash)
        val question = address.indexOf('?')
        if (question >= 0 && PARAM_SLIDE in params(address.substring(question + 1))) return campaign
        val separator = when {
            question < 0 -> "?"
            address.endsWith("?") || address.endsWith("&") -> ""
            else -> "&"
        }
        return "$address$separator$PARAM_SLIDE=$slideId${campaign.substring(hash)}"
    }

    /** Параметры запроса ссылки, как `URLSearchParams`: `+` — пробел, битый escape — без раскодирования. */
    private fun query(url: String): Map<String, String> {
        val start = url.indexOf('?')
        if (start < 0) return emptyMap()
        val end = url.indexOf('#', start).takeIf { it >= 0 } ?: url.length
        return params(url.substring(start + 1, end))
    }

    /** Пары `ключ=значение` запроса без `?`; повтор ключа — первый. */
    private fun params(query: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val separator = pair.indexOf('=')
            val key = decode(if (separator < 0) pair else pair.substring(0, separator))
            val value = if (separator < 0) "" else decode(pair.substring(separator + 1))
            if (key !in result) result[key] = value
        }
        return result
    }

    private fun decode(text: String): String =
        try {
            URLDecoder.decode(text, "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }
}
