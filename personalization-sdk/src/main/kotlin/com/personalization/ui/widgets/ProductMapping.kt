package com.personalization.ui.widgets

import com.personalization.api.responses.product.Product
import com.personalization.ui.components.PersonalizationProduct
import java.math.BigDecimal

/**
 * Товар ответа API → данные карточки кита. Один маппер на все виджеты с данными: товар
 * одной формы приходит в поиске, категории, коллекции, `products`, `products/get` и
 * рекомендациях (`recommends`).
 *
 * Строки берутся уже отформатированными сервером:
 * - цена — `price_formatted`, иначе число с валютой: без экспоненты и хвоста «.0»;
 * - старая цена — `oldprice_formatted`, только когда `oldprice` больше цены;
 * - скидка — только вместе со старой ценой и только при положительном числе скидки
 *   (`discount`, иначе `discount_percent`): текст `discount_formatted`, иначе «число%»,
 *   ровно с одним «-» впереди. Ноль, `true`/`false` и мусор — без бейджа, «-0%» не бывает;
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
    // Числа скидки уже прочитаны терпимо: булево и мусор в ответе — null.
    // `discount_formatted` — запись именно `discount`: при нулевом `discount` и числе из
    // `discount_percent` там «0%», поэтому текст тогда собирается из процента.
    val ownDiscount = discount?.takeIf { it > 0 }
    val discountNumber = (ownDiscount ?: discountPercent?.takeIf { it > 0 })?.takeIf { hasOldPrice }
    val discountText = discountNumber?.let { number ->
        val formatted = discountFormatted?.takeIf { ownDiscount != null }
            ?.trimStart { it == '-' || it.isWhitespace() }
        "-" + (formatted?.takeIf { it.isNotEmpty() } ?: "$number%")
    }
    return PersonalizationProduct(
        id = productId.orEmpty(),
        name = title.orEmpty(),
        price = priceText?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(plainNumber(price), currencyText?.takeIf { it.isNotEmpty() }).joinToString(" "),
        imageUrl = pictureUrl?.takeIf { it.isNotEmpty() } ?: originalUrl?.takeIf { it.isNotEmpty() },
        brand = brandName?.takeIf { it.isNotEmpty() },
        oldPrice = if (hasOldPrice) oldPriceFormatted else null,
        discount = discountText,
        actionText = actionText,
        url = pageUrl?.takeIf { it.isNotEmpty() },
        deeplink = deeplinkAndroid?.takeIf { it.isNotEmpty() }
    )
}

/** `1.6499E7` → `16499000`, `90.0` → `90`, `90.5` → `90.5`. */
private fun plainNumber(value: Double): String =
    if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else value.toString()
