package com.personalization.ui.components

import com.personalization.ui.InternalPersonalizationUiApi

/**
 * Данные товара для карточки и раскладок каталога.
 *
 * Строки уже отформатированы: цена с валютой, оценка с запятой, скидка со знаком —
 * форматирование и локализация остаются за интегратором.
 * Изображение по [imageUrl] компонент не грузит: раскладки отдают хосту
 * `ImageView` через свой `imageLoader`.
 */
@InternalPersonalizationUiApi
data class PersonalizationProduct(
    val id: String,
    val name: CharSequence,
    val price: CharSequence,
    val imageUrl: String? = null,
    val brand: CharSequence? = null,
    val ratingValue: CharSequence? = null,
    val reviews: Int = 0,
    val oldPrice: CharSequence? = null,
    val discount: CharSequence? = null,
    val actionText: CharSequence? = null
)
