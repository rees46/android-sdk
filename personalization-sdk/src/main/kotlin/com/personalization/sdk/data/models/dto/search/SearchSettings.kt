package com.personalization.sdk.data.models.dto.search

data class SearchSettings(
    val redirects: Map<String, String>,
    val suggestionsTitle: String,
    val categoriesTitle: String,
    val brandsTitle: String,
    val filtersTitle: String,
    val itemsTitle: String,
    val showAllTitle: String,
    val lastQueriesTitle: String,
    val lastProductsTitle: String,
    val emptyTitle: String,
    val bookAuthorTitle: String,
    val enableFullSearch: Boolean,
    val appendToBody: Boolean,
    val enableLastQueries: Boolean,
    val enableOldPrice: Boolean,
    val popularLinksTitle: String,
    val popularCategoriesTitle: String,
    val popularBrandsTitle: String,
    val priceFilterName: String,
    val priceFilterFrom: String,
    val priceFilterTo: String,
    val sortByName: String,
    val sortDirDesc: String,
    val sortDirAsc: String,
    val sortByPopular: String,
    val sortByPrice: String,
    val sortByDiscount: String,
    val sortBySalesRate: String,
    val sortByNew: String
)
