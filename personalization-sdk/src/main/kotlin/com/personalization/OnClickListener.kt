package com.personalization

/**
 * Callback for clicking on a link.
 * All methods must return Boolean: true - if you need to call opening by means of the SDK itself
 */
interface OnClickListener {
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
     * Called when a product in the story carousel is tapped, after [onClick] has returned true.
     * Decides what the SDK does with that tap:
     *  - return `false` (default) to open the product url in the browser, keeping the story open;
     *  - return `true` to close the story viewer instead of opening the product — for a host that
     *    navigates to the product itself and does not want the SDK to do anything on top.
     *
     * The default is `false`: tapping a product card opens it, which is what a user expects. The
     * previous default (`true`) closed the story and opened nothing.
     *
     * @param product The product that was clicked.
     * @param url The product url (its deeplink when present, otherwise the web url).
     * @return `true` to close the viewer, `false` to open the url in the browser.
     */
    fun onCloseDialogClick(
        product: Product?,
        url: String?
    ): Boolean = false
}
