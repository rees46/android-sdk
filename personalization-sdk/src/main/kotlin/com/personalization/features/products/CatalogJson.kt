package com.personalization.features.products

import com.personalization.api.models.products.PriceInterval
import com.personalization.api.models.products.Product
import com.personalization.api.models.products.ProductCategory
import com.personalization.api.models.products.SearchFilter
import com.personalization.api.models.products.SortBy
import com.personalization.api.models.products.SortDirection
import com.personalization.api.responses.products.product.ProductParam
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.ColorFilter
import com.personalization.api.responses.search.IndustrialFilters
import com.personalization.api.responses.search.Location
import com.personalization.api.responses.search.PopularItem
import com.personalization.api.responses.search.PriceRange
import com.personalization.api.responses.search.SizeFilter
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.putFilters
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.putList
import com.personalization.core.internal.putNumber
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requirePriceRange
import com.personalization.core.internal.strings
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.valueOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reading the catalog's answers into the models of `products`, `collections`, `recommendations` and
 * `search` (`contract/products.md`), tolerantly: a number may come as a string, a field missing or of the
 * wrong kind is null, a product's optional text that is empty is null too, and an element of a list that
 * lacks what its model cannot do without is left out.
 */
internal object CatalogJson {

    /**
     * A product; null without one of the fields every product has: an id, `name`, `url`, `price` and
     * `currency`. [uniqidFirst] reads the id from `uniqid` before `id`, as the product card needs.
     */
    fun product(json: JSONObject, uniqidFirst: Boolean = false): Product? {
        val id = (if (uniqidFirst) json.nonBlankStringOrNull("uniqid") else null)
            ?: json.nonBlankStringOrNull("id")
            ?: return null
        return Product(
            id = id,
            groupId = json.nonBlankStringOrNull("group_id"),
            name = json.stringOrNull("name") ?: return null,
            brand = json.textOrNull("brand"),
            model = json.textOrNull("model"),
            description = json.textOrNull("description"),
            barcode = json.textOrNull("barcode"),
            url = json.stringOrNull("url") ?: return null,
            deeplinkAndroid = json.textOrNull("deeplink_android"),
            deeplinkIos = json.textOrNull("deeplink_ios"),
            imageUrl = json.textOrNull("image_url"),
            picture = json.textOrNull("picture"),
            resizedImages = json.mapOrNull("image_url_resized")?.let(::stringMap),
            price = json.doubleOrNull("price") ?: return null,
            priceFormatted = json.textOrNull("price_formatted"),
            priceFull = json.doubleOrNull("price_full"),
            priceFullFormatted = json.textOrNull("price_full_formatted"),
            // The server sends 0 for a product without an old price.
            oldPrice = json.doubleOrNull("oldprice")?.takeIf { it != 0.0 },
            oldPriceFormatted = json.textOrNull("oldprice_formatted"),
            oldPriceFull = json.doubleOrNull("oldprice_full")?.takeIf { it != 0.0 },
            oldPriceFullFormatted = json.textOrNull("oldprice_full_formatted"),
            currency = json.stringOrNull("currency") ?: return null,
            // `discount` is a flag in the product card (never a number) and the percent elsewhere.
            discountPercent = json.intOrNull("discount_percent") ?: json.intOrNull("discount"),
            discountFormatted = json.textOrNull("discount_formatted"),
            salesRate = json.intOrNull("sales_rate"),
            relativeSalesRate = json.doubleOrNull("relative_sales_rate"),
            rating = json.intOrNull("rating"),
            isNew = json.booleanOrNull("is_new") ?: false,
            stockQuantity = json.intOrNull("stock_quantity"),
            categoryIds = json.arrayOrNull("category_ids")?.strings(),
            categories = json.arrayOrNull("categories")?.mapObjects(::category),
            params = params(json),
            locationIds = json.arrayOrNull("location_ids")?.strings(),
        )
    }

