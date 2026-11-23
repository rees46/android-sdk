package com.personalization.ui.widgets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.personalization.PreferencesPartition
import com.personalization.R
import com.personalization.Product
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.stories.models.Slide
import com.personalization.stories.models.Story
import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.ProductsElement
import com.personalization.stories.models.elements.TextBlockElement
import com.personalization.ui.PersonalizationTheme
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryItem
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
import com.personalization.ui.components.StorySupport
import androidx.core.graphics.ColorUtils
import java.math.BigDecimal
import org.json.JSONObject

/**
 * Перевод моделей сторис SDK (те же, что у старого `StoriesView`) в данные компонентов кита.
 * Отдельно от вьюх — чтобы проверялось тестами без экрана.
 */
internal object StoriesMapping {

    /** Длительность слайда, когда админка её не задала или задала 0 (A7). */
    const val DEFAULT_DURATION_MS = 10_000L

    const val TYPE_IMAGE = "image"
    const val TYPE_VIDEO = "video"

    /** Скрим медиа-слайда без текста и товара — 50%, всех остальных — 30% (C3). */
    const val SCRIM_STRONG = 128
    const val SCRIM_LIGHT = 77

    /** Цена товара на слайде: промокод важнее скидки, скидка важнее просто цены. */
    data class Price(
        val type: PersonalizationStoryPrice.Type,
        val price: String,
        val oldPrice: String?,
        val discount: String?,
        val promoCode: String?
    )

    /**
     * Цена товарного слайда (C10, F10). Старая цена имеет смысл, только если она численно
     * больше обычной. С промокодом крупно — цена по промокоду, зачёркнута старая цена, а без
     * неё — обычная, если она выше цены по промокоду (F10). Скидка — процент как есть,
     * с дробной частью.
     *
     * [slidePromo] — промокод текстового блока того же слайда (H2): сами блоки на товарном
     * слайде не рисуются, а код идёт в блок цены, как в макете, если у товара своего нет.
     * Цена тогда обычная, зачёркнута только старая (H3).
     */
    fun price(product: Product, slidePromo: String? = null): Price {
        val regular = product.priceDecimal ?: Decimals.parse(product.price)
        val old = product.oldPrice.takeIf {
            it.isNotEmpty() && Decimals.greater(product.oldPriceDecimal ?: Decimals.parse(it), regular)
        }
        val promo = product.promocode.trim().takeIf { it.isNotEmpty() && it != "null" }
        if (promo != null) {
            val withPromo = product.priceWithPromocode.takeIf { it.isNotEmpty() }
            val promoValue = withPromo?.let { product.priceWithPromocodeDecimal ?: Decimals.parse(it) }
            val crossed = when {
                old != null -> old
                Decimals.greater(regular, promoValue) -> product.price
                else -> null
            }
            return Price(PersonalizationStoryPrice.Type.PROMO_CODE, withPromo ?: product.price, crossed, null, promo)
        }
        if (slidePromo != null) {
            return Price(PersonalizationStoryPrice.Type.PROMO_CODE, product.price, old, null, slidePromo)
        }
        val percent = Decimals.parse(product.discountPercent)?.takeIf { it.signum() > 0 }
        if (percent != null) {
            val text = percent.stripTrailingZeros().toPlainString() + "%"
            return Price(PersonalizationStoryPrice.Type.DISCOUNT, product.price, old, text, null)
        }
        return Price(PersonalizationStoryPrice.Type.PRICE, product.price, old, null, null)
    }

    /** Товар карусели «Используемые товары» → карточка кита. */
    fun cardProduct(product: Product): PersonalizationProduct {
        val regular = product.priceDecimal ?: Decimals.parse(product.price)
        val old = product.oldPrice.takeIf {
            it.isNotEmpty() && Decimals.greater(product.oldPriceDecimal ?: Decimals.parse(it), regular)
        }
        val discount = product.discount.takeIf { old != null && it.isNotEmpty() && it != "0" }
            ?.let { if (it.endsWith("%")) it else "$it%" }
            ?.let { if (it.startsWith("-")) it else "-$it" }
        return PersonalizationProduct(
            id = product.id,
            name = product.name,
            price = product.price,
            imageUrl = product.image.takeIf { it.isNotEmpty() },
            brand = product.brand.takeIf { it.isNotEmpty() },
            oldPrice = old,
            discount = discount
        )
    }

