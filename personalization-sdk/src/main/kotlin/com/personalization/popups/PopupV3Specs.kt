package com.personalization.popups

/**
 * Тексты по умолчанию, когда поле элемента пустое (у web их подставляет компилятор из
 * `popups.compiler.*`). На устройстве — ресурсы SDK по локали; в тестах — свои.
 */
internal data class PopupV3Texts(
    val close: String = "Close",
    val detail: String = "Learn more",
    val image: String = "Image",
    val email: String = "Email",
    val subscribe: String = "Subscribe",
    val emailConsent: String = "I agree to receive emails",
    val emailError: String = "Enter a valid email and accept the terms",
    val formError: String = "Fill in the required fields",
    val send: String = "Send",
    val copy: String = "Copy",
    val copied: String = "Copied",
    val thanks: String = "Thank you!",
    val genderMale: String = "Male",
    val genderFemale: String = "Female",
    val sendFailed: String = "Could not send. Try again"
)

/**
 * Свойства элементов по типу — как их читает компилятор попапов: значение документа,
 * затем `DEFAULTS` типа, затем запасное значение компилятора. Цвета — ARGB, размеры —
 * логические px (на устройстве — dp).
 */
internal object PopupV3Specs {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val TRANSPARENT = 0
    private val ALIGNS = setOf("left", "center", "right")

    /** Коробка элемента: фон, текст, рамка, радиус, отступы, шрифт, прозрачность, тень. */
    data class Box(
        val background: Int,
        val textColor: Int,
        val borderWidth: Double,
        val borderColor: Int,
        val radius: Double,
        val paddingX: Double,
        val paddingY: Double,
        val fontFamily: String?,
        val fontSize: Double,
        val fontWeight: Double,
        val letterSpacing: Double,
        val lineHeight: Double,
        val opacity: Double,
        val shadowY: Double,
        val shadowBlur: Double,
        val shadowColor: Int
    )

    fun box(element: PopupV3Element): Box {
        val style = element.style
        val base = Box(
            background = style.color("bgColor", WHITE),
            textColor = style.color("textColor", 0xFF111827.toInt()),
            borderWidth = style.num("borderWidth", 0.0).coerceAtLeast(0.0),
            borderColor = style.color("borderColor", 0xFFD4D4D8.toInt()),
            radius = style.num("borderRadius", 0.0).coerceAtLeast(0.0),
            paddingX = style.num("paddingX", 0.0).coerceAtLeast(0.0),
            paddingY = style.num("paddingY", 0.0).coerceAtLeast(0.0),
            fontFamily = style.string("fontFamily"),
            fontSize = style.num("fontSize", 12.0),
            fontWeight = style.num("fontWeight", 500.0),
            letterSpacing = style.num("letterSpacing", 0.0),
            lineHeight = style.num("lineHeight", 1.35),
            opacity = style.num("opacity", 1.0).coerceIn(0.0, 1.0),
            shadowY = style.num("shadowY", 0.0),
            shadowBlur = style.num("shadowBlur", 0.0).coerceAtLeast(0.0),
            shadowColor = shadowColor(style.raw("shadowColor"))
        )
        return when (element.type) {
            PopupV3Element.BUTTON, PopupV3Element.CLOSE -> base.copy(background = TRANSPARENT, borderWidth = 0.0)
            PopupV3Element.EMAIL_COLLECTOR, PopupV3Element.FORM -> base.copy(
                background = style.color("bgColor", WHITE),
                textColor = style.color("textColor", 0xFF0F172A.toInt()),
                borderColor = style.color("borderColor", 0xFFE2E8F0.toInt()),
                radius = style.num("borderRadius", 12.0).coerceAtLeast(0.0),
                paddingX = style.num("paddingX", 12.0).coerceAtLeast(0.0),
                paddingY = style.num("paddingY", 12.0).coerceAtLeast(0.0)
            )
            PopupV3Element.RECOMMENDATION -> base.copy(
                background = TRANSPARENT, borderWidth = 0.0, paddingX = 0.0, paddingY = 0.0,
                shadowY = 0.0, shadowBlur = 0.0
            )
            else -> base
        }
    }

    /** Тень: к шестизначному hex компилятор дописывает альфу `55`, остальное — как есть. */
    private fun shadowColor(raw: Any?): Int {
        val parsed = CssColor.parse(raw)
        if (parsed == null || CssColor.isOpaqueHex6(raw)) {
            val rgb = (parsed ?: 0xFF000000.toInt()) and 0x00FFFFFF
            return (0x55 shl 24) or rgb
        }
        return parsed
    }

