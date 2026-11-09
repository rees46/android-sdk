package com.personalization.popups

import org.json.JSONArray
import org.json.JSONObject

/**
 * Нормализованный документ `design` попапа: рамка, сетка строк и ячеек с элементами,
 * статические переменные. Строится один раз до показа, без UI — по нему рисует рендер
 * кита и его проверяют тесты на общих фикстурах.
 *
 * Правила — как у компилятора попапов на бэке (`DesignCompiler`): строки идут столбиком,
 * у строки доли колонок (`columns` в процентах или старое `parts`), `gap` 8 и `minHeight`
 * 110 по умолчанию; элемент лежит в ячейке `rowId` + `partIndex`, порядок в ячейке —
 * порядок массива; элементы без строки или за последней колонкой не рисуются. Дизайн без
 * строк — старый формат с `x/y`: элементы идут в потоке сверху вниз. Неизвестный тип
 * элемента пропускается с записью в лог — рендер не падает.
 */
internal class PopupV3Design private constructor(
    val frame: PopupV3Frame,
    val rows: List<Row>,
    /** Старый дизайн без строк: элементы в потоке по `y`, затем по `x`. `null` — сетка. */
    val free: List<PopupV3Element>?,
    val variables: Map<String, String>,
    /** Исходник для поиска токена `{{promo_code}}`. */
    private val source: String
) {

    class Row(
        val id: String,
        val fractions: List<Double>,
        val gap: Double,
        val minHeight: Double,
        val background: Int?,
        val backgroundImage: String?,
        val backgroundSize: String,
        val backgroundPosition: String,
        val backgroundRepeat: String,
        val cells: List<Cell>
    )

    class Cell(val align: String, val verticalAlign: String, val elements: List<PopupV3Element>)

    /** Все элементы документа в порядке показа. */
    val elements: List<PopupV3Element>
        get() = free ?: rows.flatMap { row -> row.cells.flatMap { it.elements } }

    /** Нечего показать: ни одного элемента. Такой попап не показывается. */
    val isEmpty: Boolean get() = elements.isEmpty()

    /** В дизайне есть токен `{{promo_code}}` или элемент персонального промокода. */
    val usesPromoCode: Boolean
        get() = PopupV3Variables.hasPromoToken(source) ||
            elements.any { it.type == PopupV3Element.PROMO_CODE && PopupV3Values.string(it.style.raw("promoSource")) != "static" }

    companion object {
        const val MAX_ROWS = 50
        const val MAX_COLUMNS = 6
        const val MAX_ELEMENTS = 200
        const val ROWS_GAP = 8.0

        private val ALIGNS = setOf("left", "center", "right")
        private val VERTICAL_ALIGNS = setOf("top", "center", "bottom")
        private val BACKGROUND_SIZES = setOf("cover", "contain", "auto")
        private val BACKGROUND_POSITIONS = setOf("center", "top", "bottom", "left", "right")
        private val BACKGROUND_REPEATS = setOf("no-repeat", "repeat", "repeat-x", "repeat-y")

        fun parse(json: JSONObject?, log: (String) -> Unit = {}): PopupV3Design {
            val design = json ?: JSONObject()
            val variables = PopupV3Variables.parse(design.opt("variables"))
            val frame = PopupV3Frame(design.optJSONObject("frameStyle") ?: JSONObject())
            val rowsJson = objects(design.opt("rows"))
            var elementsJson = objects(design.opt("elements"))
            if (elementsJson.size > MAX_ELEMENTS) {
                log("popups v3: ${elementsJson.size - MAX_ELEMENTS} element(s) over the limit of $MAX_ELEMENTS dropped")
                elementsJson = elementsJson.take(MAX_ELEMENTS)
            }
            val elements = elementsJson.mapNotNull { element(it, variables, log) }
            val source = design.toString()

            if (rowsJson.isEmpty()) {
                val free = elements
                    .withIndex()
                    .sortedWith(compareBy({ PopupV3Values.num(it.value.json.opt("y"), 0.0) }, { PopupV3Values.num(it.value.json.opt("x"), 0.0) }, { it.index }))
                    .map { it.value }
                return PopupV3Design(frame, emptyList(), free, variables, source)
            }

            var rowsList = rowsJson
            if (rowsList.size > MAX_ROWS) {
                log("popups v3: ${rowsList.size - MAX_ROWS} row(s) over the limit of $MAX_ROWS dropped")
                rowsList = rowsList.take(MAX_ROWS)
            }
            val placed = HashSet<PopupV3Element>()
            val rows = rowsList.map { row ->
                val id = PopupV3Values.string(row.opt("id")).orEmpty()
                val parts = columns(row, log)
                val sum = parts.sum().takeIf { it > 0 } ?: 1.0
                val cellsJson = row.optJSONArray("cells")
                val rowAlign = PopupV3Values.string(row.opt("align")).takeIf { it in ALIGNS }
                val rowVertical = PopupV3Values.string(row.opt("verticalAlign")).takeIf { it in VERTICAL_ALIGNS }
                val cells = parts.indices.map { index ->
                    val cell = cellsJson?.opt(index) as? JSONObject ?: JSONObject()
                    val inCell = elements.filter { it.rowId == id && it.partIndex == index }
                    placed += inCell
                    val align = PopupV3Values.string(cell.opt("align")).takeIf { it in ALIGNS } ?: rowAlign ?: "left"
                    val vertical = PopupV3Values.string(cell.opt("verticalAlign")).takeIf { it in VERTICAL_ALIGNS } ?: rowVertical ?: "top"
                    // Элемент со своей вертикалью: ячейка прижимается кверху, он встаёт сам.
                    val overridden = inCell.any { it.cellVerticalAlign != null }
                    Cell(align, if (overridden) "top" else vertical, inCell)
                }
                val image = PopupV3Links.image(PopupV3Values.string(row.opt("bgImageUrl")))
                Row(
                    id = id,
                    fractions = parts.map { it / sum },
                    gap = PopupV3Values.num(row.opt("gap"), 8.0),
                    minHeight = PopupV3Values.num(row.opt("minHeight"), 110.0),
                    background = PopupV3Values.present(row.opt("bgColor"))?.let(CssColor::parse),
                    backgroundImage = image,
                    backgroundSize = enum(row.opt("bgSize"), BACKGROUND_SIZES, "cover"),
                    backgroundPosition = enum(row.opt("bgPosition"), BACKGROUND_POSITIONS, "center"),
                    backgroundRepeat = enum(row.opt("bgRepeat"), BACKGROUND_REPEATS, "no-repeat"),
                    cells = cells
                )
            }
            elements.filter { it !in placed }.forEach { element ->
                log("popups v3: element ${element.id} dropped: no cell ${element.rowId}:${element.partIndex}")
            }
            return PopupV3Design(frame, rows, null, variables, source)
        }

        /** Доли колонок: проценты в `columns`, у старых документов — число `parts`. */
        private fun columns(row: JSONObject, log: (String) -> Unit): List<Double> {
            val columns = row.optJSONArray("columns")
            var parts = if (columns != null && columns.length() > 0) {
                (0 until columns.length()).map { PopupV3Values.num(columns.opt(it), 0.0).coerceAtLeast(0.0) }
            } else {
                List(PopupV3Values.num(row.opt("parts"), 1.0).toInt().coerceAtLeast(1)) { 1.0 }
            }
            if (parts.size > MAX_COLUMNS) {
                log("popups v3: row ${row.opt("id")} has ${parts.size} columns, kept $MAX_COLUMNS")
                parts = parts.take(MAX_COLUMNS)
            }
            return parts
        }

        private fun element(json: JSONObject, variables: Map<String, String>, log: (String) -> Unit): PopupV3Element? {
            val id = PopupV3Values.string(json.opt("id")).orEmpty()
            val type = PopupV3Values.string(json.opt("type"))
            if (type == null || type !in PopupV3Element.TYPES) {
                log("popups v3: element ${id.ifEmpty { "?" }} skipped: unknown type ${type ?: "(none)"}")
                return null
            }
            val style = PopupV3Style(type, json.opt("style") as? JSONObject ?: JSONObject(), variables)
            return PopupV3Element(
                id = id,
                type = type,
                rowId = PopupV3Values.string(json.opt("rowId")),
                partIndex = PopupV3Values.num(json.opt("partIndex"), 0.0).toInt(),
                style = style,
                json = json
            )
        }

        private fun objects(value: Any?): List<JSONObject> {
            val array = value as? JSONArray ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }
        }

        private fun enum(value: Any?, allowed: Set<String>, fallback: String): String =
            PopupV3Values.string(value)?.takeIf { it in allowed } ?: fallback
    }
}

