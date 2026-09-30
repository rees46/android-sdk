package com.personalization.ui.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.view.doOnLayout
import com.bumptech.glide.Glide
import com.personalization.SDK
import com.personalization.api.responses.product.Product
import com.personalization.ui.InternalPersonalizationUiApi
import com.personalization.ui.components.PersonalizationProduct
import org.json.JSONArray

/**
 * Категория, на которую можно перейти из поиска: из подсказок instant-поиска
 * (есть id) или из популярных категорий пустого запроса (только имя и ссылка).
 */
@InternalPersonalizationUiApi
data class PersonalizationSearchCategory(
    val id: String?,
    val name: String,
    val url: String?
)

/** Колбэки сети приходят с фонового потока — виджеты перекладывают их на главный. */
internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun run(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }
}

/**
 * Недавние запросы пользователя — локальная история виджета поиска, по магазину.
 *
 * Сервер тоже помнит последние запросы (`last_queries` в `search/blank`), но не умеет
 * забывать по одному, а в макете у каждого тега крестик и есть «Clear». Поэтому история
 * живёт в SharedPreferences: файл кита, ключ — id магазина.
 */
internal class RecentSearches(context: Context, private val shopKey: String, private val limit: Int) {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = preferences.getString(shopKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Кладёт [query] в начало, убирая дубль, и режет по [limit]. */
    fun add(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return load()
        val next = listOf(trimmed) + load().filterNot { it.equals(trimmed, ignoreCase = true) }
        return save(next.take(limit))
    }

    fun remove(query: String): List<String> = save(load().filterNot { it == query })

    fun clear(): List<String> = save(emptyList())

    private fun save(items: List<String>): List<String> {
        preferences.edit().putString(shopKey, JSONArray(items).toString()).apply()
        return items
    }

    private companion object {
        const val FILE = "personalization_ui_recent_searches"
    }
}

/** Ключ истории и хранилища виджета: магазин инстанса, иначе запрошенный id, иначе общий. */
internal fun storageKey(sdk: SDK, requestedShopId: String?): String =
    com.personalization.SdkRegistry.shopIdOf(sdk) ?: requestedShopId ?: "default"

/**
 * Загрузчик картинок по умолчанию — Glide уже есть в SDK.
 *
 * Загрузка откладывается до layout: Glide ждёт размер view через pre-draw, а хост на
 * Compose (`AndroidView`) укладывает карточки уже внутри отрисовки кадра, после его
 * pre-draw. Экран после ответа статичен, нового кадра нет — и запрос висел бы вечно.
 * После layout размер известен, и Glide стартует сразу.
 */
internal object SearchImages {
    fun load(imageView: ImageView, url: String?) {
        imageView.tag = url
        if (url.isNullOrEmpty()) {
            Glide.with(imageView).clear(imageView)
            imageView.setImageDrawable(null)
            return
        }
        imageView.doOnLayout { view ->
            // Пока ждали layout, view могла уйти под другой товар.
            if (view.tag == url) Glide.with(view).load(url).into(view as ImageView)
        }
    }
}

/**
 * Товар ответа поиска → данные карточки кита. Строки берутся уже отформатированными
 * сервером (`price_formatted`, `oldprice_formatted`, `discount_formatted`); картинка —
 * `picture` (уменьшенная копия), иначе оригинал.
 */
internal fun Product.toCardProduct(actionText: CharSequence?): PersonalizationProduct {
    // Gson кладёт null в поля без значения даже при non-null типе — читаем через nullable копии.
    val priceText: String? = priceFormatted
    val pictureUrl: String? = picture
    val originalUrl: String? = imageUrl
    val brandName: String? = brand
    val hasOldPrice = (oldPrice ?: 0.0) > price && !oldPriceFormatted.isNullOrEmpty()
    val discountText = discountFormatted?.takeIf { it.isNotEmpty() && hasOldPrice }
        ?: discount?.takeIf { it > 0 && hasOldPrice }?.let { "$it%" }
    return PersonalizationProduct(
        id = id,
        name = name,
        price = priceText?.takeIf { it.isNotEmpty() } ?: "$price $currency",
        imageUrl = pictureUrl?.takeIf { it.isNotEmpty() } ?: originalUrl,
        brand = brandName?.takeIf { it.isNotEmpty() },
        oldPrice = if (hasOldPrice) oldPriceFormatted else null,
        discount = discountText?.let { if (it.startsWith("-")) it else "-$it" },
        actionText = actionText
    )
}