    /** [key] as a string; null for an empty one as for a missing one: a product's optional text has a value or none. */
    private fun JSONObject.textOrNull(key: String): String? = stringOrNull(key)?.takeIf { it.isNotEmpty() }

    /** The products of [key]; an element that is not a whole product is left out. Null without the list. */
    fun products(json: JSONObject, key: String = "products"): List<Product>? =
        json.arrayOrNull(key)?.mapObjects { product(it) }

    /** A category of a product (`parent_id`, `level`) or of the facets (`parent`, `count`, `alias`); null without an id. */
    fun category(json: JSONObject): ProductCategory? {
        val id = json.nonBlankStringOrNull("id") ?: return null
        return ProductCategory(
            id = id,
            name = json.stringOrNull("name"),
            url = json.stringOrNull("url"),
            parentId = json.nonBlankStringOrNull("parent_id") ?: json.nonBlankStringOrNull("parent"),
            level = json.intOrNull("level"),
            count = json.intOrNull("count"),
            alias = json.stringOrNull("alias"),
        )
    }

    /**
     * A product's parameters. The lists of products send them as `[{"key", "values"}]`; the product card
     * (`products/get`) as an object of the parameter's name → `{"value": [...], "searchable"}`, in the
     * catalog's order. Null without `params`.
     */
    private fun params(json: JSONObject): List<ProductParam>? = when (val params = json.valueOrNull("params")) {
        is JSONArray -> params.mapObjects(::param)
        is JSONObject -> params.keys().asSequence().mapNotNull { key ->
            if (key.isBlank()) return@mapNotNull null
            val values = when (val param = params.valueOrNull(key)) {
                is JSONObject -> param.valuesOf("value") ?: param.valuesOf("values")
                else -> params.valuesOf(key)
            }
            ProductParam(key = key, values = values.orEmpty())
        }.toList()
        else -> null
    }

    /** A product's parameter; its values as strings, numbers among them. Null without a key. */
    private fun param(json: JSONObject): ProductParam? {
        val key = json.nonBlankStringOrNull("key") ?: return null
        return ProductParam(key = key, values = json.valuesOf("values").orEmpty())
    }

    /** The values of [key]: a list's strings and numbers as strings, or one value as a list of it; null without them. */
    private fun JSONObject.valuesOf(key: String): List<String>? =
        arrayOrNull(key)?.strings() ?: stringOrNull(key)?.let(::listOf)

    fun categories(json: JSONObject): List<ProductCategory>? = json.arrayOrNull("categories")?.mapObjects(::category)

    /** The brands with their counts; a brand without a name or a count is left out. */
    fun brands(json: JSONObject): List<Brand>? = json.arrayOrNull("brands")?.mapObjects { brand ->
        Brand(count = brand.intOrNull("count") ?: return@mapObjects null, name = brand.stringOrNull("name") ?: return@mapObjects null)
    }

    /** The locations; one without an id or a name is left out. */
    fun locations(json: JSONObject): List<Location>? = json.arrayOrNull("locations")?.mapObjects { location ->
        Location(
            id = location.nonBlankStringOrNull("id") ?: return@mapObjects null,
            name = location.stringOrNull("name") ?: return@mapObjects null,
            type = location.stringOrNull("type"),
        )
    }

    /** The facets of the products' parameters: a parameter's name → its facet. */
    fun filters(json: JSONObject): Map<String, SearchFilter>? = json.mapOrNull("filters")?.let { filters ->
        filters.keys().asSequence().mapNotNull { name ->
            filters.objectOrNull(name)?.let { name to searchFilter(it) }
        }.toMap()
    }

    private fun searchFilter(json: JSONObject): SearchFilter {
        val ranges = json.objectOrNull("ranges")
        return SearchFilter(
            count = json.intOrNull("count"),
            priority = json.intOrNull("priority"),
            values = json.mapOrNull("values")?.let { values ->
                values.keys().asSequence().mapNotNull { value -> values.intOrNull(value)?.let { value to it } }.toMap()
            },
            min = ranges?.doubleOrNull("min"),
            max = ranges?.doubleOrNull("max"),
        )
    }