/**
 * Рамка попапа (`frameStyle`) с переопределениями `breakpoints`. Запасные значения — пустой
 * дизайн нового попапа: ширина 92 %, не шире 480, высота по содержимому, фон белый,
 * рамка 1 `#e5e7eb`, радиус 16, отступ 20.
 */
internal class PopupV3Frame(private val json: JSONObject) {

    class Resolved(
        val width: Double,
        val widthMode: String,
        val height: Double,
        val heightMode: String,
        val minHeight: Double,
        val minHeightMode: String,
        /** `null` — без предела. */
        val maxWidth: Double?,
        val maxWidthMode: String,
        val background: Int,
        val borderWidth: Double,
        val borderColor: Int,
        val radius: Double,
        val padding: Double,
        val overlayEnabled: Boolean?,
        val overlayColor: Int,
        val overlayOpacity: Double,
        val lockBackgroundScroll: Boolean,
        val offsetX: Double,
        val offsetY: Double
    ) {
        /** Ширина рамки на экране [screenWidth] (логические px): режим ширины и предел. */
        fun frameWidth(screenWidth: Double): Double {
            val wanted = when (widthMode) {
                "px" -> width
                else -> screenWidth * width / 100.0
            }
            val limit = when {
                maxWidth == null || maxWidthMode == "none" -> Double.MAX_VALUE
                maxWidthMode == "px" -> maxWidth
                else -> screenWidth * maxWidth / 100.0
            }
            return minOf(wanted, limit).coerceAtLeast(0.0)
        }

        /** Высота по режиму; `null` — по содержимому. Проценты и `vh` — от высоты экрана. */
        fun frameHeight(screenHeight: Double): Double? = when (heightMode) {
            "px" -> height
            "%", "vh" -> screenHeight * height / 100.0
            else -> null
        }

        fun frameMinHeight(screenHeight: Double): Double = when (minHeightMode) {
            "%", "vh" -> screenHeight * minHeight / 100.0
            else -> minHeight
        }
    }

