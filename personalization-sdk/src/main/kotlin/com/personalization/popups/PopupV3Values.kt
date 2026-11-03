package com.personalization.popups

import org.json.JSONArray
import org.json.JSONObject

/**
 * Разбор значений документа `design` так, как его читает компилятор попапов на бэке
 * (`Context#num`, цвета, булевы): числа приходят и строками, и `null`, цвета — в любом
 * CSS-виде. Всё, что не разобралось, — запасное значение элемента.
 */
internal object PopupV3Values {

    private val LEADING_NUMBER = Regex("""^\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)""")

    /**
     * Число: число JSON, строка с числом в начале (`"12"`, `" 7 "`, `"12px"` — как `parseFloat`),
     * булево (`true` — 1). Пусто, `null`, мусор, бесконечность — [fallback].
     */
    fun num(value: Any?, fallback: Double): Double {
        val result = when (value) {
            null, JSONObject.NULL -> null
            is Boolean -> if (value) 1.0 else 0.0
            is Number -> value.toDouble()
            is String -> LEADING_NUMBER.find(value)?.groupValues?.get(1)?.toDoubleOrNull()
            else -> null
        }
        return if (result == null || result.isNaN() || result.isInfinite()) fallback else result
    }

    /** Булево: `true`/`"true"`/`1`, `false`/`"false"`/`0`; остальное — не задано. */
    fun bool(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> when (value.toDouble()) {
            1.0 -> true
            0.0 -> false
            else -> null
        }
        is String -> when (value.trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }

    /** Строка: `null` и JSON `null` — не задана; числа и булевы — строкой. */
    fun string(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    /** Строка, если в ней есть что-то кроме пробелов. */
    fun present(value: Any?): String? = string(value)?.takeIf { it.isNotBlank() }

    /** JSON-массив строк (непустых), без `null` и вложенных объектов. */
    fun strings(value: Any?): List<String> {
        val array = value as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { string(array.opt(it)) }
    }
}

/**
 * Разбор CSS-цвета в ARGB: `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa` (порядок CSS, альфа
 * в конце — не Android-овский `#AARRGGBB`), `rgb()/rgba()` через запятые и через пробел
 * с `/ alpha`, 148 именованных цветов и `transparent`. Остальное — `null`.
 */
internal object CssColor {

    private val HEX = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val RGB = Regex("""^rgba?\((.*)\)$""", RegexOption.IGNORE_CASE)
    private const val COMPONENT = """[+-]?(?:\d+(?:\.\d*)?|\.\d+)%?"""
    private val COMMAS = Regex("""^\s*($COMPONENT)\s*,\s*($COMPONENT)\s*,\s*($COMPONENT)\s*(?:,\s*($COMPONENT)\s*)?$""")
    private val SPACES = Regex("""^\s*($COMPONENT)\s+($COMPONENT)\s+($COMPONENT)\s*(?:/\s*($COMPONENT)\s*)?$""")

    fun parse(value: Any?): Int? {
        val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.startsWith("#")) return hex(text)
        RGB.matchEntire(text)?.let { return rgb(it.groupValues[1]) }
        val lower = text.lowercase()
        if (lower == "transparent") return 0
        return NAMED[lower]?.let { 0xFF000000.toInt() or it }
    }

    /** `#rrggbbaa` в нижнем регистре — для тестов и логов. */
    fun format(color: Int): String =
        "#%02x%02x%02x%02x".format(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF, color ushr 24)

    /** Цвет с альфой [alpha] 0…1 поверх его собственной. */
    fun withAlpha(color: Int, alpha: Double): Int {
        val a = ((color ushr 24) * alpha.coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /** Шестизначный hex без альфы: к нему тень дописывает свою прозрачность. */
    fun isOpaqueHex6(value: Any?): Boolean = (value as? String)?.trim()?.matches(Regex("^#[0-9a-fA-F]{6}$")) == true

    private fun hex(text: String): Int? {
        if (!HEX.matches(text)) return null
        val digits = text.substring(1)
        val full = when (digits.length) {
            3, 4 -> digits.map { "$it$it" }.joinToString("")
            else -> digits
        }
        val rgb = full.substring(0, 6).toLong(16).toInt()
        val alpha = if (full.length == 8) full.substring(6, 8).toInt(16) else 0xFF
        return (alpha shl 24) or rgb
    }

    private fun rgb(inner: String): Int? {
        val match = COMMAS.matchEntire(inner) ?: SPACES.matchEntire(inner) ?: return null
        val (r, g, b) = (1..3).map { channel(match.groupValues[it]) ?: return null }
        val alphaText = match.groupValues[4]
        val alpha = if (alphaText.isEmpty()) 255 else alpha(alphaText) ?: return null
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(text: String): Int? {
        val percent = text.endsWith("%")
        val number = text.removeSuffix("%").toDoubleOrNull() ?: return null
        val value = if (percent) number * 255.0 / 100.0 else number
        return Math.round(value.coerceIn(0.0, 255.0)).toInt()
    }

    private fun alpha(text: String): Int? {
        val percent = text.endsWith("%")
        val number = text.removeSuffix("%").toDoubleOrNull() ?: return null
        val value = if (percent) number / 100.0 else number
        return Math.round(value.coerceIn(0.0, 1.0) * 255.0).toInt()
    }

    private val NAMED: Map<String, Int> = mapOf(
        "aliceblue" to 0xf0f8ff, "antiquewhite" to 0xfaebd7, "aqua" to 0x00ffff, "aquamarine" to 0x7fffd4,
        "azure" to 0xf0ffff, "beige" to 0xf5f5dc, "bisque" to 0xffe4c4, "black" to 0x000000,
        "blanchedalmond" to 0xffebcd, "blue" to 0x0000ff, "blueviolet" to 0x8a2be2, "brown" to 0xa52a2a,
        "burlywood" to 0xdeb887, "cadetblue" to 0x5f9ea0, "chartreuse" to 0x7fff00, "chocolate" to 0xd2691e,
        "coral" to 0xff7f50, "cornflowerblue" to 0x6495ed, "cornsilk" to 0xfff8dc, "crimson" to 0xdc143c,
        "cyan" to 0x00ffff, "darkblue" to 0x00008b, "darkcyan" to 0x008b8b, "darkgoldenrod" to 0xb8860b,
        "darkgray" to 0xa9a9a9, "darkgreen" to 0x006400, "darkgrey" to 0xa9a9a9, "darkkhaki" to 0xbdb76b,
        "darkmagenta" to 0x8b008b, "darkolivegreen" to 0x556b2f, "darkorange" to 0xff8c00, "darkorchid" to 0x9932cc,
        "darkred" to 0x8b0000, "darksalmon" to 0xe9967a, "darkseagreen" to 0x8fbc8f, "darkslateblue" to 0x483d8b,
        "darkslategray" to 0x2f4f4f, "darkslategrey" to 0x2f4f4f, "darkturquoise" to 0x00ced1, "darkviolet" to 0x9400d3,
        "deeppink" to 0xff1493, "deepskyblue" to 0x00bfff, "dimgray" to 0x696969, "dimgrey" to 0x696969,
        "dodgerblue" to 0x1e90ff, "firebrick" to 0xb22222, "floralwhite" to 0xfffaf0, "forestgreen" to 0x228b22,
        "fuchsia" to 0xff00ff, "gainsboro" to 0xdcdcdc, "ghostwhite" to 0xf8f8ff, "gold" to 0xffd700,
        "goldenrod" to 0xdaa520, "gray" to 0x808080, "green" to 0x008000, "greenyellow" to 0xadff2f,
        "grey" to 0x808080, "honeydew" to 0xf0fff0, "hotpink" to 0xff69b4, "indianred" to 0xcd5c5c,
        "indigo" to 0x4b0082, "ivory" to 0xfffff0, "khaki" to 0xf0e68c, "lavender" to 0xe6e6fa,
        "lavenderblush" to 0xfff0f5, "lawngreen" to 0x7cfc00, "lemonchiffon" to 0xfffacd, "lightblue" to 0xadd8e6,
        "lightcoral" to 0xf08080, "lightcyan" to 0xe0ffff, "lightgoldenrodyellow" to 0xfafad2, "lightgray" to 0xd3d3d3,
        "lightgreen" to 0x90ee90, "lightgrey" to 0xd3d3d3, "lightpink" to 0xffb6c1, "lightsalmon" to 0xffa07a,
        "lightseagreen" to 0x20b2aa, "lightskyblue" to 0x87cefa, "lightslategray" to 0x778899, "lightslategrey" to 0x778899,
        "lightsteelblue" to 0xb0c4de, "lightyellow" to 0xffffe0, "lime" to 0x00ff00, "limegreen" to 0x32cd32,
        "linen" to 0xfaf0e6, "magenta" to 0xff00ff, "maroon" to 0x800000, "mediumaquamarine" to 0x66cdaa,
        "mediumblue" to 0x0000cd, "mediumorchid" to 0xba55d3, "mediumpurple" to 0x9370db, "mediumseagreen" to 0x3cb371,
        "mediumslateblue" to 0x7b68ee, "mediumspringgreen" to 0x00fa9a, "mediumturquoise" to 0x48d1cc,
        "mediumvioletred" to 0xc71585, "midnightblue" to 0x191970, "mintcream" to 0xf5fffa, "mistyrose" to 0xffe4e1,
        "moccasin" to 0xffe4b5, "navajowhite" to 0xffdead, "navy" to 0x000080, "oldlace" to 0xfdf5e6,
        "olive" to 0x808000, "olivedrab" to 0x6b8e23, "orange" to 0xffa500, "orangered" to 0xff4500,
        "orchid" to 0xda70d6, "palegoldenrod" to 0xeee8aa, "palegreen" to 0x98fb98, "paleturquoise" to 0xafeeee,
        "palevioletred" to 0xdb7093, "papayawhip" to 0xffefd5, "peachpuff" to 0xffdab9, "peru" to 0xcd853f,
        "pink" to 0xffc0cb, "plum" to 0xdda0dd, "powderblue" to 0xb0e0e6, "purple" to 0x800080,
        "rebeccapurple" to 0x663399, "red" to 0xff0000, "rosybrown" to 0xbc8f8f, "royalblue" to 0x4169e1,
        "saddlebrown" to 0x8b4513, "salmon" to 0xfa8072, "sandybrown" to 0xf4a460, "seagreen" to 0x2e8b57,
        "seashell" to 0xfff5ee, "sienna" to 0xa0522d, "silver" to 0xc0c0c0, "skyblue" to 0x87ceeb,
        "slateblue" to 0x6a5acd, "slategray" to 0x708090, "slategrey" to 0x708090, "snow" to 0xfffafa,
        "springgreen" to 0x00ff7f, "steelblue" to 0x4682b4, "tan" to 0xd2b48c, "teal" to 0x008080,
        "thistle" to 0xd8bfd8, "tomato" to 0xff6347, "turquoise" to 0x40e0d0, "violet" to 0xee82ee,
        "wheat" to 0xf5deb3, "white" to 0xffffff, "whitesmoke" to 0xf5f5f5, "yellow" to 0xffff00,
        "yellowgreen" to 0x9acd32
    )

    /** Для тестов: сколько имён знает разбор. */
    val namedCount: Int get() = NAMED.size
}

/**
 * Облегчённая разметка текстов редактора: `***жирный курсив***`, `**жирный**`, `*курсив*`,
 * `__подчёркнутый__`, `~~зачёркнутый~~`, `^^надчёркнутый^^`. Один регэксп компилятора
 * (`Context::RICH_TEXT_RE`), сканирование слева направо, без вложенности; HTML — это текст,
 * перевод строки остаётся в тексте.
 */
internal object PopupMarkup {

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val overline: Boolean = false
    )

    private val RICH_TEXT = Regex("""\*\*\*([^*]+)\*\*\*|\*\*([^*]+)\*\*|~~([^~]+)~~|__([^_]+)__|\^\^([^^]+)\^\^|\*([^*]+)\*""")

    fun parse(text: String?): List<Span> {
        if (text.isNullOrEmpty()) return emptyList()
        val spans = ArrayList<Span>()
        var position = 0
        for (match in RICH_TEXT.findAll(text)) {
            if (match.range.first > position) spans += Span(text.substring(position, match.range.first))
            val groups = match.groupValues
            spans += when {
                groups[1].isNotEmpty() -> Span(groups[1], bold = true, italic = true)
                groups[2].isNotEmpty() -> Span(groups[2], bold = true)
                groups[3].isNotEmpty() -> Span(groups[3], strike = true)
                groups[4].isNotEmpty() -> Span(groups[4], underline = true)
                groups[5].isNotEmpty() -> Span(groups[5], overline = true)
                else -> Span(groups[6], italic = true)
            }
            position = match.range.last + 1
        }
        if (position < text.length) spans += Span(text.substring(position))
        return spans
    }
}

/**
 * Переменные `{{key}}`. Статические — из `design.variables` (объект или старый массив
 * `{key, value}`), подставляются при разборе дизайна, до разметки. Витринные
 * ([RUNTIME]) — при показе: значение статической переменной с таким ключом игнорируется,
 * как у компилятора. Неизвестный ключ остаётся токеном как есть.
 */
internal object PopupV3Variables {

    val RUNTIME = setOf("promo_code", "coupon_code", "campaign_end", "remaining", "progress", "cart_total", "wheel_prize")

    private val TOKEN = Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*\}\}""")
    private val PROMO_TOKEN = Regex("""\{\{\s*promo_code\s*\}\}""")

    /** Суффикс платформенной ссылки: `{{link}}` → `link_android`, потом `link`. */
    const val PLATFORM_SUFFIX = "_android"

    fun parse(raw: Any?): Map<String, String> = when (raw) {
        is JSONObject -> raw.keys().asSequence().associateWith { key -> PopupV3Values.string(raw.opt(key)).orEmpty() }
        is JSONArray -> (0 until raw.length()).mapNotNull { index ->
            val item = raw.opt(index) as? JSONObject ?: return@mapNotNull null
            val key = PopupV3Values.present(item.opt("key")) ?: return@mapNotNull null
            key to PopupV3Values.string(item.opt("value")).orEmpty()
        }.toMap()
        else -> emptyMap()
    }

    /** Статические переменные; [link] — ссылка: сначала платформенный вариант ключа. */
    fun applyStatic(text: String, variables: Map<String, String>, link: Boolean = false): String {
        if (variables.isEmpty() || !text.contains("{{")) return text
        return TOKEN.replace(text) { match ->
            val key = match.groupValues[1]
            when {
                key in RUNTIME -> match.value
                link && variables.containsKey(key + PLATFORM_SUFFIX) -> variables.getValue(key + PLATFORM_SUFFIX)
                variables.containsKey(key) -> variables.getValue(key)
                else -> match.value
            }
        }
    }

    /** Витринные переменные при показе; чего нет в [values] — остаётся токеном. */
    fun applyRuntime(text: String, values: Map<String, String>): String {
        if (values.isEmpty() || !text.contains("{{")) return text
        return TOKEN.replace(text) { match ->
            val key = match.groupValues[1]
            if (key in RUNTIME) values[key] ?: match.value else match.value
        }
    }

    fun hasPromoToken(text: String): Boolean = PROMO_TOKEN.containsMatchIn(text)
}

/** Ссылки и адреса картинок из дизайна. */
internal object PopupV3Links {

    private val BLOCKED = Regex("^\\s*(javascript|data|vbscript):", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("^https?://", RegexOption.IGNORE_CASE)

    /**
     * Ссылка, которую можно открыть: непустая, без `javascript:`/`data:`. Deep link приложения
     * (`myapp://…`) разрешён — в отличие от web, где компилятор оставляет только http/https/mailto/tel.
     */
    fun safe(url: String?): String? {
        val text = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (BLOCKED.containsMatchIn(text)) return null
        // Токен, который нечем подставить, — не ссылка.
        if (text.contains("{{")) return null
        return text
    }

    /** Адрес картинки: только http(s). */
    fun image(url: String?): String? = url?.trim()?.takeIf { IMAGE.containsMatchIn(it) && !it.contains("{{") }
}