    fun slideIds(story: Story): List<String> = List(story.slidesCount) { story.getSlide(it).id }

    /** Сторис просмотрена: так сказал сервер или видны все её слайды (A3). */
    fun isViewed(story: Story, seen: Set<String>): Boolean = isViewed(slideIds(story), seen, story.isViewed)

    fun isViewed(slideIds: List<String>, seen: Set<String>, serverViewed: Boolean): Boolean =
        serverViewed || (slideIds.isNotEmpty() && slideIds.all { it in seen })

    /**
     * С какого слайда открыть сторис (A4): часть слайдов видна — с первого невиденного;
     * не видно ничего — с `start_position` сервера; просмотрена — с начала.
     */
    fun startSlide(slideIds: List<String>, seen: Set<String>, serverViewed: Boolean, startPosition: Int): Int {
        if (slideIds.isEmpty()) return 0
        if (isViewed(slideIds, seen, serverViewed)) return 0
        val firstUnseen = slideIds.indexOfFirst { it !in seen }
        if (slideIds.any { it in seen }) return firstUnseen
        return startPosition.coerceIn(0, slideIds.lastIndex)
    }

    fun startSlide(story: Story, seen: Set<String>): Int =
        startSlide(slideIds(story), seen, story.isViewed, story.startPosition)

    /**
     * Обложка в ленте (A5). Закреплённая — всегда кольцо Semantic/Warning со звездой;
     * просмотренная — сплошное кольцо без сегментов (у закреплённой — сплошное
     * предупреждающее). Частично просмотренная — сегмент i отмечен, если виден слайд i.
     */
    fun item(story: Story, seen: Set<String>): PersonalizationStoryItem {
        val viewed = isViewed(story, seen)
        val state = when {
            story.isPinned -> PersonalizationStoryCover.State.PINNED
            viewed -> PersonalizationStoryCover.State.VIEWED
            else -> PersonalizationStoryCover.State.DEFAULT
        }
        val ids = slideIds(story)
        return PersonalizationStoryItem(
            id = story.id.toString(),
            title = story.name,
            imageUrl = story.avatar.takeIf { it.isNotEmpty() },
            state = state,
            segments = if (viewed) 1 else ids.size.coerceAtLeast(1),
            seenSegments = if (viewed) emptySet() else ids.indices.filterTo(HashSet()) { ids[it] in seen }
        )
    }

    /**
     * Обложка сторис API v3 (R8), как в веб-SDK: просмотрена — по `viewed` ленты (и уходу с неё вперёд в
     * этой сессии), без своих отметок слайдов; вид — кольцо кита.
     */
    fun itemV3(story: Story): PersonalizationStoryItem {
        val state = when {
            story.isPinned -> PersonalizationStoryCover.State.PINNED
            story.isViewed -> PersonalizationStoryCover.State.VIEWED
            else -> PersonalizationStoryCover.State.DEFAULT
        }
        return PersonalizationStoryItem(
            id = story.id.toString(),
            title = story.name,
            imageUrl = story.avatar.takeIf { it.isNotEmpty() },
            state = state,
            segments = if (story.isViewed) 1 else story.slidesCount.coerceAtLeast(1),
            seenSegments = emptySet()
        )
    }

    /** Время слайда из админки; нет или 0 — 10 с (A7). Видео идёт по своей длине (C4). */
    fun durationMs(slide: Slide): Long =
        if (slide.adminDurationSeconds > 0) slide.adminDurationSeconds * 1000L else DEFAULT_DURATION_MS

    /**
     * Трекаются только целые положительные id (A6): динамические товарные слайды
     * приходят с id вида `39_0`, и сервер их не принимает.
     */
    fun isTrackable(storyId: Int, slideId: String): Boolean {
        if (storyId <= 0) return false
        val number = slideId.toLongOrNull() ?: return false
        return number > 0 && number.toString() == slideId
    }

    fun product(slide: Slide): ProductElement? =
        slide.getElements().filterIsInstance<ProductElement>().firstOrNull { it.item != null }

    fun products(slide: Slide): ProductsElement? =
        slide.getElements().filterIsInstance<ProductsElement>().firstOrNull { it.getProducts().isNotEmpty() }

