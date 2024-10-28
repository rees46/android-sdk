package com.personalization

/**
 * Callback for clicking on a link.
 * All methods must return Boolean: true - if you need to call opening by means of the SDK itself
 */
interface OnLinkClickListener {
    /**
     * Called when clicking on a regular link or deep link
     * @param url String
     * @return boolean
     */
    fun onClick(url: String): Boolean = true

    /**
     * Called when clicking on a product
     * @param product Product
     * @return boolean
     */
    fun onClick(product: Product): Boolean = true

    /**
     * Called when clicking on a product.
     *
     * If you want to prevent navigation to the browser when clicking on the product,
     * return false from this method. The callback will provide the product object
     * and its associated URL.
     *
     * @param product The product that was clicked.
     * @param url The URL of the product.
     * @return Boolean indicating whether to navigate to the browser (true) or not (false).
     */
    fun onCloseDialogClick(
        product: Product?,
        url: String?
    ): Boolean = true
}