    /** Colours and sizes with their counts; an element without its value or count is left out. */
    fun industrialFilters(json: JSONObject): IndustrialFilters? = json.objectOrNull("industrial_filters")?.let { filters ->
        IndustrialFilters(
            colors = filters.arrayOrNull("colors")?.mapObjects { color ->
                ColorFilter(
                    color = color.stringOrNull("color") ?: return@mapObjects null,
                    count = color.intOrNull("count") ?: return@mapObjects null,
                )
            },
            fashionSizes = filters.arrayOrNull("fashion_sizes")?.mapObjects { size ->
                SizeFilter(
                    size = size.stringOrNull("size") ?: return@mapObjects null,
                    count = size.intOrNull("count") ?: return@mapObjects null,
                )
            },
        )
    }

    /** The lowest and the highest price; null unless both are there. */
    fun priceRange(json: JSONObject): PriceRange? {
        val range = json.objectOrNull("price_range") ?: return null
        return PriceRange(max = range.doubleOrNull("max") ?: return null, min = range.doubleOrNull("min") ?: return null)
    }

    fun priceRanges(json: JSONObject): List<PriceInterval>? = json.arrayOrNull("price_ranges")?.mapObjects { interval ->
        PriceInterval(
            from = interval.doubleOrNull("from") ?: 0.0,
            to = interval.doubleOrNull("to"),
            count = interval.intOrNull("count"),
        )
    }

    /** Popular categories, brands or links; one without a name or a link is left out. */
    fun popularItems(json: JSONObject, key: String): List<PopularItem>? = json.arrayOrNull(key)?.mapObjects { item ->
        PopularItem(name = item.stringOrNull("name") ?: return@mapObjects null, url = item.stringOrNull("url") ?: return@mapObjects null)
    }

    /**
     * The request's fields of a listing (`/products`), for `getList` and `getCategory`, after their checks:
     * [SortBy.RELEVANCE] is only for a search.
     */
    fun listingParams(
        categoryIds: List<String>?,
        brands: List<String>?,
        merchants: List<String>?,
        locations: List<String>?,
        priceMin: Double?,
        priceMax: Double?,
        filters: Map<String, List<String>>?,
        sortBy: SortBy?,
        sortDir: SortDirection?,
        limit: Int?,
        page: Int?,
    ): JSONObject {
        requirePriceRange(priceMin, priceMax)
        requireAtLeast(limit, 1, "limit")
        requireAtLeast(page, 1, "page")
        if (sortBy == SortBy.RELEVANCE) invalidArgument("sortBy relevance is only for search.full")
        return JSONObject()
            .putList("categories", categoryIds)
            .putList("brands", brands)
            .putList("merchants", merchants)
            .putList("locations", locations)
            .putNumber("price_min", priceMin)
            .putNumber("price_max", priceMax)
            .putFilters("filters", filters)
            .putIfNotNull("sort_by", sortBy?.value)
            .putIfNotNull("sort_dir", sortDir?.value)
            .putIfNotNull("limit", limit)
            .putIfNotNull("page", page)
    }

    /** The values of an object as strings; a value of another kind is left out. */
    private fun stringMap(json: JSONObject): Map<String, String> =
        json.keys().asSequence().mapNotNull { key -> json.stringOrNull(key)?.let { key to it } }.toMap()

    /**
     * [key] as an object; an empty array, which the server sends for an empty object, is an empty one.
     * Null when missing or of another kind.
     */
    private fun JSONObject.mapOrNull(key: String): JSONObject? = when (val value = valueOrNull(key)) {
        is JSONObject -> value
        is JSONArray -> JSONObject().takeIf { value.length() == 0 }
        else -> null
    }
}