    /** Шапка слайда (`header`, R1): первая, у которой есть заголовок, подзаголовок или иконка. */
    fun header(slide: Slide): HeaderElement? =
        slide.getElements().filterIsInstance<HeaderElement>()
            .firstOrNull { it.title.isNotBlank() || it.subtitle.isNotBlank() || it.icon.isNotBlank() }

    fun buttons(slide: Slide): List<ButtonElement> =
        slide.getElements().filterIsInstance<ButtonElement>().filter { it.title.isNotBlank() }

    /** Пустые текстовые блоки пропускаются (C15). */
    fun textBlocks(slide: Slide): List<TextBlockElement> =
        slide.getElements().filterIsInstance<TextBlockElement>().filter { it.textInput.isNotBlank() }

    /** Медиа-слайд — с картинкой или видео фоном: он рисуется тёмными токенами. */
    fun isMedia(slide: Slide): Boolean = slide.background.isNotEmpty()

    fun isVideo(slide: Slide): Boolean = slide.type == "video" && slide.background.isNotEmpty()

    /** Медиа на API v3 решает `type`, как в веб-SDK (R6): `image` или `video`; на старой ленте — фон. */
    fun isMedia(slide: Slide, v3: Boolean): Boolean = if (v3) slide.type == TYPE_IMAGE || slide.type == TYPE_VIDEO else isMedia(slide)

    fun isVideo(slide: Slide, v3: Boolean): Boolean = if (v3) slide.type == TYPE_VIDEO else isVideo(slide)

    /**
     * API v3: слайд показывается, как `show()` веб-SDK — `image`, `video` или слайд с элементом `product`;
     * остальные пропускаются ([StoriesViewerState.SlideShape.skipped]).
     */
    fun isShownV3(slide: Slide): Boolean =
        slide.type == TYPE_IMAGE || slide.type == TYPE_VIDEO || slide.getElements().any { it is ProductElement }

    /** API v3 (R2): цвет слайда — его `background_color` ([StoriesColor]); нет его или это не цвет — `null`. */
    fun ownColor(slide: Slide): Int? = StoriesColor.parse(slide.ownBackgroundColor)

    /**
     * Слайд рисуется тёмными токенами: медиа, а на API v3 ещё и слайд, на чьём `background_color` светлый
     * текст кита контрастнее тёмного ([prefersLightText]).
     */
    fun isDark(context: Context, slide: Slide, v3: Boolean): Boolean {
        if (isMedia(slide, v3)) return true
        if (!v3) return false
        val color = ownColor(slide) ?: return false
        val light = PersonalizationTheme.color(StorySupport.darkContext(context), R.color.personalization_text_primary)
        val dark = PersonalizationTheme.color(PollUi.lightContext(context), R.color.personalization_text_primary)
        return prefersLightText(color, light, dark)
    }

    /**
     * На цвете [background] светлый текст [light] контрастнее тёмного [dark] — по контрасту WCAG.
     * Полупрозрачный цвет — поверх чёрного окна просмотра, как его видно.
     */
    fun prefersLightText(background: Int, light: Int, dark: Int): Boolean {
        val opaque = ColorUtils.compositeColors(background, Color.BLACK)
        return ColorUtils.calculateContrast(light, opaque) > ColorUtils.calculateContrast(dark, opaque)
    }

    /**
     * Контекст слайда: тёмные токены ([isDark]); на API v3 у слайда со своим светлым цветом — светлые и в
     * ночной теме хоста; иначе — тема хоста.
     */
    fun slideContext(context: Context, slide: Slide, v3: Boolean): Context = when {
        isDark(context, slide, v3) -> StorySupport.darkContext(context)
        v3 && ownColor(slide) != null -> PollUi.lightContext(context)
        else -> context
    }

    /** Цвет слайда (R2): на API v3 — `background_color`, без него у медиа чёрный, иначе [card]; на старой ленте — как было. */
    fun slideColor(slide: Slide, v3: Boolean, card: Int): Int = when {
        v3 -> ownColor(slide) ?: if (isMedia(slide, true)) Color.BLACK else card
        isMedia(slide) -> letterboxColor(slide)
        else -> card
    }

