package com.personalization.api.models.products

import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.IndustrialFilters
import com.personalization.api.responses.search.Location
import com.personalization.api.responses.search.PriceRange

/*
 * The answers of `sdk.products` and the facets they share with `sdk.search`. In each, the list the answer
 * is about ([ProductsListResponse.products]) is never null; any other field the answer leaves out is null.
 */

/**
 * A page of the catalog's listing (`sdk.products.getList`): the products, and the facets of everything the
 * filters found.
 *
 * @property productsTotal how many products the filters found, on every page.
 * @property filters the facets of the products' parameters: a parameter's name → its facet.
 * @property industrialFilters colours and sizes; only a search sends them.
 * @property priceRange the lowest and the highest price found.
 * @property priceRanges how many products fall into each price interval.
 */
data class ProductsListResponse(
    val productsTotal: Int? = null,
    val products: List<Product>,
    val categories: List<ProductCategory>? = null,
    val brands: List<Brand>? = null,
    val locations: List<Location>? = null,
    val filters: Map<String, SearchFilter>? = null,
    val industrialFilters: IndustrialFilters? = null,
    val priceRange: PriceRange? = null,
    val priceRanges: List<PriceInterval>? = null,
    val priceMedian: Double? = null,
)

/** A category's screen (`sdk.products.getCategory`): its products and its facets; fields as in [ProductsListResponse]. */
data class CategoryResponse(
    val productsTotal: Int? = null,
    val products: List<Product>,
    val categories: List<ProductCategory>? = null,
    val brands: List<Brand>? = null,
    val filters: Map<String, SearchFilter>? = null,
    val priceRange: PriceRange? = null,
    val priceRanges: List<PriceInterval>? = null,
    val priceMedian: Double? = null,
)

/**
 * A facet: one parameter of the products in a filter's sidebar.
 *
 * @property count how many products carry the parameter.
 * @property priority where the facet goes among the others.
 * @property values a facet of values: a value → how many products carry it.
 * @property min the lowest value of a numeric facet (`ranges.min`).
 * @property max the highest value of a numeric facet (`ranges.max`).
 */
data class SearchFilter(
    val count: Int? = null,
    val priority: Int? = null,
    val values: Map<String, Int>? = null,
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * A price interval of the facets and how many products fall into it.
 *
 * @property from the interval's lower bound; 0 when the answer has none.
 * @property to the interval's upper bound; null for an interval without one.
 */
data class PriceInterval(
    val from: Double = 0.0,
    val to: Double? = null,
    val count: Int? = null,
)
