package com.personalization.api.models.products

import com.personalization.api.responses.products.product.ProductParam

/**
 * A product in every answer of the catalog's namespaces: the product card (`sdk.products.getInfo`), a
 * listing, a category, a collection, recommendations and search. [id], [name], [url], [price] and
 * [currency] are always there; any other field the answer leaves out, or sends as an empty string, is null,
 * never an empty string or 0.
 *
 * @property id the shop's own product id (`uniqid` in the product card, `id` elsewhere).
 * @property groupId the group of the product's variants (`group_id`); for a product without a group the
 * server sends a value of its own, such as `_37_`.
 * @property imageUrl the original picture (`image_url`).
 * @property picture the picture in the size the answer was asked for (`picture`).
 * @property resizedImages the picture in each size the server keeps (`image_url_resized`): `"120"` …
 * `"520"` and `"original"` → its link.
 * @property priceFull the price with its fraction (`price_full`).
 * @property oldPrice the price before the discount (`oldprice`); null, not 0, without one.
 * @property oldPriceFull [oldPrice] with its fraction (`oldprice_full`); null, not 0, without one.
 * @property discountPercent the discount in percent (`discount_percent`, or `discount` when that is a number).
 * @property rating the product's rating; the server sends it only when it is not 0.
 * @property isNew whether the shop marks the product as new; the server sends it only when it is.
 * @property locationIds where the product is in stock (`location_ids`), or `["global"]`; only when the
 * request asked for it (`withLocations` of `sdk.recommendations.get`).
 */
data class Product(
    val id: String,
    val groupId: String? = null,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val description: String? = null,
    val barcode: String? = null,
    val url: String,
    val deeplinkAndroid: String? = null,
    val deeplinkIos: String? = null,
    val imageUrl: String? = null,
    val picture: String? = null,
    val resizedImages: Map<String, String>? = null,
    val price: Double,
    val priceFormatted: String? = null,
    val priceFull: Double? = null,
    val priceFullFormatted: String? = null,
    val oldPrice: Double? = null,
    val oldPriceFormatted: String? = null,
    val oldPriceFull: Double? = null,
    val oldPriceFullFormatted: String? = null,
    val currency: String,
    val discountPercent: Int? = null,
    val discountFormatted: String? = null,
    val salesRate: Int? = null,
    val relativeSalesRate: Double? = null,
    val rating: Int? = null,
    val isNew: Boolean = false,
    val stockQuantity: Int? = null,
    val categoryIds: List<String>? = null,
    val categories: List<ProductCategory>? = null,
    val params: List<ProductParam>? = null,
    val locationIds: List<String>? = null,
)

/**
 * A product's category, and a category among the facets of a listing or a search.
 *
 * @property parentId the parent category (`parent_id`; `parent` among the facets).
 * @property level how deep the category is in the tree; only in a product's categories.
 * @property count how many products the listing or the search found in the category; only among the facets.
 * @property alias only among the facets.
 */
data class ProductCategory(
    val id: String,
    val name: String? = null,
    val url: String? = null,
    val parentId: String? = null,
    val level: Int? = null,
    val count: Int? = null,
    val alias: String? = null,
)