    /**
     * Длина слайда с GIF (G2): большее из времени админки и одного прохода GIF. Проход
     * неизвестен (загрузчик хоста, файл не разобрали) — время админки.
     */
    fun animatedDurationMs(serverMs: Long, loopMs: Long?): Long =
        if (loopMs == null || loopMs <= 0) serverMs else maxOf(serverMs, loopMs)

    /** Скрим сверху и снизу есть у каждого слайда; 50% — только у голого медиа (C3). */
    fun scrimAlpha(slide: Slide, v3: Boolean = false): Int {
        val bare = isMedia(slide, v3) && product(slide) == null && textBlocks(slide).isEmpty()
        return if (bare) SCRIM_STRONG else SCRIM_LIGHT
    }

    /** Поля вокруг вписанного медиа: цвет слайда, без него чёрный (C1). */
    fun letterboxColor(slide: Slide): Int = parseColor(slide.backgroundColor) ?: Color.BLACK

    /** Ссылка товара: сначала диплинк платформы, потом сайт. */
    fun productLink(product: Product): String = product.deeplink.ifEmpty { product.url }

    /**
     * Куда ведёт кнопка (C11): своя ссылка (`link_android`, затем `link` — так её уже
     * собирает модель), без неё — товар слайда.
     */
    fun buttonLink(button: ButtonElement, product: Product?): String =
        button.link.ifEmpty { product?.let(::productLink).orEmpty() }

    /** Подпись над промокодом (C9): `title` элемента товара, без него — текст хоста. */
    fun promoCaption(element: ProductElement, fallback: CharSequence): CharSequence =
        element.title.trim().ifEmpty { fallback }

    /** Промокод текстового блока (C12); модель отдаёт `"null"` для явного null. */
    fun textPromocode(element: TextBlockElement): String? =
        element.promocode?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /** Промокод слайда для блока цены товара (H2): первый текстовый блок с непустым кодом. */
    fun slidePromocode(slide: Slide): String? =
        slide.getElements().filterIsInstance<TextBlockElement>().firstNotNullOfOrNull(::textPromocode)

    /** Интерлиньяж из админки; без него — `null`, и блок берёт высоту строки пресета. */
    fun lineHeightSp(element: TextBlockElement): Float? =
        if (element.textLineSpacing > 0) (element.fontSize * element.textLineSpacing).toFloat() else null

    /** Число в начале строки, как его читает `parseFloat` в JS. */
    private val LEADING_NUMBER = Regex("^\\s*([+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?)")

    /**
     * Прозрачность подложки текстового блока на API v3 (R4), как `calculateOpacity` веб-SDK: число в начале
     * строки — проценты (`"50%"` и `"50"` — 50 %, `"0.5"` — 0,5 %). Не число или вне 0…100 — `null`.
     */
    fun opacityPercent(value: String?): Float? {
        val number = LEADING_NUMBER.find(value ?: return null)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return number.takeIf { it in 0.0..100.0 }?.toFloat()
    }

    /**
     * Подложка текстового блока на API v3, как `calculateOpacity` веб-SDK: цвет ([StoriesColor]) с прозрачностью
     * [opacityPercent], которая заменяет прозрачность самого цвета (`#RRGGBBAA`, `rgba()`); прозрачности нет или она
     * не проценты 0…100 — цвет как есть, со своей прозрачностью; 0 % или нет цвета — без фона. Умолчаний старой
     * ленты (`#FFFFFF`, `0%`) нет.
     */
    fun textBlockColorV3(element: TextBlockElement): Int? {
        val color = StoriesColor.parse(element.ownBackgroundColor) ?: return null
        val raw = element.ownBackgroundOpacity?.takeIf { it.isNotEmpty() } ?: return color
        val percent = opacityPercent(raw) ?: return color
        val alpha = Math.round(255 * percent / 100f)
        return if (alpha <= 0) null else ColorUtils.setAlphaComponent(color, alpha)
    }