    private val breakpoints: List<Pair<Double, JSONObject>> = run {
        val array = json.optJSONArray("breakpoints") ?: return@run emptyList()
        (0 until array.length()).mapNotNull { index ->
            val item = array.opt(index) as? JSONObject ?: return@mapNotNull null
            val max = PopupV3Values.num(item.opt("mediaMaxWidth"), Double.NaN)
            if (max.isNaN()) null else max to item
        }
    }

    /**
     * Рамка для экрана шириной [screenWidth]: подходящие breakpoints (`screenWidth ≤
     * mediaMaxWidth`) накладываются от широкого к узкому — узкий побеждает, как CSS
     * при порядке 768 → 400. Переопределяются только ключи, которые в breakpoint есть.
     */
    fun resolve(screenWidth: Double): Resolved {
        val merged = JSONObject(json.toString())
        breakpoints
            .filter { screenWidth <= it.first }
            .sortedByDescending { it.first }
            .forEach { (_, overrides) ->
                OVERRIDABLE.filter { overrides.has(it) }.forEach { key -> merged.put(key, overrides.opt(key)) }
            }
        return read(merged)
    }

    private fun read(style: JSONObject): Resolved {
        val maxWidthMode = PopupV3Values.string(style.opt("maxWidthMode")).takeIf { it in MAX_WIDTH_MODES } ?: "px"
        val maxWidth = if (!style.has("maxWidth")) 480.0 else PopupV3Values.num(style.opt("maxWidth"), Double.NaN).takeIf { !it.isNaN() }
        return Resolved(
            width = PopupV3Values.num(style.opt("width"), 92.0),
            widthMode = PopupV3Values.string(style.opt("widthMode")).takeIf { it in WIDTH_MODES } ?: "%",
            height = PopupV3Values.num(style.opt("height"), 0.0),
            heightMode = PopupV3Values.string(style.opt("heightMode")).takeIf { it in HEIGHT_MODES } ?: "auto",
            minHeight = PopupV3Values.num(style.opt("minHeight"), 0.0).coerceAtLeast(0.0),
            minHeightMode = PopupV3Values.string(style.opt("minHeightMode")).takeIf { it in MIN_HEIGHT_MODES } ?: "px",
            maxWidth = maxWidth,
            maxWidthMode = maxWidthMode,
            background = CssColor.parse(style.opt("bgColor")) ?: WHITE,
            borderWidth = PopupV3Values.num(style.opt("borderWidth"), 1.0).coerceAtLeast(0.0),
            borderColor = CssColor.parse(style.opt("borderColor")) ?: BORDER,
            radius = PopupV3Values.num(style.opt("borderRadius"), 16.0).coerceAtLeast(0.0),
            padding = PopupV3Values.num(style.opt("padding"), 20.0).coerceAtLeast(0.0),
            overlayEnabled = PopupV3Values.bool(style.opt("overlayEnabled")),
            overlayColor = CssColor.parse(style.opt("overlayColor")) ?: BLACK,
            overlayOpacity = PopupV3Values.num(style.opt("overlayOpacity"), 0.5).coerceIn(0.0, 1.0),
            lockBackgroundScroll = PopupV3Values.bool(style.opt("lockBackgroundScroll")) == true,
            offsetX = PopupV3Values.num(style.opt("offsetX"), 0.0).coerceAtLeast(0.0),
            offsetY = PopupV3Values.num(style.opt("offsetY"), 0.0).coerceAtLeast(0.0)
        )
    }