    /** Ссылка на всём элементе: только у text, box и image. */
    fun elementLink(element: PopupV3Element): String? = when (element.type) {
        PopupV3Element.TEXT, PopupV3Element.BOX, PopupV3Element.IMAGE -> element.style.link("linkHref")
        else -> null
    }

    data class Text(val message: String?, val align: String, val fontSize: Double, val fontWeight: Double)

    /** text и box: `message` с разметкой, его размер и начертание поверх общих. */
    fun text(element: PopupV3Element): Text {
        val style = element.style
        return Text(
            message = style.text("message"),
            align = style.enum("messageAlign", ALIGNS, "left"),
            fontSize = style.num("messageFontSize", style.num("fontSize", 12.0)),
            fontWeight = style.num("messageFontWeight", style.num("fontWeight", 500.0))
        )
    }

    data class Button(
        val action: String,
        val label: String,
        val href: String?,
        val copyText: String,
        val align: String,
        val background: Int,
        val textColor: Int,
        val fontSize: Double,
        val radius: Double,
        val paddingX: Double,
        val paddingY: Double,
        val fontFamily: String?
    )

    /** Кнопка: `link` (и неизвестное действие), `close`, `copy`; у старых документов `buttonHref`. */
    fun button(element: PopupV3Element, texts: PopupV3Texts): Button {
        val style = element.style
        val close = element.type == PopupV3Element.CLOSE
        val action = if (close) "close" else style.enum("buttonAction", setOf("link", "close", "copy"), "link")
        val label = style.text("buttonText") ?: if (close) "×" else texts.detail
        return Button(
            action = action,
            label = label,
            href = style.link("linkHref") ?: style.link("buttonHref"),
            copyText = style.text("copyText") ?: style.text("buttonText").orEmpty(),
            align = style.enum("buttonAlign", ALIGNS, "left"),
            background = style.color("buttonBgColor", if (close) TRANSPARENT else 0xFF2563EB.toInt()),
            textColor = style.color("buttonTextColor", if (close) 0xFF71717A.toInt() else WHITE),
            fontSize = style.num("buttonFontSize", if (close) 22.0 else 12.0),
            radius = style.num("buttonRadius", if (close) 999.0 else 0.0).coerceAtLeast(0.0),
            paddingX = style.num("buttonPaddingX", if (close) 8.0 else 0.0).coerceAtLeast(0.0),
            paddingY = style.num("buttonPaddingY", if (close) 2.0 else 0.0).coerceAtLeast(0.0),
            fontFamily = style.string("fontFamily")
        )
    }

    data class Close(val mode: String, val imageUrl: String?, val alt: String, val size: Double, val svg: String?)

    /**
     * Крестик: текст (`×`), картинка или SVG; размер иконки 8–128. Картинки или SVG нет —
     * текст. Из SVG вырезаны скрипты и обработчики, как у компилятора; рисуется он
     * стандартным крестиком цвета кнопки.
     */
    fun close(element: PopupV3Element, texts: PopupV3Texts): Close {
        val style = element.style
        val size = style.num("closeIconSize", style.num("buttonFontSize", 22.0)).coerceIn(8.0, 128.0)
        val alt = style.string("imageAlt") ?: texts.close
        return when (style.string("closeIconMode")) {
            "image" -> {
                val url = PopupV3Links.image(style.text("imageUrl"))
                if (url != null) Close("image", url, alt, size, null) else Close("text", null, alt, size, null)
            }
            "svg" -> {
                val svg = sanitizeSvg(style.string("closeSvg"))
                if (svg != null) Close("svg", null, alt, size, svg) else Close("text", null, alt, size, null)
            }
            else -> Close("text", null, alt, size, null)
        }
    }

