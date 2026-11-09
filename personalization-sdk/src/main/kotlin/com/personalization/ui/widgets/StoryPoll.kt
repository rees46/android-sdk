package com.personalization.ui.widgets

import android.graphics.Color
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
    /** Ответ и итоги из ленты; хранилище сессии важнее ([StoryPollStore]). */
    val answer: StoriesPollAnswer?,
    val results: StoriesPollResults?,
    val labels: Labels
) : Serializable {

    enum class Type(val scaleKind: String?) {
        SINGLE_CHOICE(null), MULTIPLE_CHOICE(null), TEXT(null), NPS("nps"), CSAT("csat"), CES("ces");

        val isScale: Boolean get() = scaleKind != null
    }

    data class Option(val id: Int, val label: String) : Serializable

    /** Полоса шкалы: текст [success] — под оценкой из `from..to`. */
    data class Band(val from: Double, val to: Double, val success: String?) : Serializable

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

    companion object {
        const val ELEMENT_TYPE = "poll"
        const val MINI_POLL = "mini_poll"
        const val DEFAULT_CHANNEL = "stories"

        /** Не больше 21 значения шкалы, как в веб-SDK. */
        const val MAX_SCALE_VALUES = 21

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
            SDK.debug("stories poll: broken element: ${throwable.message}")
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
                    Band(from, to, band.text("success"))
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

        /** `answer` ленты или ответа сервера: `{id, option_ids, text, rate}`. */
        fun answer(json: JSONObject): StoriesPollAnswer = StoriesPollAnswer(
            optionIds = (json.value("option_ids") as? JSONArray)?.let { ids -> (0 until ids.length()).mapNotNull { int(ids.opt(it)) } },
            text = json.value("text") as? String,
            rate = number(json.value("rate")),
            id = number(json.value("id"))?.toLong()
        )

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

        /** Цвет ячейки: у NPS 0..10 — критики красные, нейтралы жёлтые, промоутеры зелёные. */
        fun scaleColor(type: Type, value: Int, min: Int, max: Int): Int {
            val hex = if (type == Type.NPS && min == 0 && max == 10) {
                when {
                    value <= 2 -> RED
                    value <= 5 -> ORANGE
                    value == 6 -> AMBER
                    value <= 8 -> YELLOW
                    else -> GREEN
                }
            } else {
                val position = if (max > min) (value - min).toDouble() / (max - min) else 1.0
                when {
                    position < 0.34 -> RED
                    position < 0.68 -> YELLOW
                    else -> GREEN
                }
            }
            return Color.parseColor(hex)
        }

        /** `success` полосы, в которую попала оценка; нет оценки или полосы — `null`. */
        fun bandText(bands: List<Band>, rate: Double?): String? {
            if (rate == null) return null
            return bands.firstOrNull { rate >= it.from && rate <= it.to }?.success?.takeIf { it.isNotEmpty() }
        }

        private const val RED = "#FCA5A5"
        private const val ORANGE = "#FDBA74"
        private const val AMBER = "#FCD34D"
        private const val YELLOW = "#FDE68A"
        private const val GREEN = "#86EFAC"

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
        /** Умолчания без ресурсов — как в веб-SDK. */
        const val DEFAULT_REANSWER = "Answer again"
        const val DEFAULT_ERROR = "Failed to send the answer. Please try again"
        const val DEFAULT_SUBMIT = "Send"
    }
}

/** Тела запросов опросов — без `shop_id`, `did`, `sid`: их добавляет транспорт. */
internal object StoryPollBodies {

    /** Мини-опрос: варианты или текст, `story_id` — число, `slide_id` — всегда строка. */
    fun answer(storyId: Int, slideId: String, optionIds: List<Int>? = null, text: String? = null): Map<String, Any> {
        val body = LinkedHashMap<String, Any>()
        if (optionIds != null) body["option_ids"] = optionIds
        if (text != null) body["text"] = text
        body["story_id"] = storyId
        body["slide_id"] = slideId
        return body
    }

    /** Шкала: канал опроса или `stories`, `category` у NPS, `campaign` у CSAT и CES. */
    fun review(poll: StoryPoll, rate: Int): Map<String, Any> {
        val body = LinkedHashMap<String, Any>()
        body["channel"] = poll.channel ?: StoryPoll.DEFAULT_CHANNEL
        body[if (poll.type == StoryPoll.Type.NPS) "category" else "campaign"] = poll.code
        body["rate"] = rate
        return body
    }
}