    private companion object {
        val OVERRIDABLE = listOf("width", "widthMode", "height", "heightMode", "maxWidth", "maxWidthMode", "minHeight", "padding", "borderRadius")
        val WIDTH_MODES = setOf("px", "%", "vw")
        val HEIGHT_MODES = setOf("auto", "px", "%", "vh")
        val MIN_HEIGHT_MODES = setOf("px", "%", "vh")
        val MAX_WIDTH_MODES = setOf("none", "px", "%", "vw")
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val BORDER = 0xFFE5E7EB.toInt()
    }
}

/** Элемент дизайна в ячейке. Нормализованные свойства по типу — в [PopupV3Specs]. */
internal class PopupV3Element(
    val id: String,
    val type: String,
    val rowId: String?,
    val partIndex: Int,
    val style: PopupV3Style,
    val json: JSONObject
) {
    /** Своё выравнивание по горизонтали в ячейке; `null` — как у ячейки. */
    val cellAlign: String? get() = PopupV3Values.string(style.raw("cellAlign")).takeIf { it in ALIGNS }

    /** Своя вертикаль в ячейке; `null` — как у ячейки. */
    val cellVerticalAlign: String? get() = PopupV3Values.string(style.raw("cellVerticalAlign")).takeIf { it in VERTICAL_ALIGNS }

    /** Тянется на всю ширину ячейки, как у компилятора (`STRETCH`). */
    val stretch: Boolean get() = type in STRETCH

    companion object {
        const val TEXT = "text"
        const val BOX = "box"
        const val BUTTON = "button"
        const val CLOSE = "close"
        const val IMAGE = "image"
        const val EMAIL_COLLECTOR = "emailCollector"
        const val FORM = "form"
        const val PROMO_CODE = "promoCode"
        const val SURVEY = "survey"
        const val RECOMMENDATION = "recommendation"

        /** Элементы mobile (`ELEMENTS[mobile]` бэка). */
        val TYPES = setOf(TEXT, BOX, BUTTON, CLOSE, IMAGE, EMAIL_COLLECTOR, FORM, PROMO_CODE, SURVEY, RECOMMENDATION)
        private val STRETCH = setOf(IMAGE, EMAIL_COLLECTOR, FORM, PROMO_CODE, SURVEY, RECOMMENDATION)
        private val ALIGNS = setOf("left", "center", "right")
        private val VERTICAL_ALIGNS = setOf("top", "center", "bottom")
    }
}

