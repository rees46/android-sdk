package com.personalization.ui.widgets

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.personalization.PreferencesPartition
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
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationStoryCover
import com.personalization.ui.components.PersonalizationStoryItem
import com.personalization.ui.components.PersonalizationStoryPrice
import com.personalization.ui.components.PersonalizationStoryTextBlock
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

    /** Цвет темнее этой яркости — слайд рисуется тёмными токенами. */
    private const val DARK_LUMINANCE = 0.5

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

    /** API v3 (R2): цвет слайда — его `background_color`; нет его или это не цвет — `null`. */
    fun ownColor(slide: Slide): Int? = parseColor(slide.ownBackgroundColor)

    /**
     * Слайд рисуется тёмными токенами: медиа, а на API v3 ещё и слайд, закрашенный тёмным
     * `background_color` (светлый текст и значки на нём читаются).
     */
    fun isDark(slide: Slide, v3: Boolean): Boolean =
        isMedia(slide, v3) || (v3 && ownColor(slide)?.let { ColorUtils.calculateLuminance(it) < DARK_LUMINANCE } == true)

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

    /**
     * Прозрачность подложки текстового блока на API v3 (R4), в процентах 0…100: `"NN%"`, число 0–100 или
     * доля 0–1 (`0.4`, `1` — 100 %). Не число — `null`.
     */
    fun opacityPercent(value: String?): Float? {
        val text = value?.trim().orEmpty()
        val percent = if (text.endsWith("%")) {
            text.removeSuffix("%").trim().toFloatOrNull()
        } else {
            text.toFloatOrNull()?.let { if (it <= 1f) it * 100f else it }
        }
        return percent?.takeIf { !it.isNaN() && !it.isInfinite() }?.coerceIn(0f, 100f)
    }

    /** Подложка текстового блока на API v3: цвет с прозрачностью [opacityPercent]; 0 или не число — без фона. */
    fun textBlockColorV3(element: TextBlockElement): Int? {
        val opacity = opacityPercent(element.textBackgroundColorOpacity) ?: return null
        if (opacity <= 0f) return null
        val color = parseColor(element.textBackgroundColor) ?: return null
        return ColorUtils.setAlphaComponent(color, Math.round(opacity * 255f / 100f))
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