    /** Подложка текстового блока: цвет админки с её прозрачностью; 0% — блок без фона. */
    fun textBlockColor(element: TextBlockElement): Int? {
        val opacity = element.textBackgroundColorOpacity.trim().removeSuffix("%").toIntOrNull() ?: 0
        if (opacity <= 0) return null
        val color = parseColor(element.textBackgroundColor) ?: return null
        val alpha = (opacity.coerceAtMost(100) * 255) / 100
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun textAlign(element: TextBlockElement): PersonalizationStoryTextBlock.Align = when (element.textAlign.trim().lowercase()) {
        "center" -> PersonalizationStoryTextBlock.Align.CENTER
        "right", "end" -> PersonalizationStoryTextBlock.Align.END
        else -> PersonalizationStoryTextBlock.Align.START
    }

    fun parseColor(value: String?): Int? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Color.parseColor(it.trim()) }.getOrNull() }
}

/**
 * Цвета ленты API v3 — один разбор на всех платформах: `#RGB`, `#RRGGBB`, `#RRGGBBAA` (прозрачность
 * последней, как в CSS) и `rgb()`/`rgba()` (каналы 0–255 или проценты, прозрачность 0–1 или проценты).
 * Остальное — не цвет: `null`.
 */
internal object StoriesColor {

    private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val FUNCTION = Regex("^rgba?\\((.*)\\)$", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)%?$")

    fun parse(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        HEX.matchEntire(text)?.let { match -> return hex(match.groupValues[1]) }
        val arguments = FUNCTION.matchEntire(text)?.groupValues?.get(1) ?: return null
        val parts = arguments.split(',', '/', ' ', '\t').filter { it.isNotEmpty() }
        if (parts.size != 3 && parts.size != 4) return null
        val red = channel(parts[0]) ?: return null
        val green = channel(parts[1]) ?: return null
        val blue = channel(parts[2]) ?: return null
        val alpha = if (parts.size == 4) alpha(parts[3]) ?: return null else 255
        return Color.argb(alpha, red, green, blue)
    }

    private fun hex(digits: String): Int = when (digits.length) {
        3 -> Color.rgb(digits.substring(0, 1).repeat(2).toInt(16), digits.substring(1, 2).repeat(2).toInt(16), digits.substring(2, 3).repeat(2).toInt(16))
        6 -> (0xFF000000L or digits.toLong(16)).toInt()
        else -> ((digits.substring(6, 8).toLong(16) shl 24) or digits.substring(0, 6).toLong(16)).toInt()
    }

    /** Канал: 0–255 или проценты, зажатые в границы. */
    private fun channel(text: String): Int? {
        if (!NUMBER.matches(text)) return null
        val value = if (text.endsWith("%")) text.dropLast(1).toDouble() * 2.55 else text.toDouble()
        return Math.round(value.coerceIn(0.0, 255.0)).toInt()
    }

    /** Прозрачность: 0–1 или проценты, зажатые в границы. */
    private fun alpha(text: String): Int? {
        if (!NUMBER.matches(text)) return null
        val value = if (text.endsWith("%")) text.dropLast(1).toDouble() / 100 else text.toDouble()
        return Math.round(value.coerceIn(0.0, 1.0) * 255).toInt()
    }
}

/**
 * Числа из ответа сервера. Сырые поля (`price`, `oldprice`) — десятичные; если их нет,
 * число достаётся из отформатированной строки (`"1 190 900 ₽"`, `"19,99 €"`,
 * `"1,190.50 $"`): последний разделитель с одной-двумя цифрами после него — дробный.
 */
internal object Decimals {

    fun greater(left: BigDecimal?, right: BigDecimal?): Boolean =
        left != null && right != null && left > right

