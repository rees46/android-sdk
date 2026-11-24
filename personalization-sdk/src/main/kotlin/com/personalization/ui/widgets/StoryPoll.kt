package com.personalization.ui.widgets

import com.personalization.InternalPersonalizationV3Api
import com.personalization.SDK
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * Опрос на слайде — элемент `poll` ленты API v3: мини-опрос (выбор одного, нескольких, текст)
 * или шкала NPS / CSAT / CES. Как `createPoll` веб-SDK.
 *
 * Разбор терпимый: числа бывают строками (`"40"`, `"120"`), необязательные поля — `null`
 * или нет вовсе (лента v3 выкидывает `null` до моделей). Неизвестный вид — не опрос:
 * [parse] отдаёт `null`, слайд показывается без него.
 *
 * Сериализуется вместе со [com.personalization.stories.models.Slide]: хост может передать слайд
 * с опросом через `Intent` / `Bundle`.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class StoryPoll(
    val type: Type,
    /** `poll_type` как пришёл — часть ключа хранилища ответов. */
    val pollType: String,
    /** Код опроса; нет в ленте — пустой. */
    val code: String,
    /** Высота в процентах (0…100), как у текстовых блоков; `null` — сверху. */
    val yOffset: Float?,
    val allowReanswer: Boolean,
    val question: String?,
    val placeholder: String?,
    /** Лимит текста; `null` — без лимита и без счётчика. */
    val maxLength: Int?,
    val submitLabel: String?,
    val thanksText: String?,
    val options: List<Option>,
    val channel: String?,
    val rateMin: Int?,
    val rateMax: Int?,
    val bands: List<Band>,
    /**
     * Ответ и итоги из ленты — важнее сохранённого на устройстве ([StoryPollAnswers]). После успешной
     * отправки здесь отправленный ответ: слайд, собранный из той же ленты заново, его покажет, как
     * `element.answer` в веб-SDK.
     */
    @Volatile var answer: StoriesPollAnswer?,
    @Volatile var results: StoriesPollResults?,
    val labels: Labels
) : Serializable {

    /** Тон ячейки шкалы: плохо, средне, хорошо. */
    enum class Tone { NEGATIVE, NEUTRAL, POSITIVE }

    enum class Type(val scaleKind: String?) {
        SINGLE_CHOICE(null), MULTIPLE_CHOICE(null), TEXT(null), NPS("nps"), CSAT("csat"), CES("ces");

        val isScale: Boolean get() = scaleKind != null
    }

    data class Option(val id: Int, val label: String) : Serializable

    /**
     * Полоса шкалы `from..to`: после оценки из неё задаётся вопрос [question] (ответ уходит `comment`),
     * потом показывается [success].
     */
    data class Band(val from: Double, val to: Double, val success: String?, val question: String? = null) : Serializable

    /** Тексты сервера (`labels` элемента): непустые важнее текстов хоста. */
    data class Labels(
        val reanswer: String? = null,
        val error: String? = null,
        val submit: String? = null
    ) : Serializable

    /** Шкала: значения по порядку. */
    val scale: List<Int> get() = type.scaleKind?.let { scaleRange(type, rateMin, rateMax) }.orEmpty()

    /** Текст под отвеченной шкалой. */
    fun bandText(rate: Double?): String? = bandText(bands, rate)

    /**
     * Вопрос после оценки, как в веб-SDK: у шкалы с оценкой [answer] без комментария — непустой
     * `question` её полосы; иначе `null`.
     */
    fun followUp(answer: StoriesPollAnswer?): String? {
        if (!type.isScale || answer == null || !answer.comment.isNullOrEmpty()) return null
        return bandQuestion(bands, answer.rate)
    }

    companion object {
        const val ELEMENT_TYPE = "poll"
        const val MINI_POLL = "mini_poll"
        const val DEFAULT_CHANNEL = "stories"

        /** Не больше 21 значения шкалы, как в веб-SDK. */
        const val MAX_SCALE_VALUES = 21

        /** Лимит комментария к вопросу полосы, как в веб-SDK. */
        const val COMMENT_MAX_LENGTH = 5000

        /** Опросы слайда по порядку элементов; битый элемент пропускается. */
        fun parseAll(elements: JSONArray?): List<StoryPoll> {
            if (elements == null) return emptyList()
            return (0 until elements.length()).mapNotNull { index ->
                val json = elements.optJSONObject(index) ?: return@mapNotNull null
                if (json.optString("type") != ELEMENT_TYPE) null else parse(json)
            }
        }

        fun parse(json: JSONObject): StoryPoll? = try {
            parseOrThrow(json)
        } catch (throwable: Throwable) {
            SDK.debug("stories poll: broken element: ${throwable.javaClass.simpleName}")
            null
        }

        private fun parseOrThrow(json: JSONObject): StoryPoll? {
            val pollType = json.text("poll_type").orEmpty()
            val poll = json.value("poll") as? JSONObject ?: return unknown("no poll")
            val type = when (pollType) {
                "nps" -> Type.NPS
                "csat" -> Type.CSAT
                "ces" -> Type.CES
                MINI_POLL -> when (poll.text("kind")) {
                    "single_choice" -> Type.SINGLE_CHOICE
                    "multiple_choice" -> Type.MULTIPLE_CHOICE
                    "text" -> Type.TEXT
                    else -> return unknown("$pollType ${poll.text("kind")}")
                }
                else -> return unknown(pollType)
            }
            // Код не обязателен, как в веб-SDK: без него карточка рисуется, а ответ сервера — ошибка.
            val code = poll.text("code").orEmpty()
            return StoryPoll(
                type = type,
                pollType = pollType,
                code = code,
                yOffset = StorySlidePlacement.offset(json.value("y_offset")),
                allowReanswer = bool(json.value("allow_reanswer")),
                question = poll.text("question"),
                placeholder = poll.text("placeholder"),
                maxLength = number(poll.value("max_length"))?.takeIf { it >= 1 }?.toInt(),
                submitLabel = poll.text("submit_label"),
                thanksText = poll.text("thanks_text"),
                options = (poll.value("options") as? JSONArray).objects().mapNotNull { option ->
                    int(option.value("id"))?.let { Option(it, option.text("label").orEmpty()) }
                },
                channel = poll.text("channel"),
                rateMin = int(poll.value("rate_min")),
                rateMax = int(poll.value("rate_max")),
                bands = (poll.value("bands") as? JSONArray).objects().mapNotNull { band ->
                    val from = number(band.value("from")) ?: return@mapNotNull null
                    val to = number(band.value("to")) ?: return@mapNotNull null
                    Band(from, to, band.text("success"), band.string("question"))
                },
                answer = (json.value("answer") as? JSONObject)?.let(::answer),
                results = (json.value("results") as? JSONObject)?.let(::results),
                labels = (json.value("labels") as? JSONObject)?.let { labels ->
                    Labels(labels.string("reanswer"), labels.string("error"), labels.string("submit"))
                } ?: Labels()
            )
        }

        private fun unknown(what: String): StoryPoll? {
            SDK.debug("stories poll: $what is not supported, the poll is not shown")
            return null
        }

        /**
         * `answer` ленты или ответа сервера: `{id, option_ids, text, rate}`. Оценка — только число JSON, как
         * `typeof rate === 'number'` в `hasAnswer` веб-SDK: строка — не оценка.
         */
        fun answer(json: JSONObject): StoriesPollAnswer {
            val ids = json.value("option_ids") as? JSONArray
            val entries = ids?.let { array -> List(array.length()) { array.opt(it) } }.orEmpty()
            return answer(json, ids, entries)
        }

        /** [answer]: `option_ids` — целые id вариантов, остальные элементы массива — [StoriesPollAnswer.otherOptionIds]. */
        private fun answer(json: JSONObject, ids: JSONArray?, entries: List<Any?>): StoriesPollAnswer = StoriesPollAnswer(
            optionIds = ids?.let { entries.mapNotNull(::int) },
            otherOptionIds = entries.filter { int(it) == null }.map { if (it == null || it === JSONObject.NULL) "null" else it.toString() },
            text = json.value("text") as? String,
            rate = (json.value("rate") as? Number)?.toDouble()?.takeIf { !it.isNaN() && !it.isInfinite() },
            id = number(json.value("id"))?.toLong(),
            comment = json.value("comment") as? String
        )

        /** [answer] в виде ленты — для хранилища на устройстве; пустые поля не пишутся. */
        fun answerJson(answer: StoriesPollAnswer): JSONObject = JSONObject().apply {
            answer.id?.let { put("id", it) }
            if (answer.optionIds != null || answer.otherOptionIds.isNotEmpty()) {
                put("option_ids", JSONArray(answer.optionIds.orEmpty() + answer.otherOptionIds))
            }
            answer.text?.let { put("text", it) }
            answer.rate?.let { put("rate", it) }
            answer.comment?.let { put("comment", it) }
        }

        /** [results] в виде ленты. */
        fun resultsJson(results: StoriesPollResults): JSONObject = JSONObject()
            .put("total", results.total)
            .put("options", JSONArray(results.options.map { JSONObject().put("id", it.id).put("percent", it.percent) }))

        /**
         * `results`: `{total, options: [{id, percent}]}`. `options` не массив — итогов нет:
         * проценты не показываются, как в веб-SDK.
         */
        fun results(json: JSONObject): StoriesPollResults? {
            val options = json.value("options") as? JSONArray ?: return null
            return StoriesPollResults(
                total = int(json.value("total")) ?: 0,
                options = options.objects().mapNotNull { option ->
                    val id = int(option.value("id")) ?: return@mapNotNull null
                    StoriesPollResults.Option(id, number(option.value("percent")) ?: 0.0)
                }
            )
        }

        /**
         * Значения шкалы `rate_min..rate_max` (не целые — как нет); без них NPS 0..10, CSAT 1..5,
         * CES 1..7. Не больше [MAX_SCALE_VALUES]; `rate_min > rate_max` — пустая шкала.
         */
        fun scaleRange(type: Type, rateMin: Int?, rateMax: Int?): List<Int> {
            val (defaultMin, defaultMax) = when (type) {
                Type.NPS -> 0 to 10
                Type.CSAT -> 1 to 5
                Type.CES -> 1 to 7
                else -> return emptyList()
            }
            val min = rateMin ?: defaultMin
            val max = rateMax ?: defaultMax
            if (max < min) return emptyList()
            return (min..minOf(max.toLong(), min.toLong() + MAX_SCALE_VALUES - 1).toInt()).toList()
        }

        /**
         * Текст поля не длиннее [limit] единиц UTF-16, как `value.length` в вебе; пара суррогатов
         * не разрезается. Без лимита — как есть.
         */
        fun limitText(text: String, limit: Int?): String =
            if (limit == null || text.length <= limit) text else text.substring(0, cutIndex(text, limit))

        /** Где резать [text] до [limit]: перед парой суррогатов, а не посередине. */
        fun cutIndex(text: CharSequence, limit: Int): Int =
            if (limit > 0 && limit < text.length && Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit

        /**
         * Тон ячейки шкалы, как `scaleColor` веб-SDK, тремя семантическими цветами кита: у NPS 0..10
         * критики 0–6 — [Tone.NEGATIVE], нейтралы 7–8 — [Tone.NEUTRAL], промоутеры 9–10 —
         * [Tone.POSITIVE]; у остальных шкал и NPS со своим диапазоном — по позиции
         * `(value - min) / (max - min)`: меньше 0.34, меньше 0.68, остальное.
         */
        fun scaleTone(type: Type, value: Int, min: Int, max: Int): Tone {
            if (type == Type.NPS && min == 0 && max == 10) {
                return when {
                    value <= 6 -> Tone.NEGATIVE
                    value <= 8 -> Tone.NEUTRAL
                    else -> Tone.POSITIVE
                }
            }
            val position = if (max > min) (value - min).toDouble() / (max - min) else 1.0
            return when {
                position < 0.34 -> Tone.NEGATIVE
                position < 0.68 -> Tone.NEUTRAL
                else -> Tone.POSITIVE
            }
        }

        /** Полоса, в которую попала оценка; нет оценки или полосы — `null`. */
        fun band(bands: List<Band>, rate: Double?): Band? {
            if (rate == null) return null
            return bands.firstOrNull { rate >= it.from && rate <= it.to }
        }

        /** `success` полосы, в которую попала оценка; нет оценки или полосы — `null`. */
        fun bandText(bands: List<Band>, rate: Double?): String? = band(bands, rate)?.success?.takeIf { it.isNotEmpty() }

        /** `question` полосы оценки без пробелов по краям; пустой — `null`. */
        fun bandQuestion(bands: List<Band>, rate: Double?): String? = band(bands, rate)?.question?.trim()?.takeIf { it.isNotEmpty() }

        // --- Терпимое чтение JSON ---------------------------------------------------------

        private fun JSONObject.value(key: String): Any? = opt(key)?.takeIf { it !== JSONObject.NULL }

        /** Строка или число строкой; пустая — `null`. */
        private fun JSONObject.text(key: String): String? = when (val value = value(key)) {
            is String -> value
            is Number -> value.toString()
            else -> null
        }?.takeIf { it.isNotEmpty() }

        /** Только непустая строка — тексты `labels`. */
        private fun JSONObject.string(key: String): String? = (value(key) as? String)?.takeIf { it.isNotEmpty() }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun number(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }?.takeIf { !it.isNaN() && !it.isInfinite() }

        /** Целое число или целое строкой; дробное — `null`. */
        private fun int(value: Any?): Int? = number(value)?.takeIf { it == Math.floor(it) && Math.abs(it) <= Int.MAX_VALUE }?.toInt()

        /** Истина — только `true`, `1` или `"true"`; остальное — ложь. */
        private fun bool(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() == 1.0
            is String -> value == "true"
            else -> false
        }
    }
}

/** Тексты карточки опроса: ссылка переответа, ошибка отправки, кнопка без `submit_label`. */
internal data class PollTexts(val reanswer: CharSequence, val error: CharSequence, val submit: CharSequence) {

    /** Пустой текст хоста не перекрывает умолчание из [defaults]; из пробелов — перекрывает. */
    fun or(defaults: PollTexts): PollTexts = PollTexts(
        reanswer = reanswer.ifEmpty { defaults.reanswer },
        error = error.ifEmpty { defaults.error },
        submit = submit.ifEmpty { defaults.submit }
    )

    /** Тексты сервера (`labels`) важнее текстов хоста — непустые. */
    fun with(labels: StoryPoll.Labels): PollTexts = PollTexts(
        reanswer = labels.reanswer ?: reanswer,
        error = labels.error ?: error,
        submit = labels.submit ?: submit
    )

    companion object {
        /**
         * Встроенные тексты — `DEFAULT_POLL_TEXTS` веб-SDK: английские при любом языке устройства. Их заменяют
         * тексты хоста и `labels` опроса, кнопку — ещё и `submit_label`.
         */
        const val DEFAULT_REANSWER = "Answer again"
        const val DEFAULT_ERROR = "Failed to send the answer. Please try again"
        const val DEFAULT_SUBMIT = "Send"

        val DEFAULTS = PollTexts(DEFAULT_REANSWER, DEFAULT_ERROR, DEFAULT_SUBMIT)
    }
}

/**
 * Тела запросов опросов — без `shop_id`, `did`, `sid`, `stream` и `testmode`: их добавляет транспорт.
 * Источник ответа — как `sourceParams` веб-SDK: `source_type` `stories`, `source_id` — id сторис,
 * `source_item_id` — id слайда, строками; без id сторис источника нет, пустой слайд не уходит.
 */
internal object StoryPollBodies {

    const val SOURCE_TYPE = "stories"

    /** Мини-опрос: варианты или текст и источник. */
    fun answer(storyId: Int, slideId: String, optionIds: List<Int>? = null, text: String? = null): Map<String, Any> {
        val body = LinkedHashMap<String, Any>()
        if (optionIds != null) body["option_ids"] = optionIds
        if (text != null) body["text"] = text
        body.putAll(source(storyId, slideId))
        return body
    }

    /**
     * Шкала: канал опроса или `stories`, `category` у NPS, `campaign` у CSAT и CES, оценка и
     * [comment] (непустой — ответ на вопрос полосы), затем источник.
     */
    fun review(poll: StoryPoll, rate: Number, storyId: Int, slideId: String, comment: String? = null): Map<String, Any> {
        val body = LinkedHashMap<String, Any>()
        body["channel"] = poll.channel ?: StoryPoll.DEFAULT_CHANNEL
        body[if (poll.type == StoryPoll.Type.NPS) "category" else "campaign"] = poll.code
        body["rate"] = rate
        if (!comment.isNullOrEmpty()) body["comment"] = comment
        body.putAll(source(storyId, slideId))
        return body
    }

    /** Источник ответа; id сторис нет (0 и меньше) — пусто. */
    fun source(storyId: Int, slideId: String): Map<String, String> {
        if (storyId <= 0) return emptyMap()
        val source = LinkedHashMap<String, String>()
        source["source_type"] = SOURCE_TYPE
        source["source_id"] = storyId.toString()
        if (slideId.isNotEmpty()) source["source_item_id"] = slideId
        return source
    }
}
