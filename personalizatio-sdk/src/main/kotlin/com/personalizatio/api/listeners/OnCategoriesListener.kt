package com.personalizatio.api.listeners

import com.personalizatio.entities.categories.categories.CategoriesEntity
import com.personalizatio.entities.categories.category.CategoryEntity

interface OnCategoriesListener {

    fun onGetCategories(categoriesEntity: CategoriesEntity) {}

    fun onGetCategory(categoryEntity: CategoryEntity) {}
}
