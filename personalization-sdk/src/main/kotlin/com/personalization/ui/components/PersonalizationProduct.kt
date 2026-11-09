package com.personalization.ui.components

/**
 * Данные товара для карточки и раскладок каталога.
 *
 * Строки уже отформатированы: цена с валютой, оценка с запятой, скидка со знаком —
 * форматирование и локализация остаются за интегратором.
 * Изображение по [imageUrl] компонент не грузит: раскладки отдают хосту
 * `ImageView` через свой `imageLoader`.
 *
 * [url] и [deeplink] карточка не открывает сама — их получает хост вместе с нажатием,
 * чтобы перейти на товар: [deeplink] ведёт в приложение (`deeplink_android` из ответа),
 * [url] — на страницу товара на сайте.
 */
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
    val actionText: CharSequence? = null,
    val url: String? = null,
    val deeplink: String? = null
)