    private fun sanitizeSvg(raw: String?): String? {
        val svg = raw?.trim() ?: return null
        val start = Regex("^<svg[\\s>]", RegexOption.IGNORE_CASE)
        if (!start.containsMatchIn(svg)) return null
        val cleaned = svg
            .replace(Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
        return cleaned.takeIf { start.containsMatchIn(it) }
    }

    data class Image(
        val show: Boolean,
        val url: String?,
        val alt: String,
        /** `auto`, `px`, `%` или `vh`. */
        val heightMode: String,
        val height: Double,
        val fit: String,
        val aspectRatio: Double?,
        val radius: Double,
        val marginBottom: Double
    )

    fun image(element: PopupV3Element, texts: PopupV3Texts): Image {
        val style = element.style
        val mode = when (val raw = style.string("imageHeightMode") ?: "auto") {
            "auto", "%", "vh" -> raw
            else -> "px"
        }
        val height = when (mode) {
            "%" -> style.num("imageHeight", 100.0).coerceIn(1.0, 100.0)
            "vh" -> style.num("imageHeight", 40.0).coerceIn(1.0, 100.0)
            "px" -> style.num("imageHeight", 120.0).coerceAtLeast(0.0)
            else -> 0.0
        }
        return Image(
            show = PopupV3Values.bool(style.own("showImage")) != false,
            url = style.text("imageUrl"),
            alt = style.string("imageAlt") ?: texts.image,
            heightMode = mode,
            height = height,
            fit = style.enum("imageObjectFit", setOf("cover", "contain", "fill", "none"), "cover"),
            aspectRatio = aspectRatio(style.string("imageAspectRatio")),
            radius = style.num("imageRadius", 0.0).coerceAtLeast(0.0),
            marginBottom = style.num("imageMarginBottom", 0.0).coerceAtLeast(0.0)
        )
    }

    private fun aspectRatio(raw: String?): Double? {
        val match = Regex("""^(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$""").matchEntire(raw?.trim() ?: return null) ?: return null
        val width = match.groupValues[1].toDouble()
        val height = match.groupValues[2].toDouble()
        return if (width > 0 && height > 0) width / height else null
    }

    /** Флаги подписки, которые может поставить согласие. */
    val CONSENT_FIELDS = listOf("email_bulk", "email_chain", "email_transactional")

    data class Email(
        val inline: Boolean,
        val placeholder: String,
        val submitText: String,
        val consentLabel: String,
        val consentFields: List<String>,
        val segmentId: String?,
        val errorMessage: String,
        val successTitle: String?,
        val successMessage: String?,
        val success: Success,
        val input: Input,
        val button: FormButton
    )

    data class Success(
        val background: Int,
        val color: Int,
        val align: String,
        val radius: Double,
        val paddingX: Double,
        val paddingY: Double,
        val titleColor: Int,
        val titleFontSize: Double,
        val fontSize: Double
    )

    data class Input(val background: Int, val color: Int, val borderColor: Int, val radius: Double, val fontSize: Double, val paddingY: Double)

    data class FormButton(
        val background: Int,
        val textColor: Int,
        val radius: Double,
        val fontSize: Double,
        val paddingX: Double,
        val paddingY: Double
    )

    fun email(element: PopupV3Element, texts: PopupV3Texts): Email {
        val style = element.style
        return Email(
            inline = PopupV3Values.string(style.raw("emailLayout")) == "inline",
            placeholder = style.text("emailPlaceholder") ?: texts.email,
            submitText = style.text("emailSubmitText") ?: texts.subscribe,
            consentLabel = emailConsentLabel(style) ?: texts.emailConsent,
            consentFields = emailConsentFields(style),
            segmentId = style.string("emailSegmentId")?.trim()?.takeIf { it.isNotEmpty() },
            errorMessage = style.text("emailErrorMessage") ?: texts.emailError,
            successTitle = style.text("emailSuccessTitle"),
            successMessage = style.text("emailSuccessMessage"),
            success = Success(
                background = style.color("emailSuccessBg", 0xFFECFDF5.toInt()),
                color = style.color("emailSuccessColor", 0xFF065F46.toInt()),
                align = style.enum("emailSuccessAlign", setOf("left", "right"), "center"),
                radius = style.num("emailSuccessRadius", 12.0).coerceAtLeast(0.0),
                paddingX = style.num("emailSuccessPaddingX", 14.0).coerceAtLeast(0.0),
                paddingY = style.num("emailSuccessPaddingY", 16.0).coerceAtLeast(0.0),
                titleColor = style.color("emailSuccessTitleColor", 0xFF047857.toInt()),
                titleFontSize = style.num("emailSuccessTitleFontSize", 16.0),
                fontSize = style.num("emailSuccessFontSize", 13.0)
            ),
            input = Input(
                background = style.color("emailInputBg", 0xFFF8FAFC.toInt()),
                color = style.color("emailInputColor", 0xFF0F172A.toInt()),
                borderColor = style.color("emailInputBorderColor", 0xFFCBD5E1.toInt()),
                radius = style.num("emailInputRadius", 10.0).coerceAtLeast(0.0),
                fontSize = maxOf(style.num("fontSize", 13.0), 12.0),
                paddingY = style.num("buttonPaddingY", 10.0).coerceAtLeast(0.0)
            ),
            button = formButton(style)
        )
    }

    private fun formButton(style: PopupV3Style) = FormButton(
        background = style.color("buttonBgColor", 0xFF2563EB.toInt()),
        textColor = style.color("buttonTextColor", WHITE),
        radius = style.num("buttonRadius", 10.0).coerceAtLeast(0.0),
        fontSize = style.num("buttonFontSize", 14.0),
        paddingX = style.num("buttonPaddingX", 18.0).coerceAtLeast(0.0),
        paddingY = style.num("buttonPaddingY", 10.0).coerceAtLeast(0.0)
    )

    /** Флаги согласия: `emailConsentFields` → старые `emailConsents[].field` → подписи → все три. */
    private fun emailConsentFields(style: PopupV3Style): List<String> {
        val own = PopupV3Values.strings(style.raw("emailConsentFields")).let { fields -> CONSENT_FIELDS.filter { it in fields } }
        if (own.isNotEmpty()) return own
        val legacy = objects(style.raw("emailConsents")).mapNotNull { PopupV3Values.string(it.opt("field")) }
            .let { fields -> CONSENT_FIELDS.filter { it in fields } }
        if (legacy.isNotEmpty()) return legacy
        val labels = buildList {
            if (style.string("emailConsentBulkLabel") != null) add("email_bulk")
            if (style.string("emailConsentChainLabel") != null) add("email_chain")
            if (style.string("emailConsentTransactionalLabel") != null) add("email_transactional")
        }
        return labels.ifEmpty { CONSENT_FIELDS }
    }

    private fun emailConsentLabel(style: PopupV3Style): String? {
        style.text("emailConsentLabel")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        objects(style.raw("emailConsents")).firstOrNull()?.let { first ->
            PopupV3Values.present(first.opt("label"))?.let { return PopupV3Variables.applyStatic(it, style.variables) }
        }
        return style.text("emailConsentBulkLabel")
    }

    data class Field(
        val id: String,
        val kind: String,
        val label: String,
        val placeholder: String,
        val required: Boolean,
        val propertyKey: String
    ) {
        /** Ключ профиля: стандартное поле или своё свойство клиента. */
        val profileKey: String get() = if (kind == "custom") propertyKey else kind
    }

    data class Form(
        val fields: List<Field>,
        val consentLabel: String?,
        val consentFields: List<String>,
        val segmentId: String?,
        val submitText: String,
        val errorMessage: String,
        val successTitle: String?,
        val successMessage: String?,
        val input: Input,
        val button: FormButton
    )

    val FORM_KINDS = listOf("email", "phone", "first_name", "last_name", "birthday", "gender", "custom")
    const val MAX_FORM_FIELDS = 8

    /** Форма профиля: поля как `Form#normalized_fields` компилятора, до 8. */
    fun form(element: PopupV3Element, texts: PopupV3Texts): Form {
        val style = element.style
        val fields = objects(style.own("formFields"))
            .filter { PopupV3Values.string(it.opt("kind")) in FORM_KINDS }
            .take(MAX_FORM_FIELDS)
            .mapIndexed { index, field ->
                Field(
                    id = PopupV3Values.string(field.opt("id")).orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "").ifEmpty { "field-$index" },
                    kind = PopupV3Values.string(field.opt("kind")).orEmpty(),
                    label = PopupV3Values.string(field.opt("label"))?.let { PopupV3Variables.applyStatic(it, style.variables) }.orEmpty(),
                    placeholder = PopupV3Values.string(field.opt("placeholder"))?.let { PopupV3Variables.applyStatic(it, style.variables) }.orEmpty(),
                    required = field.opt("required") == true,
                    propertyKey = PopupV3Values.string(field.opt("propertyKey")).orEmpty().lowercase().replace(Regex("[^a-z0-9_]"), "")
                )
            }
        return Form(
            fields = fields,
            consentLabel = style.text("formConsentLabel"),
            consentFields = PopupV3Values.strings(style.own("formConsentFields")).let { own -> CONSENT_FIELDS.filter { it in own } },
            segmentId = style.string("formSegmentId")?.trim()?.takeIf { it.isNotEmpty() },
            submitText = style.text("formSubmitText") ?: texts.send,
            errorMessage = style.text("formErrorMessage") ?: texts.formError,
            successTitle = style.text("formSuccessTitle"),
            successMessage = style.text("formSuccessMessage"),
            input = Input(
                background = style.color("formInputBg", 0xFFF8FAFC.toInt()),
                color = style.color("formInputColor", 0xFF0F172A.toInt()),
                borderColor = style.color("formInputBorderColor", 0xFFCBD5E1.toInt()),
                radius = style.num("formInputRadius", 10.0).coerceAtLeast(0.0),
                fontSize = maxOf(style.num("fontSize", 13.0), 12.0),
                paddingY = style.num("buttonPaddingY", 10.0).coerceAtLeast(0.0)
            ),
            button = formButton(style)
        )
    }

