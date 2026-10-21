package com.personalization.ui.widgets

import com.personalization.api.responses.product.Product
import com.personalization.ui.components.PersonalizationProduct

/**
 * Товар ответа API → данные карточки кита. Один маппер на все виджеты с данными: товар
 * одной формы приходит в поиске, категории, коллекции, `products`, `products/get` и
 * рекомендациях (`recommends`).
 *
 * Строки берутся уже отформатированными сервером:
 * - цена — `price_formatted`, иначе число с валютой;
 * - старая цена — `oldprice_formatted`, только когда `oldprice` больше цены;
 * - скидка — только вместе со старой ценой: `discount_formatted`, иначе `discount%`,
 *   со знаком «-»;
 * - картинка — `picture` (уменьшенная копия), иначе оригинал `image_url`;
 * - ссылка в приложение — `deeplink_android`, на сайт — `url`.
 *
 * Рейтинга в API нет, поэтому у карточки его нет тоже.
 */
internal fun Product.toPersonalizationProduct(actionText: CharSequence? = null): PersonalizationProduct {
    // Gson кладёт null в поля без значения даже при non-null типе — читаем через nullable копии.
    val productId: String? = id
    val title: String? = name
    val priceText: String? = priceFormatted
    val currencyText: String? = currency
    val pictureUrl: String? = picture
    val originalUrl: String? = imageUrl
    val brandName: String? = brand
    val pageUrl: String? = url
    val hasOldPrice = (oldPrice ?: 0.0) > price && !oldPriceFormatted.isNullOrEmpty()
    val discountText = discountFormatted?.takeIf { it.isNotEmpty() && hasOldPrice }
        ?: discount?.takeIf { it > 0 && hasOldPrice }?.let { "$it%" }
    return PersonalizationProduct(
        id = productId.orEmpty(),
        name = title.orEmpty(),
        price = priceText?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(price.toString(), currencyText?.takeIf { it.isNotEmpty() }).joinToString(" "),
        imageUrl = pictureUrl?.takeIf { it.isNotEmpty() } ?: originalUrl?.takeIf { it.isNotEmpty() },
        brand = brandName?.takeIf { it.isNotEmpty() },
        oldPrice = if (hasOldPrice) oldPriceFormatted else null,
        discount = discountText?.let { if (it.startsWith("-")) it else "-$it" },
        actionText = actionText,
        url = pageUrl?.takeIf { it.isNotEmpty() },
        deeplink = deeplinkAndroid?.takeIf { it.isNotEmpty() }
    )
}
