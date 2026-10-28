package com.personalization.ui.widgets

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.personalization.api.responses.category.CategoryResponse
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.PriceRange
import com.personalization.api.responses.search.SearchFilter
import com.personalization.api.responses.search.SearchFullResponse

/**
 * Страница выдачи в общем виде: ответы поиска (`search`) и категории (`category/{id}`)
 * одной формы, и экран выдачи читает из них одно и то же.
 *
 * Фасеты нужны только экрану фильтров, поэтому читаются при его открытии, а не с каждой
 * страницей.
 */
internal class ListingPage(
    val products: List<Product>,
    val total: Int,
    readFacets: () -> ListingFacets
) {
    val facets: ListingFacets by lazy(LazyThreadSafetyMode.NONE, readFacets)
}

/**
 * Фасеты выдачи: диапазон цены, бренды, цвета и размеры, произвольные фасеты магазина.
 * Бренды, цвета и размеры — значение → число товаров с ним, в порядке ответа.
 */
internal class ListingFacets(
    val priceRange: PriceRange?,
    val brands: Map<String, Int>,
    val colors: Map<String, Int>,
    val sizes: Map<String, Int>,
    val filters: Map<String, SearchFilter>
)

internal fun SearchFullResponse.toListingPage(): ListingPage {
    // Gson кладёт null в поля без значения даже при non-null типе — читаем через nullable копии.
    val items: List<Product>? = products
    return ListingPage(items.orEmpty(), productsTotal) {
        val brandList: List<Brand>? = brands
        val range: PriceRange? = priceRange
        ListingFacets(
            priceRange = range,
            brands = counts(brandList.orEmpty()) { it.name to it.count },
            colors = counts(industrialFilters?.colors.orEmpty()) { it.color to it.count },
            sizes = counts(industrialFilters?.fashionSizes.orEmpty()) { it.size to it.count },
            filters = filters.orEmpty()
        )
    }
}

/** У категории нет `industrial_filters`, поэтому цветов и размеров тоже нет. */
internal fun CategoryResponse.toListingPage(): ListingPage {
    val items: List<Product>? = products
    return ListingPage(items.orEmpty(), productsTotal) {
        val brandList: List<Brand>? = brands
        ListingFacets(
            priceRange = priceRange,
            brands = counts(brandList.orEmpty()) { it.name to it.count },
            colors = emptyMap(),
            sizes = emptyMap(),
            filters = parseFacets(filters)
        )
    }
}

/** Значение → число товаров; значения без имени (Gson оставляет null) пропускаются. */
private fun <T> counts(items: List<T>, read: (T) -> Pair<String?, Int>): Map<String, Int> {
    val result = LinkedHashMap<String, Int>()
    items.forEach { item ->
        val (value, count) = read(item)
        if (value != null && value !in result) result[value] = count
    }
    return result
}

/**
 * Сырой `filters` категории → фасеты той же формы, что у поиска:
 * `{"Цвет": {"count": 1405, "values": {"Белый": 172, …}}, …}`. Что не по форме — пропускается.
 */
internal fun parseFacets(json: JsonObject?): Map<String, SearchFilter> {
    val result = LinkedHashMap<String, SearchFilter>()
    json?.entrySet()?.forEach { (name, element) ->
        val facet = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
        val values = facet.get("values")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
        val counts = LinkedHashMap<String, Int>()
        values.entrySet().forEach { (value, count) -> counts[value] = count.intOrZero() }
        result[name] = SearchFilter(count = facet.get("count").intOrZero(), values = counts)
    }
    return result
}

private fun JsonElement?.intOrZero(): Int =
    if (this != null && isJsonPrimitive && asJsonPrimitive.isNumber) asInt else 0

/**
 * Какие произвольные фасеты показать на экране фильтров и в каком порядке.
 *
 * Явный список хоста [wanted] главнее: его фасеты в его порядке. Иначе — фасеты больше чем
 * с одним значением по числу товаров с ними (`count`), по убыванию, первые [max]
 * (`null` или 0 — все): у категории их сотни, приоритета в ответе нет. Фасет с отмеченными
 * значениями виден всегда, чтобы их можно было снять, — на своём месте, если он есть в
 * ответе, иначе в конце.
 */
internal fun visibleFacets(
    available: Map<String, SearchFilter>,
    checked: Map<String, Set<String>>,
    wanted: Set<String>?,
    max: Int?
): List<String> {
    val applied = checked.filterValues { it.isNotEmpty() }.keys
    val shown = if (wanted != null) {
        wanted.filter { it in available || it in applied }
    } else {
        // Сортировка устойчивая: при равном числе товаров — порядок ответа.
        val byCount = available.entries.sortedByDescending { it.value.count }
        val ranked = byCount.filter { valuesOf(it.value).size > 1 }.map { it.key }
        val top = (if (max == null || max <= 0) ranked else ranked.take(max)).toSet()
        byCount.map { it.key }.filter { it in top || it in applied }
    }
    return shown + applied.filter { it !in shown }
}

/** Значения фасета; Gson оставляет null вместо пустого. */
internal fun valuesOf(facet: SearchFilter): Map<String, Int> {
    val values: Map<String, Int>? = facet.values
    return values.orEmpty()
}

/** Цена, введённая в фильтре, числом: «1 000» и «99,5» тоже; не число — `null`. */
internal fun priceNumber(text: String?): Double? =
    text?.filterNot { it.isWhitespace() }?.replace(',', '.')?.toDoubleOrNull()

/**
 * Подпись значения фасета из самого значения: магазины кладут в значение JSON-объект вроде
 * `{"slug": "super_price", "title": "Суперцена", …}`, и тогда подпись — его `title`. Только по
 * формату: строка начинается с `{`, это JSON-объект, в нём непустая строка `title`. Иначе —
 * `null`, и значение показывается как есть. В запрос всё равно уходит исходное значение.
 */
internal fun jsonTitle(value: String): String? {
    val text = value.trim()
    if (!text.startsWith("{")) return null
    val json = runCatching { JsonParser.parseString(text) }.getOrNull()
        ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val title = json.get("title")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString } ?: return null
    return title.asString.trim().takeIf { it.isNotEmpty() }
}