    data class Promo(
        val static: Boolean,
        /** Код как есть; у персонального — токен `{{promo_code}}`, его подставит показ. */
        val code: String,
        val label: String?,
        val showCopy: Boolean,
        val copyText: String,
        val copiedText: String,
        val applyHref: String?,
        val codeBackground: Int,
        val codeColor: Int,
        val codeRadius: Double,
        val codeFontSize: Double,
        val align: String
    )

    fun promo(element: PopupV3Element, texts: PopupV3Texts): Promo {
        val style = element.style
        val static = PopupV3Values.string(style.raw("promoSource")) == "static"
        return Promo(
            static = static,
            code = if (static) PopupV3Values.string(style.raw("promoStaticCode")).orEmpty().trim() else "{{promo_code}}",
            label = style.text("promoLabel"),
            showCopy = PopupV3Values.bool(style.raw("promoShowCopy")) != false,
            copyText = style.text("promoCopyText") ?: texts.copy,
            copiedText = style.text("promoCopiedText") ?: texts.copied,
            applyHref = style.link("promoApplyHref"),
            codeBackground = style.color("promoCodeBg", 0xFFFEF3C7.toInt()),
            codeColor = style.color("promoCodeColor", 0xFF92400E.toInt()),
            codeRadius = style.num("promoCodeRadius", 10.0).coerceAtLeast(0.0),
            codeFontSize = style.num("promoCodeFontSize", 20.0),
            align = style.enum("messageAlign", setOf("left", "right"), "center")
        )
    }