    fun parse(text: String?): BigDecimal? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toBigDecimalOrNull()?.let { return it }
        val kept = raw.filter { it.isDigit() || it == '.' || it == ',' }
        if (kept.none { it.isDigit() }) return null
        val lastDot = kept.lastIndexOf('.')
        val lastComma = kept.lastIndexOf(',')
        val separator = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 -> lastDot.takeIf { kept.count { it == '.' } == 1 && kept.length - lastDot - 1 in 1..2 }
            lastComma >= 0 -> lastComma.takeIf { kept.count { it == ',' } == 1 && kept.length - lastComma - 1 in 1..2 }
            else -> null
        }
        val normalized = buildString {
            kept.forEachIndexed { index, char ->
                when {
                    char.isDigit() -> append(char)
                    index == separator -> append('.')
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }
}

/** Просмотренные слайды: id сторис → id слайдов. */
internal interface SeenSlides {
    fun seen(storyId: Int): Set<String>
    fun markSeen(storyId: Int, slideId: String)
}

/** Без отметок слайдов — сторис API v3: открытие и просмотренность решает лента (R8). */
internal object NoSeenSlides : SeenSlides {
    override fun seen(storyId: Int): Set<String> = emptySet()
    override fun markSeen(storyId: Int, slideId: String) = Unit
}

/**
 * Просмотренные слайды магазина (A2): переживают перезапуск приложения. Свой файл
 * SharedPreferences рядом с файлом магазина — то же разбиение, что у SDK
 * ([PreferencesPartition.keyFor]); ключ — id сторис, значение — набор id слайдов.
 * Лента и просмотр одного магазина делят один экземпляр.
 */
internal class StoriesSeenStore internal constructor(private val preferences: SharedPreferences?) : SeenSlides {

    private val memory = HashMap<Int, MutableSet<String>>()

    @Synchronized
    override fun seen(storyId: Int): Set<String> = HashSet(slides(storyId))

    @Synchronized
    override fun markSeen(storyId: Int, slideId: String) {
        val slides = slides(storyId)
        if (!slides.add(slideId)) return
        preferences?.edit()?.putStringSet(key(storyId), HashSet(slides))?.apply()
    }

    private fun slides(storyId: Int): MutableSet<String> = memory.getOrPut(storyId) {
        HashSet(preferences?.getStringSet(key(storyId), null).orEmpty())
    }

    private fun key(storyId: Int) = "$KEY_PREFIX$storyId"

    companion object {
        private const val FILE_SUFFIX = "_stories"
        private const val KEY_PREFIX = "seen."
        private val stores = HashMap<String, StoriesSeenStore>()

        /** Хранилище только в памяти — для просмотра без SDK (витрина, тесты). */
        val local: StoriesSeenStore by lazy { StoriesSeenStore(null) }

        fun forShop(context: Context, shopKey: String): StoriesSeenStore = synchronized(stores) {
            stores.getOrPut(shopKey) {
                StoriesSeenStore(
                    context.applicationContext.getSharedPreferences(
                        PreferencesPartition.keyFor(shopKey) + FILE_SUFFIX,
                        Context.MODE_PRIVATE
                    )
                )
            }
        }

        fun forSdk(context: Context, sdk: SDK?, requestedShopId: String?): StoriesSeenStore =
            if (sdk == null) local else forShop(context, storageKey(sdk, requestedShopId))
    }
}

/**
 * Загрузка сторис-блока для V2. Тот же запрос, что у старого `StoriesView`, но с ошибкой:
 * её надо показать хосту, а не крутить скелетон вечно. Ответ — на главном потоке.
 * С [domain] (`apiV3Domain`) — лента API v3 ([StoriesApiV3]).
 */
internal object StoriesLoader {

    fun load(sdk: SDK, code: String, domain: String?, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) {
        if (domain != null) {
            StoriesApiV3.feed(sdk, domain, code, onLoaded, onFailed)
            return
        }
        val listener = object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                val stories = try {
                    parse(response)
                } catch (throwable: Throwable) {
                    MainThread.run { onFailed(ERROR_CODE, "stories/$code: ${throwable.message}") }
                    return
                }
                MainThread.run { onLoaded(stories) }
            }

            override fun onError(code: Int, msg: String?) {
                MainThread.run { onFailed(code, msg) }
            }
        }
        try {
            sdk.storiesManager.requestStories(code, listener)
        } catch (throwable: Throwable) {
            MainThread.run { onFailed(ERROR_CODE, throwable.message) }
        }
    }

    /**
     * Сторис без слайдов выбрасываются (C16): их нечего показывать. Повтор id тоже — сервер
     * иногда отдаёт одну сторис дважды; остаётся первая, иначе просмотренность склеивается.
     */
    fun parse(response: JSONObject?): List<Story> {
        val array = response?.optJSONArray("stories") ?: return emptyList()
        return List(array.length()) { Story(array.getJSONObject(it)) }
            .filter { it.slidesCount > 0 }
            .distinctBy { it.id }
    }

    /** Код ошибки, которая случилась до сервера или без кода HTTP. */
    const val ERROR_CODE = -1
}
