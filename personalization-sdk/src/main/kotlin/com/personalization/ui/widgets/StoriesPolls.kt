package com.personalization.ui.widgets

import com.personalization.InternalPersonalizationV3Api
import java.io.Serializable

/**
 * Транспорт опросов на слайдах сторис V2 (API v3): как ответ уходит на сервер.
 *
 * По умолчанию — HTTP на хост API v3 просмотра (`apiV3Domain` виджета, [PersonalizationStories.show],
 * [PersonalizationStories.handleLink]). Свой — для тестов и офлайн-демо: задаётся там же
 * (`pollsTransport`). Виджет без хоста v3 опросы не рисует и со своим транспортом; просмотр, открытый
 * напрямую ([PersonalizationStoriesViewer.pollsTransport]), рисует их по одному транспорту.
 *
 * [body] — поля запроса без `shop_id`, `did` и `sid` (их добавляет HTTP-транспорт):
 * у мини-опроса `option_ids` или `text`, `story_id` (число) и `slide_id` (строка); у шкалы
 * `channel`, `category` (NPS) или `campaign` (CSAT, CES) и `rate`.
 *
 * [callback] зовётся один раз, с любого потока: просмотр сам вернётся на главный.
 *
 * Опросы — часть v3: типы за шлюзом [InternalPersonalizationV3Api].
 */
@InternalPersonalizationV3Api
interface StoriesPollsTransport {

    /** `POST /api/v3/polls/mini_polls/{code}/answers`. */
    fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit)

    /** `POST /api/v3/polls/{kind}/reviews`, [kind] — `nps`, `csat` или `ces`. */
    fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit)
}

/** Чем кончилась отправка ответа. */
@InternalPersonalizationV3Api
sealed class StoriesPollsResult {

    /**
     * Сервер принял ответ. [answer] — ответ, как его запомнил сервер (`payload.answer`; при
     * запрещённом переответе — прежний); `null` — показывается отправленный. [results] —
     * проценты по вариантам, только у мини-опросов с `show_results`.
     */
    class Success(
        val answer: StoriesPollAnswer? = null,
        val results: StoriesPollResults? = null
    ) : StoriesPollsResult()

    /** Нет сети, HTTP не 2xx, ответ не JSON или `status` `fail|error`. [code] — HTTP или -1. */
    class Failure(val code: Int, val message: String?) : StoriesPollsResult()
}

/**
 * Ответ на опрос: варианты, текст или оценка шкалы. [rate] — число, как пришло: дробная оценка
 * из ленты тоже ответ, но ячейку шкалы не выделяет.
 */
@InternalPersonalizationV3Api
data class StoriesPollAnswer(
    val optionIds: List<Int>? = null,
    val text: String? = null,
    val rate: Double? = null,
    val id: Long? = null
) : Serializable {
    /** Ответ есть: непустые варианты, непустой текст или любая оценка — как `hasAnswer` веб-SDK. */
    val isAnswered: Boolean
        get() = !optionIds.isNullOrEmpty() || !text.isNullOrEmpty() || rate != null
}

/** Итоги мини-опроса: всего ответов и процент каждого варианта. */
@InternalPersonalizationV3Api
data class StoriesPollResults(val total: Int, val options: List<Option>) : Serializable {

    data class Option(val id: Int, val percent: Double) : Serializable

    /** Процент варианта для полоски: целый, 0…100; варианта нет в итогах — 0. */
    fun percentOf(optionId: Int): Int {
        val percent = options.firstOrNull { it.id == optionId }?.percent ?: 0.0
        return Math.round(percent).toInt().coerceIn(0, 100)
    }
}