    data class Survey(
        val type: String,
        val scale: IntRange,
        val campaignId: String?,
        val campaignCode: String?,
        val question: String?,
        val minLabel: String?,
        val maxLabel: String?,
        val commentPlaceholder: String?,
        val submitText: String,
        val thanks: String,
        val scaleBackground: Int,
        val scaleColor: Int,
        val activeBackground: Int,
        val activeColor: Int,
        val button: FormButton
    ) {
        /**
         * Тело отзыва для `POST /api/v3/polls/{type}/reviews` (shop_id, did и sid добавляет
         * транспорт): у NPS код идёт в `category`, у CSAT и CES — в `campaign`.
         */
        fun reviewBody(rate: Int, comment: String?): LinkedHashMap<String, Any> = linkedMapOf<String, Any>().apply {
            put("channel", "popup")
            campaignCode?.let { put(if (type == "nps") "category" else "campaign", it) }
            put("rate", rate)
            comment?.trim()?.takeIf { it.isNotEmpty() }?.let { put("comment", it) }
        }
    }

    val SURVEY_SCALES = mapOf("nps" to 0..10, "csat" to 1..5, "ces" to 1..7)

    fun survey(element: PopupV3Element, texts: PopupV3Texts): Survey {
        val style = element.style
        val type = PopupV3Values.string(style.raw("surveyType")).takeIf { it in SURVEY_SCALES } ?: "nps"
        return Survey(
            type = type,
            scale = SURVEY_SCALES.getValue(type),
            campaignId = style.string("surveyCampaignId"),
            campaignCode = style.string("surveyCampaignCode"),
            question = style.text("surveyQuestion"),
            minLabel = style.text("surveyMinLabel"),
            maxLabel = style.text("surveyMaxLabel"),
            commentPlaceholder = style.text("surveyCommentPlaceholder"),
            submitText = style.text("surveySubmitText") ?: texts.send,
            thanks = style.text("surveyThanksText") ?: texts.thanks,
            scaleBackground = style.color("surveyScaleBg", 0xFFF3F4F6.toInt()),
            scaleColor = style.color("surveyScaleColor", 0xFF111827.toInt()),
            activeBackground = style.color("surveyScaleActiveBg", 0xFF2563EB.toInt()),
            activeColor = style.color("surveyScaleActiveColor", WHITE),
            button = formButton(style)
        )
    }

    /** Код блока рекомендаций; пустой — элемент не рисуется. */
    fun recommenderCode(element: PopupV3Element): String? = element.style.string("recommenderCode")?.trim()?.takeIf { it.isNotEmpty() }

    private fun objects(value: Any?): List<org.json.JSONObject> {
        val array = value as? org.json.JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.opt(it) as? org.json.JSONObject }
    }
}
