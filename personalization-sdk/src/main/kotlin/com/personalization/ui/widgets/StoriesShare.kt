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
     * Ссылка показанного слайда: своя у слайда, иначе кампании (у товарных слайдов своей нет).
     * Кампания с `shareable: false` и ссылки не http(s) — без кнопки.
     */
    fun slideShareUrl(story: Story, slide: Slide): String? =
        slideShareUrl(story.isShareable, story.shareUrl, slide.shareUrl)

    fun slideShareUrl(shareable: Boolean, storyUrl: String, slideUrl: String): String? {
        if (!shareable) return null
        val url = slideUrl.ifEmpty { storyUrl }
        return url.takeIf { LINK_SCHEME.containsMatchIn(it) }
    }

    /** Параметры запроса ссылки, как `URLSearchParams`: `+` — пробел, битый escape — без раскодирования. */
    private fun query(url: String): Map<String, String> {
        val start = url.indexOf('?')
        if (start < 0) return emptyMap()
        val end = url.indexOf('#', start).takeIf { it >= 0 } ?: url.length
        val result = LinkedHashMap<String, String>()
        url.substring(start + 1, end).split('&').forEach { pair ->
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