/**
 * Стиль элемента: значение документа, иначе `DEFAULTS` типа (стиль нового элемента
 * в редакторе), иначе запасное значение компилятора у вызывающего. `null` и пустая строка —
 * отсутствие значения.
 *
 * У `emailCollector` и `form` в умолчания не входят раскладка, флаги согласий и поля: без них
 * компилятор рисует колонку, все три флага и форму без полей — так и ждут общие фикстуры.
 */
internal class PopupV3Style(
    private val type: String,
    private val json: JSONObject,
    val variables: Map<String, String>
) {
    fun raw(key: String): Any? {
        val value = json.opt(key)
        if (value != null && value !== JSONObject.NULL && !(value is String && value.isEmpty())) return value
        return DEFAULTS[type]?.get(key)
    }

    /** Значение есть в самом документе (без умолчаний). */
    fun own(key: String): Any? = json.opt(key)?.takeIf { it !== JSONObject.NULL }

    fun num(key: String, fallback: Double): Double = PopupV3Values.num(raw(key), fallback)

    fun color(key: String, fallback: Int): Int = CssColor.parse(raw(key)) ?: fallback

    fun string(key: String): String? = PopupV3Values.present(raw(key))

    /** Текст со статическими переменными (витринные остаются токенами до показа). */
    fun text(key: String): String? = string(key)?.let { PopupV3Variables.applyStatic(it, variables) }

    /** Ссылка со статическими переменными: сначала платформенный вариант ключа. */
    fun link(key: String): String? = string(key)?.let { PopupV3Variables.applyStatic(it, variables, link = true) }

    fun enum(key: String, allowed: Set<String>, fallback: String): String =
        PopupV3Values.string(raw(key))?.takeIf { it in allowed } ?: fallback

    private companion object {
        val DEFAULTS: Map<String, Map<String, Any>> = mapOf(
            PopupV3Element.TEXT to mapOf("fontSize" to 16, "bgColor" to "transparent", "messageAlign" to "left"),
            PopupV3Element.BOX to mapOf("bgColor" to "#f8fafc", "borderRadius" to 12, "paddingX" to 16, "paddingY" to 16),
            PopupV3Element.BUTTON to mapOf(
                "buttonAction" to "link", "buttonAlign" to "center", "buttonBgColor" to "#2563eb", "buttonTextColor" to "#ffffff",
                "buttonFontSize" to 14, "buttonPaddingX" to 20, "buttonPaddingY" to 10, "buttonRadius" to 10
            ),
            PopupV3Element.CLOSE to mapOf(
                "closeIconMode" to "text", "buttonAlign" to "right", "buttonBgColor" to "transparent", "buttonTextColor" to "#71717a",
                "buttonFontSize" to 22, "buttonPaddingX" to 8, "buttonPaddingY" to 2, "buttonRadius" to 999
            ),
            PopupV3Element.IMAGE to mapOf("bgColor" to "transparent", "imageHeightMode" to "auto", "imageObjectFit" to "cover", "imageRadius" to 8),
            PopupV3Element.EMAIL_COLLECTOR to mapOf("borderRadius" to 12, "buttonBgColor" to "#2563eb", "buttonTextColor" to "#ffffff"),
            PopupV3Element.FORM to mapOf("borderRadius" to 12, "buttonBgColor" to "#2563eb", "buttonTextColor" to "#ffffff"),
            PopupV3Element.PROMO_CODE to mapOf(
                "promoSource" to "personal", "promoShowCopy" to true, "promoCodeBg" to "#fef3c7", "promoCodeColor" to "#92400e",
                "promoCodeRadius" to 10, "promoCodeFontSize" to 20, "messageAlign" to "center", "bgColor" to "transparent"
            ),
            PopupV3Element.SURVEY to mapOf(
                "surveyType" to "nps", "surveyScaleBg" to "#f3f4f6", "surveyScaleColor" to "#111827",
                "surveyScaleActiveBg" to "#2563eb", "surveyScaleActiveColor" to "#ffffff",
                "buttonBgColor" to "#2563eb", "buttonTextColor" to "#ffffff"
            ),
            PopupV3Element.RECOMMENDATION to mapOf("bgColor" to "transparent")
        )
    }
}
