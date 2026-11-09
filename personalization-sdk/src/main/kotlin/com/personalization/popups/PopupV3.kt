package com.personalization.popups

import org.json.JSONArray
import org.json.JSONObject

/**
 * Попап v3 канала mobile из `GET /api/v3/storefront/popups` (или `/popups/:id` для ручного
 * показа): макет, триггер, частота, экраны и документ `design`.
 */
internal class PopupV3(
    val id: Int,
    val layout: Layout,
    val placement: Placement,
    val trigger: Trigger,
    /** `time_on_screen`: секунды на экране. */
    val triggerSeconds: Double,
    /** `track_event`: имя события. */
    val triggerEvent: String?,
    val frequency: String,
    val cooldownMinutes: Double,
    val promoCodeListId: String?,
    val targetScreens: List<String>,
    private val designJson: JSONObject?
) {
    enum class Layout(val code: String) { BANNER("banner"), MODAL("modal"), FULLSCREEN("fullscreen") }

    enum class Placement { TOP, BOTTOM, CENTER }

    enum class Trigger(val code: String) {
        APP_START("app_start"),
        SCREEN_OPEN("screen_open"),
        TIME_ON_SCREEN("time_on_screen"),
        TRACK_EVENT("track_event"),
        MANUAL("manual")
    }

    /** Модалка и фуллскрин — один слот на процесс; баннеры живут рядом с ними. */
    val isModal: Boolean get() = layout != Layout.BANNER

    val design: PopupV3Design by lazy { PopupV3Design.parse(designJson) }

    /** Экран подходит: список пуст или в нём есть [screen]. */
    fun matchesScreen(screen: String?): Boolean = targetScreens.isEmpty() || (screen != null && screen in targetScreens)

    /** Нужен персональный промокод до показа: есть список и токен или персональный элемент. */
    val needsPromoCode: Boolean get() = promoCodeListId != null && design.usesPromoCode

    override fun toString(): String = "PopupV3($id ${layout.code} ${trigger.code})"

    sealed class Parsed {
        class Ok(val popup: PopupV3) : Parsed()
        class Skipped(val id: String, val reason: String) : Parsed()
    }

    companion object {
        val FREQUENCIES = setOf("once_session", "once_life", "once_day", "once_week", "once_month", "every_page")

        /** [manual] — ручной показ: триггер не важен, неизвестный — не повод пропустить. */
        fun parse(json: JSONObject, manual: Boolean = false): Parsed {
            val rawId = PopupV3Values.string(json.opt("id")).orEmpty()
            val id = PopupV3Values.num(json.opt("id"), Double.NaN)
            if (id.isNaN()) return Parsed.Skipped(rawId, "no id")
            val layoutCode = PopupV3Values.string(json.opt("layout"))
            val layout = Layout.values().firstOrNull { it.code == layoutCode }
                ?: return Parsed.Skipped(rawId, "layout $layoutCode is not supported on mobile")
            val triggerCode = PopupV3Values.string(json.opt("trigger_type"))
            val trigger = Trigger.values().firstOrNull { it.code == triggerCode }
                ?: if (manual) Trigger.MANUAL else return Parsed.Skipped(rawId, "trigger $triggerCode is not supported on mobile")
            val placement = when (layout) {
                Layout.BANNER -> if (PopupV3Values.string(json.opt("placement")) == "bottom") Placement.BOTTOM else Placement.TOP
                else -> Placement.CENTER
            }
            val frequency = PopupV3Values.string(json.opt("frequency")).takeIf { it in FREQUENCIES } ?: "once_session"
            val screens = (json.opt("target_screens") as? JSONArray)?.let { array ->
                (0 until array.length()).mapNotNull { PopupV3Values.present(array.opt(it))?.trim() }
            }.orEmpty()
            return Parsed.Ok(
                PopupV3(
                    id = id.toInt(),
                    layout = layout,
                    placement = placement,
                    trigger = trigger,
                    triggerSeconds = PopupV3Values.num(json.opt("trigger_value"), 0.0).coerceAtLeast(0.0),
                    triggerEvent = PopupV3Values.present(json.opt("trigger_event")),
                    frequency = frequency,
                    cooldownMinutes = PopupV3Values.num(json.opt("cooldown_minutes"), 0.0).coerceAtLeast(0.0),
                    promoCodeListId = PopupV3Values.present(json.opt("promo_code_list_id"))?.let(::listId),
                    targetScreens = screens,
                    designJson = json.optJSONObject("design")
                )
            )
        }

        /** `7` и `7.0` — один и тот же список. */
        private fun listId(raw: String): String = raw.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString() ?: raw
    }
}

/** Ответ списка: попапы, разобранные и пропущенные, `excluded` сервера (с `debug=1`). */
internal class PopupsV3List(
    val ttlSeconds: Long,
    val popups: List<PopupV3>,
    val skipped: List<Pair<String, String>>,
    val excluded: List<Pair<String, String>>
) {
    companion object {
        const val DEFAULT_TTL = 300L

        /** Тело `{status, payload: {ttl, popups, excluded}}`; не тот статус — исключение. */
        fun parse(body: String): PopupsV3List {
            val json = JSONObject(body)
            val status = json.optString("status")
            require(status == "success") { json.optString("message").ifEmpty { "status $status" } }
            val payload = json.optJSONObject("payload") ?: JSONObject()
            val ttl = PopupV3Values.num(payload.opt("ttl"), DEFAULT_TTL.toDouble()).toLong().takeIf { it > 0 } ?: DEFAULT_TTL
            val popups = ArrayList<PopupV3>()
            val skipped = ArrayList<Pair<String, String>>()
            payload.optJSONArray("popups")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.opt(index) as? JSONObject ?: continue
                    when (val parsed = PopupV3.parse(item)) {
                        is PopupV3.Parsed.Ok -> popups += parsed.popup
                        is PopupV3.Parsed.Skipped -> skipped += parsed.id to parsed.reason
                    }
                }
            }
            val excluded = payload.optJSONArray("excluded")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.opt(index) as? JSONObject ?: return@mapNotNull null
                    PopupV3Values.string(item.opt("id")).orEmpty() to PopupV3Values.string(item.opt("reason")).orEmpty()
                }
            }.orEmpty()
            return PopupsV3List(ttl, popups, skipped, excluded)
        }

        /** Тело ручного показа `{status, payload: {popup}}`; попапа нет — `null`. */
        fun parseOne(body: String): PopupV3.Parsed? {
            val json = JSONObject(body)
            if (json.optString("status") != "success") return null
            val popup = json.optJSONObject("payload")?.optJSONObject("popup") ?: return null
            return PopupV3.parse(popup, manual = true)
        }
    }
}
