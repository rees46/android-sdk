package com.personalizatio

import com.personalizatio.Api
import kotlin.concurrent.Volatile

/**
 * Колбек на клик по ссылке.
 * Все методы должны возвращать Boolean: true - если нужно вызвать открытие средствами самой SDK
 */
interface OnLinkClickListener {
    /**
     * Вызывается при клике по обычной ссылке или диплинку
     * @param url String
     * @return boolean
     */
    fun onClick(url: String?): Boolean

    /**
     * Вызывается при клике по товару
     * @param product Product
     * @return boolean
     */
    fun onClick(product: Product?): Boolean
}
